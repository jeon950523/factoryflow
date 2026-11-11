package com.factoryflow;

import com.factoryflow.item.ItemRepository;
import java.util.UUID;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
class WorkOrderProductionIntegrationTest {
    @Container
    static final MySQLContainer mysql = new MySQLContainer("mysql:8.4")
            .withDatabaseName("factoryflow_production_test")
            .withUsername("test").withPassword("test");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
    }

    @Autowired MockMvc mvc;
    @Autowired ItemRepository items;
    @Autowired JdbcTemplate jdbc;

    @Test
    void startConsumesOnlyOwnReservationAndFinishWaitsForQualityControl() throws Exception {
        long raw = createItem("RAW_MATERIAL");
        long finished = createItem("FINISHED_GOOD");
        createBom(finished, raw, null);
        receive(raw, 20);
        long first = createOrder(finished, 3); // 6 units
        long second = createOrder(finished, 2); // 4 units
        command(first,"reserve",200);
        command(second,"reserve",200);
        assertEquals(10L, query("SELECT reserved_qty FROM inventory_lots WHERE item_id=?", raw));

        mvc.perform(postCommand(first,"start"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("IN_PROGRESS"));
        assertEquals(14L, query("SELECT on_hand_qty FROM inventory_lots WHERE item_id=?", raw));
        assertEquals(4L, query("SELECT reserved_qty FROM inventory_lots WHERE item_id=?", raw));
        assertEquals(6L, query("SELECT quantity FROM stock_movements WHERE reference_type='WORK_ORDER' AND reference_id=?", first));
        assertEquals(1L, query("SELECT COUNT(*) FROM material_reservations WHERE work_order_id=? AND status='CONSUMED'", first));
        assertEquals(1L, query("SELECT COUNT(*) FROM material_reservations WHERE work_order_id=? AND status='RESERVED'", second));

        mvc.perform(postCommand(first,"finish"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("AWAITING_QC"));
        assertEquals(0L, query("SELECT COUNT(*) FROM inventory_lots WHERE item_id=?", finished));
        mvc.perform(postCommand(first,"start")).andExpect(status().isConflict());
        mvc.perform(postCommand(first,"finish")).andExpect(status().isConflict());
        mvc.perform(postCommand(first,"cancel")).andExpect(status().isConflict());
    }

    @Test
    void multipleRawMaterialLotsProduceIndividualConsumptionAuditRecords() throws Exception {
        long sheet = createItem("RAW_MATERIAL");
        long bolt = createItem("RAW_MATERIAL");
        long finished = createItem("FINISHED_GOOD");
        createBom(finished, sheet, bolt);
        receive(sheet, 1);
        receive(sheet, 1);
        receive(bolt, 10);
        long order = createOrder(finished, 2); // sheet 2 + bolt 4
        command(order,"reserve",200);
        command(order,"start",200);
        assertEquals(3L, query("SELECT COUNT(*) FROM stock_movements WHERE reference_type='WORK_ORDER' AND reference_id=?",order));
        assertEquals(2L, query("SELECT SUM(quantity) FROM stock_movements WHERE movement_type='RAW_CONSUMPTION' AND item_id=?",sheet));
        assertEquals(4L, query("SELECT SUM(quantity) FROM stock_movements WHERE movement_type='RAW_CONSUMPTION' AND item_id=?",bolt));
        assertEquals(0L, query("SELECT SUM(reserved_qty) FROM inventory_lots WHERE item_id IN (?,?)",sheet,bolt));
        assertEquals(3L, query("SELECT COUNT(*) FROM material_reservations WHERE work_order_id=? AND status='CONSUMED'",order));
    }

    @Test
    void invalidStateCommandsMustNotConsumeOrMoveStock() throws Exception {
        long raw = createItem("RAW_MATERIAL");
        long finished = createItem("FINISHED_GOOD");
        createBom(finished,raw,null);
        receive(raw,12);
        long order = createOrder(finished,3);
        mvc.perform(postCommand(order,"start"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.errorCode").value("STATE_CONFLICT"));
        command(order,"reserve",200);
        mvc.perform(postCommand(order,"finish"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.errorCode").value("STATE_CONFLICT"));
        mvc.perform(postCommand(999999999L,"start")).andExpect(status().isNotFound());
        assertEquals(12L, query("SELECT on_hand_qty FROM inventory_lots WHERE item_id=?",raw));
        assertEquals(6L, query("SELECT reserved_qty FROM inventory_lots WHERE item_id=?",raw));
        assertEquals(0L, query("SELECT COUNT(*) FROM stock_movements WHERE reference_type='WORK_ORDER' AND reference_id=?",order));
    }

    @Test
    void failedMovementInsertMustRollbackLotConsumptionAndOrderState() throws Exception {
        long raw = createItem("RAW_MATERIAL");
        long finished = createItem("FINISHED_GOOD");
        createBom(finished,raw,null);
        receive(raw,10);
        long order = createOrder(finished,3);
        command(order,"reserve",200);
        long lot = jdbc.queryForObject("SELECT id FROM inventory_lots WHERE item_id=?",Long.class,raw);
        // 강제로 동일 유니크키의 이동 이력을 먼저 기록해 start 시 DB 중복 충돌을 유도.
        jdbc.update("""
                INSERT INTO stock_movements(inventory_lot_id,item_id,movement_type,quantity,reference_type,reference_id)
                VALUES(?,?,'RAW_CONSUMPTION',1,'WORK_ORDER',?)
                """,lot,raw,order);
        mvc.perform(postCommand(order,"start")).andExpect(status().isInternalServerError());
        assertEquals(10L, query("SELECT on_hand_qty FROM inventory_lots WHERE item_id=?",raw));
        assertEquals(6L, query("SELECT reserved_qty FROM inventory_lots WHERE item_id=?",raw));
        assertEquals(1L, query("SELECT COUNT(*) FROM material_reservations WHERE work_order_id=? AND status='RESERVED'",order));
        assertEquals("RESERVED",jdbc.queryForObject("SELECT status FROM work_orders WHERE id=?",String.class,order));
        assertEquals(1L, query("SELECT COUNT(*) FROM stock_movements WHERE reference_type='WORK_ORDER' AND reference_id=?",order));
    }

    @Test
    void simultaneousStartOnSameOrderMustConsumeExactlyOnce() throws Exception {
        long raw = createItem("RAW_MATERIAL");
        long finished = createItem("FINISHED_GOOD");
        createBom(finished,raw,null);
        receive(raw,20);
        long order = createOrder(finished,4);
        command(order,"reserve",200);
        int[] results = concurrently(() -> mvc.perform(postCommand(order,"start"))
                .andReturn().getResponse().getStatus());
        assertEquals(1, count(results,200));
        assertEquals(1, count(results,409));
        assertEquals(12L, query("SELECT on_hand_qty FROM inventory_lots WHERE item_id=?",raw));
        assertEquals(0L, query("SELECT reserved_qty FROM inventory_lots WHERE item_id=?",raw));
        assertEquals(1L, query("SELECT COUNT(*) FROM stock_movements WHERE reference_type='WORK_ORDER' AND reference_id=?",order));
    }

    private long createItem(String type) throws Exception {
        String sku = (type.equals("RAW_MATERIAL")?"RM-":"FG-") + random();
        mvc.perform(post("/api/v1/items").contentType(MediaType.APPLICATION_JSON)
                .content("{\"sku\":\"%s\",\"name\":\"실험 품목\",\"itemType\":\"%s\"}".formatted(sku,type)))
                .andExpect(status().isCreated());
        return items.findBySku(sku).orElseThrow().getId();
    }

    private void createBom(long finished,long first,Long second) throws Exception {
        String components= second==null
                ? "[{\"rawItemId\":%d,\"qtyPerUnit\":2}]".formatted(first)
                : "[{\"rawItemId\":%d,\"qtyPerUnit\":1},{\"rawItemId\":%d,\"qtyPerUnit\":2}]".formatted(first,second);
        mvc.perform(post("/api/v1/boms").contentType(MediaType.APPLICATION_JSON)
                .content("{\"finishedItemId\":%d,\"components\":%s}".formatted(finished,components)))
                .andExpect(status().isCreated());
    }

    private void receive(long itemId,long qty) throws Exception {
        mvc.perform(post("/api/v1/stock-receipts").contentType(MediaType.APPLICATION_JSON)
                .content("{\"requestKey\":\"REC-%s\",\"itemId\":%d,\"lotCode\":\"LOT-%s\",\"quantity\":%d}"
                        .formatted(random(),itemId,random(),qty)))
                .andExpect(status().isCreated());
    }

    private long createOrder(long finished,long qty) throws Exception {
        String key="WO-"+random();
        mvc.perform(post("/api/v1/work-orders").contentType(MediaType.APPLICATION_JSON)
                .content("{\"clientRequestId\":\"%s\",\"finishedItemId\":%d,\"plannedQty\":%d}".formatted(key,finished,qty)))
                .andExpect(status().isCreated());
        return jdbc.queryForObject("SELECT id FROM work_orders WHERE client_request_id=?",Long.class,key);
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder postCommand(long id,String operation) {
        return post("/api/v1/work-orders/{id}/{operation}",id,operation);
    }

    private void command(long order,String operation,int httpStatus) throws Exception {
        mvc.perform(postCommand(order,operation)).andExpect(status().is(httpStatus));
    }

    private long query(String sql,Object... args) {
        Long result=jdbc.queryForObject(sql,Long.class,args);
        return result==null?0:result;
    }

    private int[] concurrently(Callable<Integer> request) throws Exception {
        ExecutorService executor=Executors.newFixedThreadPool(2);
        CountDownLatch ready=new CountDownLatch(2);
        CountDownLatch start=new CountDownLatch(1);
        try {
            List<Future<Integer>> futures=new ArrayList<>();
            for(int i=0;i<2;i++) futures.add(executor.submit(()->{
                ready.countDown();
                if(!start.await(10,TimeUnit.SECONDS))throw new AssertionError("Latch timeout");
                return request.call();
            }));
            assertTrue(ready.await(10,TimeUnit.SECONDS));
            start.countDown();
            return new int[]{futures.get(0).get(30,TimeUnit.SECONDS),futures.get(1).get(30,TimeUnit.SECONDS)};
        } finally {executor.shutdownNow();}
    }

    private int count(int[] values,int target) {
        int total=0;
        for(int value:values)if(value==target)total++;
        return total;
    }

    private String random(){return UUID.randomUUID().toString().substring(0,8).toUpperCase();}
}
