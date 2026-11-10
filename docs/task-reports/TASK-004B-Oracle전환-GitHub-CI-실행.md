# TASK-004B — Oracle 무료 배포 전환 및 GitHub CI 검증

> 상태: **진행 중 / 실 CI 결과는 별도 검증 전까지 미확정**  
> 날짜: 2026-11-11

## 1. 목적

개발 PC를 24시간 켜 두지 않으면서 Java 21/Spring Boot + MySQL 기반 포트폴리오를 공개 운영할 수 있도록 배포 의사결정을 수정한다. 동시에 TASK-002/003A/003B 코드의 `clean test`를 실제 GitHub Actions에서 실행해 빌드·DB 매핑·트랜잭션 테스트를 검증한다.

## 2. 호출 및 검증 흐름

`GitHub 기능 브랜치 push → Backend CI → setup-java(21) → Gradle Wrapper → clean test → Testcontainers MySQL(8.4) → Flyway V1 → JPA validate → API/동시성 테스트 → JUnit/XML 결과`.

## 3. 파일별 역할과 설계 이유

- `README.md`: 현재 검증 상태·배포 계획을 과장 없이 보여준다.
- `docs/DELIVERY_ROADMAP_20261111.md`: 1~5단계 완료 게이트와 배포 목적지를 변경한다.
- `docs/adr/ADR-010-무료배포-Cloudflare-Tunnel.md`: 과거 결정을 삭제하지 않고 **폐기됨**으로 남긴다. 왜 변경했는지 설명할 증거다.
- `docs/adr/ADR-011-Oracle-Always-Free-배포-전환.md`: 제약, 대안, 장단점, 보안/무료 운영 위험을 기록한다.
- `.github/workflows/backend-ci.yml`: 실제 Java 21 빌드, 테스트 실행과 실패 보고서 업로드를 담당한다.
- `backend/`: 도메인, Flyway, Testcontainers 기반 코드 (기존 TASK-002~003B 내용 유지).

## 4. 장점과 단점

- **장점:** 구현 스택 유지, 개발 PC와 분리, 기능 브랜치 CI로 실증, 배포 의사결정 이력 공개.
- **단점:** Oracle Always Free VM 확보·회수 불확실성, 운영 및 DB 백업 책임, 무료 운영 지속성 미보장.

## 5. 테스트 및 검증 기준

- 기초 정적 검사: Java 파싱, YAML, SQL/JPA 테이블 대응은 빠른 참고자료이고 CI 통과를 의미하지 않는다.
- 실 검증: GitHub Actions의 `clean test` 결과, Testcontainers 도커 실행, Flyway/JPA 시작, API/동시성 테스트 JUnit 결과를 확인해야 한다.
- CI 실패 시 오류 로그를 근거로 수정하고 재실행한다. 실행하지 못한 검사는 `미검증`으로 남긴다.

## 6. 시니어 검수 및 리팩터링 판단

- **즉시 고쳐야 하는 것:** 통합 빌드 오류, 부정확한 의존성, Flyway/JPA 매핑 오류, 실패하는 통합 테스트.
- **다음 단계:** 생성 시점 `LocalDateTime.now()`를 주입 가능한 Clock으로 전환, 품목 선잠금이 과도한지 경쟁 테스트로 판단, 도메인 예외 분리.
- **운영 전 필수:** 인증·인가, TLS, MySQL 비공개, 시크릿 분리, 백업 및 복원 확인.
- **지금 불필요:** MSA/Kubernetes/Redis, 운영 DB 타 제품으로 마이그레이션.

## 7. 내가 설명할 요약

> 지속적인 공개 시연을 위해 컴퓨트 호스트와 터널링 서비스를 구분하고, Oracle Always Free VM을 우선 후보로 변경했습니다. 빌드/테스트는 GitHub Actions의 Java 21과 Testcontainers MySQL로 검증하며, CI 로그를 작업별 설계·결함 기록과 연결합니다.
