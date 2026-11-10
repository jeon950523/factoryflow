package com.factoryflow.production;

import com.factoryflow.global.error.ApiException;
import com.factoryflow.item.BomComponentEntity;
import com.factoryflow.item.BomComponentRepository;
import com.factoryflow.item.BomEntity;
import com.factoryflow.item.BomRepository;
import com.factoryflow.item.ItemEntity;
import com.factoryflow.item.ItemRepository;
import com.factoryflow.item.ItemType;
import com.factoryflow.production.dto.WorkOrderResponse;
import com.factoryflow.production.validation.CanonicalWorkOrder;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkOrderWriteService {
    private final ItemRepository items;
    private final BomRepository boms;
    private final BomComponentRepository components;
    private final WorkOrderRepository orders;
    private final WorkOrderMaterialRepository materials;

    public WorkOrderWriteService(ItemRepository items, BomRepository boms, BomComponentRepository components,
                                 WorkOrderRepository orders, WorkOrderMaterialRepository materials) {
        this.items = items;
        this.boms = boms;
        this.components = components;
        this.orders = orders;
        this.materials = materials;
    }

    @Transactional
    public WorkOrderResponse write(CanonicalWorkOrder command) {
        ItemEntity finished = items.findById(command.finishedItemId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ITEM_NOT_FOUND", "완제품이 없습니다."));
        if (!finished.isActive() || finished.getItemType() != ItemType.FINISHED_GOOD) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_FINISHED_ITEM", "활성 완제품만 생산지시할 수 있습니다.");
        }
        BomEntity bom = boms.findByFinishedItemId(finished.getId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "BOM_NOT_FOUND", "등록된 BOM이 없습니다."));
        List<BomComponentEntity> recipe = components.findByBomIdOrderByRawItemIdAsc(bom.getId());
        if (recipe.isEmpty()) {
            throw new ApiException(HttpStatus.CONFLICT, "EMPTY_BOM", "원자재가 없는 BOM은 사용할 수 없습니다.");
        }
        // 필요한 모든 수량을 저장 전에 계산한다. 곱셈 오버플로 시 어떤 행도 쓰지 않는다.
        List<Requirement> required = recipe.stream().map(c -> {
            try {
                return new Requirement(c.getRawItemId(), Math.multiplyExact(c.getQtyPerUnit(), command.plannedQty()));
            } catch (ArithmeticException ex) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "MATERIAL_QUANTITY_OVERFLOW",
                        "생산지시에 필요한 자재 수량이 허용 범위를 초과합니다.");
            }
        }).toList();

        WorkOrderEntity order = orders.saveAndFlush(WorkOrderEntity.draft(
                "WO-" + UUID.randomUUID(), command.clientRequestId(), command.payloadHash(),
                finished.getId(), bom.getId(), command.plannedQty()));
        List<WorkOrderMaterialEntity> snapshots = required.stream()
                .map(q -> WorkOrderMaterialEntity.snapshot(order.getId(), q.itemId(), q.requiredQty()))
                .toList();
        materials.saveAllAndFlush(snapshots);
        return WorkOrderResponse.from(order, snapshots);
    }

    private record Requirement(Long itemId, long requiredQty) {}
}
