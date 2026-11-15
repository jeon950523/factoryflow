# TASK-019 — 품질검사 결과 직접 입력·최종 확인 구현 및 시니어 검수

> 작성일: 2026-11-16
> 검수 상태: **PR 코드·Chrome/IndexedDB·실DB CI 검증 완료, main 병합 후 CI 별도 확인**
> 기준: `main 63dbfbb61682cc1bb1cc4ea032c343f7d0d7f051`, 기존 SSOT Draft 및 [TASK-004F](TASK-004F-품질검사-합격품입고-설계-검수.md) 검증 결과
> 작업 브랜치: `feature/task-019-qc-explicit-confirmation`
> 작성·검수: AI 지원 구현/시니어 기준 1차 코드 검토. 실제 Chrome/MySQL CI 성공 여부 별도 기재. Windows PC 수동 QA 미실시.

## 1. 목적과 사용자 문제

`OrdersView.vue#choose`가 생산지시를 조회할 때 검사 입력에 `{passedQty: plannedQty, failedQty: 0}`을 자동 설정하여 **사용자 검사 결과 입력 없이 QC 확정 버튼이 즉시 활성화**됐다. 실제 서버 `QualityInspectionService.confirm`은 검사/합격품 LOT/완제품 이동 이력/생산지시 완료를 하나의 트랜잭션으로 최종 확정하고 현재 수정 API는 없다. 시연과 현장 유사 작업에서 검수하지 않은 모든 생산품을 합격 처리하는 실수 위험이 있다.

**목표:** 기본 합격 판단을 제거하고 명시적 수량+정수/합계 검증+최종 확인 후에만 재고 반영 POST를 허용한다.

**비범위:** 품질검사 결과 변경/승인/반려·취소/재작업, 새로운 서버 승인 정책·인증·트랜잭션/DB 스키마, 공개 데모 쓰기 검증. 기존 1차 MVP의 단일 공장·창고·EA 정책 유지.

## 2. 변경 파일과 기능 상태

| 파일 | 목적·기존 연결 | 설계 이유 |
|---|---|---|
| `frontend/src/views/OrdersView.vue` | `choose()`에서 검사 필드 공란 초기화, `qcQuantity/qcNumbers/qcTotal/qcValid`, `runAction` 확인창 및 payload 스냅샷, 문구/빈 상태 UX | 검사 결과를 자동 추정하지 않고 사용자로부터 명시적 입력을 받는다. `POST /api/v1/work-orders/{id}/inspection` 유지 |
| `frontend/tests-demo/browser-demo.spec.js` | 원래 자동 전량 합격하는 데모 테스트 교정; 무입력·0/소수/음수/안전수 범위·확인창 취소·새로고침·확정 후 실제 LOT 현재고 검증 | IndexedDB 실제 데모 제조 상태 전이의 사용자 경로를 재현 |
| `frontend/tests-live/real-manufacturing.spec.js` | 실제 MySQL 검사 단계의 수동 값 입력·최종 확인 다이얼로그 검증 | UI 버튼에서 MySQL 검사/재고 반영까지 실제 계약 확인 |
| `tasks/TASK-019-품질검사-명시적확정-안전성.md` | 착수 전 범위/완료 조건 기록 | [기록 표준](../DEVELOPMENT_REPORTING_STANDARD.md)과 일치 |
| `docs/task-reports/TASK-019-품질검사-명시적확정-검수.md` | 본 보고서(설계·검수·실행 증거·후속) | 실행 성공과 코드 작성 상태 분리 |
| `docs/learning/CORE_CONCEPTS.md` | 명시적 사용자 입력/검증과 서버 최종 방어 개념 누적 | 구현 선택 이유와 복습 연결 |
| `docs/TROUBLESHOOTING.md` | 사용자 실수 유발 QC 기본값의 코드 증거, 해결/재현·CI 근거 | 실제 확인된 동작 위험만 등록 |
| `docs/DELIVERY_ROADMAP_20261111.md`, `README.md`, `docs/PROJECT_HANDOFF.md` | 완료 상태와 검증 링크/다음 작업 동기화 | 기록 누락 재발 방지 |

## 3. 호출/상태/DB 흐름

```text
Vue Router /orders?id=<orderId>
  → OrdersView.onMounted → choose(id)
  → GET /api/v1/work-orders/{id} / 상세(AWAITING_QC)
  → qcForm = {passedQty:'', failedQty:''}
  → 두 입력 수량 및 입력 합계 유효성 확인
  → 품질검사 확정 버튼 클릭 → runAction('inspection')
  → qcValid 재확인, {passedQty,failedQty} 스냅샷
  → 사용자 window.confirm(생산지시번호+합격+불합격)
       ├─ 취소: POST 없이 종료, 입력 유지, 서버/IndexedDB 상태 불변
       └─ 승인: acting=true → 기존 POST /api/v1/work-orders/{id}/inspection
  → 서버 QualityInspectionService.confirm(@Transactional READ_COMMITTED)
       → 완제품 Item FOR UPDATE → WorkOrder FOR UPDATE
       → 입력 검증·상태 검사 → inspections 저장
       → passedQty>0인 경우 QC_PASS LOT + FINISHED_RECEIPT 이력 생성
       → WorkOrder COMPLETED → 트랜잭션 commit
  → Vue choose(id)+load()로 데이터/상태 최신화
```

서버 검증·잠금 순서·트랜잭션은 변경하지 않았다. 브라우저 confirm 취소는 **POST 이전 UI 방어**이고, API 직접 호출에 대한 최종 정확성은 서버가 계속 담당한다.

## 4. 핵심 코드·선택 이유

### 4.1 빈 숫자 입력과 명시적 0 구분

```js
function qcQuantity(value) {
  if (value === '' || value === null || value === undefined) return null
  if (typeof value === 'string' && !/^\d+$/.test(value)) return null
  const parsed = Number(value)
  return Number.isSafeInteger(parsed) && parsed >= 0 ? parsed : null
}
```

`v-model.number`의 빈 값 `''`를 `Number('') === 0`로 처리하면, 실제 0을 입력하지 않았는데 수량 0을 인정하게 된다. 빈 입력은 `null` 상태로, 명시적 `0`은 유효한 검사 수량으로 구분한다. 각 값은 음수/소수/안전하지 않은 정수를 거부한다. 합산 값도 `Number.isSafeInteger`로 확인해 오버플로 수준 입력을 차단한다.

### 4.2 확정 전에 값을 스냅샷으로 고정

```js
const inspection = action === 'inspection'
  ? { passedQty: qcNumbers.value.passed, failedQty: qcNumbers.value.failed } : null
if (inspection) {
  if (!qcValid.value) return
  if (!window.confirm(message)) return
}
acting.value = true
await post(`/api/v1/work-orders/${actionOrderId}/inspection`, inspection)
```

UI 확인 전에 서버 POST는 하지 않는다. 입력을 snapshot 객체로 고정해 검증 후 payload가 바뀌는 경로를 줄인다. `acting`으로 POST 중 입력/중복 버튼을 막고 UI 상태는 기존 최신 응답 게이트로 새로고침한다.

**대안:** 자동 합격 수량을 0/0으로 세팅하면 0이 실제 입력인지 알 수 없고, 사용자 입력이 없어도 전량 0으로 검사가 유효해지는 실수 가능성이 있다. 공란 초기화가 명확하다. 모든 업무 단계에 모달 확인을 강제하는 방식은 작업 흐름을 과도하게 늦출 수 있어 **최종 재고 반영 QC**만 별도 재확인한다.

## 5. 예외·보안·데이터 정합성

- 빈 칸·공백·소수·음수·`Number.MAX_SAFE_INTEGER` 초과 → 버튼 비활성, API 요청 없음.
- 입력 `0`은 직접 입력한 경우 허용. **전량 불합격**도 `합격=0`, `불합격=계획 수량`이면 서버에서 합격품 LOT 없이 검사 완료 가능.
- `passed + failed !== plannedQty`, 합계 안전 정수 범위 초과 → 차단. 서버의 `Math.addExact`/ 상태 충돌/UNIQUE 제약과 이중 방어.
- 확인창 취소 → 입력 보존, 서버 비변경. 확인창 승인 → 화면의 기존 `acting` 단일 상태로 이중 클릭 방지.
- POST 실패 → 기존 알림+입력 유지. 서버가 이미 처리했으나 네트워크 실패할 가능성은 여전히 서버의 상태 조회·409 규칙에 따른다.
- VIEWER는 버튼이 없고 서버도 인증·인가 강제. 공개 Vercel은 서버 검증이 아닌 IndexedDB 체험.
- 서버의 검사 확정은 변경·되돌리기 API가 없으므로 작업 안내문에 이를 명시한다.

## 6. 테스트·실행 증거

**최종 기능 커밋 `729e5223869c013f1dd918dc806afc768f83dcf9` PR 회귀검사 성공:**
- [Frontend CI #43729409460](https://github.com/jeon950523/factoryflow/actions/runs/43729409460): Node 단위 **37/37**, Chrome fixture **21/21**, 실제 IndexedDB 데모 Chrome **29/29**, Vite 프로덕션 빌드 성공.
- [실DB E2E #43729409394](https://github.com/jeon950523/factoryflow/actions/runs/43729409394): Java 21/Spring Boot/MySQL 8.4 + Google Chrome **2/2** 성공. 기존 재고 입고·예약·소모·QC·출고 결과를 유지하면서 최종 확인에 대한 테스트도 통과.
- 신규 브라우저 검증 범위: 입력 2칸 공란, 명시적 0, 소수·음수·안전 정수 초과 차단, 합계 일치, 확인창 취소 시 상태/입력 유지·서버 미반영, 승인 후 합격품 재고 +8, 전량 불합격(합격0/불합격10) 시 완제품 재고 미증가.
- **병합 후 `main` Frontend/Fullstack CI와 배포 완료 상태는 별도로 확인**한다.

**초기 PR CI 검사 (실패 원인 분석 완료, 수정 후 재검증 성공):**
- 첫 [Frontend CI #43729184665](https://github.com/jeon950523/factoryflow/actions/runs/43729184665): Node/Chrome fixture/빌드는 통과, IndexedDB Chrome 28건 중 2건 실패. Playwright `getByRole('spinbutton',{name:'합격 수량'})`가 '불합격 수량'에도 부분 일치하여 strict mode violation 발생. **테스트 선택자 오류**로 확인하여 `exact:true` 수정. 이 오류는 실제 검사 저장 실패/재고 불일치 증거가 아니다.
- 첫 [실DB E2E #43729184652](https://github.com/jeon950523/factoryflow/actions/runs/43729184652): 실제 Spring Boot/MySQL Chrome 제조·검사·출고 2/2 성공. 단, 최종 HEAD 기준 다시 검증한다.
- 초기 2건 실패 발견 후 **명시적 전량 불합격(합격 0/불합격 10)** 또한 유효하며 완제품 재고를 늘리지 않는 회귀 테스트를 추가했다.
- 최종 기능 HEAD의 Frontend/Fullstack CI는 성공했다. **`main` 병합 후 재검증 전에는 최종 완료로 표시하지 않는다.**

다음 명령/워크플로를 최종 PR 헤드와 병합된 main에서 검증한다.

- `npm ci`, `npm test`, `npm run build`
- `npx playwright test --config playwright.config.js` — fixture 기존 회귀
- `npx playwright test --config playwright.demo.config.js` — 실제 IndexedDB: 새 검사 수량/취소/승인 흐름
- `npx playwright test --config playwright.live.config.js` — 실제 Spring Boot/MySQL 8.4 Chrome 제조/출고 회귀
- 검증 링크: PR https://github.com/jeon950523/factoryflow/pull/26 (생성 후 확인)
- 실제 Windows Chrome·현장 검사자 사용성: 미검증. 성능 지표: 미측정.

## 7. 시니어 검수

| 관점 | 판정 | 근거 |
|---|---|---|
| 기능 | **Chrome 29/29 PASS** | 자동 전량 합격 방지, 직접 0/전량 불합격, 예외/최종 확인 |
| 데이터 정합성 | 서버 기존 보증 유지 | 검사/완제품 LOT/이동 이력/상태 변경은 하나의 트랜잭션 |
| 보안 | 기존 정책 유지 | UI 버튼 검사는 단독 보안 경계가 아니며 서버 권한/검증 그대로 |
| 경쟁/중복 | 기존 처리 유지 | `acting`/선택 ID 스냅샷/최근 조회 우선 게이트 유지 |
| 예외 | **Chrome PASS** | 빈 필드·안전 정수·취소·재로드·실DB 결과 |
| 효율 | 기능 오버헤드 미미 | 계산은 입력 시 소규모 정수 연산. 추가 서버 조회 없음 |
| 유지보수 | 국소화 | 대규모 다이얼로그 컴포넌트/상태 리팩터링 없이 기존 View 수정 |

### 잘한 점

- 실제 트랜잭션과 감사 이력을 변경하지 않고 **UI 단계의 인적 오류**만 줄인다.
- 확인창에 **업무 식별자·합격·불합격**을 함께 보여 전량 합격 여부를 다시 판단하게 한다.
- 입력/합계 검증은 computed 하나의 규칙을 공유하며, 실제 POST 전에 다시 재검증한다.

### 단점/잔여 위험

- 브라우저 기본 `window.confirm`은 스타일을 제어하기 어렵고 일부 자동화 도구에서 확인 처리 이벤트가 필요하다. 접근성 자체는 브라우저가 제공하지만 모든 환경에서 UX까지 보증할 수 없다.
- 서버는 변경되지 않으므로 API를 직접 호출하는 관리자에 대한 별도 2단계 승인·검사 결과 변경 워크플로는 제공하지 않는다.
- QC 입력은 새로고침 시 사라진다. 이는 확정 전 미입력 상태를 명확히 유지하기 위한 의도적 선택이다.

## 8. 기능/효율 리팩터링 우선순위

| 대상 | 우선순위 | 판단 이유 및 완료 조건 |
|---|---|---|
| QC 기본 전량 합격 제거 | **즉시 수정** | 검사자 명시 입력 없이는 QC 버튼 비활성, 회귀 테스트 증명 |
| 필드/합계 안전 정수 검증 | **즉시 수정** | 0과 빈 입력 구분, 소수·음수·범위초과 거부 |
| 최종 QC 확인 및 취소 보존 | **즉시 수정** | POST 없는 취소·승인 후 재고 반영 실DB 검증 |
| 품질검사 취소/정정 API | **2차 개선** | 새로운 변경 이력/승인/감사·DB 계약이 필요 |
| `window.confirm`의 공통 커스텀 UI 대체 | **2차 개선** | 실제 사용자 요구/접근성 시험 후 설계. 현재는 브라우저 표준 사용 |
| 단순 수량 계산의 별도 상태 라이브러리 | **현 시점 불필요** | 입력 2개뿐이라 도입 복잡성이 효익 초과 |
| 수량·수정 내역 서버 데이터 강제 | **현재 서버 검증 유지** | 서버가 이미 상태/수량 검증, 중복 검사 제약 보증 |

## 9. 다음 TASK 조건 및 핸드오프

- PR 전체 CI가 통과한 뒤 병합하고, main 커밋에서 Frontend + MySQL Chrome E2E를 재확인한다.
- 실제 공개 사이트의 새 QC 동작은 Vercel 배포 완료 후 **별도의 공개 Chrome 쓰기 테스트**가 아닌 안전한 격리 실행에서 검증해야 한다. 공개 브라우저 데모 데이터 쓰기는 방문자에게 영향이 적지만 별도 QA 정책으로 관리한다.
- 후속 개선: 품질검사 상태를 사용자가 의도적으로 처리했는지 UX 조사, 제품 데모 가이드에 검수 수량을 입력한다는 안내 반영.

## 10. 20~40초 설명

“품질검사 대기 생산지시를 선택하면 계획량이 자동으로 전부 합격 처리되던 화면을 발견했습니다. 이대로는 작업자가 검사 수치를 입력하지 않고 합격품 재고를 늘릴 수 있어 두 필드를 공란으로 시작하게 바꿨고, 명시적 0·안전 정수·합계 검증 및 작업번호와 수치를 표시하는 최종 확인을 추가했습니다. 실제 서버의 트랜잭션과 LOT/이력 처리 로직은 그대로 두고 Chrome·IndexedDB·MySQL 회귀 테스트로 확인합니다.”

## 11. 이해 점검 질문

1. `v-model.number`로 입력받아도 빈 입력과 0을 별도로 판별해야 하는 이유는?
2. QC 화면 확인 취소만으로 데이터 정합성을 보증하지 못하고 서버 트랜잭션/상태 검사가 필요한 이유는?
3. 모든 API에 확인 대화상자를 붙이는 것보다 재고를 확정하는 QC에 국소적으로 적용한 이유와 단점은?

## 12. 결정/ADR

기존 상태·트랜잭션 및 서버 API 계약이 변하지 않으므로 **신규 ADR 불필요**. 변경은 [TASK-019 작업지시서](../../tasks/TASK-019-품질검사-명시적확정-안전성.md)와 본 검수 보고서로 관리한다.
