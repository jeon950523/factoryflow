package com.factoryflow;

import java.util.List;
import java.util.UUID;

import jakarta.persistence.EntityManagerFactory;
import com.factoryflow.item.ItemRepository;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class DatabaseBaselineIntegrationTest {
    @Container
    static final MySQLContainer mysql = new MySQLContainer("mysql:8.4")
            .withDatabaseName("factoryflow_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void setDatabaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
    }

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    Flyway flyway;

    @Autowired
    ItemRepository itemRepository;

    @Autowired
    EntityManagerFactory entityManagerFactory;

    @Test
    void migrationAndJpaValidateShouldStartOnRealMySql() {
        assertEquals(1, flyway.info().applied().length);
        assertEquals(12, entityManagerFactory.getMetamodel().getEntities().size());
        assertTrue(itemRepository.findById(-1L).isEmpty()); // Repository SELECT가 실제 MySQL에 닿는지 확인
        List<String> tables = jdbc.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = DATABASE()",
                String.class
        );
        assertTrue(tables.contains("inventory_lots"));
        assertTrue(tables.contains("stock_movements"));
        assertEquals(13, tables.size()); // 12 domain tables + flyway_schema_history
    }

    @Test
    void uniqueSkuShouldRejectDuplicate() {
        String sku = "RAW-" + UUID.randomUUID();
        jdbc.update("INSERT INTO items(sku, name, item_type) VALUES (?, ?, ?)", sku, "철판", "RAW_MATERIAL");
        assertThrows(DataIntegrityViolationException.class, () ->
                jdbc.update("INSERT INTO items(sku, name, item_type) VALUES (?, ?, ?)", sku, "중복", "RAW_MATERIAL")
        );
    }

    @Test
    void checkShouldRejectReservedStockGreaterThanOnHand() {
        long itemId = createRawItem();
        assertThrows(DataIntegrityViolationException.class, () ->
                jdbc.update("""
                        INSERT INTO inventory_lots(
                            item_id, lot_code, source_type, cumulative_in_qty, on_hand_qty, reserved_qty
                        ) VALUES (?, ?, 'RAW_RECEIPT', 10, 10, 11)
                        """, itemId, "LOT-" + UUID.randomUUID())
        );
    }

    @Test
    void foreignKeyShouldRejectNonexistentItem() {
        assertThrows(DataIntegrityViolationException.class, () ->
                jdbc.update("INSERT INTO boms(finished_item_id) VALUES (?)", Long.MAX_VALUE)
        );
    }

    private long createRawItem() {
        String sku = "RAW-" + UUID.randomUUID();
        jdbc.update("INSERT INTO items(sku, name, item_type) VALUES (?, ?, ?)", sku, "볼트", "RAW_MATERIAL");
        Long id = jdbc.queryForObject("SELECT id FROM items WHERE sku = ?", Long.class, sku);
        if (id == null) {
            throw new IllegalStateException("Inserted test item not found: " + sku);
        }
        return id;
    }
}
