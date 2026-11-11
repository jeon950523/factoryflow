# TASK-005C — 실제 CI/Chrome 브라우저 스모크 증거

- 구현 브랜치: feature/task-005c-batch-item-labels
- Backend CI https://github.com/jeon950523/factoryflow/actions/runs/43042942803 — **success**
  - Java 21 / Spring Boot 4.1.2 / Testcontainers MySQL 8.4.
  - GitHub Actions 원본 JUnit XML ZIP 직접 확인: **53 tests / 0 failures / 0 errors / 0 skipped**, 12 testsuite.
  - 신규 테스트: ID 105 등 조회, 중복 ID 하나만 반환, 잘못된 ID 요청 400.
- Frontend CI https://github.com/jeon950523/factoryflow/actions/runs/43044876163 — **success**
  - Node 22 npm ci + npm test **10 tests / 10 pass**.
  - npm run build (Vite 7) 성공.
  - Playwright 1.56.1 + GitHub Ubuntu 24.04 기본 설치 Google Chrome 156 실행: **5 browser tests / 5 passed**.
  - 목록/상세/BOM/출고 실제 DOM/클릭, 검색형 품목 선택, 이름 lookup 장애 fallback.
- 최초 browser CI 실패 https://github.com/jeon950523/factoryflow/actions/runs/43043829212
  - GitHub 러너 Ubuntu 26.04에 Playwright Chromium 설치 미지원. 애플리케이션 오류가 아님.
  - Ubuntu 24.04 명시 및 사전 설치 Google Chrome 사용으로 재검증 통과.
- **검증 한계:** Chrome 스모크의 응답은 page.route() Fixture. 실제 MySQL과 연결한 UI 종단 간 테스트·Oracle/Vercel 배포·인증/인가·스크린리더 검사는 아직 없음.
