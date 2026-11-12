# FactoryFlow — 개발·무료 배포 로드맵 (2026-11-11)

> 현재 단계: **4/5 Java·MySQL 인증/업무 구현 검증 완료 + Vercel 브라우저 데모 별도 제공. Oracle 실서버 배포는 가입 문제로 보류**. 운영 배포·접근성·다중 사용자 QA는 별도. 단계 간 진입 게이트를 통과하지 않으면 다음 단계의 '완료'로 표시하지 않는다.

| 순서 | 작업 | 완료 조건 | 현재 상태 |
|---|---|---|---|
| 1 | 기존 코드 통합 및 CI 빌드·MySQL 테스트 | GitHub Actions `clean test` 초록색, Testcontainers 실행 로그, Flyway/JPA 검증 | **CI 51/51 통과 · 완료** |
| 2 | 생산지시·예약·품질검사·출고 | SSOT 불변조건 및 동시성·실패 롤백 테스트 통과 | **전체 API 골든 시나리오·부족 재고 롤백·멱등성·전량 불합격 보호 포함 실제 MySQL CI 49/49 성공 · 백엔드 2단계 완료. 실브라우저/배포 검증은 3~5단계** |
| 3 | Vue 3 관리자 화면 | API 연결, 정상 업무 시나리오 클릭 시연 | **Vue API 연결, Chrome Fixture 8개 및 실제 Spring/MySQL 업무 E2E 2개 성공. 접근성/운영망 QA 미완료** |
| 4 | 로그인·인가·공개 배포 보안 | 무인증 쓰기 차단, 역할 분리, 비밀 관리, CORS·CSRF 정책 | **세션 인증·인가 기능 및 CI(백엔드 57/57, Node 23/23, Chrome 11/11, 실DB 2/2) 검증 완료. 로그인 제한 5분 슬라이딩 윈도 CI 64/64 및 실DB E2E 2/2 추가 검증 완료. 운영 TLS·프록시·쿠키·엣지 제한·관리자 복구 미검증으로 외부 공개 금지** |
| 5 | Vercel 프론트 데모 / 실제 Spring 서버 장기 배포 | 공개 체험: Vercel+IndexedDB, Java/MySQL: CI. 서버 운영은 별도 HTTPS·TLS·백업·계정 복구 검증 | **데모 TASK-008 구현/브라우저 CI 검증. Oracle 가입 실패로 VM 보류; 실제 백엔드 배포·TLS·백업 미완료** |

## 결정 원칙

1. Java 21, Spring Boot, JPA, MySQL을 유지한다.
2. Java/Spring Boot와 MySQL을 변경하지 않고 **Oracle Cloud Always Free VM**을 최우선 검토한다. 무료 VM 확보·가동은 보장되지 않으며 대안 검토가 필요하다. 이전의 개인 PC + Tunnel 안은 폐기한다.
3. 인증·인가 구현 이전에는 어떤 공인 호스팅에서도 쓰기 API를 공개하지 않는다.
4. 기술 선택과 주요 코드에는 TASK 보고서/ADR/실측 근거를 남긴다.
5. 사용자가 1단계 GitHub 통합 및 CI 검증 진행을 승인한 범위에서 기능 브랜치를 사용한다. main 병합은 테스트 통과 및 별도 검수 후 결정한다.

## 2026-11-12 배포 전략 변경 — TASK-008

- 기존 Oracle Always Free 배포는 **계정 생성 단계에서 진행이 막혀 보류**. 홈 리전 자원 확보도 미확정.
- `factoryflow.kro.kr`은 Vercel 커스텀 도메인 연결이 완료되었으므로, 사용자 시연은 서버 없이 **IndexedDB 데모**로 제공.
- 실제 Spring Security/CSRF/BCrypt/MySQL 업무 트랜잭션은 GitHub Actions로 검증하며, 데모 데이터와 실제 업무 DB를 혼동하지 않는다.
- 후속 서버가 준비될 때만 API 모드로 전환한다(별도 실환경 QA 필요). [ADR-013](adr/ADR-013-프론트데모-실백엔드분리.md).

## TASK-009 — 제조 업무 중심 UI 재구성

- **결정:** 기존 Spring Boot·MySQL API를 변경하지 않고, Vue 관리자 화면의 작업 우선순위·표 가독성·키보드 작업 접근성을 개선한다.
- **현 상태:** 작업 대기(초안/예약/검사), 생산 진행, 완료, 조회한 재고 품목의 가용재고 0을 기존 데이터만으로 표시한다.
- **검증:** Frontend Node 37/37, Chrome fixture 11/11, Vercel형 Demo Chrome 6/6 및 Spring Boot·MySQL E2E 2/2 성공.
- **향후 과제:** 업무 사용자 실측 피드백, 표 밀도·대비의 시각 검토, API 집계 호출 최적화 필요 여부 성능 측정. 설비 가동률/작업조/재고 임계치 등 미구현 데이터 사용 금지.
- [검수 문서](task-reports/TASK-009-제조업무형UI-검수.md)
