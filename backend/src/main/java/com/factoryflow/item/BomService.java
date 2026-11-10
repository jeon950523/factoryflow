package com.factoryflow.item;

import com.factoryflow.global.error.ApiException;
import com.factoryflow.item.dto.BomComponentRequest;
import com.factoryflow.item.dto.BomResponse;
import com.factoryflow.item.dto.CreateBomRequest;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class BomService {
    private final ItemRepository itemRepository;
    private final BomRepository bomRepository;
    private final BomComponentRepository componentRepository;

    public BomService(ItemRepository itemRepository, BomRepository bomRepository,
                      BomComponentRepository componentRepository) {
        this.itemRepository = itemRepository;
        this.bomRepository = bomRepository;
        this.componentRepository = componentRepository;
    }

    @Transactional
    public BomResponse create(CreateBomRequest request) {
        ItemEntity finished = itemRepository.findById(request.finishedItemId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ITEM_NOT_FOUND", "완제품 품목이 없습니다."));
        if (finished.getItemType() != ItemType.FINISHED_GOOD || !finished.isActive()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_FINISHED_ITEM", "활성 완제품만 BOM을 등록할 수 있습니다.");
        }
        if (bomRepository.existsByFinishedItemId(finished.getId())) {
            throw new ApiException(HttpStatus.CONFLICT, "BOM_ALREADY_EXISTS", "이미 BOM이 등록된 완제품입니다.");
        }

        Set<Long> componentIds = new HashSet<>();
        for (BomComponentRequest component : request.components()) {
            if (!componentIds.add(component.rawItemId())) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "DUPLICATE_BOM_COMPONENT", "동일 원자재가 중복되었습니다.");
            }
        }
        Map<Long, ItemEntity> rawItems = itemRepository.findAllById(componentIds).stream()
                .collect(Collectors.toMap(ItemEntity::getId, Function.identity()));
        for (Long componentId : componentIds) {
            ItemEntity raw = rawItems.get(componentId);
            if (raw == null) {
                throw new ApiException(HttpStatus.NOT_FOUND, "ITEM_NOT_FOUND", "구성 원자재가 없습니다.");
            }
            if (raw.getItemType() != ItemType.RAW_MATERIAL || !raw.isActive()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_RAW_MATERIAL", "활성 원자재만 BOM 구성품이 될 수 있습니다.");
            }
        }

        try {
            BomEntity bom = bomRepository.saveAndFlush(BomEntity.forFinishedItem(finished.getId()));
            List<BomComponentEntity> components = request.components().stream()
                    .sorted(Comparator.comparing(BomComponentRequest::rawItemId))
                    .map(value -> BomComponentEntity.of(bom.getId(), value.rawItemId(), value.qtyPerUnit()))
                    .toList();
            componentRepository.saveAllAndFlush(components);
            return BomResponse.from(bom, components);
        } catch (DataIntegrityViolationException exception) {
            // The DB's unique constraint also protects simultaneous BOM registrations.
            throw new ApiException(HttpStatus.CONFLICT, "BOM_CONFLICT", "동시에 BOM이 등록되었거나 DB 제약조건과 충돌했습니다.");
        }
    }

    public BomResponse getByFinishedItemId(Long finishedItemId) {
        BomEntity bom = bomRepository.findByFinishedItemId(finishedItemId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "BOM_NOT_FOUND", "BOM을 찾을 수 없습니다."));
        return BomResponse.from(bom, componentRepository.findByBomIdOrderByRawItemIdAsc(bom.getId()));
    }
}
