# TASK-007A — 로그인 요청 제한 시니어 검수

상태: **2026-11-12 기능 구현·GitHub CI 검증 완료.** 운영 프록시·TLS·계정 복구는 미검증.

## 1. 목적
기존 BCrypt 인증 앞단에 로그인 시도량 제한을 도입한다. 분산 계정 추측과 한 접속원의 다계정 시도(credential stuffing)를 분리해 제한한다.

## 2. 호출 흐름
Vue 로그인 → GET /api/v1/auth/csrf → POST /api/v1/auth/login → **Spring Security CsrfFilter** → **LoginRateLimitFilter** → LoginAttemptLimiter.reserve(username, request.getRemoteAddr()) → UsernamePasswordAuthenticationFilter → DaoAuthenticationProvider + BCrypt → 성공 시 username 창 초기화 / 실패 시 401.

- 동일 username: 5분 sliding window 내 8회 허용, 다음 요청부터 429.
- 동일 transport peer: 5분 sliding window 내 120회 허용, 다음 요청부터 429.
- 두 버킷을 별개로 검사. Atomic synchronized slot reservation prevents concurrent bypass.
- 성공한 인증은 username 기록만 초기화. IP/peer 창은 유지.
- 초과 시 `Retry-After` 헤더와 `LOGIN_RATE_LIMITED` JSON. 계정 존재 유무, 어떤 정책 초과인지 응답에 드러내지 않음.
- 20,000 키 메모리 상한 초과 시 fail closed.

## 3. 파일 역할
- LoginAttemptLimiter: JDK 21 기반 bounded rolling window, 시간 의존성 Clock 주입.
- LoginRateLimitFilter: BCrypt 연산 전에 429, JSON 표준 포맷.
- SecurityConfig: CSRF 보존 및 UsernamePasswordAuthenticationFilter 앞 필터 배치, 로그인 성공 후 계정 창 초기화.
- LoginAttemptLimiterTest: 계정·IP·롤링 만료·성공 초기화·40개 병렬 요청·비정상 아이디.
- SessionSecurityIntegrationTest: 실제 보안 필터 + DB를 거치는 401→429, CSRF 403과 세션 미인증 확인.

## 4. 검증과 예외
- 순수 Java 21 javac 빌드 및 6개 단독 시나리오 통과(동시 40건 중 8건 허용).
- [Backend CI 재검증](https://github.com/jeon950523/factoryflow/actions/runs/43191465084): **JUnit 64/64**, 실패 0, 오류 0, 스킵 0. 기존 57개 + 순수 Java 단위 6개 + 보안 필터 통합 1개.
- [실제 MySQL+Chrome E2E](https://github.com/jeon950523/factoryflow/actions/runs/43191465294): **2/2 성공**, Spring Boot/Flyway/MySQL 8.4 기동 및 관리자 로그인과 제조 업무 시나리오.
- Vue 코드 변경 없음. 프론트 단독 CI는 변경 경로 필터에 의해 자동 트리거되지 않았으며, 실DB 브라우저 E2E로 연동 회귀를 검증함.

## 5. 시니어 검수
- 좋은 점: BCrypt 이전 차단, 계정별/피어별 독립 버킷, 동시 우회 방지, 시간 주입으로 비대기 테스트, 사용자명 검증으로 메모리 방어.
- 위험한 점: 단일 인스턴스 메모리(재시작 초기화), 분산 배포 미지원, 5분 계정 로그인 지연 DoS 가능, 120회 소스 제한은 Vercel/Caddy 프록시의 합쳐진 주소에 적용될 수 있음.
- 지금 고칠 것: CI 검증, 실환경 trusted proxy 구성과 edge rate limit을 구분하여 검증.
- 나중: Redis/게이트웨이 등 여러 인스턴스 중앙 제한, 로그인 탐지·감사/경보, 사용자 셀프서비스 비밀번호 변경·관리자 복구.

## 6. 학습용 한 문장
로그인 요청을 BCrypt 이전에 계정과 접속원 두 축의 5분 슬라이딩 윈도로 원자적으로 예약해 무차별 대입 시도와 동시 우회를 줄였고, 운영 프록시·분산 환경에는 별도 대책이 필요합니다.

참고: [OWASP 인증 가이드](https://cheatsheetseries.owasp.org/cheatsheets/Authentication_Cheat_Sheet.html), [OWASP Bot Management 가이드](https://cheatsheetseries.owasp.org/cheatsheets/Bot_Management_and_Anti-Automation_Cheat_Sheet.html).

## 7. 1차 CI에서 재현된 요청 경로 판정 문제

- [1차 Backend CI](https://github.com/jeon950523/factoryflow/actions/runs/43190836250): 64개 중 1개 실패. 로그인 9번째 시도에서 기대한 429가 아닌 401 반환.
- 분석: MockMvc 테스트 요청에서는 `getServletPath()`가 공백일 수 있어 필터의 `shouldNotFilter()`가 로그인 경로를 놓침.
- 수정: 실제 Servlet 환경에서는 servletPath를 사용하고, 경로가 비어 있으면 contextPath를 제외한 requestURI로 판단.
- 검증 게이트: 429·CSRF 순서·세션 미인증 통합 테스트 재실행 성공 전 PR 병합 금지.

## 8. CI 실패·수정·재검증 기록 (증거)

- 원인: MockMvc는 요청 URI가 있어도 servletPath가 빈 문자열일 수 있음. 필터가 servletPath만 검사해 MockMvc에서 9번째 시도까지 BCrypt로 내려가 401 반환.
- 수정: servletPath가 비어 있으면 contextPath를 제외한 requestURI를 사용. 실제 서블릿에서는 servletPath 우선.
- 첫 CI: [43190836250](https://github.com/jeon950523/factoryflow/actions/runs/43190836250), **64개/1 실패**, 429 예상 / 401 실제.
- 수정 후 CI: [43191465084](https://github.com/jeon950523/factoryflow/actions/runs/43191465084), **JUnit 64/64 통과**.
- Fullstack: [43191465294](https://github.com/jeon950523/factoryflow/actions/runs/43191465294), **2/2 통과**.
- 적용 브랜치 코드 검증 커밋: `44ac36d5b599bc13ba8f81886db7f8b0eeff5879`.
- 프록시/CDN, Vercel 및 Oracle 운영 접속 환경은 검증 대상이 아님.

## 9. 리팩터링 후보

- **필수 후속:** Vercel/Caddy의 실제 remoteAddr 식별 및 엣지 rate limiting, 로그인 실패 감사·경고, 관리자 계정 복구.
- **서비스 확장 시:** 여러 서버에 걸친 분산 캐시/저장소로 로그인 허용량 통합.
- **선택:** 설정 가능한 시도 횟수·지속 시간, 사용자에게 남은 시간 표시. 단, 상세 한도 노출이 공격 자동화에 도움 되지 않도록 주의.
