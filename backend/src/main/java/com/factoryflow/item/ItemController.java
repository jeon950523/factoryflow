package com.factoryflow.item;

import com.factoryflow.global.api.ApiResponse;
import com.factoryflow.global.api.PageResponse;
import com.factoryflow.item.dto.CreateItemRequest;
import com.factoryflow.item.dto.ItemResponse;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/items")
public class ItemController {
    private final ItemService service;

    public ItemController(ItemService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ItemResponse>> create(@Valid @RequestBody CreateItemRequest request) {
        ItemResponse created = service.create(request);
        return ResponseEntity.created(URI.create("/api/v1/items/" + created.id()))
                .body(ApiResponse.of(created));
    }

    /** Resolve visible item labels in one DB query instead of one request per table row. */
    @GetMapping("/lookup")
    public ApiResponse<List<ItemResponse>> lookup(@RequestParam List<Long> ids) {
        return ApiResponse.of(service.lookup(ids));
    }

    @GetMapping("/{id}")
    public ApiResponse<ItemResponse> get(@PathVariable Long id) {
        return ApiResponse.of(service.get(id));
    }

    @GetMapping
    public ApiResponse<PageResponse<ItemResponse>> list(
            @RequestParam(required = false) ItemType itemType,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.of(service.list(itemType, keyword, page, size));
    }
}
