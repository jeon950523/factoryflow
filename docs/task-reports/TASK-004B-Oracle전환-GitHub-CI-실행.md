# TASK-004B — Oracle 무료 배포 전환 및 GitHub CI 검증

> 상태: **GitHub CI 2차 검증 성공 / main 반영 전 검수**  
> 날짜: 2026-11-11

## 1. 목적

개발 PC를 24시간 켜 두지 않으면서 Java 21/Spring Boot + MySQL 기반 포트폴리오를 공개 운영할 수 있도록 배포 의사결정을 수정한다. 동시에 TASK-002/003A/003B 코드의 `clean test`를 실제 GitHub Actions에서 실행해 빌드·DB 매핑·트랜잭션 테스트를 검증한다.

## 2. 호출 및 검증 흐름

`GitHub 기능 브랜치 push → Backend CI → setup-java(21) → Gradle Wrapper → clean test → Testcontainers MySQL(8.4) → Flyway V1 → JPA validate → API/동시성 테스트 → JUnit/XML 결과`.

## 3. 파일별 역할과 설계 이유

- `README.md`: 현재 검증 상태·배포 계획을 과장 없이 보여준다.
- `docs/DELIVERY_ROADMAP_20261111.md`: 1~5단계 완료 게이트와 배포 목적지를 변경한다.
- `docs/adr/ADR-010-무료배포-Cloudflare-Tunnel.md`: 과거 결정을 삭제하지 않고 **폐기됨**으로 남긴다. 왜 변경했는지 설명할 증거다.
- `docs/adr/ADR-011-Oracle-Always-Free-배포-전환.md`: 제약, 대안, 장단점, 보안/무료 운영 위험을 기록한다.
- `.github/workflows/backend-ci.yml`: 실제 Java 21 빌드, 테스트 실행과 실패 보고서 업로드를 담당한다.
- `backend/`: 도메인, Flyway, Testcontainers 기반 코드 (기존 TASK-002~003B 내용 유지).

## 4. 장점과 단점

- **장점:** 구현 스택 유지, 개발 PC와 분리, 기능 브랜치 CI로 실증, 배포 의사결정 이력 공개.
- **단점:** Oracle Always Free VM 확보·회수 불확실성, 운영 및 DB 백업 책임, 무료 운영 지속성 미보장.

## 5. 테스트 및 검증 기준

- 기초 정적 검사: Java 파싱, YAML, SQL/JPA 테이블 대응은 빠른 참고자료이고 CI 통과를 의미하지 않는다.
- 실 검증: GitHub Actions의 `clean test` 결과, Testcontainers 도커 실행, Flyway/JPA 시작, API/동시성 테스트 JUnit 결과를 확인해야 한다.
- CI 실패 시 오류 로그를 근거로 수정하고 재실행한다. 실행하지 못한 검사는 `미검증`으로 남긴다.

## 6. 시니어 검수 및 리팩터링 판단

- **즉시 고쳐야 하는 것:** 통합 빌드 오류, 부정확한 의존성, Flyway/JPA 매핑 오류, 실패하는 통합 테스트.
- **다음 단계:** 생성 시점 `LocalDateTime.now()`를 주입 가능한 Clock으로 전환, 품목 선잠금이 과도한지 경쟁 테스트로 판단, 도메인 예외 분리.
- **운영 전 필수:** 인증·인가, TLS, MySQL 비공개, 시크릿 분리, 백업 및 복원 확인.
- **지금 불필요:** MSA/Kubernetes/Redis, 운영 DB 타 제품으로 마이그레이션.

## 7. 내가 설명할 요약

> 지속적인 공개 시연을 위해 컴퓨트 호스트와 터널링 서비스를 구분하고, Oracle Always Free VM을 우선 후보로 변경했습니다. 빌드/테스트는 GitHub Actions의 Java 21과 Testcontainers MySQL로 검증하며, CI 로그를 작업별 설계·결함 기록과 연결합니다.

## 8. CI 실행에서 발견한 실제 문제 (근거)

- 기능 브랜치: `feature/task-004b-ci-baseline`.
- 1차 실행: https://github.com/jeon950523/factoryflow/actions/runs/42878990097 (커밋 `5674ef6`).
- Java 21 `compileJava`/`compileTestJava`, MySQL Testcontainers 시작, Flyway/JPA 검증 과정은 진행되었으며 **17개 테스트 중 16개 통과·1개 실패**.
- 실패: `DatabaseBaselineIntegrationTest.checkShouldRejectReservedStockGreaterThanOnHand`.
- 실제 SQL 예외는 MySQL `error code 3819` (`Check constraint 'chk_inventory_lot_qty' is violated`), SQLSTATE `HY000`.
- Spring `JdbcTemplate`은 이 SQLException을 `DataIntegrityViolationException`이 아니라 `UncategorizedSQLException`으로 번역했다. DB CHECK 자체는 정상 동작했다.
- 수정: 예외 번역 클래스 하나에 지나치게 의존하지 않고 `DataAccessException`을 받고 루트 `SQLException.getErrorCode() == 3819`를 검증하도록 테스트를 개선했다.
- 고친 파일: `backend/src/test/java/com/factoryflow/DatabaseBaselineIntegrationTest.java`; 커밋 `392c9f0`.

### 왜 이 수정이 타당한가

`JdbcTemplate`의 예외 추상화는 DB 드라이버 및 SQLState 변환기에 따라 달라질 수 있다. 우리가 이 테스트에서 증명하려는 것은 **CHECK가 실제 MySQL DB에서 거부됐다는 사실**이지 특정 Spring 예외 서브타입이 아니다. 따라서 데이터베이스 에러 코드까지 검증하면 허위 양성 없이 의도한 조건을 테스트할 수 있다.

**주의:** 벤더 오류 코드 3819에 묶여 있으므로 MySQL 이외 DB로 전환할 경우 테스트를 고쳐야 한다. FactoryFlow는 MySQL 8.4를 명시적으로 선택했으므로 현재 비용은 허용한다.

## 9. CI 2차 재실행 — 실제 성공

- 실행: https://github.com/jeon950523/factoryflow/actions/runs/42879590170
- 커밋: `392c9f0143e82aac9b9a111efb7b9e48753b0717`
- GitHub Actions job `mysql-integration-tests` conclusion: **success**.
- `./gradlew --no-daemon --stacktrace clean test`: **BUILD SUCCESSFUL**.
- `DatabaseBaselineIntegrationTest` 4/4, `ItemBomApiIntegrationTest` 4/4, `StockReceiptApiIntegrationTest` 6/6, `StockReceiptRollbackIntegrationTest` 1/1, `SkuTest` 2/2.
- JUnit XML 합계: **17개 실행, 실패 0, 건너뜀 0**.
- Flyway V1 및 Hibernate `ddl-auto=validate`, MySQL CHECK/UNIQUE/FK, API 입고 멱등성·동시성·롤백 테스트가 성공했다.
- 단, 운영 환경 배포/로그인/보안 테스트가 통과했다는 의미는 아니다.

### 검수 후 결론

- **지금 유지:** DB CHECK 제약을 진짜 DB로 검증, 기능과 DB 테스트를 CI에서 통합 수행.
- **추후 리팩터링 후보:** MySQL SQLException 3819 테스트는 DB 벤더 종속성이 있다는 사실을 명시하고, 향후 DB가 바뀔 때 수정한다. 테스트 속도·컨테이너 중복 기동 최적화는 현재 필수가 아니다.
- **다음 작업:** 업무 흐름의 `DRAFT` 생산지시 생성·BOM 스냅샷을 우선 구현하고, 이후 예약/소모를 별도 테스트 게이트로 나눈다.
