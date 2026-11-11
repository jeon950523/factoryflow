# TASK-004H 실제 GitHub Actions / MySQL CI 검증 기록

- 기준: main의 45개 테스트.
- 추가: FactoryFlowEndToEndIntegrationTest 4개.
- 최초 CI 실패: https://github.com/jeon950523/factoryflow/actions/runs/42987199491
  - 컴파일 오류: Jackson 2 import(com.fasterxml.jackson.databind)를 Spring Boot 4/Jackson 3 구성에서 찾지 못함.
- 수정: tools.jackson.databind로 import 교체(커밋 2117793c6fe7ad23e7f969989c49626a31e99735).
- 재검증 CI 성공: https://github.com/jeon950523/factoryflow/actions/runs/42987324239
  - Java 21 / Spring Boot 4.1.2 / Gradle / Testcontainers MySQL 8.4.
  - Gradle BUILD SUCCESSFUL in 2m 11s.
  - GitHub Actions artifact: factoryflow-backend-test-results, ID 13840695266.
  - 다운로드한 JUnit XML 11개 클래스 합계 **49 tests / failures 0 / errors 0 / skipped 0**.
  - FactoryFlowEndToEndIntegrationTest 신규 4개 성공.
- 범위: MockMvc로 API를 호출하는 백엔드 통합 E2E. 브라우저, 운영망, Oracle/Vercel 배포, 인증·인가 검증은 아직 포함하지 않는다.
