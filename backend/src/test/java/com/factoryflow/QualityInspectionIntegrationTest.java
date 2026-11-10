package com.factoryflow;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class QualityInspectionIntegrationTest {
    @Container
    static final MySQLContainer mysql = new MySQLContainer("mysql:8.4")
            .withDatabaseName("factoryflow_quality_test")
            .withUsername("test").withPassword("test");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
    }

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    @Test
    void passingQuantityIsTheOnlyQuantityPutIntoFinishedInventory() throws Exception {
        long order = awaitingQcOrder(50);
        mvc.perform(inspect(order,47,3))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.passedQty").value(47))
                .andExpect(jsonPath("$.data.failedQty").value(3));
        assertEquals(47L, count("SELECT COALESCE(SUM(on_hand_qty),0) FROM inventory_lots WHERE lot_code=?",lotCode(order)));
        assertEquals(1L, count("SELECT COUNT(*) FROM stock_movements WHERE movement_type='FINISHED_RECEIPT' AND reference_id=(SELECT id FROM quality_inspections WHERE work_order_id=?)",order));
        assertEquals("COMPLETED",jdbc.queryForObject("SELECT status FROM work_orders WHERE id=?",String.class,order));
        mvc.perform(get("/api/v1/work-orders/{id}/inspection",order))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.passedQty").value(47));
    }

    @Test
    void allRejectedUnitsMustNotProduceZeroQuantityLot() throws Exception {
        long order = awaitingQcOrder(5);
        mvc.perform(inspect(order,0,5)).andExpect(status().isCreated());
        assertEquals(0L,count("SELECT COUNT(*) FROM inventory_lots WHERE lot_code=?",lotCode(order)));
        assertEquals("COMPLETED",jdbc.queryForObject("SELECT status FROM work_orders WHERE id=?",String.class,order));
        assertEquals(1L,count("SELECT COUNT(*) FROM quality_inspections WHERE work_order_id=?",order));
    }

    @Test
    void mismatchedOrNegativeQuantityMustNotMutateDatabase() throws Exception {
        long order = awaitingQcOrder(5);
        mvc.perform(inspect(order,4,0)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("QC_QUANTITY_MISMATCH"));
        mvc.perform(inspect(order,-1,6)).andExpect(status().isBadRequest());
        mvc.perform(inspect(order,Long.MAX_VALUE,1)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("QC_QUANTITY_OVERFLOW"));
        assertEquals(0L,count("SELECT COUNT(*) FROM quality_inspections WHERE work_order_id=?",order));
        assertEquals("AWAITING_QC",jdbc.queryForObject("SELECT status FROM work_orders WHERE id=?",String.class,order));
    }

    @Test
    void repeatedInspectionMustBeRejectedWithoutDoubleCounting() throws Exception {
        long order = awaitingQcOrder(4);
        mvc.perform(inspect(order,3,1)).andExpect(status().isCreated());
        mvc.perform(inspect(order,3,1)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("STATE_CONFLICT"));
        assertEquals(3L,count("SELECT COALESCE(SUM(on_hand_qty),0) FROM inventory_lots WHERE lot_code=?",lotCode(order)));
        assertEquals(1L,count("SELECT COUNT(*) FROM quality_inspections WHERE work_order_id=?",order));
    }

    @Test
    void simultaneousInspectionsMustRecordOnlyOneResult() throws Exception {
        long order = awaitingQcOrder(10);
        int[] results = concurrently(() -> mvc.perform(inspect(order,7,3)).andReturn().getResponse().getStatus());
        assertEquals(1, countStatus(results,201));
        assertEquals(1, countStatus(results,409));
        assertEquals(7L,count("SELECT COALESCE(SUM(on_hand_qty),0) FROM inventory_lots WHERE lot_code=?",lotCode(order)));
        assertEquals(1L,count("SELECT COUNT(*) FROM quality_inspections WHERE work_order_id=?",order));
    }

    @Test
    void lotUniqueCollisionMustRollbackInspectionAndOrderTransition() throws Exception {
        long order = awaitingQcOrder(10);
        long itemId=jdbc.queryForObject("SELECT finished_item_id FROM work_orders WHERE id=?",Long.class,order);
        jdbc.update("INSERT INTO inventory_lots(item_id,lot_code,source_type,cumulative_in_qty,on_hand_qty,reserved_qty) VALUES(?,?,'QC_PASS',1,1,0)",
                itemId,lotCode(order));
        mvc.perform(inspect(order,8,2)).andExpect(status().isInternalServerError());
        assertEquals(0L,count("SELECT COUNT(*) FROM quality_inspections WHERE work_order_id=?",order));
        assertEquals("AWAITING_QC",jdbc.queryForObject("SELECT status FROM work_orders WHERE id=?",String.class,order));
        assertEquals(1L,count("SELECT SUM(on_hand_qty) FROM inventory_lots WHERE lot_code=?",lotCode(order)));
    }

    private long awaitingQcOrder(long planned) {
        String sku="FG-"+UUID.randomUUID();
        jdbc.update("INSERT INTO items(sku,name,item_type,unit,active) VALUES(?,'Finished','FINISHED_GOOD','EA',1)",sku);
        long item=jdbc.queryForObject("SELECT id FROM items WHERE sku=?",Long.class,sku);
        jdbc.update("INSERT INTO boms(finished_item_id) VALUES(?)",item);
        long bom=jdbc.queryForObject("SELECT id FROM boms WHERE finished_item_id=?",Long.class,item);
        String key="QC-"+UUID.randomUUID();
        jdbc.update("""
            INSERT INTO work_orders(order_no,client_request_id,request_hash,finished_item_id,bom_id,planned_qty,status)
            VALUES(?,?,'0000000000000000000000000000000000000000000000000000000000000000',?, ?, ?, 'AWAITING_QC')
            """,key,key,item,bom,planned);
        return jdbc.queryForObject("SELECT id FROM work_orders WHERE client_request_id=?",Long.class,key);
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder inspect(long order,long passed,long failed) {
        return post("/api/v1/work-orders/{id}/inspection",order)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"passedQty\":%d,\"failedQty\":%d}".formatted(passed,failed));
    }

    private String lotCode(long order) { return "FG-WO-"+order; }

    private long count(String sql,Object... args) {
        Long value=jdbc.queryForObject(sql,Long.class,args);
        return value==null?0:value;
    }

    private int[] concurrently(Callable<Integer> job) throws Exception {
        ExecutorService executor=Executors.newFixedThreadPool(2);
        CountDownLatch ready=new CountDownLatch(2);
        CountDownLatch start=new CountDownLatch(1);
        try {
            List<Future<Integer>> results=new ArrayList<>();
            for(int i=0;i<2;i++) results.add(executor.submit(()->{
                ready.countDown();
                if(!start.await(15,TimeUnit.SECONDS))throw new AssertionError("timeout");
                return job.call();
            }));
            assertTrue(ready.await(15,TimeUnit.SECONDS));
            start.countDown();
            return new int[]{results.get(0).get(40,TimeUnit.SECONDS),results.get(1).get(40,TimeUnit.SECONDS)};
        } finally { executor.shutdownNow(); }
    }

    private int countStatus(int[] statuses,int expected) {
        int value=0;
        for(int code:statuses) if(code==expected)value++;
        return value;
    }
}
