# TASK-007B — 도메인 기반 운영 배포 템플릿 시니어 검수

상태: **배포 템플릿 구현 및 GitHub Actions CI 검증 완료(2026-11-12)**. 실제 Oracle/Vercel 배포는 수행하지 않음.

## 1. 목적
factoryflow.kro.kr / api.factoryflow.kro.kr 호스트로 Vercel(Vue)·Oracle(Spring Boot + MySQL)·Caddy(HTTPS)를 이어주는 재현 가능한 템플릿을 만든다.

## 2. 전체 호출 순서
브라우저 factoryflow.kro.kr → Vercel 정적 Vue → 동일출처 /api → Vercel external rewrite → api.factoryflow.kro.kr HTTPS Caddy → 127.0.0.1:8080 Spring Boot Docker → 세션 인증/CSRF/RBAC → 내부 Docker MySQL.

## 3. 파일 책임
- backend/Dockerfile / .dockerignore: Java 21 multi-stage build 및 non-root runtime.
- application-prod.yml: prod 환경변수 DB 설정, Secure+HttpOnly+SameSite=Lax 세션 쿠키.
- infra/compose.prod.yml: MySQL 비공개, Spring Boot host-loopback only.
- infra/Caddyfile: API 도메인 TLS proxy.
- frontend/vercel.json: 동일출처 API rewrite 및 Vue SPA fallback.
- infra/.env.prod.example: 비밀 없는 값 형식, 실제 .env.prod는 Git 금지.
- infra/scripts/backup-db.sh: 권한 제한된 SQL 덤프, 외부 보관/암호화/복원 미구현.
- .github/workflows/deploy-config-ci.yml: 설정/컨테이너 빌드/네트워크 경계 검증.

## 4. 예외·검증
DNS는 사용자의 도메인 관리 화면 확인만 완료됐으며 A 값은 빈 칸. Vercel 프로젝트별 A 값과 Oracle 공인 IP가 없어 외부 도메인 연결은 미실행.
CI의 Caddy·Compose·Docker 빌드는 운영 TLS·DNS·VM 실행을 대체하지 못한다.

## 5. 시니어 검수
- 좋은 점: 비밀 분리, 내부 DB, localhost API, prod 쿠키 강제, Java 21 non-root runtime.
- 즉시 해결할 위험: 프록시/Docker의 동일 remoteAddr로 인해 TASK-007A per-peer 제한이 사용자별로 적용되지 않을 수 있음. Edge 제한과 신뢰 프록시 식별 설계 필수.
- 운영 전 필수: VM 리소스/비용 확인, HTTPS/세션 브라우저 검증, DB 백업 암호화·외부 저장·복원, 관리자 복구.
- 2차: 이미지 digest pinning, 이미지 취약점 검사, 로그·경보 자동화. 현 단계에서 MSA/Kubernetes 도입 불필요.

## 6. 설명 요약
브라우저는 Vercel에만 접속하고 /api를 HTTPS 프록시로 전달합니다. Oracle에서는 Caddy 뒤 Spring Boot를 loopback으로 제한하고 MySQL은 Docker 내부에 둬 공격 표면을 줄였습니다. 실환경 인증/DB 복구 검증은 배포 전 게이트입니다.

## 7. CI 실제 검증 증거

| 검증 | 결과 | 근거 |
|---|---|---|
| 배포 설정 구성·Caddy·Dockerfile·non-root 검사 | **성공** | [43202990848](https://github.com/jeon950523/factoryflow/actions/runs/43202990848) |
| Frontend Node · Vite · Chrome Fixture | **Node 23/23, Chrome 11/11, Vite build 성공** | [43202990778](https://github.com/jeon950523/factoryflow/actions/runs/43202990778) |
| Backend Java21·Testcontainers MySQL | **JUnit 64/64, 실패 0, 오류 0, 스킵 0** | [43202990845](https://github.com/jeon950523/factoryflow/actions/runs/43202990845) |
| Fullstack Spring Boot·MySQL·Chrome | **2/2 성공** | [43202990622](https://github.com/jeon950523/factoryflow/actions/runs/43202990622) |

- 검증된 구현 커밋: `fb079a5a78f183362d3178de4e9460c062bee39e`. 이 이후는 문서 갱신.
- Docker 이미지 **빌드 및 non-root 메타데이터**가 검증됐으며 Oracle VM에서 `prod` 프로필 실제 기동까지 검증한 것은 아님.
- Vercel Custom Domain, 루트 A 레코드, API A 레코드, Oracle 공인 IP, 실제 HTTPS, Cookie, MySQL 복원은 여전히 **미검증**.
- 출시 판정: **템플릿 준비 완료 / 공개 배포 승인 보류**.
