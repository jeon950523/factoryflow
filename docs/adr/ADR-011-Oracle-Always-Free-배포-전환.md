# ADR-011 — Oracle Always Free 우선 배포로 전환

> 상태: **조건부 채택 / 실행 및 무료 VM 확보 미검증**  
> 날짜: 2026-11-11  
> 대체한 결정: [ADR-010 Cloudflare Tunnel](ADR-010-무료배포-Cloudflare-Tunnel.md)

## 1. 목적과 제약

- 포트폴리오 방문자는 개인 개발 PC가 꺼져 있어도 서비스에 접속해야 한다.
- AWS 무료 크레딧 소진을 피하고 월 고정비용 0원을 우선한다.
- Java 21, Spring Boot, JPA, MySQL 8.4를 유지한다. Workers + D1로 재작성하지 않는다.

## 2. 선택 및 요청 흐름 (예정)

`브라우저 → Vercel(Vue) → HTTPS/TLS 프록시 → Oracle Cloud Always Free VM(Docker, Spring Boot) → MySQL 8.4(내부 네트워크)`

- VM은 실행 호스트다. Cloudflare Tunnel과 달리 개인 개발 PC에 의존하지 않는다.
- MySQL의 3306 포트는 인터넷에 노출하지 않는다. 앱 컨테이너와 DB 컨테이너만 내부 통신한다.
- 빌드·검증은 GitHub Actions의 Java 21/Testcontainers를 사용한다. 운영 VM에는 필수 런타임만 배치한다.
- 인증·인가 전에는 공개 쓰기 API를 배포하지 않는다. TLS, CORS, 비밀 관리, DB 백업 및 복구 시연이 배포 완료 조건이다.

## 3. 대안 비교

| 방안 | 장점 | 단점/제약 | 선택 |
|---|---|---|---|
| Oracle Always Free VM | JVM·MySQL 유지, 개발 PC 상시 가동 불필요, 무료 한도 | 리전별 용량 부족, 계정 검증, 유휴 VM 회수, 직접 운영 | 1순위 후보 |
| Cloudflare Tunnel + 집 PC | Tunnel 자체 무료, JVM 변경 없음 | 집 PC를 켜 둬야 서비스가 동작 | 제외 |
| Render Free + 별도 DB | PC 가동 불필요, 배포 편의 | 유휴 시 재기동, DB 서비스 별도, 요금/쿼터 변화 | 대안 후보 |
| Workers Free + D1 | Cloudflare 엣지/무료 할당 | Java JVM/MySQL 사용 불가, 동시성·DB 설계 교체 필요 | 제외 |

## 4. 리스크 및 실제 검증 게이트

1. Oracle 계정과 무료 VM 리전 용량을 확보할 수 있는지 확인한다. 무료라고 항상 생성 가능한 것은 아니다.
2. VM 리소스가 Spring Boot + MySQL을 안정적으로 감당하는지 측정한다. 사용량·비용 경보 설정.
3. SSH 접속 최소화, 방화벽 80/443 제한, MySQL 외부 비공개, non-root 컨테이너 검토.
4. 인증·권한·세션·CORS/CSRF가 설계되고 무인증 쓰기 차단 테스트를 통과해야 공개한다.
5. GitHub Actions `clean test`와 스키마·DB 동시성 검증 기록을 확보한다.
6. 백업 파일을 별도 저장하고 복원 리허설을 성공해야 운영 전환한다.

## 5. 장단점과 리팩터링

- **장점:** 자바/DB 코드 재작성이 없고, 개인 PC를 켜 둘 필요가 없다. 기존 도메인 설계와 테스트를 유지한다.
- **단점:** 관리형 서비스와 달리 보안 패치, 로그, 재부팅, 장애, 데이터 복구를 직접 책임진다. 무료 컴퓨트 자원 회수가 가능하다.
- **즉시:** 배포 문서의 Tunnel 채택 표현 수정, CI 실제 실행/검증.
- **운영 전 필수:** 인증·인가, 최소권한 네트워크, TLS, 백업, 자원·비용 알림.
- **후속 개선:** 배포 자동화, healthcheck/restart, 모니터링, DB 분리.
- **현재 불필요:** JVM 스택을 버리고 Workers/D1로 재작성하거나 Kubernetes를 도입하는 것.

## 내가 설명할 한 문장

> 무료 네트워크 터널과 서버 호스팅은 다릅니다. 개발 PC를 상시 가동할 수 없어 JVM 실행 호스트가 필요한 Oracle Always Free VM을 1순위로 선택했고, Java와 MySQL의 트랜잭션 의미를 유지했습니다. 다만 VM 확보와 무료 운영은 별도 확인이 필요합니다.
