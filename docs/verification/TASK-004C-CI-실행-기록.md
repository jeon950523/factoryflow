# TASK-004C 실 MySQL CI 검증 기록

> 날짜: 2026-11-11 · GitHub Actions run [42882000269](https://github.com/jeon950523/factoryflow/actions/runs/42882000269)

## 실제 실행

- 기능 브랜치: `feature/task-004c-work-order-draft`
- 코드 커밋: `4ef16e939670257658eb7ea4c9bf8050019f18ca`
- Workflow: `Backend CI` / `mysql-integration-tests` → **success**
- JDK 21 + Gradle Wrapper 9.5.1 + Spring Boot 4.1.2 + Testcontainers MySQL 8.4
- JUnit XML: **22 실행 / 0 실패 / 0 건너뜀**

| 테스트 클래스 | 건수 | 결과 |
|---|---:|---|
| DatabaseBaselineIntegrationTest | 4 | 통과 |
| ItemBomApiIntegrationTest | 4 | 통과 |
| StockReceiptApiIntegrationTest | 6 | 통과 |
| StockReceiptRollbackIntegrationTest | 1 | 통과 |
| WorkOrderDraftIntegrationTest | 5 | 통과 |
| SkuTest | 2 | 통과 |

## 범위 주의

생산지시 생성과 BOM 필요량 스냅샷은 검증되었다. 원자재 예약·차감, 품질검사, 출고, 인증 및 운영 배포는 미구현이다. 우연히 테스트가 통과했다는 사실을 성능·장애복구 검증으로 과장하지 않는다.
