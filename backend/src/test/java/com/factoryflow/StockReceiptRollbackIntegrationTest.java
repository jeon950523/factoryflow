package com.factoryflow;

import com.factoryflow.inventory.StockMovementEntity;
import com.factoryflow.inventory.StockMovementRepository;
import com.factoryflow.inventory.StockReceiptService;
import com.factoryflow.inventory.dto.CreateStockReceiptRequest;
import com.factoryflow.item.ItemEntity;
import com.factoryflow.item.ItemRepository;
import com.factoryflow.item.ItemType;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
class StockReceiptRollbackIntegrationTest {
    @Container
    static final MySQLContainer mysql = new MySQLContainer("mysql:8.4")
            .withDatabaseName("factoryflow_receipt_rollback_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
    }

    @Autowired StockReceiptService service;
    @Autowired ItemRepository items;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean StockMovementRepository movements;

    @Test
    void movementFailureShouldRollbackReceiptAndNewLot() {
        String sku = "RM-" + UUID.randomUUID().toString().substring(0, 8);
        Long itemId = items.saveAndFlush(ItemEntity.create(sku, "원자재", ItemType.RAW_MATERIAL)).getId();
        String requestKey = "ROLLBACK-" + UUID.randomUUID();
        String lotCode = "LOT-" + UUID.randomUUID();
        when(movements.saveAndFlush(any(StockMovementEntity.class)))
                .thenThrow(new IllegalStateException("Injected movement save failure"));
        assertThrows(IllegalStateException.class, () -> service.receive(
                new CreateStockReceiptRequest(requestKey, itemId, lotCode, 5L)));
        assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM inventory_lots WHERE item_id = ?",
                Long.class, itemId));
        assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM stock_receipts WHERE item_id = ?",
                Long.class, itemId));
    }
}
