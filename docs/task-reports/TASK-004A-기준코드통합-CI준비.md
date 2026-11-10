# TASK-004A — TASK-002·003A·003B 코드 통합 및 CI 실행 준비

> 상태: **파일 통합·정적 검수 완료 / GitHub CI 및 실제 MySQL 검증 대기**  
> 작성일: 2026-11-11  
> 별도 보안 규칙: 인증 없는 쓰기 API의 외부 공개 금지.

## 1. 목적

산발적인 3개의 '변경 파일 ZIP'을 한 번에 열 수 있는 백엔드 기준 작업본으로 통합한다. 기능을 늘리기 전에 실제 컴파일, Flyway 마이그레이션, JPA 매핑, 중복 입고 경쟁 테스트를 CI에서 검증할 수 있도록 한다.

## 2. 포함된 구현과 범위

- TASK-002: Gradle Wrapper, MySQL/Flyway V1, JPA 엔티티, 공통 API 예외, Testcontainers 기초.
- TASK-003A: Item/BOM 생성·조회, 입력 검증, SKU 정규화, UNIQUE 방어.
- TASK-003B: LOT 입고·재고 조회, 동일 요청 중복 재생/충돌, 품목 선잠금, 물리 재고 이동 기록.
- **이번 차수의 변경**: Docker 배포에 대비한 `SERVER_ADDRESS` 설정, GitHub Actions 검증 결과물 보존, 무료 배포 경로 ADR-010, 전체 통합 패키징.
- **아직 미구현**: 생산지시/재고 예약/품질검사/출고 기능, 인증·인가, Vue 화면, 실제 공개 배포.

## 3. 전체 실행/호출 흐름

### 빌드/테스트

`GitHub Actions → JDK 21 준비 → Gradle 9.5.1 Wrapper → clean test → Testcontainers mysql:8.4 시작 → Flyway V1 DDL → JPA validate → MockMvc API 테스트 → XML/HTML 테스트 결과 기록`

### 원자재 입고

`StockReceiptController.receive → StockReceiptService.receive (멱등성 선조회) → StockReceiptWriteService.write (@Transactional READ_COMMITTED) → ItemRepository.findForUpdate → InventoryLotRepository.findForUpdate → lot 수량 변경 → StockReceiptRepository.saveAndFlush → StockMovementRepository.saveAndFlush → 커밋`.

기존 `requestKey`가 재전송되면 같은 결과 200, 첫 등록이면 201, 기존 키 다른 페이로드면 409. DB UNIQUE 경쟁 예외 시 실패한 쓰기 트랜잭션 *밖*에서 기존 요청을 재조회한다.

## 4. 주요 변경 파일과 이유

| 경로 | 목적 | 이유 |
|---|---|---|
| `backend/src/main/resources/application.yml` | 서버 바인딩을 `SERVER_ADDRESS`로 지정 | 개발 PC의 localhost 기본값은 유지하되 컨테이너 실행 시 선택적으로 0.0.0.0 바인딩 가능 |
| `.github/workflows/backend-ci.yml` | Testcontainers 기반 clean test와 HTML/XML 결과 수집 | 테스트 실패 시 증거를 남기고 빌드 실패를 명확히 표현 |
| `docs/adr/ADR-010-무료배포-Cloudflare-Tunnel.md` | 무료 배포 제약 및 선택 근거 | Cloudflare Workers/D1로 Java/MySQL 코드를 무리하게 재작성하지 않음 |
| `README.md` | 상태·작업 순서·실행 방법 정리 | 미검증과 완료를 명확히 분리 |

## 5. 검증 결과

- 파일 통합: 이전 전체 작업본 `factoryflow-task003b-work` 기준, 원본 파일이 누락되지 않도록 복사.
- Java 21 컴파일러 API의 **파싱 검사**: 별도 검증 로그 참고. 이는 의존성을 사용한 타입 검사/전체 빌드가 아니다.
- YAML 문법 검사, SQL 테이블 개수·JPA 엔티티 대상 확인, 자격 증명 파일 누출 검사: 별도 검증 로그 참고.
- `./gradlew clean test`: **미실행**. 실행 환경에서 `services.gradle.org` DNS를 해석할 수 없어 Gradle 다운로드 실패; Docker Engine도 없음.
- GitHub Actions: **아직 실행되지 않음**. 따라서 상태를 성공으로 표시할 수 없음.

## 6. 시니어 검수: 장점·단점·리팩터링

### 잘한 점

- 단일 MySQL 트랜잭션에서 LOT 수량과 입고/이동 기록의 원자성 확보를 목표로 설계.
- 멱등성 재조회와 쓰기 트랜잭션을 별도 서비스에 분리하여 실패한 트랜잭션 재사용 위험 감소.
- Java/Spring/MySQL 백엔드 역량을 유지하는 배포 선택, 인증 없는 공개 금지.

### 현재 위험

- **가장 큼**: 실제 DB의 JPA 매핑·Boot 4 의존성·CI 결과를 검증하지 못함.
- 같은 품목의 모든 입고를 직렬화하는 잠금으로 처리량이 제한될 수 있음.
- 모든 `@Transactional` 경로, 고유키 충돌 재조회, SQL 정합성은 실제 Testcontainers에서 입증 필요.
- 코드가 작성됐어도 Spring Boot 기능이 정상이라고 단정할 수 없음.

### 리팩터링 판단

| 우선순위 | 내용 | 근거 |
|---|---|---|
| **즉시** | CI에서 Gradle·Flyway·JPA·Testcontainers 통과 확인, 실패 시 코드 수정 | 검증 전에는 후속 재고 기능을 안전하게 추가하기 어려움 |
| **공개 배포 전 필수** | 인증·인가·CORS·백업/복원 | 외부에서 재고 수정 API 노출 위험 |
| **2차 개선** | Item 선잠금과 LOT 잠금 성능 비교, `Clock` 주입으로 시간 테스트 개선 | 현재는 정합성 우선, 성능 근거 없음 |
| **지금 불필요** | Kafka·Redis·K8s 도입 및 Workers/D1 재작성 | 핵심 과제와 무관한 복잡도 증가 |

## 7. 다음 단계 게이트

1. 원격 저장소에서 **명시적 push 승인 시** 통합 코드를 별도 기능 브랜치에 올림. 기본 브랜치 변경/PR 머지는 별도 검수 후 진행.
2. GitHub Actions `Backend CI` 실행 로그와 테스트 결과를 확인하고 실패 항목부터 수정.
3. 1~2 통과 후 생산지시/재고 예약을 구현, 통합 테스트 추가.
4. 이후 Vue 화면, 인증·인가, Cloudflare Tunnel/Vercel 배포.

## 8. 설명 연습

> 기존 세 작업의 코드를 단일 프로젝트로 통합해, MySQL 데이터 무결성을 Flyway 및 Testcontainers에서 확인할 수 있는 CI를 준비했습니다. 서버 바인딩을 환경변수로 분리한 이유는 로컬에서는 localhost로 제한하고, Docker에 넣을 때는 컨테이너 내부 네트워크 수신이 필요하기 때문입니다. 현재 통합 테스트는 실행 환경 제약으로 미검증이어서 CI를 다음 필수 게이트로 두었습니다.

1. `127.0.0.1`과 `0.0.0.0`은 컨테이너에서 왜 다르게 동작할까?
2. Flyway 마이그레이션을 성공해도 JPA 검증이 필요한 이유는?
3. `@Transactional`이 같은 클래스 내부 호출에서는 기대대로 작동하지 않을 수 있는 이유는?
