# TASK-007B 배포 검증 상태

- 설정 대상: factoryflow.kro.kr / api.factoryflow.kro.kr
- 구성: Java 21 Spring Boot / MySQL 8.4 / Vue 3 Vercel / host Caddy.
- GitHub Actions workflow: .github/workflows/deploy-config-ci.yml
- CI 상태: 실행 대기. CI 결과가 나온 뒤 기록 갱신.
- 운영 실측: 미실행. Oracle VM 공인 IP, Vercel Domain DNS record, TLS certificate, SSH, VM memory/fees 모두 확인 전.
- 업무 보안: 기존 TASK-006/007A 로컬·CI 성공이 운영 환경에서의 쿠키·프록시·IP 제한 작동을 보증하지 않음.
