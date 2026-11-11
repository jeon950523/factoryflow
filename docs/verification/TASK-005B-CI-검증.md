# TASK-005B 실제 CI 검증 기록 (2026-11-11)

- 대상: feature/task-005b-searchable-items
- Frontend Actions: https://github.com/jeon950523/factoryflow/actions/runs/43036690995
  - Node 22 / npm ci / npm test: **6 tests, 6 pass, 0 fail**
  - Vite 7.3.8 npm run build: **성공**
  - 신규 API JS 테스트 2개: 검색 파라미터/AbortController signal 전달과 AbortError 구분.
- Backend Actions: https://github.com/jeon950523/factoryflow/actions/runs/43036048446
  - Java 21 / Spring Boot 4.1.2 / Testcontainers MySQL 8.4
  - JUnit XML 12개 클래스 합계 **52 tests / 0 failures / 0 errors / 0 skipped**.
  - 신규 통합 테스트: 원자재 105개를 SQL로 준비하고 GET /api/v1/items?itemType=RAW_MATERIAL&keyword=...&page=10&size=10 호출. totalElements 105, totalPages 11, 마지막 페이지 5건 반환 확인.
- 중요: Vue 컴포넌트의 시각/키보드 실제 브라우저 검증은 CI 테스트 범위에 아직 포함되지 않음. 서버와 UI를 연결한 실제 시연 전 QA 필요.
