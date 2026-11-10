package com.factoryflow.item;

import com.factoryflow.global.api.PageResponse;
import com.factoryflow.global.error.ApiException;
import com.factoryflow.item.dto.CreateItemRequest;
import com.factoryflow.item.dto.ItemResponse;
import java.util.Locale;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ItemService {
    private final ItemRepository itemRepository;

    public ItemService(ItemRepository itemRepository) {
        this.itemRepository = itemRepository;
    }

    @Transactional
    public ItemResponse create(CreateItemRequest request) {
        String sku = Sku.normalize(request.sku());
        if (itemRepository.existsBySku(sku)) {
            throw new ApiException(HttpStatus.CONFLICT, "DUPLICATE_SKU", "이미 등록된 품목코드입니다.");
        }
        try {
            ItemEntity saved = itemRepository.saveAndFlush(ItemEntity.create(sku, request.name(), request.itemType()));
            return ItemResponse.from(saved);
        } catch (DataIntegrityViolationException exception) {
            // existsBySku is a friendly pre-check; UNIQUE(sku) is the final guard against races.
            throw new ApiException(HttpStatus.CONFLICT, "DUPLICATE_RESOURCE", "품목 등록이 충돌했습니다.");
        }
    }

    public ItemResponse get(Long id) {
        ItemEntity item = itemRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ITEM_NOT_FOUND", "품목을 찾을 수 없습니다."));
        return ItemResponse.from(item);
    }

    public PageResponse<ItemResponse> list(ItemType itemType, String keyword, int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PAGINATION", "page는 0 이상, size는 1~100이어야 합니다.");
        }
        Specification<ItemEntity> criteria = (root, query, builder) -> builder.conjunction();
        if (itemType != null) {
            criteria = criteria.and((root, query, builder) -> builder.equal(root.get("itemType"), itemType));
        }
        if (keyword != null && keyword.strip().length() > 100) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_KEYWORD", "검색어는 100자 이하여야 합니다.");
        }
        if (keyword != null && !keyword.isBlank()) {
            String escaped = keyword.strip().toLowerCase(Locale.ROOT)
                    .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
            String pattern = "%" + escaped + "%";
            criteria = criteria.and((root, query, builder) -> builder.or(
                    builder.like(builder.lower(root.get("sku")), pattern, '\\'),
                    builder.like(builder.lower(root.get("name")), pattern, '\\')
            ));
        }
        Page<ItemResponse> result = itemRepository.findAll(criteria, PageRequest.of(page, size, Sort.by("id").ascending()))
                .map(ItemResponse::from);
        return PageResponse.from(result);
    }
}
