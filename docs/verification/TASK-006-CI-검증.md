# TASK-006 — 인증·인가 및 실DB CI 실행 기록

- 기준: PR #12, `feature/task-006-session-security`
- 애플리케이션 테스트 대상 커밋: `eb5e8780380f0c4ffe9b160f1196b1485f53bdfb`
- 날짜: 2026-11-12 (KST)
- 구분: CI 완료 사실과 미완료 운영 보안 과제를 분리

| 검증 | 결과 | 근거 |
|---|---|---|
| Backend CI · Java 21 · Spring Boot · Testcontainers MySQL 8.4 | JUnit 57/57 성공, 실패 0, 오류 0, 스킵 0 | [Actions #43110093904](https://github.com/jeon950523/factoryflow/actions/runs/43110093904) |
| Frontend CI · Node 22 · Vue 3 | Node 23/23 · Vite build 성공 · Chrome Fixture 11/11 | [Actions #43110093813](https://github.com/jeon950523/factoryflow/actions/runs/43110093813) |
| Fullstack Browser E2E · Chrome→Vue→Spring Boot→MySQL | 정상 제조 전체 흐름 1건 + 재고 부족 롤백 1건, 2/2 성공 | [Actions #43110093834](https://github.com/jeon950523/factoryflow/actions/runs/43110093834) |

## 검증한 구조

- 익명 사용자 API 거부, ADMIN/VIEWER 권한 분리, 로그인/로그아웃, CSRF 보호를 Spring Security 필터를 포함한 백엔드 통합 테스트로 검증.
- Vue 로그인 후 원자재 입고 → 생산지시 → 예약·소모 → 품질검사 → 출고.
- 부족 재고 예약 실패 시 DRAFT 유지, 예약량 0, 현재고 보존.
- 빠른 API 응답 경합, CSRF 토큰 세대, 세션 만료/권한 부족 분리를 Node/Chrome 테스트로 검증.

## 완료와 구분할 사항

- 테스트의 Chrome 환경은 GitHub Actions 내부이며, 실제 Vercel 및 Oracle 서버는 아직 배포하지 않았음.
- TLS, Reverse Proxy forwarded header, SESSION_COOKIE_SECURE, SameSite, 실운영 CORS/CSRF/세션의 브라우저 정책은 검증 전.
- 로그인 rate limit, 비밀번호 변경/초기화, 관리자 계정 복구 및 사용자 감사로그는 향후 보안 게이트.
- 따라서 **MVP 인증·인가 구현 완료**지만 **인터넷 공개 및 상용 수준 운영 보안 완료는 아님**.
