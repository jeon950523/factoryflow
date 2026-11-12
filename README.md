# FactoryFlow

> **공개 웹 데모: Vercel + IndexedDB 시뮬레이션 (TASK-008). Java 21/Spring Boot·MySQL 실제 구현은 독립 유지. 백엔드 CI 64/64, 프론트 Node 37/37, Chrome Fixture 11/11, 브라우저 데모 3/3, 실DB E2E 2/2 검증.**

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
| TASK-002 · 개발 기반 구축 | CI 빌드·MySQL 테스트 검증 완료 | [구현·검수 기록](docs/TASK_002_구현_및_검수_기록.md) |
| TASK-003A · 품목·BOM API | CI 빌드·MySQL 테스트 검증 완료 | [코드·설계 검수 보고서](docs/task-reports/TASK-003A-품목-BOM-구현-검수.md) |
| TASK-003B · LOT 입고·멱등성 | CI 빌드·MySQL 테스트 검증 완료 | [구현·검수 보고서](docs/task-reports/TASK-003B-원자재입고-구현-검수.md) |
| TASK-004C · 생산지시 DRAFT/BOM 스냅샷 | CI 통합 테스트 검증 완료 | [작업 보고서](docs/task-reports/TASK-004C-생산지시-스냅샷-구현-검수.md) |
| TASK-004D · FIFO LOT 재고 예약·취소 | MySQL 동시성/롤백 테스트 통과 | [구현·검수 보고서](docs/task-reports/TASK-004D-원자재예약-취소-구현-검수.md) |
| TASK-004E · 생산 시작·소모·완료 | MySQL 트랜잭션/동시성 테스트 통과 | [구현·검수 보고서](docs/task-reports/TASK-004E-원자재소모-생산완료-구현-검수.md) |
| TASK-004F · 품질검사·합격품 입고 | CI 동시성/롤백 테스트 완료 | [보고서](docs/task-reports/TASK-004F-품질검사-합격품입고-설계-검수.md) |
| TASK-004G · 완제품 출고 | CI FIFO/동시성/멱등성 테스트 완료 | [보고서](docs/task-reports/TASK-004G-완제품출고-구현-검수.md) |
| TASK-004H · 전 업무 API E2E | CI 49/49 성공 | [보고서](docs/task-reports/TASK-004H-전업무-E2E-회귀검증.md) |
| TASK-005A~005D · Vue 3 관리자 화면/연속 조회 | Chrome 화면 11/11, 실 MySQL 브라우저 E2E 2/2 성공 | [005D 보고서](docs/task-reports/TASK-005D-실브라우저-동시성-검수.md) |
| TASK-006 · 세션 인증·인가·CSRF | CI 백엔드 57/57, Node 23/23, Chrome 11/11, 실DB E2E 2/2 · 구현 검증 완료 | [TASK-006 검증](docs/verification/TASK-006-CI-검증.md) |

**최신 TASK-006 PR 기준:** Java 21/Spring Boot + MySQL 8.4 Testcontainers JUnit **57개**, Node **23개**, Chrome Fixture **11개**, 실제 로그인 후 Spring Boot·MySQL 브라우저 E2E **2개** 성공. **Oracle/Vercel 공개 배포, TLS·쿠키·프록시 실환경 보안 및 계정 복구는 미검증**입니다.

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

구현 및 테스트 코드를 공개 저장소에서 관리합니다. DB 비밀번호, `.env`, 실제 개인정보, 내부 인프라 접속 정보는 Git에 올리지 않습니다. 인증·인가 테스트는 구현·CI 검증했지만 TLS·Secure Cookie·동일 출처 Reverse Proxy와 로그인 제한을 검증하기 전에는 API를 인터넷에 공개하지 않습니다.

## TASK-002 개발 기반 (CI 검증 완료)

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

Docker 엔진이 없으면 로컬에서 Testcontainers 테스트는 실행할 수 없습니다. GitHub Actions에서는 실제 Docker/MySQL 기반 테스트를 검증했습니다.


## TASK-003A 품목·BOM API (CI 검증 완료)

- `POST /api/v1/items`: 품목 등록, 대문자 SKU 정규화, UNIQUE(sku) 충돌 방어
- `GET /api/v1/items`, `GET /api/v1/items/{id}`: 품목 목록·상세 조회 (타입/문자열 검색/페이지)
- `POST /api/v1/boms`, `GET /api/v1/boms/{finishedItemId}`: 완제품 BOM 등록 및 조회, 원자재 유형·중복 검증
- 모든 응답은 DTO로 반환, 업무 저장 트랜잭션은 Service에서 관리
- [구현·검수·학습 보고서](docs/task-reports/TASK-003A-품목-BOM-구현-검수.md)
- [작업별 문서화 규칙](docs/DEVELOPMENT_REPORTING_STANDARD.md)

**테스트 상태:** GitHub Actions에서 Java 21 `clean test`, Testcontainers MySQL 8.4, Flyway/JPA 매핑 검증, 총 17개 테스트 통과. [실행 증거](docs/verification/TASK-004B-CI-실행-기록.md).


## TASK-003B 원자재 입고·재고 조회 API (CI 검증 완료)

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

> **검증 결과:** [TASK-004E GitHub Actions 성공 실행](https://github.com/jeon950523/factoryflow/actions/runs/42891184237) — Java 21 빌드·Testcontainers MySQL 기반 JUnit 34개 통과. **Oracle VM 배포/외부 접속/인증은 아직 완료되지 않았습니다.**

## TASK-004C 생산지시 DRAFT/BOM 스냅샷 (CI 22/22 통과)

- `POST /api/v1/work-orders`: 완제품·BOM 검증 후 생산지시와 구성 자재 스냅샷을 한 트랜잭션으로 생성.
- `GET /api/v1/work-orders/{id}`: 생산지시와 저장된 구성 자재 조회.
- 중복 `clientRequestId` 동일 내용은 200 재조회, 다르면 409; `Math.multiplyExact`로 필요 수량 계산 오버플로 방어.
- 아직 재고 예약·차감은 하지 않는다. [CI 22개 테스트 통과](https://github.com/jeon950523/factoryflow/actions/runs/42882000269).
- [코드 선택 이유·테스트·시니어 검수·리팩터링 보고서](docs/task-reports/TASK-004C-생산지시-스냅샷-구현-검수.md)


## TASK-004D 재고 예약·취소 (CI 29/29 통과)

- `POST /api/v1/work-orders/{id}/reserve`: `DRAFT → RESERVED`, LOT FIFO 전량 예약. 실제 현재고는 변경하지 않는다.
- `POST /api/v1/work-orders/{id}/cancel`: `DRAFT/RESERVED → CANCELED`, 예약 해제(`RELEASED`), 가용량 복구.
- 원자재 품목 오름차순 → 생산지시 → LOT 잠금. 두 번째 자재 부족 시 모든 예약 변경과 이력이 전체 롤백된다.
- 동일 생산지시 및 서로 다른 생산지시의 경쟁 예약 테스트를 포함한다.
- [핵심 원리 모음](docs/learning/CORE_CONCEPTS.md) · [코드 설명/장단점/리팩터링 보고서](docs/task-reports/TASK-004D-원자재예약-취소-구현-검수.md) · [실제 CI 증거](docs/verification/TASK-004D-CI-실행-기록.md)


## TASK-004E 생산 시작·원자재 소모·생산 완료 (CI 34/34 통과)

- `POST /api/v1/work-orders/{id}/start`: RESERVED → IN_PROGRESS, 해당 생산지시 예약분만 `on_hand/reserved` 함께 차감, `RAW_CONSUMPTION` 이동 로그 저장.
- `POST /api/v1/work-orders/{id}/finish`: IN_PROGRESS → AWAITING_QC. 이 단계는 완제품 재고 증가 없음.
- 중복 명령/동시 시작 요청 방어, DB 이동 이력 실패 주입 시 재고·예약·상태 전체 롤백을 검증했어.
- [핵심 원리 누적 노트](docs/learning/CORE_CONCEPTS.md) · [TASK-004E 작업 보고서](docs/task-reports/TASK-004E-원자재소모-생산완료-구현-검수.md) · [실제 CI 증거](docs/verification/TASK-004E-CI-실행-기록.md)


## TASK-004F~004G 품질검사·완제품 출고 (CI 45/45)
- 품질검사 확정 후 합격품만 QC_PASS LOT로 입고. 동시 검사 시 JPA 영속성 컨텍스트의 이전 상태 재사용 오류를 수정했다.
- POST /api/v1/shipments: 완료된 품질검사의 QC_PASS LOT에서 FIFO 전량 출고. 재고 부족 시 전체 롤백.
- GET /api/v1/shipments/{id}: 출고 및 LOT 배분 이력 조회.
- 같은 요청 키·내용 재전송은 기존 출고 반환, 같은 키·다른 수량은 409.
- [핵심 개념 누적](docs/learning/CORE_CONCEPTS.md) · [TASK-004G 구현 보고서](docs/task-reports/TASK-004G-완제품출고-구현-검수.md) · [실제 CI 기록](docs/verification/TASK-004G-CI-실행-기록.md)


## TASK-004H 전 업무 API E2E — MySQL CI 49/49
- 정상: 원자재 LOT 입고(철판 100, 볼트 200) → BOM(1,2) → 생산지시 50 → FIFO 예약·소모 → 완료 → QC 합격 47/불합격 3 → 완제품 10 출고, 최종 완제품 재고 37 및 재고 이동 합계 검증.
- 실패/복구: 두 번째 자재 부족 예약 롤백, 추가 입고 후 예약·취소, 요청 재전송 멱등성, 전량 불합격 제품 출고 차단.
- 실제 Spring HTTP 컨트롤러부터 MySQL 커밋 상태까지 Testcontainers/MockMvc로 검사. 아직 웹 브라우저나 배포 서버를 포함한 E2E는 아님.
- [TASK-004H 보고서](docs/task-reports/TASK-004H-전업무-E2E-회귀검증.md) · [CI 증거](docs/verification/TASK-004H-CI-실행-기록.md) · [핵심 학습](docs/learning/CORE_CONCEPTS.md)


## TASK-005A 관리자 UI

Vue 3 + Vite 프론트엔드에 운영 개요, 품목/BOM, 재고/LOT 입고, 생산지시·품질검사, 완제품 출고, 재고 이동 이력 화면을 구현했습니다. 데이터는 실제 Spring Boot API만 사용하며 가짜 통계는 없습니다.

로컬 개발: `cd frontend` → `npm ci` → `npm run dev` (API 127.0.0.1:8080 프록시).

[외부 ERP UI 참고](docs/design/ADMIN_UI_REFERENCES.md) · [작업 보고서](docs/task-reports/TASK-005A-관리자UI-설계-구현-검수.md) · [핵심 학습](docs/learning/TASK-005A-관리자UI-학습핵심.md) · [CI 검증](docs/verification/TASK-005A-CI-검증.md). 인증·인가 전에는 외부 공개 배포하지 않습니다.


## TASK-005B — 100개 이상 품목 검색형 선택기 (CI 검증 완료)
- 입고·생산지시·출고·BOM 원자재 선택을 서버 검색 + 10건 단위 페이지 이동 컴포넌트로 교체. 첫 100건 제한으로 선택하지 못하던 문제 개선.
- 빠른 연속 검색 시 이전 요청을 AbortController로 중단하여 오래된 응답이 최신 결과를 덮어쓰지 않게 함.
- 실제 MySQL 105건 테스트에서 11페이지 5개 반환 확인, 백엔드 52/52 통과. 프론트 Node 테스트 6/6 및 Vite 빌드 통과.
- [작업별 코드·장단점·리팩터링 보고서](docs/task-reports/TASK-005B-검색형품목선택-구현-검수.md) · [검증 증거](docs/verification/TASK-005B-CI-검증.md) · [핵심 학습 모음](docs/learning/CORE_CONCEPTS.md).
- 참고: TASK-005C에서 목록/상세 첫 100개 이름 캐시 문제를 해결함. 브라우저 실제 동작은 테스트 API 응답 기준으로 검증했으며 실 MySQL을 연결한 브라우저 E2E·스크린리더 QA는 후속 과제.


## TASK-005C — 생산·출고·BOM의 품목명 정확도 및 실제 Chrome QA
- 신규 **GET /api/v1/items/lookup?ids=105,205**: 화면에 필요한 품목 ID만 1회 DB 조회로 해결(한 요청 최대 100개, 중복 제거, 입력 순서 유지).
- Vue 목록/상세는 실제 필요한 품목 ID를 50개 단위로 묶어 이름·SKU를 조회. 101번째 이후 품목도 정확한 정보 표시. 이름 조회만 실패하면 업무 목록·작업 가능 상태는 유지하고 ID 대체 표시와 경고를 제공.
- [실제 MySQL CI](docs/verification/TASK-005C-CI-브라우저-검증.md): JUnit **53/53**. [Frontend CI](docs/verification/TASK-005C-CI-브라우저-검증.md): Node **10/10**, Vite 빌드, **Chrome 브라우저 스모크 5/5**.
- 테스트 브라우저의 API 응답은 고정 Fixture이며, 실제 배포·인증/인가·라이브 MySQL 통합 브라우저 E2E와는 구별.
- [TASK-005C 코드·의사결정·장단점·리팩터링 보고서](docs/task-reports/TASK-005C-품목일괄조회-브라우저검증.md) · [핵심 개념 모음](docs/learning/CORE_CONCEPTS.md).


## TASK-005D — 실제 DB 관리자 브라우저 E2E와 연속 조회 경합
- **실제 업무 검증:** GitHub Actions MySQL 8.4 → Java 21/Spring Boot/Flyway → Vite → Chrome. 품목/BOM 초기 데이터만 API로 준비하고, **입고·생산지시·예약·소모·QC·출고는 Vue 화면에서 처리**한다.
- **재고 검증:** 원자재 25 입고 → 20 소모(남은 5), QC 합격8/불합격2 → 완제품 3 출고(남은 5). 물리 재고 이동 이력과 예약고도 확인.
- **재고 부족:** 3개 있는 원자재가 4개 필요한 생산지시에서 예약 거부, DRAFT 상태 유지, 현재고3·예약고0.
- **연속 클릭 레이스 수정:** 생산지시·출고·BOM·LOT 조회에서 이전 GET 취소 및 최신 요청 시퀀스 확인. A→B 클릭 시 오래된 A 응답이 B 상세를 덮어쓰지 않음.
- **실제 검증:** Node 12/12, Vite 빌드, Chrome Fixture 8/8, Chrome 실제 Spring/MySQL 2/2. 백엔드 Testcontainers 53/53은 기존 main 기준 유지(업무 코드 변경 없음).
- [설계·코드 검수](docs/task-reports/TASK-005D-실브라우저-동시성-검수.md) · [실행 증거](docs/verification/TASK-005D-실브라우저-CI-기록.md) · [핵심 개념 누적](docs/learning/CORE_CONCEPTS.md).
- 외부 서버에 배포한 것은 아니며 인증·인가 전 무인증 쓰기 API 외부 공개 금지.


## TASK-006 보안 구현 (CI 검증 완료, 공개 배포 보안 게이트 별도)
- Spring Security 세션 로그인/로그아웃, BCrypt12, HttpSession CSRF, ADMIN/VIEWER 서버 인가
- Flyway V2 사용자 계정 테이블. 신규 DB 관리자는 FACTORYFLOW_ADMIN_USERNAME, FACTORYFLOW_ADMIN_PASSWORD 환경변수로만 생성(기본 비밀번호 없음)
- Vue 로그인/계정관리/역할별 화면/세션 만료 처리. 기존 제조 업무 브라우저 테스트에 로그인·CSRF 연결
- **GitHub Actions 검증:** [백엔드 57/57](https://github.com/jeon950523/factoryflow/actions/runs/43110093904), [프론트 Node 23/23 + Chrome 11/11](https://github.com/jeon950523/factoryflow/actions/runs/43110093813), [실 MySQL 브라우저 E2E 2/2](https://github.com/jeon950523/factoryflow/actions/runs/43110093834).
- **인증 구현은 공개 배포 승인과 다름:** 비밀번호 복구·로그인 제한·HTTPS 프록시/쿠키/CSRF/CORS 운영검증 필요
- [ADR](docs/adr/ADR-012-세션인증-동일출처-배포.md) · [시니어 검수](docs/task-reports/TASK-006-세션인증-역할보안-검수.md) · [핵심 학습](docs/learning/CORE_CONCEPTS.md)

## TASK-007A — 로그인 무차별 시도 제한 (CI 검증 완료)

- 비밀번호 BCrypt 검증 전에 Spring Security 필터를 거치는 **5분 슬라이딩 윈도**를 적용.
- 계정별 8회와 서버가 인식한 접속원별 120회를 독립 적용; 동시에 들어오는 로그인도 원자적으로 횟수를 예약.
- 한도 초과 시 **HTTP 429 / Retry-After / LOGIN_RATE_LIMITED**; 로그인 성공 시 계정 버킷만 초기화.
- [Backend CI](https://github.com/jeon950523/factoryflow/actions/runs/43191465084) **JUnit 64/64**, [Chrome+실 MySQL E2E](https://github.com/jeon950523/factoryflow/actions/runs/43191465294) **2/2** 통과.
- **주의:** 단일 JVM 인메모리·프록시 IP 공유·사용자 계정 잠금 방해(DoS) 가능성 때문에 이 기능만으로 공개 배포 승인 불가. 엣지 제한·HTTPS·쿠키·관리자 계정 복구는 후속 작업.
- [시니어 검수](docs/task-reports/TASK-007A-로그인제한-검수.md) · [테스트 근거](docs/verification/TASK-007A-CI-검증.md)

## TASK-008 — Vercel 브라우저 체험 데모 (Oracle 가입 실패 대안)

- 접속 주소: **https://factoryflow.kro.kr**. Vercel 프로젝트에 연결된 사용자 지정 도메인.
- 브라우저는 사용자 로그인 없이 **IndexedDB**에 저장된 샘플 품목/BOM/LOT/생산지시 데이터를 체험한다. 이 데이터는 방문자 개인 브라우저에만 저장되며 서버로 전송되지 않는다.
- 초기 샘플: 산업용 제어 박스 1종, 원자재 3종, BOM, 원자재 LOT, 완료 생산지시 1건, 초안 생산지시 **WO-DEMO-READY** 1건, 합격품 출고 예시.
- 추천 시연: **재고 확인 → 생산지시 WO-DEMO-READY 선택 → 자재 예약 → 생산 시작 → 생산 완료 → 품질검사 확정 → 출고 → 재고 이동 이력**.
- **샘플 초기화** 버튼으로 해당 브라우저의 체험 데이터를 다시 채운다. 브라우저 저장소를 삭제하면 변경한 기록은 사라진다.
- Vite `PROD` 빌드는 기본 데모; `VITE_DEMO_MODE=false`로 빌드하면 기존 Spring Boot API 사용. 로컬 `npm run dev`는 계속 실제 백엔드.
- **진짜 백엔드 증거:** Spring Boot/MySQL Testcontainers와 Fullstack CI 테스트. 공개 데모는 MySQL 트랜잭션이나 Spring Security 서버 로그인과 다르다.
- [상세 구현/검수](docs/task-reports/TASK-008-브라우저데모-검수.md) · [사용 가이드](docs/DEMO_WALKTHROUGH.md) · [설계 결정](docs/adr/ADR-013-프론트데모-실백엔드분리.md).

**운영 주의:** Oracle 배포용 PR #14는 미병합 Draft로 유지하며, 실제 DB·로그인 운영 배포 준비 후 별도 검토한다.
