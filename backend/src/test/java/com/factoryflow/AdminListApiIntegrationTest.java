package com.factoryflow;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 관리자 목록 조회 API: 상세 조회와 달리 N+1 없이 페이지·필터·정렬을 확인한다. */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
class AdminListApiIntegrationTest {
    @Container
    static final MySQLContainer mysql = new MySQLContainer("mysql:8.4")
            .withDatabaseName("factoryflow_admin_test")
            .withUsername("test").withPassword("test");

    @DynamicPropertySource
    static void dataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
    }

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    @Test
    void workOrderListMustSupportStatusFilterAndPagination() throws Exception {
        long item = createFinishedItem();
        jdbc.update("INSERT INTO boms(finished_item_id) VALUES(?)", item);
        long bomId = jdbc.queryForObject("SELECT id FROM boms WHERE finished_item_id=?", Long.class, item);
        String code = UUID.randomUUID().toString();
        insertOrder(code + "-DRAFT", item, bomId, "DRAFT");
        insertOrder(code + "-QC", item, bomId, "AWAITING_QC");

        mvc.perform(get("/api/v1/work-orders").param("status", "AWAITING_QC")
                        .param("page", "0").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].status").value("AWAITING_QC"));
        mvc.perform(get("/api/v1/work-orders").param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.totalElements").value(2));
        mvc.perform(get("/api/v1/work-orders").param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_PAGINATION"));
    }

    @Test
    void shipmentListMustReturnPageWithoutLotAllocationsAndRejectBadPagination() throws Exception {
        long item = createFinishedItem();
        String code = UUID.randomUUID().toString();
        String hash = "0".repeat(64);
        jdbc.update("INSERT INTO shipments(request_key,payload_hash,finished_item_id,quantity) VALUES(?,?,?,?)",
                code + "-A", hash, item, 2);
        jdbc.update("INSERT INTO shipments(request_key,payload_hash,finished_item_id,quantity) VALUES(?,?,?,?)",
                code + "-B", hash, item, 3);
        mvc.perform(get("/api/v1/shipments").param("page", "0").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.content.length()").value(2))
                .andExpect(jsonPath("$.data.content[0].quantity").value(3))
                .andExpect(jsonPath("$.data.content[0].allocations").doesNotExist());
        mvc.perform(get("/api/v1/shipments").param("page", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_PAGINATION"));
    }

    private long createFinishedItem() {
        String sku = "FG-ADMIN-" + UUID.randomUUID();
        jdbc.update("INSERT INTO items(sku,name,item_type,unit,active) VALUES(?,'Admin test','FINISHED_GOOD','EA',1)", sku);
        return jdbc.queryForObject("SELECT id FROM items WHERE sku=?", Long.class, sku);
    }

    private void insertOrder(String key, long itemId, long bomId, String state) {
        jdbc.update("""
                INSERT INTO work_orders(order_no,client_request_id,request_hash,finished_item_id,bom_id,planned_qty,status)
                VALUES(?,?,?, ?, ?, 7, ?)
                """, key, key, "0".repeat(64), itemId, bomId, state);
    }
}
