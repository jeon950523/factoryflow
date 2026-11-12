# TASK-007A — GitHub Actions 실측 기록

- 저장소: jeon950523/factoryflow
- 기준 브랜치: feature/task-007a-login-throttle
- 수정 커밋: `44ac36d5b599bc13ba8f81886db7f8b0eeff5879`
- 날짜: 2026-11-12 KST

| 유형 | 결과 | 원본 |
|---|---|---|
| 1차 Backend CI | JUnit 64개 중 1개 실패 (429 예상 / 401 실제) | [43190836250](https://github.com/jeon950523/factoryflow/actions/runs/43190836250) |
| 수정 Backend CI | **64개 전부 통과**, 실패·오류·스킵 0 | [43191465084](https://github.com/jeon950523/factoryflow/actions/runs/43191465084) |
| 실DB 브라우저 E2E | **2/2 통과** | [43191465294](https://github.com/jeon950523/factoryflow/actions/runs/43191465294) |

## 실제 수정된 장애
`OncePerRequestFilter.shouldNotFilter`에서 `getServletPath()`만 사용해 MockMvc 요청 경로를 정확히 인식하지 못했다. `servletPath`가 비어 있을 때 Context Path를 뺀 `requestURI`로 판단하도록 수정해 429 테스트를 통과시켰다.

## 보안 검증의 범위
Spring Security 세션·CSRF 유지, 로그인 8회 후 429, 로그인 시도 제어, 기존 제조·MySQL E2E 회귀까지 검증. 그러나 인터넷 노출 환경의 실제 client IP, reverse proxy forwarded headers, TLS, Secure cookie, Vercel rewrite, Oracle VM 보안그룹, 복구 절차는 실환경 검증 전이다.
