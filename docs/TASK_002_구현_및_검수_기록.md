# FactoryFlow TASK-002 — 구현·검수 기록 (2026-11-11)

## 1. 현재 상태 (사실)

- Java 21 애플리케이션, Gradle Wrapper(9.5.1), Spring Boot 4.1.2, Flyway 스키마, JPA 엔티티 스켈레톤, MySQL Compose, Testcontainers 통합 테스트 코드, GitHub Actions를 **작성**했다.
- 이 실행 환경에서는 `java -version`만 정상 확인했다. Gradle 실행 환경·Docker 엔진·외부 네트워크가 없어 **Gradle 빌드 / MySQL 실기동 / JPA validate / 테스트 통과를 검증하지 못했다**.
- 원본 SSOT와 TASK-002 문서를 보존했다. 구현 전제로 작성된 문서의 실적을 임의로 완료 처리하지 않았다.

### 수행한 정적 검증 (실제 실행 결과)

- Java 21 `javac --release 21`로 외부 라이브러리가 필요 없는 enum 6개와 오류 응답 record 1개를 **컴파일 성공**.
- Flyway V1에 정의된 12개 도메인 테이블과 JPA 엔티티 12개가 1:1 대응하고 SQL 컬럼 목록과 `@Column` 필드 이름이 정적 비교에서 일치함.
- Spring 설정 3개 및 Docker Compose, GitHub Actions YAML 파싱 성공.
- Gradle Wrapper JAR 존재(48,462바이트)와 `gradlew` 셸 문법 확인. Wrapper 실행 / Gradle 배포파일 다운로드는 검증 못함.
- ZIP 무결성 검사 통과. **이 검증만으로 Spring 애플리케이션이 기동된다고 판단하면 안 됨.**

## 2. 구현 구조 및 목적

| 파일 | 목적 | 이유 |
|---|---|---|
| `backend/build.gradle` | Spring Boot/Java21 의존성 고정 | 버전 BOM으로 호환 가능한 의존성 세트 유지 |
| `backend/gradlew*`, `backend/gradle/wrapper/*` | Gradle 9.5.1 실행 버전 고정 | 실행자의 전역 Gradle 설치 차이 방지 |
| `backend/src/main/resources/db/migration/V1__...sql` | 12개 테이블+CHECK/FK/UNIQUE/INDEX | 개발자의 상태 검증 누락 시 DB가 최종 방어 |
| `backend/src/main/java/com/factoryflow/*/*Entity.java` | JPA 필드↔DB 컬럼 기준 정의 | `ddl-auto=validate`로 스키마 드리프트 조기 발견 |
| `backend/src/main/java/com/factoryflow/global/error/*` | 공통 API 오류 형식 | 내부 예외를 그대로 사용자에게 노출하지 않기 위해 |
| `backend/src/main/java/com/factoryflow/global/logging/RequestTraceFilter.java` | 요청별 traceId 발급과 MDC 정리 | 같은 ID로 응답과 서버 로그 추적 |
| `infra/compose.yml` | 로컬 MySQL 8.4 실행 | 환경 편차 축소 / 비밀번호 .env 분리 / localhost 바인딩 |
| `backend/src/test/.../DatabaseBaselineIntegrationTest.java` | 실제 MySQL 기동 및 제약 검사 | H2에서는 MySQL CHECK/FK/JPA 타입 동작 검증 불가 |
| `.github/workflows/backend-ci.yml` | push/PR 시 Gradle 테스트 | 로컬과 CI 검증 환경 일관성 확보 |

## 3. 전체 호출 흐름 (TASK-002)

```text
Docker Compose → MySQL 8.4 실행
→ Spring Boot 시작 → DataSource 연결
→ Flyway V1 마이그레이션 실행
→ Hibernate(JPA) ddl-auto=validate 스키마 비교
→ Actuator /actuator/health 응답

테스트: Gradle test → Testcontainers MySQL 시작 → @DynamicPropertySource 주소 제공
→ Flyway V1 → JPA validate → SQL CHECK/UNIQUE/FK 검증
```

## 4. 장점

- 수량은 `BIGINT/long`이고 0 미만/초과 예약을 DB CHECK로 차단한다.
- 사용자의 로컬 MySQL에 접근하지 않고 Docker 컨테이너에서 반복 테스트하도록 준비했다.
- API 기능 구현 이전에 제약과 스키마를 먼저 확정해 동시성·재고 정합성 개발에 기준이 생긴다.
- ORM이 자동 테이블 생성하지 않으므로 Flyway가 스키마 변경의 단일 출처다.

## 5. 단점 / 위험

- 지금은 **실행 검증 미완료**이며, 실제 API도 없다. Testcontainers 테스트 실행 후 오류 수정이 필요할 수 있다.
- JPA 엔티티는 필드 매핑만 존재한다. 상태 전이·재고 변경을 위한 도메인 메서드는 TASK-003~에서 작성한다.
- FK는 같은 품목 유형, 생산지시-BOM 일치, LOT-품목 일치까지 교차 검증하지 못한다. 서비스 로직·통합 테스트로 방어한다.
- 모든 REST 오류에 대한 표준화/추적 로그가 완성된 상태는 아니다. 서버에서 traceId를 발급해 응답과 MDC에 연결하지만, 분산 추적 시스템(OpenTelemetry)은 미구현이다.
- 도메인 엔티티가 `Long` FK만 갖고 JPA 연관관계를 정의하지 않아 조인 조회는 명시적으로 설계해야 한다. 장점은 ORM 지연 로딩·순환 관계 오염 감소.

## 6. 우선 리팩터링 계획

**실제 빌드·MySQL 통합 테스트 결과 확인 후** JPA 타입 매핑 차이(특히 MySQL의 `ENUM`/VARCHAR, `DATETIME(6)`), Spring Boot 모듈 스타터를 점검해 오류 수정한다.

TASK-003에서:
- `ItemEntity`에 생성 팩토리/검증 및 조회 API 도입
- enum 타입을 서비스/HTTP DTO와 분리하고 상태 변경 전이를 도메인 메서드에 캡슐화
- 입고 멱등성: 동일 요청 비교와 충돌 후 새 트랜잭션 재조회
- 일반 요청 ID를 MDC에 넣어 실제 HTTP 로그-오류 응답 연결
- DTO/Service/Repository 단위로 테스트 세분화

## 7. 완료 전 남은 실제 검증 명령 (PowerShell, 한 줄)

```powershell
Copy-Item .\infra\.env.example .\infra\.env; notepad .\infra\.env
```

```powershell
docker compose --env-file .\infra\.env -f .\infra\compose.yml up -d
```

```powershell
$env:DB_PASSWORD=(Get-Content .\infra\.env | Select-String '^FACTORYFLOW_DB_PASSWORD=').ToString().Split('=',2)[1]; Set-Location .\backend; .\gradlew.bat clean test
```

`./gradlew` 는 macOS/Linux, `.\gradlew.bat` 는 Windows. 실제 테스트 결과(성공/실패, 환경·버전)를 확인하기 전까지 TASK-002는 미완료.

## 8. 내가 직접 설명할 수 있는 요약

> Spring Boot 시작 시 Flyway가 테이블을 생성하고 Hibernate는 `validate`만 수행하도록 분리했습니다. 재고 수량에 대한 CHECK, 중복 요청 키의 UNIQUE, 참조 무결성 FK를 DB에 정의했습니다. Testcontainers MySQL 통합 테스트로 실제 제약이 강제되는지 검증할 예정이며, 현재는 실행 환경 제한으로 테스트 통과를 아직 주장하지 않습니다.
