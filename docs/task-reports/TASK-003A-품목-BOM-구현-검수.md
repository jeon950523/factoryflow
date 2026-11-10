# FactoryFlow TASK-003A — 품목·BOM API 구현·설계 판단·검수 보고서

> 작성일: 2026-11-11  
> 상태: **구현 초안 작성 / Java 문법·순수 로직 검증 완료 / Spring Boot·MySQL 통합 검증 대기**  
> 기준: `docs/FACTORYFLOW_SSOT_v0.1.md`  
> 코드 기준: 로컬 산출물, 원격 `main` 미반영  
> 원칙: 코드 존재와 실행·테스트 통과를 혼동하지 않음

## 1. 이 기능의 목적

제조 업무의 입구인 **품목(Item)과 BOM(Bill of Materials)** 정보를 등록하고 조회할 수 있도록 한다. 예를 들어 완제품 `FG-BRACKET` 1개를 생산하는 데 원자재 `RM-SHEET` 1개, `RM-BOLT` 2개가 필요한 구성을 저장한다. LOT 입고와 생산지시를 만들기 전에 품목 식별·유형·원자재 소요량을 신뢰할 수 있어야 한다.

- 기존 연결점: TASK-002에서 작성한 `items`, `boms`, `bom_components` 테이블·JPA 엔티티 및 공통 오류 응답을 사용.
- 범위: `POST /api/v1/items`, `GET /api/v1/items`, `GET /api/v1/items/{id}`, `POST /api/v1/boms`, `GET /api/v1/boms/{finishedItemId}`.
- **비범위**: 원자재 실제 입고, LOT 수량 증감, 재고 예약, BOM 수정·버전, 인증·인가, 프론트엔드.
- `GET /api/v1/items/{id}`는 POST 응답의 `Location`이 유효한 리소스를 가리키게 하기 위해 SSOT API 초안에 덧붙인 항목. 다음 SSOT 개정 때 반영 필요.

## 2. 작업 상태와 정적 검수

| 대상 | 구현 내용 | 검증 |
|---|---|---|
| 품목 등록/조회 API | DTO 검증, 정규화, 페이지 조회, 상세 조회 | 코드 작성, 실행 미검증 |
| BOM 등록/조회 API | 완제품/원자재 구분, 중복 구성 방어, DB 원자 저장 | 코드 작성, 실행 미검증 |
| 스키마 | TASK-002 Flyway V1 그대로 사용 | 정적 대조, MySQL 미검증 |
| `Sku.normalize` | 소문자 SKU를 대문자로 통일, 부적합 문자 거부 | **Java 21로 직접 실행 PASS** |
| Java 문법 | 전체 Java 소스 41개 `javac` 파싱 | **문법 파싱 PASS** (타입/의존성 검사 아님) |
| Spring Boot 테스트 코드 | MockMvc + MySQL Testcontainers 시나리오 4종 | 테스트 작성, 실행 미검증 |
| Spring/Gradle 빌드 | Gradle Wrapper 실행 시도 | **실패** — `UnknownHostException: services.gradle.org`, 배포파일 다운로드 불가 |
| GitHub 게시 | 사용자 명시적 push 승인 전 원격 반영 금지 | 미반영 |

## 3. 호출 흐름과 트랜잭션

### 3-1. 품목 등록

```text
POST /api/v1/items
→ ItemController.create(@Valid CreateItemRequest)
→ ItemService.create(@Transactional)
→ Sku.normalize() 대문자/규격 통일
→ ItemRepository.existsBySku() 사용성 오류 사전 확인
→ ItemEntity.create() 유효한 엔티티 팩토리
→ ItemRepository.saveAndFlush() → DB items UNIQUE(sku)
→ ItemResponse.from(entity) → HTTP 201 + Location + {"data": ...}
```

- `existsBySku`는 사용자 친화적 사전 검사이고, **동시 생성 경쟁의 최종 차단은 DB `UNIQUE(sku)`**가 담당한다.
- 서비스의 `@Transactional`이 성공 시 commit, 런타임 예외 발생 시 rollback을 보장하도록 설계했다. DB와 실제 동작은 통합 검증이 필요하다.
- `readOnly=true`를 서비스 기본값으로 적용하고, 쓰기 메서드만 `@Transactional`로 다시 선언했다.

### 3-2. BOM 등록

```text
POST /api/v1/boms
→ BomController.create(@Valid CreateBomRequest)
→ BomService.create(@Transactional)
→ 완제품 ITEM 검증(FINISHED_GOOD, active)
→ 구성 원자재 ID 중복 확인 → findAllById(한 번에 조회)
→ 각 구성의 RAW_MATERIAL·active 확인
→ BomRepository.saveAndFlush()로 BOM id 확보
→ BomComponentRepository.saveAllAndFlush()로 여러 구성품 저장
→ DB UNIQUE(finished_item_id) + UNIQUE(bom_id,raw_item_id)
→ BOM/구성 저장 전부 성공한 경우에만 commit → HTTP 201
```

- 원자재 구성을 품목 ID 오름차순으로 정렬해 등록 직후 응답과 GET 응답의 배열 순서를 일치시켰다.
- 다른 요청이 중간에 BOM을 등록하면 두 번째 요청은 DB UNIQUE 충돌로 실패해야 하며 **구성품 일부만 남으면 안 된다**.
- BOM 수정 API는 없다. 생산지시가 참조하는 구성 변경 문제는 버전·스냅샷 설계가 필요한 후속 범위로 남겼다.

### 3-3. 조회

- `ItemService.list`: `JpaSpecificationExecutor`로 선택 조건(품목 유형·키워드)을 조합하고 `PageRequest`로 DB 페이징. `%`, `_` 같은 SQL LIKE 특수문자를 이스케이프하여 *검색어를 글자 그대로* 찾게 한다.
- `BomService.getByFinishedItemId`: BOM 기본 정보 조회 후 구성 목록을 ID 오름차순으로 읽는다. 첫 버전은 BOM당 최대 100개 원자재로 제한한다.
- Controller는 JPA 엔티티를 외부로 그대로 반환하지 않고 DTO(`ItemResponse`, `BomResponse`)로 변환한다.

## 4. 주요 파일의 역할과 선택 이유

| 파일 | 책임 | 왜 이렇게 구현했는가 |
|---|---|---|
| `item/ItemController.java`, `item/BomController.java` | HTTP 경로·요청·응답 | 비즈니스 로직이 Controller에 중복되는 것을 방지 |
| `item/ItemService.java`, `item/BomService.java` | 검증·트랜잭션·저장 순서 | 컨트롤러 호출이 아닌 업무 명령을 트랜잭션 단위로 묶음 |
| `item/ItemEntity.java` | 품목 생성·불변 생성 시각 | `sku/unit/active/createdAt`를 유효한 상태로 동시에 초기화 |
| `item/BomEntity.java`, `item/BomComponentEntity.java` | BOM 기본/상세 엔티티 생성 | JPA에 필요한 기본 생성자를 `protected`로 제한, 임의 상태 변경 방지 |
| `item/Sku.java` | 식별 코드 정규화 | 품목 등록·중복 조회에서 동일한 규칙 사용 |
| `item/*Repository.java` | DB 접근·조건 조회 | JPA 표준 CRUD와 필요한 질의만 명시 |
| `item/dto/*.java` | 요청 Bean Validation·응답 형태 | 엔티티를 그대로 반환하지 않고 HTTP 계약 분리 |
| `global/api/*.java` | 성공 응답·페이지 응답 | API 호출자에게 일관된 `data` 구조 전달 |
| `backend/build.gradle` | Boot 4 테스트 스타터 추가 | 4.x에서 `@AutoConfigureMockMvc` 지원 모듈 분리에 대응 |
| `ItemBomApiIntegrationTest.java` | MockMvc + MySQL 통합 테스트 | HTTP→서비스→JPA→실제 DB까지 검증하는 테스트 초안 |

## 5. 핵심 코드의 이유

### 5-1. SKU 정규화

```java
public static String normalize(String value) {
    if (value == null || !value.matches("[A-Za-z0-9][A-Za-z0-9_-]{1,63}")) {
        throw new IllegalArgumentException("SKU must be 2-64 ASCII letters, digits, '_' or '-'");
    }
    return value.toUpperCase(Locale.ROOT);
}
```

- 입력 `rm-bolt`를 `RM-BOLT`로 통일하므로 대소문자만 다른 중복 코드를 줄인다.
- `Locale.ROOT`는 시스템 로캘에 따라 대문자 변환이 달라지지 않게 한다.
- 제약: 한글·공백·마침표 포함 코드가 실제 공급처에 필요하다면 SKU 표준을 변경해야 한다. 현재는 데모 시스템의 명시적 규칙이다.
- 범위: 문자열 정규화이지 재고 코드 생성이나 재고 수량 검증이 아니다.

### 5-2. 중복 BOM 구성 방어

```java
Set<Long> componentIds = new HashSet<>();
for (BomComponentRequest component : request.components()) {
    if (!componentIds.add(component.rawItemId())) {
        throw new ApiException(HttpStatus.BAD_REQUEST, "DUPLICATE_BOM_COMPONENT", "동일 원자재가 중복되었습니다.");
    }
}
```

- 같은 원자재가 요청에 두 번 등장하면 어떤 수량을 정본으로 삼아야 하는지 불분명하다. 조용히 합산하거나 마지막 값으로 덮지 않고 요청 오류로 거부한다.
- DB에도 `(bom_id, raw_item_id)` UNIQUE를 유지해 코드의 검증 누락 시 최종 방어한다.

### 5-3. JPA Specification 사용

```java
if (itemType != null) {
    criteria = criteria.and((root, query, builder) -> builder.equal(root.get("itemType"), itemType));
}
```

- 조회 조건이 없으면 전체 목록, 있으면 해당 조건만 SQL에 반영한다.
- 장점: `findByItemTypeAndNameContaining...` 같은 파생 메서드를 조합별로 늘리지 않아도 된다.
- 단점: `get("itemType")`은 문자열 기반이어서 필드명 변경 시 컴파일러가 잡지 못한다. 2차에 정적 메타모델 또는 QueryDSL은 복잡성이 실제로 커진 뒤 검토한다.

## 6. 예외 및 테스트 방법

| 상황 | 기대 결과 | 작성한 검증 |
|---|---|---|
| 품목 SKU 대소문자만 다른 두 번째 등록 | 409, 1개만 저장 | `duplicateSkuAndInvalidRequestsShouldNotWriteTwice` |
| 유효하지 않은 SKU/빈 품목명 | 400, 데이터 저장 없음 | 위 통합 테스트 |
| 없는 품목 조회 | 404 | 공통 `ApiException`과 Controller 경로 (보강 테스트 예정) |
| 완제품 아닌 품목을 BOM 완제품으로 사용 | 400 | 서비스 코드 검증, 별도 테스트 보강 필요 |
| 원자재 아닌 품목으로 BOM 구성 | 400, BOM 생성 없음 | `invalidBomMustNotCreatePartialData` |
| BOM 구성 원자재 중복 | 400 | 위 통합 테스트 |
| 동일 완제품 BOM 2회 생성 | 409 | `bomShouldAcceptRawMaterialsAndRejectDuplicatedComponents` |
| 수량 0 | 400 (@Positive) | `invalidBomMustNotCreatePartialData` |
| DB UNIQUE 동시 경합 | 마지막 방어 DB UNIQUE | 실제 경쟁 테스트 미작성·미검증 |
| DB 장애·SQL 제약 충돌 | 쓰기 트랜잭션 롤백 예상 | 실제 MySQL 검증 대기 |
| 인증·인가 | 아직 미구현 | 외부 인터넷에 API 노출 금지 |

### 실제 검증 기록

- `java -version`: OpenJDK **21.0.12.1** 확인.
- `javac --release 21`: `Sku.java`와 별도 실행형 Smoke 코드 컴파일 성공, `java` 실행으로 정상/부적합 SKU 처리 **PASS**.
- JDK Compiler API `JavacTask.parse()`: `backend/src/` Java 소스 **41개 문법 파싱 PASS**. 타입 해결·Spring DI·SQL 실행 검증과는 다르다.
- `./gradlew --offline --version`: Gradle 9.5.1 배포본을 새로 받으려다가 `java.net.UnknownHostException: services.gradle.org`로 종료. **Gradle 빌드 실행 불가**.
- Docker CLI/Engine 부재: **Flyway/MySQL/Testcontainers 실제 동작 검증 불가**.
- 테스트 코드는 작성했지만 JUnit 결과 **미확인**. 통과했다고 기록하지 않는다.

### Windows PowerShell 1줄 재현

프로젝트 루트에서:

```powershell
Copy-Item .\infra\.env.example .\infra\.env; notepad .\infra\.env
```

비밀번호 입력 후:

```powershell
docker compose --env-file .\infra\.env -f .\infra\compose.yml up -d
```

```powershell
$env:DB_PASSWORD=(Get-Content .\infra\.env | Select-String '^FACTORYFLOW_DB_PASSWORD=').ToString().Split('=',2)[1]; Set-Location .\backend; .\gradlew.bat clean test
```

테스트 성공 후에만 TASK-002/003A 검증 완료 판정을 기록한다. 매번 DB 볼륨을 삭제해 초기화하는 방식은 데이터가 사라지므로 별도 승인 전 실행하지 않는다.

## 7. 시니어 검수 결과

### 잘한 점

1. **도메인 정합성:** FINISHED_GOOD만 BOM 완제품으로, RAW_MATERIAL만 구성으로 허용한다.
2. **데이터 계약:** 엔티티 외부 반환을 피하고 HTTP DTO를 사용한다.
3. **안전장치 중복:** 사용자 친화적인 사전 검사 + DB UNIQUE를 분리한다.
4. **확장 경계:** BOM/품목을 재고/생산에 앞서 독립 구현하므로 복잡한 LOT 트랜잭션과 결합시키지 않는다.
5. **검증 가능성:** 실제 MySQL 사용을 전제로 하는 테스트 코드를 우선 작성했다.

### 위험·단점

1. **최우선:** 실제 Gradle/Testcontainers 검증이 없다. 의존성/매핑/트랜잭션 오류 가능성을 배제할 수 없다.
2. **BOM 동시 생성:** 경쟁을 단위 테스트로 확인하지 못했다. DB UNIQUE는 존재하지만 예외가 원하는 API 오류로 변환되는지 미검증.
3. **문자열 기반 Specification:** 필드 리팩터링 시 컴파일 타임 안정성이 약하다.
4. **항목별 출처:** `createdAt`을 서비스 코드의 UTC 시계로 채우며 DB 기본값과 다른 클럭이 될 수 있다. 서버간 시각 동기화는 후속 운영 환경에서 확인할 것.
5. **색인/성능:** `%keyword%` 검색은 일반 B-Tree 인덱스 효율이 낮다. 검색 데이터가 늘면 쿼리 계획 측정 후 개선.
6. **제품 한계:** BOM 수정·버전·중복 수량 합산·외부 자재코드 매핑을 MVP에서 지원하지 않음.
7. **접근 제어:** 인증·인가가 없으므로 포트 외부 노출 불가.

## 8. 리팩터링 우선순위

| 항목 | 판단 | 이유 / 완료 조건 |
|---|---|---|
| Spring Boot 4 테스트 스타터 분리 대응 | **즉시 수정 완료(코드)** | `spring-boot-starter-webmvc-test` 추가와 신규 import 적용. 실제 Gradle 검증은 대기 |
| Gradle·Docker 테스트 실패 여부 확정 | **다음 TASK 전에 필수** | 현재 실행 불가이므로 사용자/CI 환경에서 `clean test` 결과 확보 |
| 조회 API의 없는 ID·타입 오류 검증 | **다음 TASK 전에 보강** | HTTP 404/400 실제 응답 테스트 추가 |
| BOM 동시 생성 충돌 테스트 | **TASK-003B 전 보강** | UNIQUE로 중복 저장 방지 및 오류 매핑 확인 |
| `Specification` 정적 타입화 | **2차 개선** | 현재 필터 2개로 단순, 메타모델 도입 비용이 더 큼 |
| 생성시각 `Clock` 의존성 주입 | **2차 개선** | 타임아웃/기간 규칙이 들어오기 전에는 테스트 고정 시각의 필요성이 낮음 |
| Keyword `LIKE` 성능 개선 | **측정 이후** | 임의 인덱스·검색 엔진 추가는 과설계 |
| 엔티티 객체 그래프(@OneToMany 등) 추가 | **현 시점 불필요** | FK 값 기반으로 SQL을 명확히 통제, N+1 위험 낮춤 |
| Redis/Kafka/MSA 도입 | **현 시점 불필요** | 모놀리식 트랜잭션 학습 목적에 역행 |

## 9. 다음 작업 연계

1. **TASK-002 실빌드·JPA validate·MySQL 제약 검증** 완료가 우선.
2. TASK-003A HTTP 통합 테스트·예외/경합 회귀 검사.
3. TASK-003B 원재료 입고·LOT 수량·재고 이동과 멱등성 구현(새 보고서 분리).
4. 이후 TASK-004 생산지시 및 LOT 재고 예약.
5. GitHub `main`은 아직 README/SSOT/작업지시서만 있음. **원격 push 하지 않았다.**

## 10. 직접 설명할 수 있는 30초 요약

> 품목은 원자재와 완제품을 구분하고, 완제품의 BOM에는 원자재만 등록되게 했습니다. Controller는 HTTP 요청만 받고, Service에서 업무 검증과 트랜잭션을 처리합니다. 코드와 DB 양쪽에서 중복 등록을 방지하고, JPA 엔티티 대신 DTO로 응답합니다. 이번에는 Java 21 기반 정적 검사를 마쳤지만 MySQL 통합 테스트는 아직 실행 환경 제한으로 검증하지 못했습니다.

**이해 점검 (면접 예상 질문·답변 아님):**
1. `existsBySku()`가 true/false를 반환하는데도 DB UNIQUE가 필요한 이유는?
2. BOM을 저장할 때 Header와 Component를 같은 트랜잭션에 넣는 이유는?
3. `@Transactional(readOnly=true)`를 클래스에 선언하고 등록 메서드에 다시 `@Transactional`을 선언한 이유는?
