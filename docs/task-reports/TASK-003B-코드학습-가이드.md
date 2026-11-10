# FactoryFlow TASK-003B 코드 학습 가이드

> 이 문서는 **구현된 코드가 어떻게 동작하는지**를 학습하기 위한 자료다. 통합 테스트 PASS를 의미하지 않는다.

## 1. 먼저 알아둘 세 가지 데이터

- `inventory_lots`: 현재 창고에 있는 실제 수량(`onHandQty`)과 예약된 수량(`reservedQty`), LOT별 누적 입고량(`cumulativeInQty`)을 가진다.
- `stock_receipts`: '어떤 요청'이 '어느 품목/LOT'에 '몇 개'를 입고했는지 남긴다. `requestKey`를 UNIQUE로 묶어 재시도를 식별한다.
- `stock_movements`: 물리 재고가 왜 증가하거나 감소했는지에 대한 변경 이력. 입고 시 `RAW_RECEIPT`, `referenceType=RECEIPT`, `referenceId=입고 ID`를 남긴다.

예를 들어 LOT-X에 10개 입고하고, 다른 요청으로 5개 추가 입고하면 `onHand=15`, `reserved=0`, `available=15`, 입고 이력 2건, 이동 이력 2건이다.

## 2. 파일과 호출 흐름

1. `StockReceiptController`: POST JSON 수신 후 응답을 201/200으로 분기한다.
2. `CanonicalReceipt`: LOT/요청키를 대문자로 통일하고 해시를 만들어 동일 요청인지 비교한다.
3. `StockReceiptService`: 사전 중복 조회 → 실제 쓰기 위임 → UNIQUE 충돌 시 실패 트랜잭션 외부에서 재조회한다.
4. `StockReceiptWriteService`: `@Transactional(READ_COMMITTED)` 적용, `ItemRepository.findForUpdate`로 잠근 후 LOT 수량, Receipt, Movement를 저장한다.
5. `StockReceiptLookupService`: 이미 성공한 요청을 같은 응답으로 조회하거나, 다른 본문일 경우 409를 반환한다.
6. `InventoryReadService`: 재고 합계는 SQL `SUM()`, LOT/이력은 JPA 페이지 조회로 처리한다.

## 3. 왜 이렇게 했는가? 핵심 개념

### 트랜잭션의 원자성

'재고가 +10 되었지만 이동 이력이 없다'는 상황은 운영자가 재고의 변동 원인을 추적할 수 없다는 뜻이다. 그래서 LOT 수량 변경 + Receipt 저장 + Movement 저장을 묶어 하나라도 실패하면 전부 취소한다.

### 잠금(PESSIMISTIC_WRITE)

두 요청이 동시에 현재고 10을 읽어 각자 +5 한 뒤 15를 쓰면 결과가 15로 남는 **갱신 분실(lost update)** 가능성이 있다. 품목 행을 먼저 잠가 같은 품목의 쓰기 요청을 직렬화하면 수량을 순서대로 갱신해 최종 20이 된다. 다만 같은 품목의 다른 LOT 입고까지 직렬화되어 처리량은 낮아질 수 있다.

### 멱등성

HTTP 클라이언트가 입고 요청을 보내고 응답을 받기 전에 네트워크가 끊길 수 있다. 서버는 이미 처리했는데 사용자가 재시도하면 입고가 두 번 반영될 위험이 있다. `requestKey`로 한 번 처리된 요청을 찾고, 본문 해시까지 같으면 원래 응답을 반환한다. 같은 키로 내용이 바뀌면 409를 반환해야 한다.

### 데이터베이스 UNIQUE가 꼭 필요한 이유

'요청키가 없다'는 사전 조회는 **동시성 보장이 아니다**. 두 요청이 같은 순간에 모두 '없다'고 읽을 수 있다. 그래서 MySQL UNIQUE가 유일성을 마지막에 강제한다. UNIQUE 충돌 후 재조회는 쓰기 트랜잭션이 실패해 끝난 다음 별도 트랜잭션에서 진행한다.

### 왜 `Math.addExact`를 쓰는가?

Java `long` 최대치(9,223,372,036,854,775,807)에 1을 더하면 일반 덧셈은 음수로 순환한다. `Math.addExact`는 예외를 발생시켜 이 오류를 명시적으로 거부한다. 누적고와 현재고는 **둘 다 계산한 후** 대입해야 부분 변경을 피한다.

## 4. 장점·단점·리팩터링 시점

| 결정 | 장점 | 단점 | 언제 바꿀지 |
|---|---|---|---|
| Item 선잠금 | 복잡한 LOT 경쟁 제어가 단순 | 품목별 병렬 처리 감소 | 동시성/성능 측정 후 |
| `READ_COMMITTED` | 락 이후 재조회가 최신 커밋을 관찰 | 기본 격리수준과 차이 | 실제 MySQL 경쟁 테스트 후 확인 |
| DTO 분리 | 엔티티 무분별한 노출 방지 | 코드 수 증가 | 유지, 불필요한 추상화는 피함 |
| `NamedParameterJdbcTemplate` 집계 | SQL로 LOT 합계 계산 | JPA+JDBC 혼합 | 조회 복잡도 커질 때 분리 |
| 3개 Service 역할 분리 | 실패 트랜잭션/재시도 경계 선명 | 계층 수 증가 | 재검토 후에도 필요하면 유지 |

## 5. 확인 방법

GitHub의 `main`은 아직 문서 위주이고, 작성된 코드는 **TASK-002 → TASK-003A → TASK-003B 순서**로 반영해야 한다. 로컬에 Java 21·Docker Desktop(또는 Docker Engine)이 준비되어야 한다.

저장소 루트 PowerShell에서:

```powershell
docker compose --env-file .\infra\.env -f .\infra\compose.yml up -d
```

```powershell
cd .\backend; .\gradlew.bat --no-daemon clean test
```

테스트 성공 시 단순 HTTP 201만 보지 말고 `inventory_lots`, `stock_receipts`, `stock_movements`의 레코드 수와 값이 일치하는지 확인해야 한다. 현재 환경에서는 이 테스트를 실제 실행하지 못했다.

## 6. 짧게 설명하기

> 이번 작업에서는 재고의 실제 수량과 입고·이동 이력을 같은 트랜잭션에서 기록하도록 설계했습니다. 같은 요청이 두 번 오더라도 멱등성 키와 DB UNIQUE로 수량이 중복 증가하지 않도록 했고, 여러 요청이 같은 LOT를 변경하면 품목 선잠금으로 처리 순서를 보장합니다. 이 방식은 안전성을 설명하기 쉽지만 품목별 처리량이 낮을 수 있어 추후 성능 실험으로 개선할 예정입니다.
