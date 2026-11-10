# TASK-004F 실제 MySQL CI 검증 기록

- 대상: 품질검사 확정, 합격품 LOT 입고, 중복 검사 동시성 방어, 검사 실패 시 트랜잭션 롤백
- 최초 실패: https://github.com/jeon950523/factoryflow/actions/runs/42919367508 — 40개 중 1개 실패. JPA 영속성 컨텍스트에 잠금 이전 상태가 남아 동시 검사 요청 하나에서 LOT 중복(500) 발생
- 수정 커밋: 527a54c(스칼라 쿼리 도입), 1bc14cf(잠금 후 엔티티 조회)
- 재검증: https://github.com/jeon950523/factoryflow/actions/runs/42923833862 — GitHub Actions success
- JUnit XML 원본: 40 tests / failures 0 / skipped 0; 신규 QualityInspectionIntegrationTest 6개 성공
- 실행 환경: Java 21, Gradle, MySQL 8.4 Testcontainers, Spring Boot 4.1.2
- 아직 미구현: 완제품 출고, 인증/인가, Vue UI, Oracle 서버 배포
