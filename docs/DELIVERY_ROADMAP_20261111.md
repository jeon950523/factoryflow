# FactoryFlow — 개발·무료 배포 로드맵 (2026-11-11)

> 현재 단계: **1/5 — 코드 통합 완료, CI 실검증 대기**. 단계 간 진입 게이트를 통과하지 않으면 다음 단계의 '완료'로 표시하지 않는다.

| 순서 | 작업 | 완료 조건 | 현재 상태 |
|---|---|---|---|
| 1 | 기존 코드 통합 및 CI 빌드·MySQL 테스트 | GitHub Actions `clean test` 초록색, Testcontainers 실행 로그, Flyway/JPA 검증 | **통합 완료 · CI 대기** |
| 2 | 생산지시·예약·품질검사·출고 | SSOT 불변조건 및 동시성·실패 롤백 테스트 통과 | 계획 |
| 3 | Vue 3 관리자 화면 | API 연결, 정상 업무 시나리오 클릭 시연 | 계획 |
| 4 | 로그인·인가·공개 배포 보안 | 무인증 쓰기 차단, 역할 분리, 비밀 관리, CORS·CSRF 정책 | 계획 |
| 5 | Vercel + Oracle Cloud Always Free | Vercel UI에서 Oracle VM API 접근, TLS, 인증, DB 백업/복원, 무료 할당량 검증 | 계획 |

## 결정 원칙

1. Java 21, Spring Boot, JPA, MySQL을 유지한다.
2. Java/Spring Boot와 MySQL을 변경하지 않고 **Oracle Cloud Always Free VM**을 최우선 검토한다. 무료 VM 확보·가동은 보장되지 않으며 대안 검토가 필요하다. 이전의 개인 PC + Tunnel 안은 폐기한다.
3. 인증·인가 구현 이전에는 어떤 공인 호스팅에서도 쓰기 API를 공개하지 않는다.
4. 기술 선택과 주요 코드에는 TASK 보고서/ADR/실측 근거를 남긴다.
5. 사용자가 1단계 GitHub 통합 및 CI 검증 진행을 승인한 범위에서 기능 브랜치를 사용한다. main 병합은 테스트 통과 및 별도 검수 후 결정한다.
