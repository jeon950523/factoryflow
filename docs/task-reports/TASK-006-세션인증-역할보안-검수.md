# TASK-006 — Spring Security 세션·CSRF·역할 기반 인가 시니어 검수

상태: 2026-11-12 기준 기능 구현·CI 검증 완료. 공개 배포와 운영 보안 검증은 미완료.

## 1. 목적
로그인 없이 입고·생산·검사·출고 명령을 전송할 수 있던 보안 경계를 Spring Security에서 차단한다.

## 2. 전체 호출 순서
- 최초 DB: Flyway V2 app_users 생성 → 환경변수 ADMIN 부트스트랩(새 DB, 미설정 기동 거부).
- 로그인: Vue LoginView → GET /auth/csrf → POST /auth/login(form + CSRF header) → Spring Security Filter → DaoAuthenticationProvider/UserDetailsService → BCrypt + MySQL → 세션 회전 → JSON 사용자 정보 → 최초 쓰기 요청 시 CSRF 재조회.
- 조회: Vue 라우터 GET /auth/me → 세션과 Role 검증 → Controller GET → Service.
- 쓰기: Vue api.post + X-CSRF-TOKEN → 세션 인증+ADMIN Role+CSRF 검증 → 기존 제조 Controller/Service/JPA.
- 로그아웃: POST /auth/logout → HttpSession 폐기, Vue 인증 상태 초기화 후 /login.

## 3. 파일 역할
- backend/build.gradle: Spring Security, Security Test 의존성.
- db/migration/V2__create_app_users.sql: 계정 DB/유일 ID/역할 CHECK.
- security/AppUser/UserRole/Repository/DetailsService: 사용자 저장과 해시 검증.
- security/SecurityConfig: URL 권한, login/logout 성공·오류 JSON, CSRF, 세션 인증.
- security/AdminBootstrap: 관리자의 환경변수 기반 첫 생성, 기본 계정·비밀번호 없으면 새 DB 기동 차단.
- security/AuthController/AdminUserController: 현재 계정·CSRF·계정 발급, 비밀번호 해시 반환 금지.
- frontend/src/lib/api.js: cookie same-origin, CSRF 메모리, POST에 토큰 자동 적용, 세션 만료 이벤트.
- frontend/src/stores/auth.js 및 LoginView/UsersView: 로그인과 계정관리.
- frontend/src/router/index.js, App.vue, 업무 4개 View: 로그인 라우팅·조회자 쓰기 UI 제한.
- SessionSecurityIntegrationTest 및 브라우저 CI: 실제 Spring Security 필터와 사용자 화면 시나리오 검증.

## 4. 예외/테스트 계획
- 익명 GET 401, 익명 POST(토큰 누락) 403, 관리자의 정상 POST 성공, VIEWER GET 허용/POST 및 계정조회 403.
- 로그인 CSRF 누락/잘못된 비밀번호/로그아웃 이후 401.
- 기존 제조 테스트는 보안 필터 제외로 비즈니스 회귀 책임 분리, 보안 테스트는 실제 필터 포함.
- 실 MySQL+Chrome 업무 E2E는 로그인/CSRF 추가 후 전체 업무 재실행.

## 5. 시니어 검수
좋은 점: 단일 VM에 맞는 서버 세션, BCrypt, CSRF, 서버 인가, 계정 생성 응답에서 민감정보 제거, 비밀 없는 최초 DB 기동 거부.
실제 발견/수정: V2 추가 후 Flyway/JPA 기준선 테스트 카운트 교정, 비웹 SpringBootTest의 servlet filter 구성 방어, 로그아웃 후 보호 화면 이탈, 보안 필터 테스트 분리.
위험: login rate limit, 관리자 계정 복구, 비밀번호 변경/비활성화, 운영 HTTPS Proxy 및 Secure Cookie, 배포 환경 인증/세션 테스트는 남아 있음.
나중 개선: API 오류 JSON traceId 통일, 공통 권한 컴포넌트, Playwright 의존성 잠금, 사용자 인증·행위 감사로그.

## 6. 설명용 요약
FactoryFlow의 제조 API에 서버 세션 인증과 CSRF 보호를 적용하고 관리자는 조회/쓰기, 조회자는 읽기만 가능하게 했습니다. 프론트 버튼 숨김에 의존하지 않고 백엔드 필터에서 실제 권한을 강제합니다.

## 7. TASK-006 인증 경합 추가 검수

- 기준 PR: #12, `feature/task-006-session-security` (TASK-005D main과 분리).
- 변경 위치: `frontend/src/lib/api.js`, `frontend/src/stores/auth.js`, `frontend/src/lib/api.security.test.js`.
- 로그인 성공과 후속 CSRF 조회를 분리. 로그인이 성공한 뒤 CSRF 재조회 장애 때문에 잘못된 로그인 실패 UI가 발생하지 않도록 한다.
- 동시 라우터 가드는 진행 중인 `/auth/me` Promise를 공유. 로그인/로그아웃 세대가 바뀌면 이전 응답은 사용자 상태를 덮어쓰지 못한다.
- 403은 VIEWER 권한 거부일 수도 있으므로 단순 로그아웃하지 않고 `/auth/me`로 확인한다. 오래된 요청의 401/403은 새 세션을 무효화하지 않는다.
- 기능적 비용: 진단용 `/auth/me` GET은 업무 API 403 발생 시에만 수행. 네트워크 장애 시 기존 403을 보존한다.
- 단위 테스트: Node fetch 모의 9개 시나리오. 로컬 독립 테스트 9/9 통과(2026-11-12).
- **CI 성공 확인:** Backend 57/57, Frontend Node 23/23 · Vite 빌드 · Chrome Fixture 11/11, Fullstack MySQL+Chrome 2/2. 운영 배포 보안 검증 이전 외부 공개 금지.
- 후속 리팩터링: 운영 배포에서 로그인 시도 제한·HTTPS·Secure Cookie·프록시 헤더·세션/CSRF 실환경 확인.

## 8. CI 실제 실행 증거 및 판정

- [Backend CI](https://github.com/jeon950523/factoryflow/actions/runs/43110093904): Java 21, Testcontainers MySQL 8.4, `clean test` / **JUnit 57·실패 0·오류 0·스킵 0**.
- [Frontend CI](https://github.com/jeon950523/factoryflow/actions/runs/43110093813): Node 23/23, Vite 프로덕션 빌드 성공, Chrome Fixture 11/11.
- [Fullstack Browser E2E](https://github.com/jeon950523/factoryflow/actions/runs/43110093834): Spring Boot·Flyway·MySQL 기동 성공, 로그인+CSRF 포함 정상 제조 업무 1건 및 재고 부족 롤백 1건, **2/2**.
- 검증된 코드 SHA: `eb5e8780380f0c4ffe9b160f1196b1485f53bdfb`. CI 실행 뒤 문서만 갱신하며 애플리케이션 코드는 변경하지 않음.
- **검증 경계:** CI는 테스트 격리 환경. 외부 Vercel/Oracle, 실제 HTTPS Proxy, Secure/SameSite Cookie, 세션 제한·관리자 복구·사용자 비활성화 정책은 검증되지 않음.
- 최종 판단: **TASK-006 MVP 인증·인가 구현 완료 / 인터넷 공개 배포 보류**. 실무 전환 단계에서 로그인 시도 제한, 세션·쿠키 실환경 검증, 관리자 계정 복구, 감사로그를 우선 처리.
