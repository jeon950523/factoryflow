# TASK-004G — 완제품 출고·LOT 차감·멱등성 구현 검수

> 상태: GitHub Actions Java 21 / Testcontainers MySQL CI **45/45 통과** (2026-11-11)

## 목적
품질검사 합격 제품을 FIFO LOT 단위로 출고하고, 네트워크 재전송·동시 요청에도 실물 재고와 출고/이동 기록의 정합성을 유지한다.

## 전체 호출 순서 및 파일 책임
1. POST /api/v1/shipments → ShipmentController: JSON 입력/HTTP 201 또는 중복 200.
2. ShipmentService → CanonicalShipment: 요청 키 정규화·payload SHA-256 비교; 기존 출고 조회.
3. ShipmentWriteService: MySQL READ_COMMITTED 트랜잭션, 완제품 Item 행 선잠금.
4. InventoryLotRepository: 동일 품목 LOT를 receivedAt, id 순서로 잠그고 QC_PASS 수량만 차감.
5. ShipmentRepository + ShipmentAllocationRepository + StockMovementRepository: 출고 기록·배분 기록·FINISHED_SHIPMENT 이력을 같은 트랜잭션에 저장.
6. ShipmentLookupService: 재전송 응답 또는 GET 시 출고와 LOT 할당 내역 재구성. UNIQUE 충돌 후 새 트랜잭션에서 확인.

## 핵심 코드 선택 이유
- **LOT 출고 도메인 메서드:** InventoryLotEntity.shipFinished가 QC_PASS 여부, 양수 수량, 가용량 한도를 검사하므로 Service 외부에서 수량을 임의 변경하지 않는다.
- **Item 선잠금:** 품질검사/출고가 같은 품목을 수정할 때 정합성을 단순하고 일관된 순서로 보호. 대가로 동일 품목 출고 처리량이 제한될 수 있다.
- **부분 출고 금지:** 부족 수량을 발견하면 예외로 전체 트랜잭션 롤백한다. API 요청된 수량과 실제 출고 수량이 달라지는 모호함을 제거한다.
- **requestKey + payloadHash:** 200 재응답/409 다른 내용 충돌. 검사 + DB UNIQUE의 이중 방어.
- **별도 Writer/Lookup Bean:** 실패한 UNIQUE 삽입 트랜잭션을 끝내고, 오케스트레이터가 새 읽기 트랜잭션에서 기존 출고를 비교할 수 있다.
- **재고 이동 이력:** 재고 잔량뿐 아니라 어떤 출고 요청에서 어느 LOT가 차감됐는지 추적한다.

## 실제 검증
- GitHub Actions: https://github.com/jeon950523/factoryflow/actions/runs/42925260962
- JUnit XML: **45 tests / 0 failures / 0 skipped**. 신규 FinishedShipmentIntegrationTest 5개 모두 통과.
- 시나리오: FIFO 2 LOT 배분, 같은 requestKey 재전송·다른 수량 충돌, 부족 시 원자적 롤백, 두 요청 동시 출고 초과 할당 방지, 잘못된 품목/수량 거부.

## 시니어 검수 — 장단점·리팩터링
- 유지: DB 트랜잭션 원자성, LOT 할당 추적, 명령/조회 분리, DB UNIQUE/락 조합.
- 위험: 모든 해당 품목 LOT을 잠그는 쿼리는 대규모 LOT 환경에서 병목 가능. 실제 성능 실험으로 개선 여부 결정.
- 지금 당장 추가할 검증: 원자재 입고부터 품질검사·완제품 출고까지 한 번에 호출하는 E2E 회귀 시나리오. 인증 전에 공개 배포 금지.
- 후속 리팩터링: StockReceipt/Shipment/WorkOrder 멱등성 정규화 로직의 공통화(실제 중복 확인 후); 시간 주입 Clock; LOT 잠금 최적화; 출력 DTO와 API 문서 정합성.
- 추후 기능: 출고 취소·반품·부분 출고·다중 창고 및 권한 분리. MVP 범위에서 제외.
- **근거 없는 최적화 보류:** Redis 락·Kafka·MSA 추가하지 않는다.

## 나중에 학습할 핵심 질문
1. 같은 requestKey 재전송에서 왜 201이 아니라 200으로 기존 출고를 반환하는가?
2. LOT를 2개 차감한 뒤 3번째에서 부족하면 왜 앞의 2개도 롤백해야 하는가?
3. 서비스에서 재고 수량을 확인했어도 왜 비관적 락과 DB CHECK가 필요한가?
