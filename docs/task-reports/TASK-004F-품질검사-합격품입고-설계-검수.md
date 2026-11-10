# TASK-004F — 품질검사 확정과 합격품 재고 반영

> 상태: 구현 코드 작성 / 실제 CI 결과는 별도 검증 후 갱신

## 목적
생산 완료(AWAITING_QC) 전에는 완제품 재고가 생기지 않도록 하며, 검사 확정 시 합격 수량만 LOT에 적립한다. 불합격 수량은 검사 이력에만 기록한다.

## 호출 순서와 파일 책임
- POST /api/v1/work-orders/{workOrderId}/inspection → QualityInspectionController → QualityInspectionService → ItemRepository(완제품 행 잠금) → WorkOrderRepository(생산지시 행 잠금) → QualityInspectionRepository → InventoryLotRepository → StockMovementRepository → WorkOrder 상태 변경.
- GET /api/v1/work-orders/{workOrderId}/inspection → 조회 Service → 품질검사 저장소 → DTO 응답.
- ConfirmInspectionRequest: 요청 수량 Bean Validation. QualityInspectionEntity: 한 번 확정된 검사 결과. InventoryLotEntity: QC_PASS 완제품 LOT 생성. StockMovementEntity: 실제 재고 증가 이력 기록.

## 핵심 설계 이유
1. **재고 반영 시점 분리**: 생산 완료는 검사 대기를 뜻하고, 합격품만 물리적 완제품 재고에 반영한다.
2. **단일 DB 트랜잭션**: 검사 기록, 완제품 LOT, FINISHED_RECEIPT 이력, 생산지시 COMPLETED 전이가 전부 확정되거나 전부 롤백된다.
3. **Item → WorkOrder 잠금**: 동일 완제품에 대한 다른 재고 쓰기 명령과 잠금 순서를 공유하며, 중복 검사 동시 실행 시 상태 재검증으로 방어한다.
4. **DB UNIQUE**: quality_inspections.work_order_id와 (item_id, lot_code) 고유키가 최종 안전망 역할을 한다.
5. **합격 0개 처리**: 합격 수량이 0인 경우 품질검사·상태만 저장하고 0수량 LOT/이동 이력은 만들지 않는다.
6. **정수 연산**: Math.addExact로 검사 수량 합계 오버플로를 감지한다.

## 장점과 단점
- 장점: 검사 전 제품 출고 차단, 중복 검사 방지, 데이터 원자성, 생산별 LOT 추적.
- 단점: 검사 결과 정정과 재작업을 허용하지 않는 MVP 단순화. 생산지시 단위 검사 1건만 지원.
- 단점: 동일 완제품 Item 행 선잠금은 높은 동시성에서 경합을 유발할 수 있다.

## 예외 및 테스트 대상
- 정상: 합격 47/불합격 3 → 완제품 47개 생성, 이동 이력 1건, 상태 COMPLETED.
- 전량 불합격: 합격 0 → LOT 없음, 검사 결과 확정.
- 합계 불일치·음수·오버플로: 400, DB 무변경.
- DRAFT/RESERVED/IN_PROGRESS에서 검사 및 동일 검사 재실행: 409.
- 중복 동시 검사: 성공 1건, 실패 1건, 재고 한 번만 반영.
- UNIQUE 오류 주입: 검사/LOT/상태 전체 롤백.
- 실제 CI 검증 후 결과를 추후 이 문서에 기재.

## 리팩터링 판단
- 즉시: CI 실패가 발생하면 테스트/구현을 우선 수정한다.
- 2차: 검사 결과 정정·재작업/스크랩 모델, 시간 주입 Clock, DTO 응답 구조의 표현 일관성.
- 성능 실측 후: Item 선잠금과 LOT 잠금 최적화.
- 지금 보류: 이벤트 발행·Kafka·분산 트랜잭션.

## 이후 학습용 요약
검사 대기와 출고 가능 상태를 분리하고, 합격품 입고를 검사 확정과 같은 트랜잭션에 묶어 품질 검증 전 재고가 외부로 나가는 문제를 막는다.
