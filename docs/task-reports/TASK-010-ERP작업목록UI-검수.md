# TASK-010 — 제조 업무 처리 목록 중심 ERP형 UI 재설계

- 기준 기능 코드: `38328ce39a09b34d3ce124f9e00f1274c11da174`
- 상태: **기능 구현 및 CI 검증 완료 / 실제 Vercel 도메인 최신 배포·시각 QA 별도**
- 범위: 프론트 Vue 컴포넌트, CSS, Chrome UI 테스트. Spring Boot, MySQL, IndexedDB 제조 엔진은 변경하지 않음.

## 1. 기능 목적

TASK-009에서 카드 그림자·모서리를 줄였지만, 같은 크기의 KPI 카드 4개를 계속 사용했다. '0건' 같은 짧은 수치가 가로 공간을 크게 차지하며 AI SaaS 템플릿처럼 보여 실제 제조 업무가 뒷전이었다. 요구사항은 UI 외양만 개선하는 것이 아니라 **처리할 작업 → 상세 처리 → 재고 확인** 흐름을 화면 최상단에 배치하는 것이다.

## 2. 실제 호출 순서

1. Vue Router가 `/`에서 `DashboardView.vue`를 표시한다. `onMounted(refresh)` 호출.
2. `createLatestRequest().begin()`로 이전 요청을 무효화하고 AbortSignal을 만든다.
3. 기존 `GET /api/v1/inventory?page=0&size=8`, `GET /api/v1/work-orders?status=DRAFT|RESERVED|AWAITING_QC&page=0&size=6` (각 1회), `IN_PROGRESS/COMPLETED` 집계용 `size=1` (각 1회)를 `Promise.all`로 요청한다.
4. 세 대기 상태의 서버 `totalElements`를 합산해 상단 '처리 대기'를 표시한다. 표에는 각 상태 응답의 `content`만 합쳐 **최대 18건** 표시한다.
5. `lookupItemNames`가 표에 실제 보이는 완제품 ID만 배치 조회한다. 실패할 경우 표는 남기고 '완제품 #ID'로 대체한다.
6. 사용자가 상단 상태 항목을 클릭하면 `/orders?status=...`로 이동하여 기존 `OrdersView` 상태 필터가 유지된다.
7. 표에서 '상세·처리'를 클릭하면 `/orders?id=...`로 이동해 기존 `OrdersView.choose(id)`가 상세를 가져오고 자재 예약·생산 시작·QC 수행 버튼을 노출한다. 쓰기 권한 검사·API·업무 트랜잭션은 기존 코드 그대로다.

## 3. 파일별 역할

| 파일 | 역할 및 연결 |
|---|---|
| `frontend/src/views/DashboardView.vue` | 상태 요약·작업 대기 표·재고 표, 기존 API 연결. 불필요한 4열 KPI 카드와 별도 큐 제거 |
| `frontend/src/views/dashboard-workbench.css` | 대시보드 전용 배치·행 밀도·링크 포커스 표시 및 좁은 화면 레이아웃. 컴포넌트별 CSS 분리 |
| `frontend/src/assets/base.css` | 쓰지 않는 `.operations-summary`·`.operation-stat`·`.queue-row` 스타일 제거. 기존 공통 테이블/기능 유지 |
| `frontend/tests-demo/browser-demo.spec.js` | 실제 Chromium 프로덕션 번들에서 카드 제거·단일 작업 표·상태 필터·정확한 상세 업무 화면을 검증 |

## 4. 예외 및 확인 방법

- **페이지 수 제한:** `totalElements`가 많아도 표에는 각 대기 상태 최대 6건만 표시. 합계와 화면 건수를 각각 고지해 작업 누락을 전체 조회로 오해하지 않도록 처리.
- **품목명 조회 장애:** 업무 표 유지, ID 대체와 경고 메시지 표시.
- **연속 새로고침·화면 이탈:** AbortSignal + `isCurrent()`로 이전 요청 결과 무효화.
- **권한:** 업무 표는 VIEWER도 읽을 수 있으나, 상태 변경은 `OrdersView`와 Spring Security의 기존 권한 경계에 그대로 맡긴다. 데이터 처리 코드 변경 없음.
- **가용재고 0:** 창고 부족 경보가 아님. **현재 조회한 첫 8개 품목**에서 가용재고가 0인 품목 수만 표 하단에 표시.
- **샘플 저장:** Vercel 공개 체험은 여전히 방문자 브라우저 IndexedDB. 실제 MySQL 트랜잭션 실행 증거로 사용하지 않음.

### CI 검증 증거

| 테스트 | 확인 결과 |
|---|---|
| [Frontend Actions #43296997198](https://github.com/jeon950523/factoryflow/actions/runs/43296997198) | **SUCCESS**. Node 37/37, Vite build, Chrome API fixture 11/11, Chrome IndexedDB 데모 7/7 |
| [Fullstack Actions #43296997468](https://github.com/jeon950523/factoryflow/actions/runs/43296997468) | **SUCCESS**. Spring Boot + MySQL 8.4 실행 및 실제 업무 Chrome E2E 2/2 |

## 5. 시니어 기준 코드 검수

**잘한 점:** KPI 수치의 공간 낭비 제거, 작업 우선 동선, 읽기/쓰기 경계 보존, 요청 레이스 방어, 조회량 상한 명시, 데이터 출처 표시, 원래의 4열 KPI CSS 제거.

**위험한 점:** `DRAFT/RESERVED/AWAITING_QC` 중 어떤 상태를 가장 먼저 처리해야 하는지는 현장 정책에 따라 달라질 수 있다. 현재 표는 상태별로 묶여 있으며 업무 중요도 기반 정렬이라고 주장하지 않는다. 건수가 많은 사업장에서는 상태별 6건만 보여주므로 전체 목록으로 바로 이동할 수 있어야 한다.

**지금 고칠 것:** 자동 테스트 오류·중복 선택자·잘못된 데이터 범위 문구가 생기면 즉시 수정한다. 현재 해당 사례 없음.

**기능 리팩터링:** 운영 개요의 기존 API 호출 6건을 사용한다. 상용 서비스에서 실제 지연 문제가 측정되면 집계·우선순위 업무 목록용 서버 API를 설계한다. 아직 성능 실측 근거가 없으므로 선제 최적화는 보류한다.

**효율·유지보수 리팩터링:** 전용 `dashboard-workbench.css`로 새 구조를 분리해 TASK-009의 일회성 글로벌 스타일 추가 문제를 줄였다. 완전한 공통 CSS 변수·토큰화는 다수 페이지에 영향이 있어 후순위.

**검증 한계:** CI는 실제 브라우저 요소와 업무 흐름을 검증하지만, 실 Vercel 배포 후 사용자별 시각적 적합성과 제조 현장 업무 효율성을 입증하지는 않는다.

## 6. 직접 설명할 30초 요약

“기존 대시보드는 네 장의 KPI 카드가 공간을 많이 차지해 실제 업무가 아래로 밀려 있었습니다. 상태별 수치를 한 줄에 요약하고, 자재 예약·생산 시작·품질검사가 필요한 생산지시를 표로 노출하도록 수정했습니다. 데이터는 기존 API에서 읽으며 건수 집계와 부분 목록의 범위를 명확히 구분했습니다. 제조 도메인 로직은 변경하지 않고 Chrome과 실제 MySQL E2E로 검증했습니다.”

이해 점검: (1) 전체 대기 건수와 목록 행 수가 다른 이유는? (2) 새로고침을 연속해서 눌렀을 때 이전 결과를 어떻게 막는가?

## 7. 후속

실제 `factoryflow.kro.kr`의 Vercel 배포 후 PC 화면에서 표의 컬럼 폭/글자 크기/버튼 동선을 확인한다. 실제 사용자 검토나 트래픽 실측 후에만 정렬 규칙과 별도 통합 집계 API를 검토한다.
