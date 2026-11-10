# TASK-004D CI 실행 증거

- 브랜치: `feature/task-004d-material-reservations`
- 커밋: `10d72fe6905535fe39f762c878a3df2f3ae47cbe`
- 실행: https://github.com/jeon950523/factoryflow/actions/runs/42888753170
- 환경: GitHub Actions Ubuntu, JDK 21, Gradle Wrapper, Testcontainers MySQL 8.4
- 명령: `cd backend && ./gradlew clean test --stacktrace`
- 결과: GitHub Actions `success`, Gradle BUILD SUCCESSFUL
- JUnit XML 수집(실행/실패/건너뜀): **29 / 0 / 0**

| 테스트 클래스 | 실행 | 실패 | 건너뜀 |
|---|---:|---:|---:|
| DatabaseBaselineIntegrationTest | 4 | 0 | 0 |
| ItemBomApiIntegrationTest | 4 | 0 | 0 |
| StockReceiptApiIntegrationTest | 6 | 0 | 0 |
| StockReceiptRollbackIntegrationTest | 1 | 0 | 0 |
| WorkOrderDraftIntegrationTest | 5 | 0 | 0 |
| WorkOrderReservationIntegrationTest | 7 | 0 | 0 |
| SkuTest | 2 | 0 | 0 |

**검증 범위 밖:** 실제 외부 Oracle VM 배포, 인증/인가, 생산 시작·소모·품질검사·출고, 장기 성능·운영 안정성.
