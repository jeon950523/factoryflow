# TASK-009 — 제조 업무형 UI 2차 개선 · 시니어 코드 검수

- 검증 코드 SHA: `85df1acb3a6f267cd116acd9061680911dd1eee5`
- 상태: **기능 브랜치 CI 검증 완료 / 실 Vercel 도메인 최종 시각 QA는 미검증**
- 범위: **Vue 3 화면·CSS·브라우저 테스트**만 수정. Spring Boot/API/DB/IndexedDB 제조 엔진 변경 없음.

## 1. 목적

기존 운영 개요는 KPI 장식 카드·영문 키커·빠른 작업 카드에 비중이 있어, 실제 직원이 자재 예약·생산 시작·QC를 무엇부터 처리할지 판단하기 어려웠다. 단일 공장·단일 창고 MVP의 **실제 구현 API**만 사용해 업무 처리 우선순위와 정보 밀도를 개선한다. 존재하지 않는 설비 가동률·작업조·재고 알림 기준은 만들지 않는다.

## 2. 전체 호출 순서

**대시보드 → 상태별 작업 처리**

1. `DashboardView.refresh()`: `GET /api/v1/items`, `GET /api/v1/inventory` (첫 8개), `GET /api/v1/work-orders` 최근 6개와 상태별 5회 요청을 `Promise.all`로 수행.
2. `totals`로 `DRAFT`·`RESERVED`·`AWAITING_QC`의 작업 대기 건수를 합산. 각 작업 대기 행은 `/orders?status=...`로 연결.
3. `OrdersView`가 검증된 `status` 쿼리만 `statusFilter` 초기값으로 읽고, `GET /api/v1/work-orders?status=...` 요청. 다른 쿼리 값은 전체 목록.
4. 사용자가 상태 필터를 바꾸면 `router.replace`로 URL을 갱신; `App.vue`의 키 기반 뷰 재생성과 `onBeforeUnmount` 취소 처리에 따라 목록을 다시 조회.
5. 지시번호 선택 시 기존 `GET /api/v1/work-orders/{id}` 상세·원자재 이름을 조회하고, 허용된 상태에 따라 **기존** 예약·소모·QC 명령을 실행.
6. `InventoryView`에서 품목명 또는 LOT 보기 버튼을 클릭/Enter하면 `GET /api/v1/inventory/lots?itemId=...`을 통해 기존 LOT 테이블 표시.

**중요한 데이터 범위:** 대시보드의 '처리 대기'는 전체 상태별 지시 건수 기반이지만, '표시 품목 중 가용재고 0'은 **첫 8개 조회된 품목 기준**이다. 이를 창고 전체 부족 경보라고 주장하지 않음.

## 3. 변경 파일과 역할

| 파일 | 변경 목적 |
|---|---|
| `frontend/src/views/DashboardView.vue` | 상태별 작업 대기 목록과 최근 지시/재고를 작업 순서로 재배치, 데이터 범위 표시 |
| `frontend/src/views/OrdersView.vue` | 상태별 URL 초기화·동기화, 선택 지시 강조, 작업 목록/상세 정보 계층 |
| `frontend/src/views/InventoryView.vue` | 페이지 한정 가용 0 수치, LOT 보기 진짜 버튼·키보드 실행, 선택 품목명 제목 |
| `frontend/src/App.vue` | 영문 장식·브랜드 하단 문구의 한글화, 기존 데모 안전 안내 유지 |
| `frontend/src/assets/base.css` | 그림자·모서리 축소, 표 13px/컴팩트 셀, 작업 대기 행, 제목·레이블 대비 |
| `frontend/tests-demo/browser-demo.spec.js` | 기존 테스트 선택자 명확화, 작업 대기 링크·URL 필터·LOT 키보드 접근성 Chrome 회귀 테스트 |

## 4. 주요 예외 및 테스트

- **잘못된 상태 쿼리:** `DRAFT/RESERVED/IN_PROGRESS/AWAITING_QC/COMPLETED/CANCELED`만 인정하고, 나머지는 전체 목록으로 처리.
- **필터 전환 중 이전 상세 응답:** `detailGate.cancel()` 및 새로고침 시 컴포넌트 언마운트로 이전 응답 결과가 현재 선택을 덮지 않도록 기존 last-request-wins 규칙 유지.
- **생산 재고 부족·QC·출고:** 화면만 수정. 도메인 상태 전이와 MySQL/IndexedDB 처리 엔진의 롤백·멱등성 수정 없음.
- **없는 경보 기준:** 가용재고가 0인 품목만 검토 대상 표시; 생산 계획량 대비 부족 여부는 계산하지 않음.
- **읽기 실패:** 기존 `AsyncState` 오류/재시도 경로 유지.
- **접근성:** 선택 지시 `aria-pressed`, 상태 필터 레이블과 LOT 보기 버튼 및 Enter 실행 테스트 추가.

### GitHub Actions 실행 증거

- [Frontend CI #43266729962](https://github.com/jeon950523/factoryflow/actions/runs/43266729962): Node **37/37**, Vite build 성공, 기존 Chrome **11/11**, IndexedDB 브라우저 **6/6**.
- [Fullstack MySQL E2E #43266730205](https://github.com/jeon950523/factoryflow/actions/runs/43266730205): Spring Boot/MySQL 8.4 실제 업무 Chrome **2/2**.
- [첫 프론트 CI 실패 #43266084592](https://github.com/jeon950523/factoryflow/actions/runs/43266084592): 기존 테스트의 `getByRole('button',{name:'산업용 강판'})`가 품목 버튼과 새 LOT 버튼 2개에 매칭되어 strict mode 실패.
- 수정: 품목명 버튼 조회 시 `exact:true`를 추가하고, LOT 버튼은 별도로 키보드 검증. UI 접근 기능을 제거하지 않고 테스트 의도를 명확히 함.

## 5. 시니어 검수

**잘한 점:** 실제 구현된 필드와 상태 코드만 표시, 사용자 행위의 우선순위 개선, 읽기/쓰기 경계와 데이터 레이어 불변, 기존 인증·인가 영향 없음, UI의 숫자 범위와 시뮬레이션 범위 고지.

**위험한 점:** 업무 수행자가 필요한 핵심 화면 구성은 업종/작업자 역할에 따라 달라짐. 이 화면이 상용 MES 전체를 대체할 수 있다고 보장할 수 없고, 가용재고 0은 재고 부족의 충분조건이 아님.

**지금 당장 고칠 것:** CI 실패나 URL·LOT 접근성 회귀는 수정. 첫 실행에서 재현된 Playwright 선택자 중복 문제를 수정하고 6개 Chrome 데모 테스트 재실행 성공.

**2차 개선:** `base.css`가 단일 대용량 스타일 시트이고 새 UI 규칙이 하단 override로 추가된 상태. 실제 화면 시각 검수 후 공통 토큰/컴포넌트별 스타일로 나눌 수 있음. 단, 지금 스타일 분리는 회귀 면적이 더 커지고 사용자 가치 대비 우선도가 낮음.

**효율:** 운영 개요는 상태별 `GET /work-orders` 5회 등을 병렬로 호출하는 기존 API 구조를 유지. 성능 문제가 측정되면 서버에서 통합 집계 API를 도입하는 방법을 비교할 수 있지만, 현재는 병목 수치가 없으므로 선제 최적화 금지.

**운영/포트폴리오 구분:** 1차 MVP에는 실습 가능한 업무형 UI로 적절. 실무 배포 기준으로는 현장 역할별 기능, 실사용자 가독성/키보드 QA, 재고 부족 기준, 감사 로그 등이 추가로 필요.

## 6. 테스트 명령·확인 방법

- `cd frontend && npm ci && npm test && npm run build`
- `npx playwright test --config playwright.config.js`: 로컬 API fixture Chrome 11건
- `npx playwright test --config playwright.demo.config.js`: 프로덕션 빌드/IndexedDB Chrome 6건
- Fullstack workflow: `.github/workflows/fullstack-ui-ci.yml`에서 Java 21·MySQL 8.4·Chrome 2건
- 사용자가 실제 도메인에서 확인: `https://factoryflow.kro.kr/` 운영 개요 → '자재 예약 대기' → 상태 필터 변경 및 새로고침 → 'LOT 보기'의 키보드 실행. **실도메인 최신 배포 전파 및 육안 QA는 별도로 확인해야 함**.

## 7. 선택 이유와 대안

- 대안 A: 상용 MES 외형 그대로 재현 → 아직 제공하지 않는 생산라인/설비/작업조/불량률을 임의로 표시할 위험이 크므로 제외.
- 대안 B: 현재 카드 중심 UI 유지 → 기능 동작은 문제없지만 업무 동선과 데이터 밀도 개선 효과가 작아 제외.
- **선택:** 실제 구현된 상태·재고 집계와 단일 공장 범위를 활용한 업무형 화면. 기능/테스트 범위를 보존하면서 사용자 경험을 강화.

## 8. 직접 설명할 30초 요약

“기존 대시보드는 등록 품목과 완료 건수를 먼저 보여줘 다음에 처리할 업무를 찾기 어려웠습니다. 기존 생산지시 상태 API를 사용해 예약·생산 시작·품질검사 대기를 우선 노출하고 필터가 URL과 연동되도록 개선했습니다. 재고 화면의 LOT 조회는 키보드 접근성을 확보했고, 서버와 데모 엔진은 건드리지 않은 채 CI 및 실DB E2E로 회귀 검증했습니다.”

이해 점검: (1) 처리 대기와 가용재고 0의 집계 범위가 다른 이유는? (2) 상태 필터를 URL에 반영해야 하는 이유는?

## 9. 후속

최종 Vercel 배포 후 PC 브라우저로 주요 업무 흐름/표 가독성/스크롤 동작을 직접 확인하고 실제 업무 담당자의 피드백이 확보되면 우선순위를 재정리한다.
