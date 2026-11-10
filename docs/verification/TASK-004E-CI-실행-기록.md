# TASK-004E GitHub Actions / MySQL CI 실행 기록

- 브랜치: `feature/task-004e-production-consumption`
- 코드 커밋: `fbeb2eed0eaa966af38b64b62cb50d4ca0707a25`
- 실행: https://github.com/jeon950523/factoryflow/actions/runs/42891184237
- Java 21 + Spring Boot 4.1.2 + Gradle Wrapper + Testcontainers MySQL 8.4
- 명령: `cd backend && ./gradlew clean test --stacktrace`
- GitHub Actions: success, Gradle `BUILD SUCCESSFUL`
- JUnit XML 실제 결과: **34개 실행 / 실패 0 / 건너뜀 0**

| 테스트 클래스 | 실행 | 실패 | 건너뜀 |
|---|---:|---:|---:|
| DatabaseBaselineIntegrationTest | 4 | 0 | 0 |
| ItemBomApiIntegrationTest | 4 | 0 | 0 |
| StockReceiptApiIntegrationTest | 6 | 0 | 0 |
| StockReceiptRollbackIntegrationTest | 1 | 0 | 0 |
| WorkOrderDraftIntegrationTest | 5 | 0 | 0 |
| WorkOrderReservationIntegrationTest | 7 | 0 | 0 |
| WorkOrderProductionIntegrationTest | 5 | 0 | 0 |
| SkuTest | 2 | 0 | 0 |

현재 미구현: 품질검사 합격품 반영/출고/인증/배포. 장기 부하 테스트는 별도 작업으로 진행.
