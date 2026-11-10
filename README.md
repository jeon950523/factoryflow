# FactoryFlow

> **TASK-002·003A·003B 코드 초안 작성 / 빌드·DB 통합 테스트 미검증 (2026-11-11)**

Java 21 / Spring Boot 기반으로 제조업의 **원자재 입고 → BOM → 생산지시 → 재고 예약·소모 → 품질검사 → 완제품 입고·출고** 흐름을 구현하는 개인 백엔드 포트폴리오 프로젝트입니다.

**현재 개발 순서:** [1~5 로드맵](docs/DELIVERY_ROADMAP_20261111.md) · **배포 후보:** [Oracle Always Free ADR-011](docs/adr/ADR-011-Oracle-Always-Free-배포-전환.md) · **통합 검수:** [TASK-004A 보고서](docs/task-reports/TASK-004A-기준코드통합-CI준비.md)

## 프로젝트 목적

- 제조 업무의 데이터 모델과 상태 전이 설계
- 원자재 **현재고(on-hand)**, **예약고(reserved)**, **가용재고(available)**의 구분
- MySQL 트랜잭션·잠금과 멱등성 키를 통한 동시성 및 중복 요청 처리
- JUnit 5 + Testcontainers(MySQL)로 재고 정합성 검증
- SQL 실행 계획 및 측정 결과로 성능 개선의 근거 제시

## 현재 진행 상태

| 작업 | 상태 | 문서 |
|---|---|---|
| TASK-001 · 요구사항·도메인·기술 설계 | 설계 초안 작성 | [SSOT v0.1](docs/FACTORYFLOW_SSOT_v0.1.md) |
| TASK-002 · 개발 기반 구축 | 코드 초안 작성 / 실기동 검증 대기 | [구현·검수 기록](docs/TASK_002_구현_및_검수_기록.md) |
| TASK-003A · 품목·BOM API | 코드 초안 작성 / 실기동 검증 대기 | [코드·설계 검수 보고서](docs/task-reports/TASK-003A-품목-BOM-구현-검수.md) |
| TASK-003B · LOT 입고·멱등성 | 코드 초안 작성 / 실기동 검증 대기 | [구현·검수 보고서](docs/task-reports/TASK-003B-원자재입고-구현-검수.md) |
| TASK-004 이후 · 생산·품질·출고 | 계획 | [SSOT의 구현 범위](docs/FACTORYFLOW_SSOT_v0.1.md) |

**현재는 TASK-002 기반 코드와 TASK-003A 품목·BOM, TASK-003B 원자재 입고 API 초안을 작성했으며, 실제 빌드·통합 테스트·배포 통과 여부는 CI에서 확인해야 합니다.** 생산 예약·QC·출고는 아직 미구현입니다.

## 기술 방향 (계획)

| 영역 | 기술 |
|---|---|
| Backend | Java 21, Spring Boot, Spring Data JPA |
| Database | MySQL 8.4, Flyway |
| Frontend | Vue 3, Pinia |
| Testing | JUnit 5, Testcontainers(MySQL) |
| Infrastructure | Docker Compose, GitHub Actions |

첫 버전은 **모듈형 모놀리식**으로 구현합니다. Redis·Kafka·MSA·Kubernetes·RAG는 1차 범위에서 제외합니다.

## 핵심 업무 흐름

```text
품목 및 BOM 등록
    ↓
원자재 LOT 입고
    ↓
생산지시 생성 (DRAFT)
    ↓
원자재 전량 예약 (RESERVED)
    ↓
생산 시작: 예약 원자재 소모 (IN_PROGRESS)
    ↓
생산 완료 (AWAITING_QC)
    ↓
품질검사: 합격품만 재고 반영 (COMPLETED)
    ↓
완제품 출고
```

## 설계 원칙

1. 재고 예약 시 실제 수량을 차감하지 않고, 생산 시작 시 예약 물량을 소모합니다.
2. 생산지시에 필요한 자재가 부족하면 부분 예약 없이 전체 작업을 취소합니다.
3. LOT 단위 추적과 재고 이동 이력을 남깁니다.
4. 동일한 입고·출고 요청의 재전송이 수량을 중복 변경하지 않도록 설계합니다.
5. 구현 전에 테스트할 불변조건과 예외 시나리오를 먼저 작성합니다.

## 문서

- [제품·도메인·기술 설계 SSOT v0.1](docs/FACTORYFLOW_SSOT_v0.1.md)
- [TASK-002 초기 개발 기반 구축 작업지시서](tasks/FACTORYFLOW_TASK_002_개발기반구축.md)

## 공개 범위 및 보안

통합 코드 패키지는 검증·원격 등록 전 초안이며, 기존 main 브랜치는 설계 문서만 등록되어 있습니다. DB 비밀번호, `.env`, 실제 개인정보, 내부 인프라 접속 정보는 Git에 올리지 않습니다. 인증·인가가 구현되기 전의 쓰기 API는 인터넷에 공개하지 않습니다.

## TASK-002 개발 기반 (초안, 실행 미검증)

- 백엔드: `backend/` — Java 21 / Spring Boot 4.1.2 / Gradle Wrapper 9.5.1
- DB: `infra/compose.yml` — MySQL 8.4, Flyway V1 12개 도메인 테이블
- 테스트: `backend/src/test/` — Testcontainers 실제 MySQL 제약 검증 코드
- [구현 및 검수 기록](docs/TASK_002_구현_및_검수_기록.md) · [코드 학습 가이드](docs/TASK_002_학습가이드.md)

**로컬 실행 방법 (Windows PowerShell, 각 명령은 한 줄):**

```powershell
Copy-Item .\infra\.env.example .\infra\.env; notepad .\infra\.env
```

비밀번호 2개를 수정하고, 아래 명령을 실행하세요.

```powershell
docker compose --env-file .\infra\.env -f .\infra\compose.yml up -d
```

```powershell
$env:DB_PASSWORD=(Get-Content .\infra\.env | Select-String '^FACTORYFLOW_DB_PASSWORD=').ToString().Split('=',2)[1]; Set-Location .\backend; .\gradlew.bat clean test
```

Docker 엔진이 없으면 Testcontainers 테스트는 실행할 수 없습니다. 현재 외부 의존성 다운로드와 빌드도 검증되지 않았습니다.


## TASK-003A 품목·BOM API (코드 초안 / 테스트 미실행)

- `POST /api/v1/items`: 품목 등록, 대문자 SKU 정규화, UNIQUE(sku) 충돌 방어
- `GET /api/v1/items`, `GET /api/v1/items/{id}`: 품목 목록·상세 조회 (타입/문자열 검색/페이지)
- `POST /api/v1/boms`, `GET /api/v1/boms/{finishedItemId}`: 완제품 BOM 등록 및 조회, 원자재 유형·중복 검증
- 모든 응답은 DTO로 반환, 업무 저장 트랜잭션은 Service에서 관리
- [구현·검수·학습 보고서](docs/task-reports/TASK-003A-품목-BOM-구현-검수.md)
- [작업별 문서화 규칙](docs/DEVELOPMENT_REPORTING_STANDARD.md)

**테스트 상태:** JDK 21 SKU 검증 통과, Java 문법 파싱 통과. `./gradlew clean test`와 MySQL/Testcontainers는 외부 다운로드·Docker 부재로 아직 실행하지 못했습니다.


## 현재 구현 후보 API (TASK-003B — 런타임 미검증)

- `POST /api/v1/stock-receipts`: 원자재 LOT 입고 / 중복 요청에 201·200·409 분기
- `GET /api/v1/stock-receipts/{id}`: 입고 내역 조회
- `GET /api/v1/inventory`: 품목별 현재고·예약고·가용재고 합계
- `GET /api/v1/inventory/lots?itemId={id}` 및 `GET /api/v1/inventory/lots/{lotId}`: LOT 조회
- `GET /api/v1/stock-movements?itemId={id}`: 재고 이동 이력

[원자재 입고 코드 검수 보고서](docs/task-reports/TASK-003B-원자재입고-구현-검수.md) · [학습 가이드](docs/task-reports/TASK-003B-코드학습-가이드.md) · [잠금·멱등성 ADR](docs/adr/ADR-009-원자재입고-멱등성-잠금.md)


## 비용을 고려한 배포 계획 (미구현)

- **Vue 3/Vite:** Vercel 배포 예정.
- **Spring Boot 4 + MySQL 8.4:** Oracle Cloud Always Free VM 확보를 1순위로 검토. Docker Compose, TLS 역방향 프록시, 비공개 MySQL 포트.
- **제약:** 무료 VM은 리전 가용성/계정 검증/유휴 인스턴스 회수에 따라 생성·상시 가동이 보장되지 않는다. AWS 사용료·상시 켜 둔 개인 PC를 사용하지 않기 위한 선택이지 무료 운영이 확정된 상태는 아니다.
- **대체안:** Oracle 확보 실패 시 Render Free + 별도 DB 등으로 검토하되, MySQL CHECK/락/트랜잭션 의미가 달라지는 DB 전환은 CI 통합 검증 없이 승인하지 않는다.
- **과거안:** Cloudflare Tunnel은 상시 실행 호스트가 필요해 채택하지 않는다. 이유는 [ADR-010](docs/adr/ADR-010-무료배포-Cloudflare-Tunnel.md)과 [ADR-011](docs/adr/ADR-011-Oracle-Always-Free-배포-전환.md)에 기록했다.
- 인증·인가가 완성되기 전에는 인터넷에 쓰기 API를 공개하지 않는다.

> **검증 상태:** 본 코드는 Github Actions에서 Gradle + Testcontainers MySQL 실제 실행이 확인되기 전까지 배포/통합 테스트 완료로 표시하지 않는다.
