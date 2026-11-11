package com.factoryflow;

import java.util.UUID;

import com.factoryflow.item.ItemRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
class ItemBomApiIntegrationTest {
    @Container
    static final MySQLContainer mysql = new MySQLContainer("mysql:8.4")
            .withDatabaseName("factoryflow_item_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
    }

    @Autowired MockMvc mvc;
    @Autowired ItemRepository items;
    @Autowired JdbcTemplate jdbc;

    @Test
    void itemCreateListAndGetShouldPreserveCanonicalSku() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String lowerSku = "rm-" + suffix;
        mvc.perform(post("/api/v1/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sku":"%s","name":" 철판 ","itemType":"RAW_MATERIAL"}
                                """.formatted(lowerSku)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.sku").value(lowerSku.toUpperCase()))
                .andExpect(jsonPath("$.data.name").value("철판"))
                .andExpect(jsonPath("$.data.unit").value("EA"));

        Long id = items.findBySku(lowerSku.toUpperCase()).orElseThrow().getId();
        mvc.perform(get("/api/v1/items/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(id));
        mvc.perform(get("/api/v1/items").param("itemType", "RAW_MATERIAL").param("keyword", suffix))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1));
        mvc.perform(get("/api/v1/items/{id}", 9_999_999_999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("ITEM_NOT_FOUND"));
        mvc.perform(get("/api/v1/items").param("keyword", "%"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(0));
    }

    @Test
    void duplicateSkuAndInvalidRequestsShouldNotWriteTwice() throws Exception {
        String sku = "RM-" + UUID.randomUUID().toString().substring(0, 8);
        mvc.perform(post("/api/v1/items").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sku":"%s","name":"볼트","itemType":"RAW_MATERIAL"}
                                """.formatted(sku)))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/v1/items").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sku":"%s","name":"볼트2","itemType":"RAW_MATERIAL"}
                                """.formatted(sku.toLowerCase())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("DUPLICATE_SKU"));
        mvc.perform(post("/api/v1/items").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sku":"invalid space","name":"", "itemType":"RAW_MATERIAL"}
                                """))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/items").param("page", "-1"))
                .andExpect(status().isBadRequest());
        long count = jdbc.queryForObject("SELECT count(*) FROM items WHERE sku = ?", Long.class, sku);
        org.junit.jupiter.api.Assertions.assertEquals(1L, count);
    }

    @Test
    void bomShouldAcceptRawMaterialsAndRejectDuplicatedComponents() throws Exception {
        Long rawId = createItem("RM-", "철판", "RAW_MATERIAL");
        Long finishedId = createItem("FG-", "브래킷", "FINISHED_GOOD");

        mvc.perform(post("/api/v1/boms").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"finishedItemId":%d,"components":[{"rawItemId":%d,"qtyPerUnit":2}]}
                                """.formatted(finishedId, rawId)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/boms/" + finishedId))
                .andExpect(jsonPath("$.data.components[0].qtyPerUnit").value(2));
        mvc.perform(get("/api/v1/boms/{finishedItemId}", finishedId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.components[0].rawItemId").value(rawId));
        mvc.perform(post("/api/v1/boms").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"finishedItemId":%d,"components":[{"rawItemId":%d,"qtyPerUnit":2}]}
                                """.formatted(finishedId, rawId)))
                .andExpect(status().isConflict());
    }

    @Test
    void invalidBomMustNotCreatePartialData() throws Exception {
        Long finishedId = createItem("FG-", "브래킷", "FINISHED_GOOD");
        Long anotherFinishedId = createItem("FG-", "못", "FINISHED_GOOD");
        mvc.perform(post("/api/v1/boms").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"finishedItemId":%d,"components":[{"rawItemId":%d,"qtyPerUnit":1}]}
                                """.formatted(finishedId, anotherFinishedId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_RAW_MATERIAL"));
        mvc.perform(post("/api/v1/boms").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"finishedItemId":%d,"components":[{"rawItemId":%d,"qtyPerUnit":1},{"rawItemId":%d,"qtyPerUnit":2}]}
                                """.formatted(finishedId, anotherFinishedId, anotherFinishedId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("DUPLICATE_BOM_COMPONENT"));
        mvc.perform(post("/api/v1/boms").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"finishedItemId":%d,"components":[{"rawItemId":%d,"qtyPerUnit":0}]}
                                """.formatted(finishedId, anotherFinishedId)))
                .andExpect(status().isBadRequest());
        long count = jdbc.queryForObject("SELECT COUNT(*) FROM boms WHERE finished_item_id = ?", Long.class, finishedId);
        org.junit.jupiter.api.Assertions.assertEquals(0L, count);
    }


    @Test
    void itemSearchMustReachRowsBeyondFirstHundred() throws Exception {
        String marker = "PICK" + UUID.randomUUID().toString().substring(0, 8);
        for (int i = 0; i < 105; i++) {
            jdbc.update("""
                    INSERT INTO items(sku,name,item_type,unit,active)
                    VALUES (?,?,'RAW_MATERIAL','EA',1)
                    """, "RM-" + marker + "-" + String.format("%03d", i), "검색 원자재 " + marker);
        }
        mvc.perform(get("/api/v1/items")
                        .param("itemType", "RAW_MATERIAL")
                        .param("keyword", marker)
                        .param("page", "10").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(105))
                .andExpect(jsonPath("$.data.totalPages").value(11))
                .andExpect(jsonPath("$.data.content.length()").value(5))
                .andExpect(jsonPath("$.data.content[4].sku").value("RM-" + marker + "-104"));
    }

    @Test
    void bulkLookupShouldResolveAnyItemIncludingBeyondHundredWithoutPerRowRequests() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        for (int i = 0; i < 105; i++) {
            jdbc.update("INSERT INTO items(sku,name,item_type,unit,active) VALUES(?,?,'RAW_MATERIAL','EA',1)",
                    "RM-BULK-" + suffix + "-" + String.format("%03d", i), "품목 " + i);
        }
        Long first = jdbc.queryForObject("SELECT id FROM items WHERE sku=?", Long.class, "RM-BULK-" + suffix + "-000");
        Long last = jdbc.queryForObject("SELECT id FROM items WHERE sku=?", Long.class, "RM-BULK-" + suffix + "-104");
        mvc.perform(get("/api/v1/items/lookup").param("ids", last + "," + first + "," + last))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].id").value(last))
                .andExpect(jsonPath("$.data[0].name").value("품목 104"))
                .andExpect(jsonPath("$.data[1].id").value(first));
        mvc.perform(get("/api/v1/items/lookup").param("ids", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_ITEM_IDS"));
        mvc.perform(get("/api/v1/items/lookup").param("ids", "-1,2"))
                .andExpect(status().isBadRequest());
    }

    private Long createItem(String prefix, String name, String type) throws Exception {
        String sku = prefix + UUID.randomUUID().toString().substring(0, 8);
        mvc.perform(post("/api/v1/items").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sku":"%s","name":"%s","itemType":"%s"}
                                """.formatted(sku, name, type)))
                .andExpect(status().isCreated());
        return items.findBySku(sku).orElseThrow().getId();
    }
}
