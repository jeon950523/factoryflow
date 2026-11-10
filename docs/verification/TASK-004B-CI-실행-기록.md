# TASK-004B — GitHub Actions 실제 실행 검증

> 시작일 2026-11-11, `feature/task-004b-ci-baseline`. 코드/테스트는 `main`이 아닌 기능 브랜치에서 검증한다.

## CI 1차 — 실제 결과

- Run https://github.com/jeon950523/factoryflow/actions/runs/42878990097 · commit `5674ef6` · **failure**
- Java 21 / Gradle 9.5.1 Spring Boot 빌드: `compileJava` 및 `compileTestJava` **성공**
- Testcontainers MySQL: 실제 Docker 연결 및 SQL 실행 **확인**
- Flyway V1 + JPA 검증: 정상 기동 테스트는 **통과**
- JUnit: 17개 중 16개 성공, 1개 실패
- 실패 원인: MySQL CHECK 위반은 DB에서 정확히 거부(code 3819)됐지만, Spring `JdbcTemplate`이 `UncategorizedSQLException`으로 번역해 `assertThrows(DataIntegrityViolationException.class)`에 맞지 않았음
- 교정: `DataAccessException`을 받고 가장 안쪽 `SQLException` error code 3819 확인. 커밋 `392c9f0`.

## CI 2차 — 실제 성공

- Run: https://github.com/jeon950523/factoryflow/actions/runs/42879590170
- status: **completed / success**
- Gradle `clean test`: **BUILD SUCCESSFUL**
- JUnit XML: **17 tests / 0 failures / 0 skipped** (4 + 4 + 6 + 1 + 2)
- 실행된 MySQL 8.4 Testcontainers, Flyway V1/JPA validate, API/동시성/롤백 테스트 모두 성공.
- 테스트 증거: GitHub Actions의 `factoryflow-backend-test-results` artifact 및 Job 로그.
- 범위 한계: Oracle 배포, 공개 API 인증/보안 및 프론트 연동은 검증하지 않았다.

## 선택한 검증 방법과 단점

MySQL JDBC의 CHECK 위반 코드 3819를 직접 확인하면 DB 제약이 정말 발동했다는 사실을 검증할 수 있다. 다만 MySQL 의존적인 테스트가 되므로 DB 교체 시 해당 테스트 코드를 검토해야 한다.
