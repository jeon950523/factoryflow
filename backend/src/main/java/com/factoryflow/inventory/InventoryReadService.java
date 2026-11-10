package com.factoryflow.inventory;

import com.factoryflow.global.api.PageResponse;
import com.factoryflow.global.error.ApiException;
import com.factoryflow.inventory.dto.InventoryLotResponse;
import com.factoryflow.inventory.dto.InventorySummaryResponse;
import com.factoryflow.inventory.dto.StockMovementResponse;
import com.factoryflow.item.ItemRepository;
import com.factoryflow.item.ItemType;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class InventoryReadService {
    private final NamedParameterJdbcTemplate jdbc;
    private final ItemRepository items;
    private final InventoryLotRepository lots;
    private final StockMovementRepository movements;

    public InventoryReadService(NamedParameterJdbcTemplate jdbc, ItemRepository items,
                                InventoryLotRepository lots, StockMovementRepository movements) {
        this.jdbc = jdbc;
        this.items = items;
        this.lots = lots;
        this.movements = movements;
    }

    public PageResponse<InventorySummaryResponse> summary(Long itemId, ItemType itemType, int page, int size) {
        validatePage(page, size);
        if (itemId != null) {
            requireItem(itemId);
        }
        // 집계는 DB에 맡긴다. LOT 전체를 애플리케이션 메모리로 올리지 않는다.
        String condition = " WHERE 1=1" + (itemId == null ? "" : " AND i.id = :itemId")
                + (itemType == null ? "" : " AND i.item_type = :itemType");
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("limit", size).addValue("offset", (long) page * size);
        if (itemId != null) params.addValue("itemId", itemId);
        if (itemType != null) params.addValue("itemType", itemType.name());

        long total = jdbc.queryForObject("SELECT COUNT(*) FROM items i" + condition, params, Long.class);
        String query = """
                SELECT i.id AS item_id, i.sku, i.name, i.item_type,
                       COALESCE(SUM(l.on_hand_qty), 0) AS sum_on_hand,
                       COALESCE(SUM(l.reserved_qty), 0) AS sum_reserved
                FROM items i LEFT JOIN inventory_lots l ON l.item_id = i.id
                """ + condition + """
                 GROUP BY i.id, i.sku, i.name, i.item_type
                 ORDER BY i.id
                 LIMIT :limit OFFSET :offset
                """;
        List<InventorySummaryResponse> content = jdbc.query(query, params, (rs, rowNum) -> {
            long onHand = exactLong(rs.getBigDecimal("sum_on_hand"));
            long reserved = exactLong(rs.getBigDecimal("sum_reserved"));
            return new InventorySummaryResponse(rs.getLong("item_id"), rs.getString("sku"),
                    rs.getString("name"), ItemType.valueOf(rs.getString("item_type")),
                    onHand, reserved, Math.subtractExact(onHand, reserved));
        });
        int totalPages = (int) Math.min(Integer.MAX_VALUE, (total / size) + (total % size == 0 ? 0 : 1));
        return new PageResponse<>(content, page, size, total, totalPages);
    }

    public PageResponse<InventoryLotResponse> lots(Long itemId, int page, int size) {
        validatePage(page, size);
        requireItem(itemId);
        Page<InventoryLotResponse> result = lots.findByItemId(itemId, PageRequest.of(page, size,
                Sort.by("receivedAt").ascending().and(Sort.by("id").ascending())))
                .map(InventoryLotResponse::from);
        return PageResponse.from(result);
    }

    public InventoryLotResponse lot(Long lotId) {
        if (lotId == null || lotId <= 0) throw invalidId();
        return lots.findById(lotId).map(InventoryLotResponse::from)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "LOT_NOT_FOUND", "LOT를 찾을 수 없습니다."));
    }

    public PageResponse<StockMovementResponse> movements(Long itemId, int page, int size) {
        validatePage(page, size);
        requireItem(itemId);
        Page<StockMovementResponse> result = movements.findByItemId(itemId, PageRequest.of(page, size,
                Sort.by("createdAt").descending().and(Sort.by("id").descending())))
                .map(StockMovementResponse::from);
        return PageResponse.from(result);
    }

    private void requireItem(Long itemId) {
        if (itemId == null || itemId <= 0) throw invalidId();
        if (!items.existsById(itemId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "ITEM_NOT_FOUND", "품목을 찾을 수 없습니다.");
        }
    }

    private ApiException invalidId() {
        return new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ITEM_ID", "올바른 양의 정수 ID가 필요합니다.");
    }

    private void validatePage(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PAGINATION", "page는 0 이상, size는 1~100이어야 합니다.");
        }
    }

    private long exactLong(BigDecimal value) {
        // SUM(BIGINT)가 long 범위를 넘어가면 조용히 잘라내지 않는다.
        return value.longValueExact();
    }
}
