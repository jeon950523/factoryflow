# TASK-020 — 입고·출고 응답 유실 재시도 및 멱등성 검수

> 작성: 2026-11-16 · 기준 main `2b6cde9b2cb724bec512bd5ea4851a260060b4c4`
> 상태: **TASK-020 기능 코드·실DB CI 완료 / 공개 Vercel 배포 제한으로 사이트 반영 미확인** — [PR #28](https://github.com/jeon950523/factoryflow/pull/28) 병합 `8cb8617`, main Frontend/Fullstack 성공.
> PR: [#28](https://github.com/jeon950523/factoryflow/pull/28) · 브랜치 `feature/task-020-write-retry-safety`
> 기본 계약: [초기 SSOT](../FACTORYFLOW_SSOT_v0.1.md) Draft, [ADR-013](../adr/ADR-013-프론트데모-실백엔드분리.md), 서버 코드·기존 MySQL 통합 테스트 기준

## 1. 왜 작업하는가

기존 `StockReceiptService.receive`와 `ShipmentService.ship`은 **동일 requestKey + 동일 payload hash**에 대해 이미 처리한 결과(HTTP 200)를 재전송하며, 다른 내용의 동일 키는 `IDEMPOTENCY_KEY_CONFLICT`(409)로 막는다. 원자재 품목 잠금/LOT 재고와 출고 FIFO는 서버 트랜잭션으로 보호된다. [StockReceiptApiIntegrationTest](../../backend/src/test/java/com/factoryflow/StockReceiptApiIntegrationTest.java)와 [FinishedShipmentIntegrationTest](../../backend/src/test/java/com/factoryflow/FinishedShipmentIntegrationTest.java)가 이 계약을 검증했다.

반면 **브라우저의 최초 POST가 실제 DB에 반영됐지만 응답만 사라지는 상황**에 기존 UI는 `e.message`만 표시하고 모달 입력을 다시 수정하거나 닫은 뒤 새 requestKey로 신규 요청을 만들 수 있었다. 이때 *새 키*로 같은 작업을 전송하면 서버는 이를 새로운 입고/출고로 해석해 재고를 이중 변경할 수 있다. 단순 실패 메시지와 기존 백엔드의 멱등성 구현만으로는 이 사용자 동선을 막지 못한다.

## 2. 변경 파일/책임

| 파일 | 변경/책임 | 기존 계약 |
|---|---|---|
| `frontend/src/lib/pendingWrite.js` | 응답 불확실성 분류, 저장 payload 정형 검증·세션 보관·삭제 | 서버 동일 키/동일 본문 재전송 규칙에 맞춤 |
| `frontend/src/lib/pendingWrite.test.js` | status 0/5xx/INVALID_RESPONSE와 4xx 구분, 세션 저장·복원, 오염 데이터 거절 | Node 단위 테스트 |
| `frontend/src/views/InventoryView.vue` | 미확인 원자재 입고 payload 고정, LOT/수량 편집 잠금, 동일 요청 재시도, 화면 이동·새로고침 복원 | `POST /api/v1/stock-receipts`, 키·LOT 정규화 |
| `frontend/src/views/ShipmentsView.vue` | 미확인 완제품 출고 payload 고정, 재고 차감 재전송 방어/UX, 성공 후 상세 복원 | `POST /api/v1/shipments`, 키·수량 정규화 |
| `frontend/tests-live/real-manufacturing.spec.js` | **실제 MySQL에 커밋 후 브라우저 응답만 유실**하는 네트워크 결함을 Playwright route.fetch + abort로 재현, 재시도 후 물리 이동 1회/LOT 잔량 검증 | 기존 실DB 제조 전체 흐름 |
| `frontend/tests-demo/browser-demo.spec.js` | 재고 부족(명백한 409) 시 편집 가능/미확인 잠금 없음 검증 | IndexedDB 실제 데모 |
| `tasks/TASK-020-입출고-재시도-멱등성.md` | 착수 지시서·범위·완료기준 | 문서 작성 표준 |
| `docs/task-reports/TASK-020-쓰기재시도-멱등성-검수.md` | 현재 보고서·시니어 검토·증거/한계 | 보고서 템플릿 |
| `docs/learning/CORE_CONCEPTS.md`, `docs/TROUBLESHOOTING.md` | 멱등성·응답 유실·작업 실패 재현 및 보완 이유 누적 | 실제 오류/근거를 분리 기록 |
| `README.md`, `docs/DELIVERY_ROADMAP_20261111.md`, `docs/PROJECT_HANDOFF.md` | 최신 TASK/검증/다음 작업 상태 일치 | 과거 TASK 이력 유지 |

**설계·API·DB 변경 없음.** 새로운 ADR 불필요: 서버의 기존 idempotency 보장과 기존 데모/실DB 분리 원칙을 재사용한다.

## 3. 실행 흐름/정합성/트랜잭션

```text
사용자: 입고/출고 모달 선택 → 입력 유효성 검증
  → 유일 requestKey + 정규화할 payload 최초 생성 (브라우저)
  → POST 기존 Spring Boot Controller
  → CanonicalReceipt.of / CanonicalShipment.from: 정규화 + SHA-256 payloadHash
  → StockReceiptService.receive / ShipmentService.ship:
      기존 requestKey 탐색 → 해시 일치: 200 기존 결과 / 해시 불일치: 409
      신규면 Item 선잠금 → WorkService.write(@Transactional READ_COMMITTED)
      원자재 입고: LOT 현재고+ / RECEIPT 이력 1회
      완제품 출고: QC_PASS FIFO LOT 차감 / ShipmentAllocation / Movement 1회
      UNIQUE 충돌/트랜잭션 롤백은 기존 서비스 책임
  → [예상 실패] MySQL COMMIT은 성공했지만 Chrome 응답만 유실(status=0)
  → UI: pendingWrite(snapshot) + tab sessionStorage 기록, 입력 잠금
  → 닫기/재진입/새로고침 후 같은 key+payload로 POST 재전송
  → 서버: 기존 receipt/shipment 결과 HTTP 200. **LOT·원장 두 번째 변경 없음**
  → UI: 정상 결과 확인 후 pending clear → 상세/재고 화면 갱신
```

**의도적 제약:** 응답을 못 받았다는 사실은 *처리가 실패했다는 증거가 아니다.* 이 시점에 새로운 key를 발급하지 않고 기존 payload만 재전송해야 한다. UI 잠금은 운영자의 실수 방지이고, DB 멱등성의 최종 보장은 기존 고유 제약/잠금/트랜잭션이 맡는다.

## 4. 핵심 코드 이해

### 4.1 확실한 4xx와 처리 여부가 불확실한 오류 구분

```js
export function isUncertainWriteError(error) {
  return error?.status === 0 || error?.status >= 500 || error?.code === 'INVALID_RESPONSE'
}
```

- **입력:** `ApiError(status, code)`. 서버가 명확한 `INSUFFICIENT_FINISHED_STOCK(409)` 등을 반환하면 그 요청이 정상 출고됐다고 추정하지 않고 일반 오류로 처리한다.
- **의미:** 연결 실패·5xx·성공 HTTP의 잘못된 payload는 서버 커밋 결과를 단정할 수 없어 **동일 요청 재전송 필요**. 서버가 기록했다는 가정 또한 하지 않는다.
- **대안:** 모든 오류에서 새 키를 발급하거나 모든 오류를 pending으로 잠그면 각각 중복 위험 또는 조작 불가 UX가 생긴다.

### 4.2 재시도 payload의 불변성 및 보관

```js
const payload = pendingShipment.value ?? {
  requestKey: shipment.value.requestKey, finishedItemId, quantity,
}
try {
  created = await post('/api/v1/shipments', payload)
} catch (e) {
  if (isUncertainWriteError(e)) {
    pendingShipment.value = payload
    pendingPersisted.value = savePendingWrite('shipment', payload)
  }
}
```

- **입력:** 최초 제출 순간의 key/id/qty. 응답 유실 이후 수정 UI 비활성.
- **처리:** `sessionStorage`는 이 브라우저 탭에서만 같은 요청의 재진입과 새로고침 복원을 돕는다. 복원 데이터는 최소 허용 key/id/qty/LOT를 검증한 뒤 정의된 API 필드만 다시 보낸다.
- **출력:** 같은 key+payload 재전송, 서버의 HTTP 200 replay/201 create 모두 성공 처리 후 pending 삭제.
- **세부 검수:** 브라우저의 UUID는 소문자가 포함되지만 서버는 `Locale.ROOT`로 대문자 정규화해 회신한다. 응답 비교는 대문자 정규화 후 수행하되 **재전송 payload 원문은 최초 전송 값을 그대로** 유지한다.

### 4.3 성공 이후 UI 후처리 실패와 POST 실패 분리

서버 정상 응답 및 핵심 ID/키/수량 확인 → pending 삭제 → 모달 닫기 → **별도** 화면 이동/조회. 성공한 DB 쓰기 이후 `router.replace`나 목록 새로고침 오류를 POST 실패로 잘못 표기하지 않는다.

## 5. 경계·위험·보안

- **거래 결과 미확인:** HTTP 연결 끊김/status 0, 5xx, 파싱/계약 위반은 잠금/동일 재전송. 재전송 결과가 **확실한 미처리**(400 검증 실패, 404 대상 없음, 409 재고 부족·품목 유형 충돌)이면 pending 해제 후 새 요청 키로 수정 가능하게 한다. **IDEMPOTENCY_KEY_CONFLICT(409), 401/403, 5xx는 원본 완료 여부가 불분명해 잠금을 유지**한다.
- **확정 거절:** 출고 재고부족/검증/인가 4xx는 잠금 없이 기존 입력 수정 가능. 이 응답이 서버 커밋 성공과 같은 뜻은 아니다.
- **LOT·품목 정규화:** 서버 canonical key/LOT 대문자 정규화와 브라우저 UUID 케이스 차이는 *비교*에서만 해결한다. 서로 다른 키 발행·payload 임의 수정은 방지한다.
- **응답 유효성:** 반환된 ID/요청 키/품목/수량이 예상과 달라지면 성공으로 간주하지 않고 미확인으로 처리해 중복 우려를 낮춘다.
- **세션 범위:** sessionStorage는 같은 탭·브라우저 세션만 지원. 다른 탭·저장소 제한·브라우저 세션 종료/스토리지 수동 삭제에는 새 키 중복 위험이 남는다. 제한된 브라우저에서는 안내를 표시한다.
- **개인정보:** 저장하는 것은 해당 탭의 업무 요청 식별자/LOT/수량이고 비밀번호·JWT·실계정 토큰이 아님. 실제 운영에서는 저장 정책과 권한·보안을 재설계해야 한다.
- **성능:** 추가 네트워크 요청/서버 조회·DB 스키마/쿼리 없음. 쓰기 오류에서만 작은 JSON을 sessionStorage에 한 번 쓰고, 성공 시 삭제한다. 저장소 장애는 try/catch로 UI를 계속 사용 가능하게 한다.
- **중복/경쟁:** 같은 탭의 중복 클릭은 `saving` 가드로 방어, 서버는 UNIQUE requestKey·payload hash·Item 선잠금과 트랜잭션으로 복수 요청을 보호한다. 다중 탭 동기화는 범위 밖.

## 6. 실행 검증

**첫 PR 검증 완료, 강화된 최종 테스트 재실행 중:**
- [Frontend CI #43732039360](https://github.com/jeon950523/factoryflow/actions/runs/43732039360) **SUCCESS**: Node **41/41**, Chrome fixture **21/21**, IndexedDB Chrome **30/30**, 프로덕션 빌드 성공.
- [Spring Boot/MySQL E2E #43732039371](https://github.com/jeon950523/factoryflow/actions/runs/43732039371) **SUCCESS**: Java 21 + MySQL 8.4 실제 서버/Chrome E2E **2/2**. `route.fetch()`로 먼저 DB 커밋한 후 `route.abort()`로 브라우저 응답 유실, 동일 키 retry 뒤 현재고/원장 일치.
- **사전 검수 후 추가 강화:** 실DB 테스트가 브라우저 최초 요청과 retry의 `requestKey`뿐 아니라 **JSON body 전체가 같은지**, 서버가 **HTTP 200 replay**로 응답했는지 명시 단언하도록 강화했다. 이후 **미전송된 네트워크 실패 → 재전송 409 재고부족 → 입력 잠금 해제 및 새 키 발급**도 실제 MySQL Chrome 테스트에 추가했다. 강화된 기능 테스트는 PR 코드 `278c60fe`에서 성공했다. 문서 반영 후 최종 PR HEAD 및 병합 main CI는 별도 확인한다.

- Node: `cd frontend && npm ci && npm test` → pendingWrite classification/read/write/clear/오염 저장 데이터 테스트
- Build: `cd frontend && npm run build`
- Chrome fixture: `npx playwright test --config playwright.config.js`
- IndexedDB demo: `npx playwright test --config playwright.demo.config.js` → 명백한 재고부족 409 UX
- Spring Boot/MySQL 8.4 Chrome: `npx playwright test --config playwright.live.config.js` → `route.fetch()`가 서버 커밋 완료 응답을 수신한 뒤 `route.abort()`로 브라우저만 실패; 재전송 후 현재고/LOT 이동/출고 배분 검증
- [PR #28](https://github.com/jeon950523/factoryflow/pull/28) 병합 `8cb86179294ca702b5d0ad3154b3efca46c3052b`, [main Frontend #43733088956](https://github.com/jeon950523/factoryflow/actions/runs/43733088956), [main 실DB #43733088860](https://github.com/jeon950523/factoryflow/actions/runs/43733088860)에서 실제 성공 확인. Vercel 배포는 rate limit 실패로 별도.
- Windows 네이티브 DPI 확대·실제 외부 공개 MySQL 운영 브라우저 검증은 수행하지 않음.

## 7. 시니어 검수: 기능/안전/효율

| 관점 | 코드 정적 검토와 판정 | 검증 필요 |
|---|---|---|
| 기능 | 최초 payload snapshot 보존, reopen/refresh 재시도 | Playwright 실제 화면 |
| 정확성 | 같은 key+same payload/200 replay, 원장·현재고 1회 | 실 MySQL E2E |
| 입력 유효성 | 원자재·완제품 ID, 양의 안전 정수 수량 검사 유지 | Chrome 입력 실패 |
| 동시성 | Spring 기존 unique key/선잠금/트랜잭션 유지 | 서버 기존 통합 테스트 + 새 Chrome |
| 보안 | 서버 CSRF/세션 역할 유지, sessionStorage에 비밀키 없음 | 세션 만료 시 UX는 후속 |
| 예외 | 불확실한 결과와 명백한 실패를 UI·키 정책으로 분리 | 409/응답 유실 실험 |
| 효율 | 저장 1건/탭당 operation, 추가 API 검색 없음 | 다중 탭/대량 요청 미측정 |
| 유지보수 | 공통 무상태 helper와 각 모달의 작은 상태 관리 | 코드 리뷰와 회귀 |

## 8. 장점/단점/대안

- **선택:** 회복 가능한 pending 요청을 같은 탭에서만 보관. 장점은 단순 구현·기존 API 재사용·이중 처리 방어의 사용자 경로 보강, 단점은 탭 종료/다중 탭에서 완전한 복구를 못 한다는 점.
- **대안 A:** 새 `GET ?requestKey` API로 서버에서 처리 여부를 우선 조회. 보편적인 운영 UX에 유리하나 API·권한·조회 인덱스/보관기간·장애 처리 정책과 추가 설계가 필요해 이번 TASK 범위를 초과.
- **대안 B:** 전역 LocalStorage 영구 pending. 탭 간 재발행 위험은 줄지만 보안·stale 데이터·동기화 충돌과 영구 보관 위험이 커져 시연 단계에서 채택하지 않음.
- **대안 C:** 네트워크 오류 시 즉시 새 키 생성. 서버의 멱등성 의미를 훼손하므로 제외.

## 9. 리팩터링 우선순위

| 대상 | 우선순위 | 이유/완료 조건 |
|---|---|---|
| 응답 유실 뒤 payload 수정/키 재발행 | **즉시 수정** | 동일 키·payload로만 재시도, 새로고침 복원 |
| 성공 후 상세 이동 실패와 POST 실패 혼동 | **즉시 수정** | DB 반영 성공이면 pending 삭제, 목록 오류 별도 안내 |
| 키 케이스 비교 | **즉시 수정** | 소문자 UUID 요청/대문자 정규화 응답 수용 |
| 서버 requestKey 조회·처리 중(PENDING) 이력 조회 | **2차 개선** | 실제 운영 재시도/조회 API 계약 선결정 |
| 여러 탭 간 미확인 작업 알림/동기화 | **2차 개선** | 브라우저 storage 정책·동시 작업 제어 필요 |
| 단일 폼마다 도메인별 상태 중복 | **현 시점 불필요** | 2개 폼이므로 거대 공통 폼 abstraction은 과설계 |
| Redis/메시지 큐/재시도 백오프 자동화 | **현 시점 불필요** | 서버 DB UNIQUE/idempotency가 존재하고 자동 재시도는 오히려 과잉 요청 가능 |

## 10. 20~40초 설명

“실제 MySQL에 입고나 출고가 정상 반영됐어도 응답을 못 받은 브라우저에서 새 요청 키를 발급하면 이중 반영될 수 있다는 문제를 발견했습니다. 기존 서버는 같은 키·같은 내용에 대해서 이미 결과를 재생하는 멱등성 기능이 있으므로, 프론트에서 결과 미확인 요청의 키와 내용을 스냅샷으로 고정하고 같은 탭에서 복원·동일 재전송하도록 했습니다. 테스트는 서버에서 먼저 커밋한 뒤 브라우저의 응답만 차단해 물리 이동이 한 번만 생기는지 직접 확인하도록 만들었습니다.”

## 11. 이해 점검

1. 서버가 201로 커밋했어도 브라우저에서 네트워크 오류가 나면 왜 재시도 시 새 UUID를 만들면 안 되는가?
2. payload hash가 같을 때 200 replay를 받는 것이 '두 번 저장'과 다른 이유는?
3. `sessionStorage` 보호가 없는 다른 브라우저 탭·장애 복구에서는 어떤 한계가 있으며 향후 어떤 API가 필요한가?

## 12. 다음 단계와 정본

- CI 실패면 반드시 원인/수정/재실행 수치를 기록하고, PR HEAD 성공 후 병합/main CI를 확인한다.
- 최종 코드/기술 설명은 이 보고서, 누적 [CORE_CONCEPTS](../learning/CORE_CONCEPTS.md), 실제 재현 [TROUBLESHOOTING](../TROUBLESHOOTING.md), [PROJECT_HANDOFF](../PROJECT_HANDOFF.md)로 연결한다.
- 신규 ADR 불필요: 기존 재고 멱등성 계약을 변경하지 않고 브라우저 복구 단계를 보완한 것이다.

### 2차 시니어 검수 발견: 미확인 → 확정 거절로 바뀌는 상태 전이

최초 네트워크 실패에서는 트랜잭션 커밋 여부를 알 수 없어 잠금이 옳다. 그러나 같은 키/본문을 재전송했더니 서버가 **재고 부족 등 명시적 미처리 409**를 반환한 경우, 서버 멱등성 조회 계약상 원래 성공한 요청이었다면 200 replay가 반환되어야 한다. 그러므로 이 409는 새 요청을 수정해 제출하도록 풀어줘야 UI가 영구 잠김 상태에 빠지지 않는다.

`isConfirmedWriteRejection`은 400/404 및 허용된 업무 충돌 코드만 잠금 해제한다. 인증 만료 401/403, 다른 payload의 같은 키 사용 충돌, 5xx는 원 요청 결과를 보증하지 않으므로 계속 잠가둔다. 확정 거절 해제에서는 새 requestKey를 발급하며, 실제 MySQL Chrome에서 이전 두 시도에 같은 키·세 번째에는 새 키가 사용되는지 HTTP 본문으로 검증한다.

## 13. PR 기능 검증 — 강화된 실제 MySQL 회귀 성공

- 검증 커밋 `278c60fec74525ca029e09635f2ab052f2d67cc8`에서 [Frontend CI #43732644025](https://github.com/jeon950523/factoryflow/actions/runs/43732644025) **SUCCESS**: Node **42/42**, Chrome fixture **21/21**, IndexedDB Chrome **30/30**, Vite 프로덕션 빌드.
- 동일 코드 커밋에서 [Spring Boot/MySQL E2E #43732644015](https://github.com/jeon950523/factoryflow/actions/runs/43732644015) **SUCCESS**: 실제 Java 21·Spring Boot·MySQL 8.4 + Chrome **3/3**.
- 입고 첫 POST는 실제 서버에서 201 커밋 후 브라우저 응답만 유실 → 같은 탭 재진입/새로고침 후 최초 key+JSON payload와 똑같이 재전송 → 서버 **HTTP 200 replay**, 실제 원장 RAW_RECEIPT **1개**, 재고 25(후속 생산 소모 뒤 5).
- 출고 첫 POST도 실제 DB 201 커밋 후 응답 유실 → 동일 key+payload 전송, **HTTP 200 replay**, FINISHED_SHIPMENT 원장 **1개**, FIFO 출고 3, 완제품 현재고 5.
- 네트워크 첫 요청이 서버에 미전송된 별도 시나리오: status0로 pending → 재전송 시 명백한 `INSUFFICIENT_FINISHED_STOCK` 409 → 잠금 해제 후 수정한 요청은 새 키로 전달, 유령 원장/출고 없음.
- [IndexedDB 데모 409 테스트](../../frontend/tests-demo/browser-demo.spec.js)는 명백한 재고 부족일 때 편집이 가능한 것을 확인하되 실제 MySQL 트랜잭션이라고 주장하지 않는다.
- **최종 병합 판정:** 기능 코드 병합 main `8cb8617`의 Frontend/MySQL CI는 성공했다. PR 마지막 HEAD는 기능 코드 이후 문서 변경만 있어 새 CI가 실행되지 않았으며, 정확한 병합 main SHA의 실행 성공으로 기능을 완료 판정한다. Vercel 배포는 별도 실패 상태이며 공개 화면 최신화는 미완료.
- 재현한 CI 실패: 이번 TASK에서는 확인한 기능 실패 없음. 초기 연속 커밋 때문에 이전 헤드 작업이 `cancelled` 된 것은 기능 실패가 아니라 GitHub Actions 동시 실행 취소 정책이다. 실제 오류가 생기면 [TS-009](../TROUBLESHOOTING.md)에 추가한다.

## 14. 최종 main 검증 및 배포 차단 (2026-11-16)

- [PR #28](https://github.com/jeon950523/factoryflow/pull/28) squash merge SHA: `8cb86179294ca702b5d0ad3154b3efca46c3052b`.
- [병합 main Frontend CI #43733088956](https://github.com/jeon950523/factoryflow/actions/runs/43733088956) **SUCCESS**: Node 42/42, Chrome fixture 21/21, IndexedDB Chrome 30/30, Vite build.
- [병합 main Fullstack UI E2E #43733088860](https://github.com/jeon950523/factoryflow/actions/runs/43733088860) **SUCCESS**: 실제 Spring Boot/MySQL/Chrome 3/3. 첫 실행은 컨테이너 준비 중 `cancelled` 상태였으나 **동일 실행의 job 재실행**에 성공하여 최종 conclusion success를 확인했다. 취소를 테스트 실패로 간주하지 않는다.
- [커밋 상태](https://github.com/jeon950523/factoryflow/commit/8cb86179294ca702b5d0ad3154b3efca46c3052b): Vercel `failure`, 메시지 `Deployment rate limited — retry in 24 hours.`. **배포가 완료되지 않았으므로 공개 `factoryflow.kro.kr`에서 TASK-020 UI가 보인다고 주장하지 않는다.** 차단 해제 후 재배포 및 실제 공개 Chrome 확인 필요.
- 상세 원인/대응: [TS-010](../TROUBLESHOOTING.md). 특정 요금제·쿼터 소비량·자동 재시도 여부는 커밋 상태만으로 판정 불가.
- 리팩터링 후속: 운영 서비스에서 requestKey 기반 서버 상태 조회 API를 도입한다면 만료/복구/다중 탭 정책을 함께 설계. 현재 세션 저장은 같은 탭의 안전 재시도만 보조한다.
- 문서만 후속 갱신한 PR은 GitHub Actions의 `frontend/**`/`backend/**` 경로 조건상 새 CI가 실행되지 않을 수 있다. 신규 기능 테스트 결과와 문서 변경 확인을 혼용하지 않는다.
