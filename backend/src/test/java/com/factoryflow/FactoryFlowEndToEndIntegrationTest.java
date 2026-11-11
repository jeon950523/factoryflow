package com.factoryflow;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
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
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * All business commands enter through HTTP; JdbcTemplate is used only to verify committed DB invariants.
 * Do not annotate this test with @Transactional: each API request must own its own transaction.
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FactoryFlowEndToEndIntegrationTest {
    @Container
    static final MySQLContainer mysql = new MySQLContainer("mysql:8.4")
            .withDatabaseName("factoryflow_e2e_test")
            .withUsername("test").withPassword("test");

    @DynamicPropertySource
    static void mysqlProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;

    @Test
    void realApiJourneyMustBalanceAllRawAndFinishedLotsAndMovementLedgers() throws Exception {
        long sheet = createItem("RAW_MATERIAL");
        long bolt = createItem("RAW_MATERIAL");
        long bracket = createItem("FINISHED_GOOD");
        createBom(bracket, sheet, bolt, 1, 2);

        // The first material spans two LOTs: 30 + 70 = 100.
        receive(sheet, "LOT-SHEET-A", 30, key("REC"));
        receive(sheet, "LOT-SHEET-B", 70, key("REC"));
        receive(bolt, "LOT-BOLT-A", 200, key("REC"));
        assertInventory(sheet, 100, 0, 100);
        assertInventory(bolt, 200, 0, 200);
        assertInventory(bracket, 0, 0, 0);

        long order = createOrder(bracket, 50, key("WO"));
        assertEquals("DRAFT", workOrderStatus(order));
        assertEquals(50, number("SELECT required_qty FROM work_order_materials WHERE work_order_id=? AND material_item_id=?", order, sheet));
        assertEquals(100, number("SELECT required_qty FROM work_order_materials WHERE work_order_id=? AND material_item_id=?", order, bolt));

        command(order, "reserve", "RESERVED");
        assertInventory(sheet, 100, 50, 50);
        assertInventory(bolt, 200, 100, 100);
        assertEquals(30, number("SELECT reserved_qty FROM inventory_lots WHERE item_id=? AND lot_code=?", sheet, "LOT-SHEET-A"));
        assertEquals(20, number("SELECT reserved_qty FROM inventory_lots WHERE item_id=? AND lot_code=?", sheet, "LOT-SHEET-B"));
        assertEquals(3, number("SELECT COUNT(*) FROM material_reservations WHERE work_order_id=? AND status='RESERVED'", order));

        command(order, "start", "IN_PROGRESS");
        assertInventory(sheet, 50, 0, 50);
        assertInventory(bolt, 100, 0, 100);
        assertEquals(3, number("SELECT COUNT(*) FROM material_reservations WHERE work_order_id=? AND status='CONSUMED'", order));
        assertEquals(50, movements(sheet, "RAW_CONSUMPTION"));
        assertEquals(100, movements(bolt, "RAW_CONSUMPTION"));

        command(order, "finish", "AWAITING_QC");
        assertInventory(bracket, 0, 0, 0);
        mvc.perform(post("/api/v1/work-orders/{id}/inspection", order)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"passedQty\":47,\"failedQty\":3}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.passedQty").value(47));
        assertEquals("COMPLETED", workOrderStatus(order));
        assertInventory(bracket, 47, 0, 47);

        String shipmentKey = key("SHIP");
        JsonNode shipment = postCreated("/api/v1/shipments",
                "{\"requestKey\":\"%s\",\"finishedItemId\":%d,\"quantity\":10}"
                        .formatted(shipmentKey, bracket));
        assertEquals(10, shipment.path("quantity").asLong());
        assertInventory(bracket, 37, 0, 37);

        // Three receipts + three consumption rows + one QC receipt + one shipment = eight physical movements.
        assertEquals(8, number("SELECT COUNT(*) FROM stock_movements WHERE item_id IN (?,?,?)", sheet, bolt, bracket));
        assertEquals(100, movements(sheet, "RAW_RECEIPT"));
        assertEquals(200, movements(bolt, "RAW_RECEIPT"));
        assertEquals(47, movements(bracket, "FINISHED_RECEIPT"));
        assertEquals(10, movements(bracket, "FINISHED_SHIPMENT"));
        assertEquals(1, number("SELECT COUNT(*) FROM shipment_allocations WHERE shipment_id=?", shipment.path("id").asLong()));
        assertEquals(1, number("SELECT COUNT(*) FROM quality_inspections WHERE work_order_id=?", order));
        assertEquals(0, number("SELECT COUNT(*) FROM inventory_lots WHERE reserved_qty < 0 OR reserved_qty > on_hand_qty"));
        assertLedgerMatchesOnHand(sheet);
        assertLedgerMatchesOnHand(bolt);
        assertLedgerMatchesOnHand(bracket);
    }

    @Test
    void reservationShortageThenReplenishmentAndCancelMustRestoreAvailability() throws Exception {
        long sheet = createItem("RAW_MATERIAL");
        long bolt = createItem("RAW_MATERIAL");
        long product = createItem("FINISHED_GOOD");
        createBom(product, sheet, bolt, 1, 2);
        receive(sheet, "LOT-SHEET", 12, key("REC"));
        receive(bolt, "LOT-BOLT", 5, key("REC"));

        long order = createOrder(product, 4, key("WO")); // needs four sheets and eight bolts
        mvc.perform(post("/api/v1/work-orders/{id}/reserve", order))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("INSUFFICIENT_STOCK"));
        assertEquals("DRAFT", workOrderStatus(order));
        assertEquals(0, number("SELECT COUNT(*) FROM material_reservations WHERE work_order_id=?", order));
        assertInventory(sheet, 12, 0, 12);
        assertInventory(bolt, 5, 0, 5);

        // A later receipt allows the existing DRAFT order to be reserved and then cancelled.
        receive(bolt, "LOT-BOLT", 5, key("REC"));
        command(order, "reserve", "RESERVED");
        assertInventory(sheet, 12, 4, 8);
        assertInventory(bolt, 10, 8, 2);
        command(order, "cancel", "CANCELED");
        assertInventory(sheet, 12, 0, 12);
        assertInventory(bolt, 10, 0, 10);
        assertEquals(2, number("SELECT COUNT(*) FROM material_reservations WHERE work_order_id=? AND status='RELEASED'", order));
        mvc.perform(post("/api/v1/work-orders/{id}/start", order))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("STATE_CONFLICT"));
        assertEquals(0, number("SELECT COUNT(*) FROM stock_movements WHERE movement_type='RAW_CONSUMPTION' AND item_id IN (?,?)", sheet, bolt));
        assertLedgerMatchesOnHand(sheet);
        assertLedgerMatchesOnHand(bolt);
    }

    @Test
    void retriedCommandsAcrossFullJourneyMustNotDuplicateStockOrHistory() throws Exception {
        long raw = createItem("RAW_MATERIAL");
        long product = createItem("FINISHED_GOOD");
        createBom(product, raw, null, 1, 0);
        String receiptKey = key("REC");
        String receiptBody = "{\"requestKey\":\"%s\",\"itemId\":%d,\"lotCode\":\"LOT-RETRY\",\"quantity\":12}"
                .formatted(receiptKey, raw);
        JsonNode receipt = postCreated("/api/v1/stock-receipts", receiptBody);
        mvc.perform(post("/api/v1/stock-receipts").contentType(MediaType.APPLICATION_JSON).content(receiptBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(receipt.path("id").asLong()));
        assertInventory(raw, 12, 0, 12);

        String orderKey = key("WO");
        String orderBody = "{\"clientRequestId\":\"%s\",\"finishedItemId\":%d,\"plannedQty\":5}"
                .formatted(orderKey, product);
        JsonNode orderCreated = postCreated("/api/v1/work-orders", orderBody);
        long order = orderCreated.path("id").asLong();
        mvc.perform(post("/api/v1/work-orders").contentType(MediaType.APPLICATION_JSON).content(orderBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(order));

        command(order, "reserve", "RESERVED");
        command(order, "start", "IN_PROGRESS");
        command(order, "finish", "AWAITING_QC");
        String inspection = "{\"passedQty\":4,\"failedQty\":1}";
        mvc.perform(post("/api/v1/work-orders/{id}/inspection", order)
                        .contentType(MediaType.APPLICATION_JSON).content(inspection))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/v1/work-orders/{id}/inspection", order)
                        .contentType(MediaType.APPLICATION_JSON).content(inspection))
                .andExpect(status().isConflict());

        String shipKey = key("SHIP");
        String shipBody = "{\"requestKey\":\"%s\",\"finishedItemId\":%d,\"quantity\":2}"
                .formatted(shipKey, product);
        JsonNode shipped = postCreated("/api/v1/shipments", shipBody);
        mvc.perform(post("/api/v1/shipments").contentType(MediaType.APPLICATION_JSON).content(shipBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(shipped.path("id").asLong()));
        mvc.perform(post("/api/v1/shipments").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"requestKey\":\"%s\",\"finishedItemId\":%d,\"quantity\":3}"
                                .formatted(shipKey, product)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("IDEMPOTENCY_KEY_CONFLICT"));

        assertInventory(raw, 7, 0, 7);
        assertInventory(product, 2, 0, 2);
        assertEquals(1, number("SELECT COUNT(*) FROM stock_receipts WHERE request_key=?", receiptKey));
        assertEquals(1, number("SELECT COUNT(*) FROM work_orders WHERE client_request_id=?", orderKey));
        assertEquals(1, number("SELECT COUNT(*) FROM quality_inspections WHERE work_order_id=?", order));
        assertEquals(1, number("SELECT COUNT(*) FROM shipments WHERE request_key=?", shipKey));
        assertEquals(4, number("SELECT COUNT(*) FROM stock_movements WHERE item_id IN (?,?)", raw, product));
        assertLedgerMatchesOnHand(raw);
        assertLedgerMatchesOnHand(product);
    }

    @Test
    void allFailedQualityInspectionMustNeverCreateShippableInventory() throws Exception {
        long raw = createItem("RAW_MATERIAL");
        long product = createItem("FINISHED_GOOD");
        createBom(product, raw, null, 1, 0);
        receive(raw, "LOT-ALL-REJECTED", 5, key("REC"));
        long order = createOrder(product, 5, key("WO"));
        command(order, "reserve", "RESERVED");
        command(order, "start", "IN_PROGRESS");
        command(order, "finish", "AWAITING_QC");
        mvc.perform(post("/api/v1/work-orders/{id}/inspection", order)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"passedQty\":0,\"failedQty\":5}"))
                .andExpect(status().isCreated());
        assertEquals("COMPLETED", workOrderStatus(order));
        assertInventory(product, 0, 0, 0);
        mvc.perform(post("/api/v1/shipments").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"requestKey\":\"%s\",\"finishedItemId\":%d,\"quantity\":1}"
                                .formatted(key("SHIP"), product)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("INSUFFICIENT_FINISHED_STOCK"));
        assertEquals(0, number("SELECT COUNT(*) FROM inventory_lots WHERE item_id=?", product));
        assertEquals(0, number("SELECT COUNT(*) FROM shipments WHERE finished_item_id=?", product));
        assertEquals(0, movements(product, "FINISHED_RECEIPT"));
        assertLedgerMatchesOnHand(raw);
        assertLedgerMatchesOnHand(product);
    }

    private long createItem(String type) throws Exception {
        String sku = (type.equals("RAW_MATERIAL") ? "RM-" : "FG-") + UUID.randomUUID();
        JsonNode created = postCreated("/api/v1/items",
                "{\"sku\":\"%s\",\"name\":\"E2E test item\",\"itemType\":\"%s\"}".formatted(sku, type));
        return created.path("id").asLong();
    }

    private void createBom(long finished, long first, Long second, long firstQty, long secondQty) throws Exception {
        String components = second == null
                ? "[{\"rawItemId\":%d,\"qtyPerUnit\":%d}]".formatted(first, firstQty)
                : "[{\"rawItemId\":%d,\"qtyPerUnit\":%d},{\"rawItemId\":%d,\"qtyPerUnit\":%d}]"
                        .formatted(first, firstQty, second, secondQty);
        postCreated("/api/v1/boms",
                "{\"finishedItemId\":%d,\"components\":%s}".formatted(finished, components));
    }

    private void receive(long item, String lotCode, long quantity, String requestKey) throws Exception {
        postCreated("/api/v1/stock-receipts",
                "{\"requestKey\":\"%s\",\"itemId\":%d,\"lotCode\":\"%s\",\"quantity\":%d}"
                        .formatted(requestKey, item, lotCode, quantity));
    }

    private long createOrder(long finished, long quantity, String requestKey) throws Exception {
        return postCreated("/api/v1/work-orders",
                "{\"clientRequestId\":\"%s\",\"finishedItemId\":%d,\"plannedQty\":%d}"
                        .formatted(requestKey, finished, quantity)).path("id").asLong();
    }

    private void command(long order, String command, String expectedStatus) throws Exception {
        mvc.perform(post("/api/v1/work-orders/{id}/{command}", order, command))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value(expectedStatus));
    }

    private JsonNode postCreated(String path, String body) throws Exception {
        MvcResult result = mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn();
        return json.readTree(result.getResponse().getContentAsString()).path("data");
    }

    private void assertInventory(long item, long onHand, long reserved, long available) throws Exception {
        mvc.perform(get("/api/v1/inventory").param("itemId", String.valueOf(item)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].onHand").value(onHand))
                .andExpect(jsonPath("$.data.content[0].reserved").value(reserved))
                .andExpect(jsonPath("$.data.content[0].available").value(available));
        assertEquals(onHand, number("SELECT COALESCE(SUM(on_hand_qty),0) FROM inventory_lots WHERE item_id=?", item));
        assertEquals(reserved, number("SELECT COALESCE(SUM(reserved_qty),0) FROM inventory_lots WHERE item_id=?", item));
    }

    private void assertLedgerMatchesOnHand(long item) {
        long in = movements(item, "RAW_RECEIPT") + movements(item, "FINISHED_RECEIPT");
        long out = movements(item, "RAW_CONSUMPTION") + movements(item, "FINISHED_SHIPMENT");
        assertEquals(in - out, number("SELECT COALESCE(SUM(on_hand_qty),0) FROM inventory_lots WHERE item_id=?", item));
    }

    private long movements(long item, String type) {
        return number("SELECT COALESCE(SUM(quantity),0) FROM stock_movements WHERE item_id=? AND movement_type=?",
                item, type);
    }

    private String workOrderStatus(long order) {
        return jdbc.queryForObject("SELECT status FROM work_orders WHERE id=?", String.class, order);
    }

    private long number(String sql, Object... args) {
        Long value = jdbc.queryForObject(sql, Long.class, args);
        return value == null ? 0L : value;
    }

    private String key(String prefix) {
        return prefix + "-" + UUID.randomUUID();
    }
}
