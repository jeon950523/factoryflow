# TASK-004E — 생산 시작/원자재 실제 소모 및 생산 완료

> 상태: 구현 및 정적 검사 완료 / MySQL 통합 CI 검증 대기

## 목적
- `RESERVED`로 확보한 원자재를 생산 시작할 때 실제 소모하고, 이동 이력을 남기며 상태를 `IN_PROGRESS`로 전환한다.
- 생산 완료는 `AWAITING_QC`로만 전환하며 합격품 재고 반영은 아직 수행하지 않는다.

## 호출 흐름
1. `POST /api/v1/work-orders/{id}/start` → `WorkOrderController` → `WorkOrderProductionService.start` (READ_COMMITTED 트랜잭션).
2. 불변 생산지시 자재 스냅샷 조회 → Item ID 오름차순 PESSIMISTIC_WRITE → WorkOrder 행 PESSIMISTIC_WRITE / `RESERVED` 재검증.
3. `RESERVED` 예약을 LOT ID 순으로 조회해 스냅샷의 필요 총량과 일치하는지 확인.
4. 각 LOT PESSIMISTIC_WRITE → `on_hand -= quantity`, `reserved -= quantity` → 예약 `CONSUMED` → `RAW_CONSUMPTION` 재고 이동 기록.
5. `work_orders.status=IN_PROGRESS` → 전체 commit. 중간 한 단계라도 실패하면 전체 rollback.
6. `POST /api/v1/work-orders/{id}/finish`는 WorkOrder 행을 잠그고 `IN_PROGRESS → AWAITING_QC`만 수행한다.

## 파일별 역할/선택 이유
- `production/WorkOrderProductionService`: 트랜잭션과 잠금 순서의 소유자, 기록 누락/초과 소모 방지.
- `production/WorkOrderController`: HTTP 엔드포인트와 응답 연결. DB 쓰기 금지.
- `production/WorkOrderEntity`, `MaterialReservationEntity`: 유효한 상태에서만 상태 전이시키는 메서드.
- `inventory/InventoryLotEntity`: 예약분만 현재고와 예약고에서 함께 차감하고 불변조건 유지.
- `inventory/StockMovementEntity`: `WORK_ORDER` 기준 `RAW_CONSUMPTION` 이력을 기록해 감사 추적 가능.
- `WorkOrderProductionIntegrationTest`: 정상/불변조건/오류주입 rollback/동시 시작을 MySQL 실제 컨테이너로 검증하도록 작성.

## 장점과 단점
- **장점**: 상태 전이와 물리 재고 이동을 분리하고, 소모·이력·상태를 원자성 있게 보존. 예약 취소 로직과 충돌하지 않는 Item 선잠금 순서.
- **단점**: 같은 품목의 입고/예약/소모가 직렬화돼 고경합에서는 처리량이 낮아짐. 실제 제조의 부분 실적/재작업/스크랩을 다루지 않음.
- **대안 비교**: 생산 시작에 바로 LOT을 잠그는 방식은 Item 선잠금보다 동시 처리량 가능성이 있지만, 현재 입고·예약과 잠금 순서가 달라지므로 데드락 위험 재검증이 필요하다.

## 예외/검증
- 이미 시작된 작업 재시작·취소는 409.
- 예약이 누락되거나 BOM 스냅샷 합계와 다르면 409, 쓰기 없음.
- 이동 이력 DB UNIQUE 오류를 인위적으로 주입해, LOT 현재고·예약고·예약 상태·생산지시 상태가 모두 롤백되는지 테스트.
- 두 요청이 같은 생산지시를 동시에 시작할 때 실제 소모가 한 번만 발생하는지 확인.

## 리팩터링 판단
- **즉시 검증**: 실제 CI Testcontainers MySQL/전체 회귀 테스트 통과, 상태 전이 원자성 확인.
- **후속 리팩터링**: `WorkOrderReservationService`와 잠금 순서가 중복되므로 업무 기능 추가 후 `WorkOrderLockCoordinator` 추출 검토. 추상화보다 잠금 순서의 일관성이 중요.
- **성능 실측 후**: Item 선잠금 축소·LOT 쿼리 키셋 조회, 배치 INSERT로 이동 로그 처리 비교.
- **2차 개선**: 생산 실적 수량/부분 실패·불량 스크랩, 반납·재작업 업무, 고경합 락 타임아웃 정책.

## 학습 정리
- [`핵심 기술 개념 모음`](../learning/CORE_CONCEPTS.md)에 추가. 사용자 학습은 구현·검증을 끝낸 뒤 진행.
