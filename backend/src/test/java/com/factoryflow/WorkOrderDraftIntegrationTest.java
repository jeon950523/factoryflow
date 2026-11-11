package com.factoryflow;

import com.factoryflow.item.ItemRepository;
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
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
class WorkOrderDraftIntegrationTest {
    @Container
    static final MySQLContainer mysql = new MySQLContainer("mysql:8.4")
            .withDatabaseName("factoryflow_workorder_test")
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
    void draftShouldSnapshotBomWithoutChangingInventory() throws Exception {
        Long sheet = createItem("RAW_MATERIAL");
        Long bolt = createItem("RAW_MATERIAL");
        Long finished = createItem("FINISHED_GOOD");
        createBom(finished, sheet, bolt);
        String key = "WO-" + random();
        mvc.perform(postDraft(key, finished, 50))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.clientRequestId").value(key))
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.plannedQty").value(50))
                .andExpect(jsonPath("$.data.materials.length()").value(2));
        Long orderId = jdbc.queryForObject("SELECT id FROM work_orders WHERE client_request_id = ?", Long.class, key);
        mvc.perform(get("/api/v1/work-orders/{id}", orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(orderId))
                .andExpect(jsonPath("$.data.status").value("DRAFT"));
        assertEquals(50L, jdbc.queryForObject(
                "SELECT required_qty FROM work_order_materials WHERE work_order_id = ? AND material_item_id = ?",
                Long.class, orderId, sheet));
        assertEquals(100L, jdbc.queryForObject(
                "SELECT required_qty FROM work_order_materials WHERE work_order_id = ? AND material_item_id = ?",
                Long.class, orderId, bolt));
        assertEquals(0L, jdbc.queryForObject(
                "SELECT COUNT(*) FROM inventory_lots WHERE item_id IN (?, ?)", Long.class, sheet, bolt));
        assertEquals(0L, jdbc.queryForObject(
                "SELECT COUNT(*) FROM material_reservations WHERE work_order_id = ?", Long.class, orderId));
    }

    @Test
    void sameRequestKeyMustReplayAndDifferentPayloadMustConflict() throws Exception {
        Long raw = createItem("RAW_MATERIAL");
        Long finished = createItem("FINISHED_GOOD");
        createBom(finished, raw, null);
        String key = "REQ-" + random();
        String original = mvc.perform(postDraft(key, finished, 7))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String replay = mvc.perform(postDraft(key.toLowerCase(), finished, 7))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertEquals(original, replay);
        mvc.perform(postDraft(key, finished, 8))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("IDEMPOTENCY_KEY_CONFLICT"));
        assertEquals(1L, jdbc.queryForObject(
                "SELECT COUNT(*) FROM work_orders WHERE client_request_id = ?", Long.class, key));
    }

    @Test
    void absentBomAndOverflowMustNotLeavePartialOrders() throws Exception {
        Long finished = createItem("FINISHED_GOOD");
        String keyMissing = "MISSING-" + random();
        mvc.perform(postDraft(keyMissing, finished, 5))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("BOM_NOT_FOUND"));
        assertEquals(0L, jdbc.queryForObject(
                "SELECT COUNT(*) FROM work_orders WHERE client_request_id = ?", Long.class, keyMissing));

        Long raw = createItem("RAW_MATERIAL");
        createBom(finished, raw, null);
        String keyOverflow = "OVER-" + random();
        mvc.perform(postDraft(keyOverflow, finished, Long.MAX_VALUE))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("MATERIAL_QUANTITY_OVERFLOW"));
        assertEquals(0L, jdbc.queryForObject(
                "SELECT COUNT(*) FROM work_orders WHERE client_request_id = ?", Long.class, keyOverflow));
    }

    @Test
    void invalidRequestsAndNotFoundShouldBeRejected() throws Exception {
        Long raw = createItem("RAW_MATERIAL");
        mvc.perform(postDraft("WRONG-" + random(), raw, 1))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_FINISHED_ITEM"));
        mvc.perform(postDraft("NEG-" + random(), raw, 0))
                .andExpect(status().isBadRequest());
        mvc.perform(postDraft("bad key", raw, 1))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST_ID"));
        mvc.perform(get("/api/v1/work-orders/999999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("WORK_ORDER_NOT_FOUND"));
    }

    @Test
    void concurrentIdenticalDraftRequestsMustCreateOnlyOneOrder() throws Exception {
        Long raw = createItem("RAW_MATERIAL");
        Long finished = createItem("FINISHED_GOOD");
        createBom(finished, raw, null);
        String key = "RACE-" + random();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        try {
            Callable<Integer> work = () -> {
                ready.countDown();
                if (!go.await(10, TimeUnit.SECONDS)) throw new AssertionError("Latch timeout");
                return mvc.perform(postDraft(key, finished, 3)).andReturn().getResponse().getStatus();
            };
            Future<Integer> a = pool.submit(work);
            Future<Integer> b = pool.submit(work);
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            go.countDown();
            int first = a.get(30, TimeUnit.SECONDS);
            int second = b.get(30, TimeUnit.SECONDS);
            assertEquals(1, (first == 201 ? 1 : 0) + (second == 201 ? 1 : 0));
            assertEquals(1, (first == 200 ? 1 : 0) + (second == 200 ? 1 : 0));
            assertEquals(1L, jdbc.queryForObject(
                    "SELECT COUNT(*) FROM work_orders WHERE client_request_id = ?", Long.class, key));
            assertEquals(1L, jdbc.queryForObject(
                    "SELECT COUNT(*) FROM work_order_materials m JOIN work_orders w ON m.work_order_id=w.id WHERE w.client_request_id=?",
                    Long.class, key));
        } finally {
            pool.shutdownNow();
        }
    }

    private Long createItem(String type) throws Exception {
        String sku = (type.equals("RAW_MATERIAL") ? "RM-" : "FG-") + random();
        mvc.perform(post("/api/v1/items").contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"sku":"%s","name":"테스트 품목","itemType":"%s"}
                        """.formatted(sku, type))).andExpect(status().isCreated());
        return items.findBySku(sku).orElseThrow().getId();
    }

    private void createBom(Long finished, Long first, Long second) throws Exception {
        String components = second == null
                ? "[{\"rawItemId\":%d,\"qtyPerUnit\":2}]".formatted(first)
                : "[{\"rawItemId\":%d,\"qtyPerUnit\":1},{\"rawItemId\":%d,\"qtyPerUnit\":2}]".formatted(first, second);
        mvc.perform(post("/api/v1/boms").contentType(MediaType.APPLICATION_JSON)
                .content("{\"finishedItemId\":%d,\"components\":%s}".formatted(finished, components)))
                .andExpect(status().isCreated());
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder postDraft(
            String key, Long itemId, long quantity) {
        return post("/api/v1/work-orders").contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"clientRequestId":"%s","finishedItemId":%d,"plannedQty":%d}
                        """.formatted(key, itemId, quantity));
    }

    private String random() { return UUID.randomUUID().toString().substring(0, 8).toUpperCase(); }
}
