# FactoryFlow TASK-002 — 초기 개발 기반 구축 작업지시서

> 상태: **미구현 / 다음 작업**  
> 선행 기준: `FACTORYFLOW_SSOT_v0.1.md`  
> 작업 목적: 생산 로직 작성 전, 동일 환경에서 DB 스키마와 실제 MySQL 통합 테스트를 반복 실행할 수 있는 최소 프로젝트 골격 확보.

## 1. 범위

### 백엔드

- Java 21 + Spring Boot 프로젝트 생성, Gradle Wrapper 고정.
- `item`, `inventory`, `production`, `quality`, `shipment`, `global` 도메인 패키지 생성(기능 구현은 아직 하지 않음).
- Spring Web, Validation, Spring Data JPA, MySQL Driver, Flyway, Spring Boot Test, Testcontainers(MySQL/JUnit) 구성.
- 환경별 설정: 기본값은 로컬 개발, 운영 비밀번호는 환경변수로 분리. `.env`/실제 비밀번호 Git 커밋 금지.
- `ApiErrorResponse`/`GlobalExceptionHandler`의 최소 틀과 `GET /actuator/health` 등 헬스체크 추가(실제 의존성 확인 후).
- Flyway V1 DDL에서 SSOT의 테이블·PK/FK·UNIQUE·CHECK·INDEX 구현. JPA `ddl-auto=validate` 기준.
- `README.md`에 실행 전제조건, Docker Compose 시작 방법, 테스트 명령 설명.

### 인프라

- MySQL 8.4를 Docker Compose로 제공. DB 데이터 볼륨, healthcheck, 외부 포트 충돌 방지.
- `application-local.yml`에서 로컬 DB 정보 주입, `application-test.yml`은 Testcontainers 동적 연결.
- GitHub Actions에서는 Java 21 빌드 및 테스트 실행. Testcontainers Docker 실행 가능 여부 확인.

### 테스트

- 빈 DB에서 Flyway V1 마이그레이션이 정상 적용되는 통합 테스트.
- JPA 매핑과 DB 스키마가 `validate`에 통과하는 기동 테스트.
- DB CHECK / UNIQUE / FK 실제 제약 검증용 최소 테스트.
- 새 DB 컨테이너에서 테스트를 다시 실행해도 통과하는지 확인.

## 2. 구현 순서

1. 저장소·Java/Gradle 환경 생성.
2. Docker Compose와 DB 연결 상태 확인.
3. Flyway V1 마이그레이션 작성 및 DB 제약 확인.
4. JPA 엔티티 스켈레톤과 Repository 기반 기동 테스트.
5. 예외 처리 기본 구조 및 README 작성.
6. Testcontainers 기반 통합 테스트.
7. CI 실행 및 검증 로그 저장.

## 3. 종료 조건 (필수)

- [ ] JDK 21 기준 Gradle clean test 성공.
- [ ] 새 MySQL 컨테이너에서 Flyway migration 성공.
- [ ] 모든 핵심 테이블의 PK/FK/UNIQUE/CHECK 확인.
- [ ] JPA `ddl-auto=validate` 성공.
- [ ] Testcontainers MySQL 통합 테스트 성공.
- [ ] 시크릿·실제 계정 정보가 Git에 포함되지 않음.
- [ ] 변경 파일 목록, 실행 명령, 테스트 증거, 남은 위험 기록.

## 4. 지금 하지 말 것

- 생산 예약 서비스 로직, Redis/카프카/K8s/MSA, AI/RAG, 과도한 프론트 UI 개발.
- 테스트를 통과시키기 위해 H2로 교체하거나 DB 제약을 비활성화하는 행위.
- 검증 없이 성능 개선을 완료했다고 주장하는 행위.
- 사용자 지시 없이 원격 Git 저장소에 push.

## 5. 검수 우선 포인트

- 실제 Spring Boot/Java 21/Gradle 조합에 맞는 의존성 버전을 확인했는가?
- FK 컬럼에 인덱스가 존재하고, 복합 UNIQUE가 의도대로 동작하는가?
- 수량 타입을 `BIGINT`/`long`으로 통일하고 음수·오버플로 방어가 가능한가?
- `@Transactional` 경계가 Controller가 아닌 Service에 위치할 계획인가?
- 재고 LOT의 `CHECK (reserved_qty <= on_hand_qty)`가 실제 MySQL에서 강제되는가?
- 테스트 결과와 미구현 기능을 혼동하지 않았는가?

## 6. 예상 구현 후 리팩터링 검토 사항

- 동일한 예외/응답 매핑이 도메인별로 중복된다면 공통화.
- JPA 엔티티를 API 응답으로 직접 반환하는 코드가 있다면 DTO로 분리.
- 상태 전이와 수량 연산이 Controller/Repository에 분산되었다면 Service/도메인 메서드로 응집.
- 모든 조회에 비관적 락을 사용하면 처리량 저하가 발생하므로 쓰기 명령에만 적용.

### 다음 작업에 필요한 입력

- 실제 코드 생성·빌드·검증을 실행할 작업 저장소 또는 로컬 작업 환경.
- 저장소 초기 상태와 선택한 패키지/아티팩트 이름(기본값 `com.factoryflow`).

> 이 작업지시서는 구현 완료 표시가 아니다. 작업 결과가 나오면 테스트 증거를 검수한 뒤 TASK-003으로 넘어간다.
