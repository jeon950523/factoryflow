# TASK-002 — 코드 이해 가이드

## 목적
FactoryFlow를 실행할 수 있는 개발 기반을 먼저 만들고, 업무 로직을 추가하기 전에 데이터의 기본 무결성을 검증한다.

## 전체 호출 순서

**애플리케이션 기동:** `FactoryFlowApplication.main()` → Spring Boot 설정 로딩 → JDBC DataSource 연결 → Flyway V1 마이그레이션 → JPA 엔티티/테이블 비교(`ddl-auto=validate`) → Actuator Health.

**DB 테스트:** Gradle `test` → `DatabaseBaselineIntegrationTest` → Testcontainers MySQL 컨테이너 실행 → `@DynamicPropertySource`로 DB 주소 제공 → Spring 컨텍스트 기동 → Flyway 적용 → JPA 검증 → CHECK/UNIQUE/FK 오류 재현.

## 주요 파일과 왜 이렇게 작성했나

- `build.gradle`: **4.1.2** 버전과 Java **21** 선택. Spring Boot BOM을 사용해 라이브러리를 일관된 버전 조합으로 유지한다. Gradle 9.5.1은 Wrapper로 고정한다.
- `V1__create_factoryflow_schema.sql`: DB 테이블은 코드가 아닌 **Flyway가 관리**한다. `ddl-auto=validate`는 JPA가 테이블을 임의 변경하지 않고 설계와 차이를 감지하도록 한다.
- `InventoryLotEntity`: `onHandQty`(현재고)와 `reservedQty`(예약고)를 분리한다. 가용재고는 `onHandQty - reservedQty`이며 저장된 별도 컬럼이 없어 불일치 위험을 줄인다.
- `ItemType` 등 enum: 업무 상태를 제한하고 IDE 자동완성과 컴파일 단계에서 잘못된 값을 줄인다. Hibernate가 MySQL native ENUM으로 매핑하지 않도록 VARCHAR JDBC 타입을 지정했다.
- `RequestTraceFilter` + `GlobalExceptionHandler`: 서버가 요청별 traceId를 만들어 MDC 로그와 `X-Trace-Id` 응답 헤더에 넣는다. 예상 가능한 400/404/405 오류와 서버 내부 500을 구분하며 내부 스택트레이스를 외부에 내보내지 않는다.
- `compose.yml`: DB를 `127.0.0.1`에만 연결하고 암호를 별도 파일로 주입한다. 데이터 볼륨은 컨테이너 재생성 후에도 보존할 수 있다.
- `Testcontainers`: H2를 쓰면 실제 MySQL의 잠금/제약을 보장할 수 없으므로 실제 MySQL 이미지를 구동해 검사한다.

## 핵심 예외·테스트
- DB 연결 안 됨: Flyway와 애플리케이션 시작 실패 (정상적인 실패). `.env`와 Docker 상태 확인.
- 재고 예약 11 / 현재고 10: CHECK 위반으로 insert 실패해야 함.
- 동일 SKU 중복: UNIQUE 위반으로 insert 실패해야 함.
- 존재하지 않는 품목 FK: FK 위반으로 insert 실패해야 함.
- JPA 필드명 또는 타입이 SQL과 다르면: `ddl-auto=validate`에서 기동 실패해야 함.

## 장점/단점/리팩터링 포인트
| 선택 | 장점 | 단점·차후 개선 |
|---|---|---|
| Flyway + JPA validate | 스키마 변경 추적, 자동 DDL 변형 방지 | 엔티티/SQL 두 곳을 일치시켜야 함 |
| BIGINT / long + CHECK | 수량 범위와 동시성 안전망 | 덧셈 오버플로 방지는 서비스에서 추가 필요 |
| ID 참조형 엔티티 | 의도치 않은 JPA N+1 줄임 | 조회 JOIN 직접 설계, 참조 무결성은 FK로 제한 |
| 모듈형 모놀리식 | 단일 트랜잭션, 구현/테스트 쉬움 | 독립 배포·확장 불가 |
| MySQL Testcontainers | 운영 DB와 비슷한 동작 검증 | Docker와 이미지 다운로드 필요, 테스트 시간 증가 |

**직접 설명할 20초 요약:** "테이블은 Flyway가 버전별로 만들고, JPA는 매핑 검증만 합니다. 수량 CHECK·중복 UNIQUE·관계 FK 제약을 두어 코드가 잘못 동작해도 데이터베이스가 최종 방어하도록 설계했습니다. Testcontainers로 실제 MySQL에서 검증할 예정입니다."
