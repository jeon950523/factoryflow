# FactoryFlow TASK-003B — 원자재 LOT 입고와 멱등성 구현

> 상태: **코드 작성 및 정적 검수 완료 / Gradle·MySQL 통합 검증 대기**  
> 전제: TASK-002/003A의 Gradle 및 Testcontainers 실검증 완료 필요.

## 목적

원자재 입고 API를 구현해 품목/LOT별 실제 현재고를 변경하고 같은 트랜잭션으로 입고 내역·재고 이동 이력을 남긴다. 네트워크 재전송으로 같은 입고가 중복 반영되지 않아야 한다.

## 요구 API

- `POST /api/v1/stock-receipts`: `requestKey`, `itemId`, `lotCode`, `quantity` 입력; 최초 201, 같은 키·같은 내용 재조회 200, 같은 키·다른 내용 409.
- `GET /api/v1/inventory`: 품목별 현재고·예약고·가용재고 집계. `onHand - reserved` 서버 계산.
- `GET /api/v1/inventory/lots`: 품목의 LOT 목록 및 추적 정보.
- `GET /api/v1/stock-movements`: 이동 이력.

## 구현 규칙

1. `requestKey`는 동일 요청을 식별하는 유일키; 정규화된 요청 본문으로 `payload_hash`를 생성한다. 다른 키로 동일 입고가 오는 것은 별개 업무 요청으로 처리한다.
2. `Item` 행을 `PESSIMISTIC_WRITE`로 잠근 뒤 원자재 타입·활성 여부 검사.
3. 동일 LOT가 있으면 잠금 후 `Math.addExact`로 누적수량/현재고 증가. 없으면 새로운 LOT 생성.
4. 입고·LOT 수량 변화·`RAW_RECEIPT` 이동은 하나의 Service 트랜잭션으로 처리.
5. 동시 `requestKey` 충돌은 DB UNIQUE로 차단. 실패 트랜잭션 내부에서 기존 요청을 다시 읽지 말고 **새 트랜잭션**에서 요청 해시를 조회해 200/409 처리.
6. `CHECK(reserved_qty <= on_hand_qty)`와 DB UNIQUE는 최종 방어이며, 락 정책을 대체하지 않음.
7. MySQL/Testcontainers로 2개 다른 요청이 같은 LOT에 동시 입고될 때 합산이 정확한지 확인.

## 필수 테스트

- 신규 원자재 LOT 입고, 기존 LOT 추가 입고, 0/음수/오버플로 수량 거부
- 원자재 아닌 품목 입고 거부, 존재하지 않는 품목/LOT 조회 404
- 같은 `requestKey` 순차 2회 및 병렬 2회 시 한 번만 입고
- 다른 내용으로 같은 키 전송 409
- DB 이동 이력 삽입 실패 주입 시 입고·LOT 수량 모두 롤백
- 동시 다른 키가 같은 LOT에 입고될 때 누적 재고와 이동 로그 합계 일치

## 이 작업에서 제외할 것

- 생산 예약/소모, 완제품 QC 입고, 출고, 다중 창고, Redis, Kafka.

## 완료 기준

실제 MySQL 8.4에서 테스트 전체 통과 증거, 파일별 호출 흐름·설계 이유·장단점·리팩터링 항목을 `docs/task-reports/TASK-003B-원자재입고-검수.md`로 기록한다. 미검증은 미검증으로 표기한다.

## 구현 결과 참고

- [TASK-003B 작업별 검수 보고서](../docs/task-reports/TASK-003B-원자재입고-구현-검수.md)
- [코드 학습 가이드](../docs/task-reports/TASK-003B-코드학습-가이드.md)
- 런타임 검증 미통과 상태에서는 완료 체크 금지.
