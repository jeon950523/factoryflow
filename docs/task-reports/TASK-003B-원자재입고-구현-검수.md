# FactoryFlow TASK-003B — 원자재 LOT 입고 구현·설계 판단·검수 보고서

> 날짜: 2026-11-11  
> **상태: 코드 작성 및 정적·독립 Java 검수 완료 / Gradle·MySQL 통합 검증 대기**  
> 기준: `docs/FACTORYFLOW_SSOT_v0.1.md`, `tasks/FACTORYFLOW_TASK_003B_입고_LOT_멱등성.md`  
> 코드 기준: **미커밋·GitHub main 미반영**. 기존 TASK-002 전체 소스 + TASK-003A 변경 파일 위에 적용.  
> 검수 범위: AST 문법 검사, 규칙 독립 실행, SQL/매핑 정적 비교. 실제 DB 동시성 및 프레임워크 동작은 아직 확인하지 못함.

## 1. 이 기능의 목적

공장 창고에 원자재가 입고될 때 해당 품목의 LOT 재고를 누적하고, 입고 요청·물리 재고 이동 이력을 함께 기록한다. 네트워크 타임아웃 후 같은 요청을 재전송해도 재고가 이중 증가하지 않게 한다. `Item`/BOM 등록만 있던 TASK-003A와 연결되는 최초의 **재고 쓰기** 기능이다.

**비범위:** 생산지시 예약·소모, 품질검사 합격품 입고, 출고, 다중 창고, 인증, Kafka·Redis·MSA. 사용자별 권한이 없는 상태이므로 로컬 시연 전용이다.

## 2. 구현과 검증 상태

| 항목 | 코드 작성 | 실행 검증 |
|---|---|---|
| `POST /api/v1/stock-receipts` 최초 201·동일 키 200·다른 내용 409 | 완료 | **Spring 통합 미검증** |
| `GET /api/v1/stock-receipts/{id}` | 완료 | **Spring 통합 미검증** |
| `GET /api/v1/inventory` 재고 요약/페이지 | 완료 | **Spring 통합 미검증** |
| `GET /api/v1/inventory/lots`, `GET /lots/{lotId}` | 완료 | **Spring 통합 미검증** |
| `GET /api/v1/stock-movements` | 완료 | **Spring 통합 미검증** |
| 재고/입고/이동 단일 트랜잭션, Item/LOT 잠금 | 완료(의도) | **MySQL 실검증 필요** |
| 동일 키 경쟁, 다른 키 LOT 경쟁, 강제 실패 롤백 테스트 | 테스트 코드 작성 | **실행 대기** |
| 정규화·해시/수량 규칙 독립 Java 검사 | 완료 | **직접 실행 통과** |

## 3. 전체 호출 순서 및 트랜잭션 경계

```text
POST /api/v1/stock-receipts (requestKey, itemId, lotCode, quantity)
  ↓ StockReceiptController.receive: JSON/Bean Validation
  ↓ StockReceiptService.receive: CanonicalReceipt.of (입력 정규화·SHA-256 요청 해시)
  ↓ StockReceiptLookupService.findSameOrConflict: 사전 중복 검사 (새 읽기 트랜잭션)
      ├─ 같은 키·동일 해시 → 기존 입고 응답 200 (쓰기 없음)
      └─ 같은 키·다른 해시 → 409 (쓰기 없음)
  ↓ StockReceiptWriteService.write: @Transactional(READ_COMMITTED)
      ↓ ItemRepository.findForUpdate: Item 행 PESSIMISTIC_WRITE 잠금
      ↓ 품목 존재·활성·RAW_MATERIAL 검사
      ↓ StockReceiptRepository.findByRequestKey 재검사 (락 획득 후)
      ↓ InventoryLotRepository.findForUpdate: 같은 LOT 잠금 (없으면 신규 저장)
      ↓ InventoryLotEntity.receiveAdditionalRawMaterial / receiveNewRawMaterial
      ↓ StockReceiptRepository.saveAndFlush: 입고 내역 INSERT
      ↓ StockMovementRepository.saveAndFlush: RAW_RECEIPT 이동 로그 INSERT
      ↓ 전부 성공하면 COMMIT → 최초 응답 201
      └─ 하나라도 실패하면 ROLLBACK
  ↓ 동시 UNIQUE 충돌은 writer 트랜잭션 종료 후 Service가 예외 수신
      ↓ StockReceiptLookupService (REQUIRES_NEW)로 재조회
      ├─ 동일 해시 → 200
      ├─ 다른 해시 → 409
      └─ 요청 없음(다른 DB 장애) → 원래 예외 재전파
```

- **중요:** `@Transactional`은 별도 Bean인 `StockReceiptWriteService`에 붙여 Spring 프록시를 지나가도록 했다. 같은 클래스 내부 호출의 자기 호출(`this.write()`) 방식으로는 트랜잭션 경계가 적용되지 않을 수 있다.
- **잠금:** 같은 Item ID를 쓰는 요청은 하나씩 진행하도록 품목 행을 선잠금한다. 이후 LOT 행도 잠금한다. 이것이 높은 처리량보다 **일관성 확인 가능성**을 선택한 지점이다.
- **현재고:** 새 입고는 `onHand += qty`, `cumulativeIn += qty`이고 `reserved`는 그대로. 집계 화면은 `available = onHand - reserved`를 서버에서 계산한다.

## 4. 변경 파일별 역할과 설계 근거

| 파일 | 역할 / 기존 연결점 | 왜 이렇게 했는가 |
|---|---|---|
| `inventory/StockReceiptController.java` | 입고 생성·단건 조회 | HTTP 상태(201/200/409)와 Location 연결점 |
| `inventory/StockReceiptService.java` | 트랜잭션 **밖** 재시도 조정 | 실패한 트랜잭션 안에서 UNIQUE 재조회하지 않게 분리 |
| `inventory/StockReceiptWriteService.java` | 입고 단일 트랜잭션 | 재고·입고·이동 원자성 및 잠금 순서 통일 |
| `inventory/StockReceiptLookupService.java` | 키 조회·기존 결과 반환 | `REQUIRES_NEW`의 명확한 재조회 경계 |
| `inventory/InventoryReadService.java` | 재고 합계·LOT·이동 이력 읽기 | `SUM`은 DB로 집계해 전체 LOT를 메모리로 읽지 않음 |
| `inventory/InventoryController.java`, `StockMovementController.java` | 조회 API | 쓰기 서비스와 분리된 조회 책임 |
| `inventory/validation/CanonicalReceipt.java` | 키·LOT 정규화, payload SHA-256 | MySQL CI collation과 대소문자 차이에서 생기는 중복 혼란 방지 |
| `inventory/InventoryLotEntity.java` | 새 LOT 생성, 기존 LOT 누적 | `Math.addExact`로 overflow 및 부분 변경 방지 |
| `inventory/StockReceiptEntity.java` | 입고 데이터와 해시 기록 | UNIQUE 키를 통한 멱등성 근거 보존 |
| `inventory/StockMovementEntity.java` | 원자재 입고 이벤트 로그 | 수량 증감 근거를 별도 불변 이력으로 저장 |
| `inventory/*Repository.java`, `item/ItemRepository.java` | JPA 쓰기/잠금/페이징 | 역할별 Repository 유지, `PESSIMISTIC_WRITE` 명시 |
| `inventory/dto/*.java`, `ReceiptOutcome.java` | HTTP 응답 및 생성/중복 여부 | 엔티티 직접 노출 방지, 201/200 분기 |
| `StockReceiptApiIntegrationTest.java` | MockMvc + Testcontainers 시나리오 | 실제 HTTP/DB 경로로 수량 정합성 확인 예정 |
| `StockReceiptRollbackIntegrationTest.java` | 이동 저장 실패 주입 | 트랜잭션 원자성 확인 예정 |

## 5. 핵심 코드 설명 — 이유까지

### 5-1. 중복 요청을 트랜잭션 바깥에서 재확인

```java
try {
    return writer.write(command);
} catch (DataIntegrityViolationException exception) {
    return lookup.findSameOrConflict(command).orElseThrow(() -> exception);
}
```

`existsByRequestKey`와 같은 사전 검사만으로는 두 요청이 동시에 통과할 수 있다. DB `UNIQUE(request_key)`가 마지막으로 막는다. 위 코드는 **실패한 쓰기 트랜잭션이 종료된 뒤** 저장된 요청을 새 트랜잭션에서 읽어 응답을 결정한다. 조회된 기존 요청이 없으면 유일키 충돌이 아닌 DB 오류일 수 있으므로 예외를 감추지 않는다.

**장점:** 네트워크 재전송 시 2중 입고를 방지하고, 사용자에게 200(기존 결과) 또는 409(다른 본문)를 구별해 반환.  
**단점:** 서비스 클래스가 세 개로 나뉘고 추가 DB 조회 비용이 듦. 재시도 경계가 더 중요하므로 MVP에서는 유지.

### 5-2. 원자재 입고 수량은 계산 완료 후 대입

```java
long nextCumulative = Math.addExact(cumulativeInQty, quantity);
long nextOnHand = Math.addExact(onHandQty, quantity);
cumulativeInQty = nextCumulative;
onHandQty = nextOnHand;
```

`long` 오버플로는 일반 `+`에서는 조용히 음수로 돌아갈 수 있다. 두 값을 먼저 계산한 뒤 대입해야 두 번째 계산이 실패할 때 첫 번째 필드만 바뀌는 문제가 없다. 오버플로는 `QUANTITY_OVERFLOW` 400으로 응답한다. `reservedQty`는 입고 시 변하지 않는다.

**장점:** 경계값 방어가 명시적이다.  
**단점:** 긴 수량을 계속 쓰는 정책에서 전체 LOT 합계를 `long`으로 표현하기 어렵다. 실제 수량 단위나 규모가 확대되면 `DECIMAL` 고려.

### 5-3. 재고 합계는 SQL에서 처리

`InventoryReadService.summary()`는 `items LEFT JOIN inventory_lots`와 `SUM(on_hand_qty)`, `SUM(reserved_qty)`를 사용한다. LOT가 하나도 없는 품목도 **0개 재고**로 조회된다. `BigDecimal.longValueExact()`를 사용해 합계가 `long` 범위를 넘을 때 값이 조용히 잘리지 않게 했다.

**장점:** 전체 LOT을 Java 메모리로 적재하지 않아 비용 감소.  
**단점:** 조회 계층에 SQL과 JPA가 혼합된다. 조회 전용 쿼리는 SQL이 더 명확하다는 이유로 의도적으로 선택했다. 팀 규모가 커지면 읽기 Repository 인터페이스 분리를 검토.

## 6. 주요 예외·경계·보안

| 상황 | 기대 처리 | 확인 방법 |
|---|---|---|
| 최초 입고 | 201, LOT/Receipt/Movement 반영 | 통합 테스트 `newAndExistingLot...` |
| 같은 `requestKey`, 같은 내용 | 기존 응답 200, 재고 미증가 | `repeatedKey...` |
| 같은 키, 다른 `quantity/itemId/lotCode` | 409, 수량 미변경 | `repeatedKey...` |
| 다른 키, 동일 LOT 동시 입고 | 현재고·누적고 정확한 합산 | `concurrentDifferentKeys...` |
| 같은 키 동시 입고 | 201 1건 + 200 1건, DB 1건 | `concurrentIdenticalKey...` |
| 유효하지 않은 수량(0/음수) | 400 | `invalidReceipts...` |
| `Long.MAX_VALUE` LOT 추가 입고 | 오버플로 400, 기존 수량 불변 | `overflowMustNot...` |
| 비원자재, 존재하지 않는 품목 | 400 / 404 | `invalidReceipts...` |
| 이동 기록 저장 중 실패 | LOT 변경 및 입고 내역 롤백 | `movementFailure...` |
| 미존재 LOT / 입고 ID 조회 | 404 | GET 테스트 |
| 잘못된 코드/문자셋 | 400 | `CanonicalReceipt` 독립 실행 |
| 인증 없음 | 인터넷 공개 금지 | 로컬 `server.address=127.0.0.1` |

> SHA-256 해시는 **동일 요청 판단**에만 사용하며 인증, 악성 조작 탐지, 감사 서명 역할을 하지 않는다.

## 7. 실제 수행한 검증과 수행하지 못한 검증

**환경:** 이 작업 실행 환경 JDK `21.0.12.1`; Gradle 실행 파일 미설치, Wrapper 배포파일 네트워크 DNS 해석 불가, Docker CLI/Engine 없음. 사용자 PC/CI에서의 테스트 결과는 아직 수신하지 못했다.

| 검증 | 실측 결과 | 해석 범위 |
|---|---|---|
| JDK 21 Java Compiler AST 구문 검사 | **Java 60개, 문법 오류 0개** | 문법만 확인. Spring 타입/의존성 검증 아님 |
| `CanonicalReceipt` 규칙 독립 컴파일·실행 | **11개 조건 통과** | 코드 정규화·해시만 확인. Spring/DB 제외 |
| `InventoryLotEntity` 수량 독립 컴파일·실행 | **7개 조건 통과** | `Math.addExact`·수량 불변 규칙만 확인. JPA 제외 |
| SQL 스키마 ↔ JPA 매핑 테이블 명 비교 | **12 대 12 일치** | 실제 DB DDL 실행·Hibernate validate 아님 |
| 5개 YAML 문서 파싱 | **파싱 성공** | Docker Compose/Actions 실제 실행 아님 |
| `./gradlew --offline clean test` | **실패: Gradle 9.5.1 배포 ZIP 다운로드 DNS 실패** | 테스트 수행 자체가 시작되지 않음 |
| Testcontainers MySQL 8.4 / MockMvc / 동시성 | **미실행** | PASS로 기재 금지 |
| GitHub Actions | **미실행** | 저장소 main에 코드가 아직 없음 |
| 성능 p50/p95·처리량 | **미측정** | 성능 개선 완료 주장 금지 |

재현 명령 (**PowerShell, 저장소 루트에서 한 줄씩 실행**):

```powershell
Copy-Item .\infra\.env.example .\infra\.env
```

`.env` 비밀번호 예제값을 로컬에서 교체한 뒤 다음을 실행한다.

```powershell
docker compose --env-file .\infra\.env -f .\infra\compose.yml up -d
```

```powershell
cd .\backend; .\gradlew.bat --no-daemon clean test
```

성공 시 Flyway/MockMvc/Testcontainers 통합 테스트 로그를 저장하고, `docker compose logs mysql`에서 DB 오류가 없는지 확인한다.

## 8. 시니어 기준 검수

| 영역 | 결과 | 근거 / 조치 |
|---|---|---|
| 기능 정확성 | **주의 — 정적 검수** | REST 계약·정규화 수량 규칙은 검토됨, 실제 DB 트랜잭션 미검증 |
| 데이터 정합성 | **설계 적합 / 실검증 미완** | Item→LOT 선잠금, UNIQUE, 단일 트랜잭션. 실제 MySQL 동시성 테스트 필수 |
| 테스트 품질 | **기반 마련 / 런타임 미검증** | 정상·경계·동시성·강제 롤백 테스트 코드 있음, 실행 증거 없음 |
| 오류 처리 | **대체로 적합** | 201/200/409/400/404 구분. DB 락 타임아웃과 데드락 명시적 분류는 후속 개선 |
| 효율 | **보수적/과최적화 없음** | Item 선잠금은 안전성 우선, 같은 품목 병목. 조회 집계 SQL 사용 |
| 유지보수 | **적합하나 계층 수 증가** | Service/Writer/Lookup 분리 이유는 명확. 조회 DB API 분리는 다음 개선 후보 |
| 보안 | **외부 배포 불가** | 인증/인가 없음. `requestKey`가 권한을 대신하지 않음 |

### 장점

- 실제 제조 업무에서 중요한 재고 수량과 이력의 **원자적 저장**을 구현 경계로 잡음.
- 멱등성 체크를 단순 '조회 후 INSERT'로 끝내지 않고 DB 유일성 경쟁까지 고려.
- `Math.addExact`와 검증으로 음수·오버플로 경계값을 방어.
- 기존 LOT에 추가 입고하는 경우에도 원본 최초 입고 시각을 보존.
- 조회 API는 응답 DTO를 별도로 두고 서버에서 가용량을 계산.

### 한계

- MySQL 실제 통합 테스트를 통과하지 않았으므로 현재는 **포트폴리오 구현 완료 사례가 아니다**.
- 품목 행을 잠그면 LOT가 다른 입고 요청도 직렬화되어 처리량이 낮아질 수 있음.
- 2개 요청 경쟁 테스트는 기본 정확성 검증이며, 충분한 부하/데드락 증명을 대신하지 않음.
- 수량 정수·단일 창고·부분 입고 미지원 등 제조 현장 기능을 단순화함.
- 검색·이력 조회 페이지의 count와 본문이 서로 다른 순간의 데이터일 수 있음.

## 9. 리팩터링 판단

| 항목 | 우선순위 | 판단 이유 / 완료 조건 |
|---|---|---|
| **Spring Boot + MySQL 8.4 통합 테스트 정상 실행** | **즉시, 다음 작업 전 필수** | 런타임 미검증. Gradle Wrapper 다운로드 가능한 사용자/CI 환경에서 PASS 확보 |
| **데드락·락 타임아웃 명시적 응답 매핑** | 다음 TASK 전 | 예외 시 무한 재시도 대신 제한된 재시도/오류 코드 정책 추가 |
| **동시 멱등성 테스트 확대 (다른 품목·같은 키 경합)** | 다음 TASK 전 | UNIQUE 경합 후 재조회 경로를 실제 DB에서 추가 검증 |
| **Item 선잠금 vs LOT 단위/조건부 UPDATE 성능 비교** | 2차 개선 | 측정 전 성능 최적화는 과설계. 정확성 입증 후 p95/처리량 비교 |
| **조회 SQL 전용 Repository 분리 및 읽기 테스트** | 2차 개선 | 조회 로직 확대 시 서비스가 쿼리 구현까지 안도록 된 점 개선 |
| **시계 주입/시간 경계 표준화** | 2차 개선 | 현재 UTC 시각을 엔티티에서 직접 만들며 시간 제어 테스트 어려움. Clock 주입 고려 |
| **Redis·Kafka·MSA 분리** | 현재 불필요 | 단일 MySQL MVP의 재고 원자성에 부가 복잡도만 높임 |

관련 ADR: [ADR-009](../adr/ADR-009-원자재입고-멱등성-잠금.md)

## 10. 후속 작업과 전제조건

1. **우선:** Gradle + Testcontainers(MySQL) 실제 기동으로 TASK-002/003A/003B의 통합 테스트 검증 및 필요 수정.
2. 검증 통과 후 TASK-004: 생산지시 생성·BOM 스냅샷·다중 원자재 전량 예약·취소.
3. 이후 생산 시작 때 원자재 실제 소모, 품질검사 합격품 입고, 출고를 구현.
4. 한 작업마다 설계 이유/호출 흐름/검증 증거/리팩터링 판단 문서화를 계속.

## 11. 내가 직접 설명할 수 있는 요약

> 원자재를 입고할 때 LOT의 현재고와 누적 입고량을 증가시키고, 입고 이력 및 재고 이동 이력을 하나의 MySQL 트랜잭션에 저장하도록 구성했습니다. 동일 입고 요청은 `requestKey`와 정규화된 요청 해시로 구분하고, 동시 실행에도 DB UNIQUE 제약이 마지막 방어를 합니다. 여러 요청이 같은 품목의 재고를 동시에 바꿀 때는 먼저 Item 행을 잠그도록 설계했습니다. 현재 코드의 기본 규칙은 Java 21로 검증했지만, 실제 Spring Boot/MySQL 동시성 통합 테스트는 아직 실행해야 합니다.

이해 점검(스스로 설명 연습):

1. 요청을 미리 검색하는데도 DB UNIQUE가 필요한 이유는 무엇인가?
2. 왜 `StockReceiptService`와 `StockReceiptWriteService`를 다른 Spring Bean으로 분리했는가?
3. 왜 LOT 재고 증가 전에 `Math.addExact`로 두 수량을 모두 계산하는가?
