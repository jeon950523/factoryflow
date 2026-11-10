# ADR-010 — 무료 배포에서 Cloudflare Tunnel 채택

> 상태: **폐기(Superseded by [ADR-011](ADR-011-Oracle-Always-Free-배포-전환.md)) / 미배포**  
> 작성일: 2026-11-11  
> 배경: AWS 무료 크레딧 소진 위험. Java 21/Spring Boot 4.1.2 + MySQL 8.4 기술 스택 유지가 포트폴리오의 핵심 목표.

## 1. 당시 의사결정 (현재 채택하지 않음)

- 프론트엔드: **Vue 3/Vite → Vercel** (향후 TASK-007).
- 백엔드/DB: **Spring Boot + MySQL → 개발 PC 또는 별도 상시 실행 호스트의 Docker**.
- 외부 연결: **Cloudflare DNS/HTTPS + Cloudflare Tunnel** (`cloudflared`가 오리진에서 아웃바운드 연결).
- 개발 중: 로컬 전용(`SERVER_ADDRESS=127.0.0.1`). Docker화할 때만 컨테이너 내부에서 `SERVER_ADDRESS=0.0.0.0`로 실행하고 호스트 포트는 `127.0.0.1`에 바인딩.
- **보안 선행조건**: 인가/인증 또는 별도의 강제 접근 제어 없이 쓰기 API를 공개하지 않는다.

## 2. Cloudflare를 '무료 Java 호스팅'이라고 볼 수 없는 이유

| 선택지 | 무료 여부 | Java/Spring Boot/MySQL 유지 | 판단 |
|---|---|---|---|
| Cloudflare Workers Free | 무료 할당량 | JVM 기반 Spring Boot 직접 실행 불가 | 제외 |
| Cloudflare D1 | 무료 할당량 | SQLite 기반이며 MySQL 트랜잭션/DDL과 다름 | 제외 |
| Cloudflare Containers | **Workers Paid 월 $5부터** | Java 컨테이너 실행 가능, MySQL은 별도 관리 필요 | 비용 때문에 보류 |
| Cloudflare Tunnel | 무료 플랜에서 공개 호스트네임 가능 | 오리진 컴퓨터에서 Spring Boot와 MySQL 실행 | **채택** |

공식 문서:
- Containers 가격: https://developers.cloudflare.com/containers/platform/pricing/
- Workers 지원 언어: https://developers.cloudflare.com/workers/languages/
- D1 개요: https://developers.cloudflare.com/d1/
- Tunnel 공개 애플리케이션: https://developers.cloudflare.com/cloudflare-one/networks/connectors/cloudflare-tunnel/routing-to-tunnel/

## 3. 전체 호출 흐름 (예정)

`사용자 브라우저 → Vercel 프론트엔드 → HTTPS(api 서브도메인) → Cloudflare Tunnel → cloudflared(오리진) → Spring Boot → MySQL`

- Cloudflare Tunnel은 서버를 호스팅하지 않는다. **오리진 PC가 꺼지면 API도 중단**된다.
- MySQL 포트는 외부 공개하지 않는다.
- Tunnel 토큰·Cloudflare 계정 인증 정보·`.env` 파일은 Git에 저장하지 않는다.
- 웹 프론트엔드와 API가 다른 도메인이면 CORS 정책과 쿠키/세션/CSRF 모델을 명시적으로 설계한다. 임의의 `*` CORS 설정은 금지.
- 공개 데모를 위한 인증·접근제어, 장애 시 복구, 백업/복원 절차를 별도 작업으로 관리한다.

## 4. 장점과 단점

**장점**: AWS 컴퓨트 사용료 없음, Java/JPA/MySQL 코드 재작성 없음, 터널 연결에 인바운드 포트포워딩 불필요, 실제 Docker 운영 경험 증명.

**단점**: 항상 켜진 호스트 및 네트워크 의존, 가정용 PC의 절전·재부팅·회선 장애, 운영 DB 백업 책임, 인터넷 공개 시 보안 위험, 실무용 SLA 제공 어려움.

**대안**: 비용을 허용하면 Cloudflare Containers + 외부 DB 또는 다른 JVM 호스팅, 상시 호스트를 확보하지 못하면 공개 데모 대신 녹화 영상 + 로컬 Docker 시연.

## 5. 반드시 검증할 항목

1. CI 빌드/DB 통합 테스트 통과.
2. 실사용 가능한 서버/호스트를 확보하고 Docker 재시작 정책 확인.
3. 로그인/인가 선행, 인증 없는 쓰기 요청 거부 증거.
4. Tunnel 연결 및 단일 API 정상 접근, DB 포트 외부 비공개 확인.
5. Vercel에서 CORS/인증 흐름 확인, 오리진 재시작/터널 단절 장애 재현.
6. MySQL 백업 파일 생성·복원 리허설.

> 검증 전에는 'Cloudflare에 배포 완료', '무료로 상시 가동'이라고 주장하지 않는다.

## 6. 추후 리팩터링 판단

- **지금 필요**: `server.address`를 환경변수로 변경하여 로컬과 Docker 컨테이너의 수신 바인딩을 분리.
- **공개 전 필수**: 인증/인가, CORS 제한, 최소 권한 DB 계정, 오리진 접근제어, 백업/복원.
- **2차 개선**: 전용 저전력 서버/대체 호스트, 운영 메트릭·재시작 경고, 컨테이너 이미지 크기 최적화.
- **지금 불필요**: Workers/D1 재작성, Kubernetes, Redis, Kafka.

### 내가 설명할 핵심

Cloudflare Tunnel은 Java 서버가 실행되는 곳이 아니라, 다른 컴퓨터에서 실행 중인 Spring Boot 서비스를 안전한 HTTPS 주소로 연결하는 프록시다. 무료 비용을 유지하며 JVM과 MySQL을 보존할 수 있지만, 실행 호스트와 DB 운영은 직접 책임져야 한다.
