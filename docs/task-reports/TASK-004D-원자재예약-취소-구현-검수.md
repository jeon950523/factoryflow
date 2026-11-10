# TASK-004D — 원자재 전량 예약 / 예약 취소 구현·검수

> 상태: 구현 초안 완료 / GitHub Actions CI 검증 진행 전

## 목적 / 기존 시스템 연결
- 생산지시 DRAFT에 복사된 BOM 스냅샷을 기준으로, 물리 현재고는 변경하지 않고 LOT별 예약 수량만 반영한다.
- 출고·소모의 전단계로, 경쟁 상태에서도 모든 원자재가 확보되었을 때만 생산지시를 RESERVED로 전환한다.

## 호출 순서 / 역할
- `POST /api/v1/work-orders/{id}/reserve` → `WorkOrderController` → `WorkOrderReservationService.reserve()` → `ItemRepository.findForUpdate()`(ID 오름차순) → `WorkOrderRepository.findForUpdate()` → `InventoryLotRepository.findAllForReservation()`(FIFO, PESSIMISTIC_WRITE) → `MaterialReservationRepository.saveAllAndFlush()` → 상태 전환 → 응답.
- `POST /api/v1/work-orders/{id}/cancel` → 동일 순서로 아이템·생산지시 잠금 → (RESERVED이면) 활성 예약마다 LOT을 잠가 해제 → 상태 CANCELED.

## 왜 이렇게 구현했는가
1. **품목 선잠금**: 입고 명령도 Item을 먼저 잠그므로, 입고/예약/취소의 공통 잠금 순서를 유지해 갱신 분실 및 데드락 가능성을 줄인다.
2. **LOT FIFO**: 단일 창고·EA 수량 MVP에서 수량 배분의 기준을 일관되게 설명하고 LOT 이력을 보존할 수 있다.
3. **동일 트랜잭션 + `READ_COMMITTED`**: 두 번째 품목이 부족하면 첫 번째 품목 예약도 롤백. 행 잠금과 상태 재검증으로 동시에 들어온 상태 변경을 처리한다.
4. **예약과 물리 차감 분리**: 예약은 `reserved_qty`만 바꾸고 `on_hand_qty`는 생산 시작에서 바꾸므로 실제 자재 사용과 계획 수량의 의미가 명확하다.
5. **중복 명령**: reserve/cancel은 일회성 상태 전이 명령이며 같은 명령 재호출은 HTTP 409로 거부한다(멱등 키 기반 재조회는 후속 검토).

## 장점
- 비관적 잠금과 행 수준 불변조건으로 초과 예약을 방지하도록 설계.
- 자재별 전량 예약 실패 시 rollback. LOT 수량과 예약 기록의 원자성.
- 상태 변경 및 예약 이력이 남아 취소 후 원인 추적 가능.

## 단점·위험
- Item 선잠금은 같은 품목에 대한 입고/예약/취소 처리량을 제한한다. 테스트 후 범위 최적화 검토.
- `findAllForReservation()`은 LOT이 많아지면 재고가 없는 LOT까지 읽고 잠글 수 있다. 후속 성능 실험에서 필터/키셋 조회 검토.
- 상태 변경 명령에 멱등 키가 없어 네트워크 재전송 시 409. 1차 계약에 맞지만 사용자 친화적 자동 재시도는 별도 설계 필요.
- MySQL 데드락/락 타임아웃을 DB 동작과 함께 검증해야 한다.

## 검증 대상(통합 테스트)
- FIFO 2개 LOT 할당, 현재고 변경 없음, 이동 로그 생성 안 함.
- BOM 2종 중 두 번째 원자재 부족 → 첫 번째 예약 포함 전체 롤백.
- RESERVED 취소 → 예약 해제, 현재고 불변, RELEASED 이력 보존, 재예약 가능.
- DRAFT 취소 → 예약 내역 없음; 중복 명령 409.
- 서로 다른 생산지시 2건 동시 예약 → 같은 10개 재고를 8+8로 중복 예약하지 않음.
- 같은 생산지시 중복 예약과 예약/취소 경쟁 → 상태 검증 및 잔여 예약 0.

## 후속 리팩터링 결정
- **현재 구현에서 우선 확인**: CI의 MySQL Testcontainers 결과와 실제 Spring JPA PESSIMISTIC_WRITE 동작.
- **완료 후 검토**: 공통 Item 잠금 순서를 여러 서비스에서 강제하는 헬퍼/도메인 서비스(중복이 실제로 발생할 때만).
- **성능 측정 후**: LOT 필터링, 잠금 범위 조정, 낙관적 락 또는 조건부 UPDATE 비교.
- **지금 보류**: Redis 락, Kafka, MSA, 전용 분산 트랜잭션.

## 학습/면접 대비
- [`docs/learning/CORE_CONCEPTS.md`](../learning/CORE_CONCEPTS.md)에 누적. 사용자 학습은 기능 구현 뒤 진행.
