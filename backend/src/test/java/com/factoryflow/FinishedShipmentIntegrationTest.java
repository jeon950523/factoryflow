package com.factoryflow;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
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
class FinishedShipmentIntegrationTest {
    @Container
    static final MySQLContainer mysql = new MySQLContainer("mysql:8.4")
            .withDatabaseName("factoryflow_shipment_test")
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
    void shippingUsesOldestAcceptedLotsAndWritesAuditsAtomically() throws Exception {
        long item = createItem("FINISHED_GOOD");
        receiveFinishedLot(item,3,2);
        receiveFinishedLot(item,4,1);
        String key = key();

        mvc.perform(ship(key,item,5))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.quantity").value(5))
                .andExpect(jsonPath("$.data.allocations.length()").value(2));
        assertEquals(2L, query("SELECT SUM(on_hand_qty) FROM inventory_lots WHERE item_id=?",item));
        assertEquals(2L, query("SELECT COUNT(*) FROM stock_movements WHERE item_id=? AND movement_type='FINISHED_SHIPMENT'",item));
        assertEquals(2L, query("SELECT COUNT(*) FROM shipment_allocations WHERE shipment_id=(SELECT id FROM shipments WHERE request_key=?)",key));
        long id = jdbc.queryForObject("SELECT id FROM shipments WHERE request_key=?",Long.class,key);
        mvc.perform(get("/api/v1/shipments/{id}",id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.quantity").value(5));
    }

    @Test
    void replayWithSameKeyMustNotDoubleShipAndDifferentPayloadMustConflict() throws Exception {
        long item = createItem("FINISHED_GOOD");
        receiveFinishedLot(item,7,1);
        String key = key();
        mvc.perform(ship(key,item,2)).andExpect(status().isCreated());
        mvc.perform(ship(key,item,2)).andExpect(status().isOk());
        mvc.perform(ship(key,item,3)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("IDEMPOTENCY_KEY_CONFLICT"));
        assertEquals(5L,query("SELECT SUM(on_hand_qty) FROM inventory_lots WHERE item_id=?",item));
        assertEquals(1L,query("SELECT COUNT(*) FROM shipments WHERE finished_item_id=?",item));
    }

    @Test
    void insufficientStockMustRollbackAllLotChangesAndShipmentRecords() throws Exception {
        long item = createItem("FINISHED_GOOD");
        receiveFinishedLot(item,3,2);
        receiveFinishedLot(item,2,1);
        mvc.perform(ship(key(),item,6)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("INSUFFICIENT_FINISHED_STOCK"));
        assertEquals(5L,query("SELECT SUM(on_hand_qty) FROM inventory_lots WHERE item_id=?",item));
        assertEquals(0L,query("SELECT COUNT(*) FROM shipments WHERE finished_item_id=?",item));
        assertEquals(0L,query("SELECT COUNT(*) FROM stock_movements WHERE item_id=? AND movement_type='FINISHED_SHIPMENT'",item));
    }

    @Test
    void simultaneousShipmentsMustNeverOversell() throws Exception {
        long item = createItem("FINISHED_GOOD");
        receiveFinishedLot(item,6,1);
        String firstKey=key(), secondKey=key();
        int[] results=concurrently(
                () -> mvc.perform(ship(firstKey,item,4)).andReturn().getResponse().getStatus(),
                () -> mvc.perform(ship(secondKey,item,4)).andReturn().getResponse().getStatus());
        assertEquals(1,countStatus(results,201));
        assertEquals(1,countStatus(results,409));
        assertEquals(2L,query("SELECT SUM(on_hand_qty) FROM inventory_lots WHERE item_id=?",item));
        assertEquals(1L,query("SELECT COUNT(*) FROM shipments WHERE finished_item_id=?",item));
    }

    @Test
    void rawMaterialAndInvalidQuantityMustNotShip() throws Exception {
        long raw = createItem("RAW_MATERIAL");
        mvc.perform(ship(key(),raw,1)).andExpect(status().isConflict());
        mvc.perform(ship(key(),raw,0)).andExpect(status().isBadRequest());
        mvc.perform(ship(key(),999999999L,1)).andExpect(status().isNotFound());
        assertEquals(0L,query("SELECT COUNT(*) FROM shipments WHERE finished_item_id=?",raw));
    }

    private long createItem(String type) {
        String sku="FGTEST-"+UUID.randomUUID();
        jdbc.update("INSERT INTO items(sku,name,item_type,unit,active) VALUES(?,'Test',?,'EA',1)",sku,type);
        return jdbc.queryForObject("SELECT id FROM items WHERE sku=?",Long.class,sku);
    }

    private void receiveFinishedLot(long item,long qty,int daysOld) {
        String lotCode="FGTEST-LOT-"+UUID.randomUUID();
        jdbc.update("""
                INSERT INTO inventory_lots(item_id,lot_code,source_type,cumulative_in_qty,on_hand_qty,reserved_qty,received_at)
                VALUES (?,?,'QC_PASS',?,?,0,?)
                """,item,lotCode,qty,qty,LocalDateTime.now(ZoneOffset.UTC).minusDays(daysOld));
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder ship(String key,long item,long qty) {
        return post("/api/v1/shipments").contentType(MediaType.APPLICATION_JSON)
                .content("{\"requestKey\":\"%s\",\"finishedItemId\":%d,\"quantity\":%d}".formatted(key,item,qty));
    }

    private long query(String sql,Object... args) {
        Long n=jdbc.queryForObject(sql,Long.class,args);
        return n == null ? 0L : n;
    }

    private int[] concurrently(Callable<Integer> a,Callable<Integer> b) throws Exception {
        ExecutorService workers=Executors.newFixedThreadPool(2);
        CountDownLatch ready=new CountDownLatch(2), start=new CountDownLatch(1);
        try {
            Future<Integer> x=workers.submit(() -> { ready.countDown(); if(!start.await(10,TimeUnit.SECONDS)) throw new AssertionError("timeout"); return a.call(); });
            Future<Integer> y=workers.submit(() -> { ready.countDown(); if(!start.await(10,TimeUnit.SECONDS)) throw new AssertionError("timeout"); return b.call(); });
            assertTrue(ready.await(10,TimeUnit.SECONDS));
            start.countDown();
            return new int[]{x.get(40,TimeUnit.SECONDS),y.get(40,TimeUnit.SECONDS)};
        } finally { workers.shutdownNow(); }
    }

    private int countStatus(int[] codes,int expected) {
        int count=0;
        for (int code:codes) if(code==expected) count++;
        return count;
    }

    private String key() { return "SHIP-"+UUID.randomUUID(); }
}
