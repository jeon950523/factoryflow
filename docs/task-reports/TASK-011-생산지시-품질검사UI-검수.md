# TASK-011 — 생산지시·품질검사 화면 재설계 시니어 검수

검수 일자: 2026-11-13 KST  
기준 코드: `2dc531f5c8b821ae9bae7c8e33a94a0b39fbc7a4`  
상태: **기능 및 GitHub Actions 검증 완료 / 실제 Vercel 프로덕션 최종 시각 QA는 별도**

## 1. 목적과 판단

초기 `OrdersView.vue`는 생산지시 목록/상세를 좁은 화면에 좌우 두 장의 카드로 나누고 원자재 필요량 및 처리 명령을 카드 안에서 세로로 배치했다. TASK-010 운영 개요를 작업 목록으로 개선한 뒤에도 이 화면이 SaaS 템플릿처럼 보여 정보 우선순위가 일관되지 않았다.

**선택한 설계:** 상단 `진행 상태/새 지시 생성` 툴바 → 전체 너비 생산지시 테이블 → 선택 지시 기본정보/상태 단계 → 하단 좌측 소요 원자재, 우측 실행 명령·QC 입력. 상태 전이·비밀번호·권한/재고의 실제 처리 코드를 바꾸지 않는다.

## 2. 전체 호출 순서

1. Vue Router의 `/orders` 진입 → `OrdersView.vue` → `load()` 호출 → `GET /api/v1/work-orders?status=...&page=...&size=15`. 상태 쿼리 허용값만 사용.
2. 목록의 실제 `finishedItemId`만 모아 `lookupItemNames` → `GET /api/v1/items/lookup?ids=...`로 이름 표시. 실패 시 완제품 ID로 대체.
3. 생산지시번호 버튼을 누르거나 `상세 보기` 링크를 통해 `/orders?id=...`로 진입하면 `choose(id)` → `GET /api/v1/work-orders/{id}`, 소요 원자재 이름 배치 조회.
4. 선택한 지시의 스냅샷 `materials[].requiredQty`를 **필요 수량 표**에 표시. LOT별 실제 예약 분배 내역은 현재 응답에 없으므로 표시하지 않음.
5. 관리자에게만 상태별 명령 버튼 렌더링: `DRAFT → reserve`, `RESERVED → start`, `IN_PROGRESS → finish`, `AWAITING_QC → inspection`; 초안·예약에서는 cancel 가능.
6. `runAction()` → 기존 `POST /api/v1/work-orders/{id}/{operation}`을 호출. 처리가 성공하면 선택 지시와 목록을 다시 조회. VIEWER에게는 쓰기 버튼 숨김(최종 거부는 서버 Spring Security).
7. QC 단계에서 `passedQty + failedQty === plannedQty`, 0 이상 안전 정수인지 UI에서도 검증. 미충족이면 확정 버튼 비활성화. 서버의 동일한 도메인 검증은 그대로 유지.
8. 신규 생산지시 생성 시 `POST /api/v1/work-orders` 성공 → `/orders?id=신규ID`로 이동해 필터 중인 화면에서도 신규 지시의 상세를 다시 열 수 있음.

## 3. 파일과 책임

| 파일 위치 | 역할 |
|---|---|
| `frontend/src/views/OrdersView.vue` | 리스트·상세·QC·관리자 작업 표시. 기존 GET/POST와 조회 응답 경쟁 방어 재사용. 상세 재시도 요청 ID 기억·신규 지시 URL 복원 등 개선 |
| `frontend/src/views/orders-workbench.css` | 전용 ERP 테이블, 상태 진행 스트립, 원자재·작업 분할 레이아웃. 기존 공통 스타일과 결합하되 전용 클래스 prefix로 영향 범위 한정 |
| `frontend/tests-demo/browser-demo.spec.js` | 카드 2열 제거/전체 목록/원자재 상세/QC 합계/필터 중 생성·URL 복원 Chrome 검증 |
| `frontend/tests/security-shell.spec.js` | VIEWER의 제조 지시 조회 및 생성·예약 명령 비노출 검사 |

**기존 코드 연결:** `lib/api.js`의 실제 API/브라우저 데모 분기, `createLatestRequest`, `itemNames`, `AsyncState`, `StatusBadge`, `PageControls`, `ItemSearchSelect`, Spring Boot 서버 및 MySQL 구조는 변경하지 않았다.

## 4. 주요 예외·테스트

- **비정상/지연 응답:** 마지막 사용자 선택만 적용하도록 `detailGate` 보호. `requestedDetailId`를 기억해 상세 조회 실패 후 재시도 가능.
- **페이지·필터 이동:** 이전 상세 요청 취소 및 선택 해제로 목록과 다른 지시의 상세가 남는 것을 방지.
- **실제 권한:** `auth.canWrite`가 아닌 계정에는 지시 생성/자재 예약·생산·QC 쓰기 버튼 미노출; 실제 HTTP 쓰기는 기존 Spring Security 검증에 의존.
- **QC 합계:** 불일치 시 확정 버튼 비활성화, 완성된 입력에서만 실행. 서버의 수량 검증은 최종 방어선.
- **재고 부족/상태 충돌:** 예전과 동일한 HTTP 응답을 알림으로 전달; 재고 rollback과 LOT FIFO는 서버/데모 엔진에서 처리한다.
- **직접 URL:** 신규 지시 `/orders?id=...`와 상태별 `?status=` 새로고침 경로 검증.

### GitHub Actions 결과

| 항목 | 결과 |
|---|---|
| [Frontend CI #43306931753](https://github.com/jeon950523/factoryflow/actions/runs/43306931753) | **성공** — Node 37/37, Vite build, fixture Chrome **12/12**, 실제 IndexedDB Chrome **10/10** |
| [Fullstack E2E #43306931723](https://github.com/jeon950523/factoryflow/actions/runs/43306931723) | **성공** — Java21 Spring Boot + MySQL 8.4 실제 기동, Chrome E2E **2/2** |
| [1차 UI CI #43306300619](https://github.com/jeon950523/factoryflow/actions/runs/43306300619) | **실패** — 테스트에서 지시번호가 상세 두 곳에 표시된 것과 ‘합격’/‘불합격’ 라벨 부분 문자열 중복을 엄격하게 구분하지 못함. 중복 UI 요소를 삭제하는 대신 테스트 선택자를 고유 클래스·`exact:true`로 보정 |
| [수정 후 UI CI #43306625731](https://github.com/jeon950523/factoryflow/actions/runs/43306625731) | **성공** — Node 37/37, fixture Chrome 11/11, IndexedDB Chrome 10/10. 이후 VIEWER 테스트 1건 추가하여 12/12 달성 |

검증 경계: Chrome 테스트와 서버 E2E 성공은 기능 회귀 증거다. 온라인 Vercel 배포 전파 및 실제 사용자·현장 업무 적합성을 입증하지는 않는다.

## 5. 시니어 검수

### 잘한 점
- **장식 대신 작업 정보 우선:** 표에서 생산 상태/다음 작업/상세로 이어짐. 두 개의 폭 좁은 카드 대신 목록과 상세의 위계를 분명하게 구분.
- **원래 기능 보존:** 생산 예약·소모·품질·취소의 백엔드 상태 전이 및 IndexedDB 데모를 바꾸지 않았다.
- **공유 가능한 업무 링크:** 신규 생성 직후 직접 링크와 새로고침 재진입을 보장.
- **에러/경쟁 상태:** 기존 latest-request-wins 방어, 상세 실패 재시도, 잘못된 수량 입력 UI 차단.
- **역할 분리:** 관리자/조회자 버튼 차이를 자동 검사. 서버 권한과 프론트 표시 권한을 혼동하지 않음.

### 위험한 점
- **QC 합격 수량은 현재 기존 기능대로 전체 계획량으로 초기화됨.** 현장 검수자가 기본값을 확인 없이 누르면 전량 합격을 확정할 위험이 있다. 본 UI 개선에서는 기존 데모·업무 계약을 보존했지만 실제 상용화를 목표로 한다면 합격/불합격을 명시적으로 입력하도록 하고 최종 확인 단계 및 품질검사 담당자의 권한 분리를 검토할 것.
- **실제 LOT 예약 세부를 보여주지 않는다.** 현재 API에는 생산지시별 LOT 예약 allocation 목록이 없으므로 원자재 필요량을 예약 결과처럼 표현하면 안 된다.
- 상태 표시용 진행 스트립은 `DRAFT/RESERVED/IN_PROGRESS/AWAITING_QC/COMPLETED`의 순차 워크플로 설명이며, 이벤트별 감사 이력이나 작업시간 증거는 아님.

### 지금 고칠 것
- 1차 Chrome 테스트의 Locator 부분 문자열 중복은 수정하고 재검증 완료.
- 실제 Vercel 반영 시 컬럼 폭/스크롤/키보드 포커스와 `/orders?id=` 새로고침을 PC에서 육안 검증할 것.

### 2차 개선 및 효율
- QC 명시적 값 입력·최종 확인, 승인자/작업자 역할 분리 등 상용 MES 경계 검토.
- `OrdersView.vue`는 여전히 조회/명령/폼 상태를 한 파일에 보유한다. 2차에는 `OrderList`, `OrderDetail`, `QcEntry` 등 역할별 컴포넌트 분리가 유지보수에 도움될 수 있다. 지금은 구현/검증 비용 대비 기능 이익이 작아 보류.
- 상세가 목록 하단에 있어 생산지시가 많으면 이동해야 한다. 현재 선택 시 자동 스크롤을 적용했으며 현장 피드백에 따라 오른쪽 도킹 패널 또는 고정 액션 바로 개선할 수 있다.

## 6. 확인 방법

- 데모: `https://factoryflow.kro.kr/orders` → 생산지시 `WO-DEMO-READY` → 자재 예약 → 생산 시작 → 생산 완료 → QC 합계 확인 → 검사 확정.
- 생산지시 생성: 필터 `COMPLETED` 적용 → 새 생산지시 생성 → `?id=...` 복귀 → 새로고침.
- `cd frontend && npm ci && npm test && npm run build`
- `npx playwright test --config playwright.config.js` (fixture Chrome), `npx playwright test --config playwright.demo.config.js` (prod build + IndexedDB).
- 실제 서버 E2E: `.github/workflows/fullstack-ui-ci.yml` 환경에서 Spring Boot/MySQL 기동 후 Chrome 2건 검증.

## 7. 30초 설명

“기존 생산지시 화면은 좁은 목록과 상세 카드가 동시에 나와 업무 흐름을 파악하기 어려웠습니다. 전체 너비 생산지시 표를 먼저 보여주고, 선택한 지시의 원자재 스냅샷·상태·다음 실행 명령을 하단 작업 영역에서 처리하도록 바꿨습니다. 기존 서버/DB 도메인은 손대지 않고 QC 합계·조회자 권한·직접 링크를 강화했고, 브라우저/실DB E2E로 검증했습니다.”

스스로 설명할 질문: (1) 원자재 필요 수량과 실제 LOT 예약 결과는 어떻게 다른가? (2) 프론트의 QC 버튼 비활성화만으로 검증이 충분하지 않은 이유는?
