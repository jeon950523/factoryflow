# TASK-024 — 데모 초기화·다중 탭 미확인 요청 경합 시니어 검수

> 2026-11-24 · 시작 기준 `main 41eac9d5e71e2d266d32cf1e344446bf388f53b1` · **정적 코드 검수 및 테스트 작성, 실행 근거 확인 전**
> [작업지시서](../../tasks/TASK-024-데모초기화-경합.md) · [ADR-015](../adr/ADR-015-데모초기화-WebLocks-세대값.md)

## 1. 목적/배경
TASK-023 다중 탭 요청 자체의 claim/release는 보호하지만 `resetDemoData()`가 공유 미확인 요청을 고려하지 않고 IndexedDB를 다시 seed 상태로 쓰는 결함 경로를 코드 검수로 확인했다. 타 탭에서 공유 기록이 해제된 뒤 원래 탭에 남은 sessionStorage 요청의 재전송도 초기화 세대와 구분할 방법이 없었다.

## 2. 구현 범위와 상태
| 변경 대상 | 설계 의도 | 상태 |
|---|---|---|
| `frontend/src/lib/pendingWrite.js` | reset 전용 양쪽 Web Locks, 기존 미확인 raw 검사, reset epoch 저장·검증, 전송 전 generation mismatch 차단 | 구현안, 실행 대기 |
| `frontend/src/lib/demo/store.js` | IndexedDB readwrite reset 전체를 잠금 범위에 포함 | 구현안, 실행 대기 |
| `frontend/src/App.vue` | 중복 초기화 클릭 차단, 오류 메시지·종료 시 상태 복원 | 구현안, 실행 대기 |
| `InventoryView.vue` / `ShipmentsView.vue` | 폼 생성 당시 epoch 보존 및 submit 시 일치 확인 | 구현안, 실행 대기 |
| `pendingWrite.test.js` | 미확인 2종/저장 실패/락 미지원/queued pre-reset claim/old key 복구 검증 | 테스트 작성, 실행 대기 |
| `tests-demo/browser-demo.spec.js` | 실제 IndexedDB + 두 탭 거절/승인/구 세대 폼 거절 3건 | 테스트 작성, 실행 대기 |
| TASK/ADR/핸드오프·학습 | 결정/예외·대안·검증 기준 누적 | 문서 작성 |

## 3. 전체 호출 흐름
`App.vue#resetDemo → demo/store.js#resetDemoData → pendingWrite.js#withDemoResetGuard → navigator.locks.request(receipt) → navigator.locks.request(shipment) → localStorage/shared presence 및 sessionStorage 검사 → 새 epoch 동기 저장/읽기 확인 → IndexedDB transaction('snapshot','readwrite') → tx.oncomplete → 화면 갱신`.

기존 입고/출고: `openReceipt/openCreate`에서 현재 epoch 기록 → `submit`에서 `claimPendingWrite(kind,body,owner,expectedEpoch)` → Web Lock 내부 epoch 일치 검사 → 같을 때만 기존 local/tab intent 기록 후 POST. 예상값 불일치 시 `RESET_CHANGED` (HTTP POST 0회).

## 4. 코드 핵심 3곳
```js
return navigator.locks.request(lockName('receipt'), { mode: 'exclusive' },
  () => navigator.locks.request(lockName('shipment'), { mode: 'exclusive' }, async () => {
    // unresolved records -> reject, epoch -> persist BEFORE IndexedDB reset
    return resetTransaction()
  }))
```
같은 종류의 claim/release는 자기 락 하나만 잡고, 초기화는 receipt→shipment 고정 순서를 적용한다. 잠금은 실제 reset commit까지 유지한다.

```js
if (epoch !== expectedEpoch) return { ok: false, reason: 'RESET_CHANGED' }
```
사용자가 초기화 전 열어둔 오래된 폼이나 락 대기 중 늦게 제출되는 명령이 초기화된 데모로 적용되는 것을 차단한다.

```js
return withDemoResetGuard(async () => {
  const db = await openDatabase()
  // readwrite transaction commits before the lock is released
})
```
업무 스냅샷은 기존 indexedDB 계약을 유지하고, 영속 데이터 리셋 외에 API·서버 DB 호출을 늘리지 않는다.

## 5. 예외·경계·동시성
- shared raw가 하나라도 있으면 다른 owner/손상 JSON 포함 reset 거부. 현재 탭의 미확인 key도 거부.
- 저장소 접근 거절/Web Locks 부재/epoch 저장 읽기 검증 실패 시 reset 거절. 버튼은 finally에서 다시 활성화. 두 번 클릭은 `resettingDemo`로 방지.
- reset을 먼저 lock으로 획득한 경우 뒤에 대기하던 초기화 전 claim을 epoch mismatch로 거절. claim이 먼저 record를 남기면 reset이 거절.
- 다른 탭에서 공유 intent가 해제된 뒤에도 원래 탭에 남는 기록은 과거 epoch로 판정되어 복구 UI에서 제외되며, 동일 key 재사용이 거절된다.
- epoch 저장 후 IndexedDB reset 실패는 이전 키를 보수적으로 무효화할 수 있다(보장보다 안전 우선). storage/DB 사이 원자적 2PC를 주장하지 않는다.
- 사용자 보안: localStorage 값은 민감정보/인증 저장소가 아니며 개발자도구/XSS에 노출 가능하다. 요청에는 이미 제한된 key/품목/LOT/수량만 사용, 서버 세션 CSRF/권한 규칙 그대로.

## 6. 장점/대안
Web Locks+epoch는 기존 브라우저 지원·저장소 계약을 재사용해 최소 변경으로 초기화 경합을 막는다. 단점은 Web Locks 없는 브라우저에서 초기화가 중단되고 저장소 두 종류간 ACID가 없다는 것이다. IndexedDB intent store 통합은 더 강하지만 마이그레이션과 보안 정책이 필요해 별도 TASK 후보로 보류한다.

## 7. 실제 검증
- **정적 검수만 완료.** CI 로그와 숫자/스크린샷/실DB/공개 배포는 아직 확인되지 않음.
- 예상: GitHub Actions Node22 `npm test`; `npm run build`; Chrome fixture `npx playwright test --config playwright.config.js`; 실 IndexedDB `npx playwright test --config playwright.demo.config.js`; Java21 Spring Boot+MySQL8.4 `npx playwright test --config playwright.live.config.js`.
- 성공/실패를 PR SHA별로 기록, 병합 main/운영 SHA/공개 Chrome 결과 분리. 직접 Windows DPI/키보드 확인은 범위 밖.

## 8. 시니어 검수
- 기능/데이터 정합성: 최초 정적 검수상 초기화 경합 차단이 필요하며 실제 Chrome 2탭/DB 테스트 검증 전 완료 판정 금지.
- 잠금 순서: receipt→shipment 고정, claim은 하나만 잡아 lock inversion 없음. IndexedDB tx 완료 대기 후 해제.
- 안정성: 예외에서 UI reset flag는 finally 복원. epoch mismatch는 HTTP 쓰기 전 확인.
- 유지보수: reset과 intent 책임을 pendingWrite 모듈로 묶되, IndexedDB 리셋 자체는 store.js에서만 실행. 서로 다른 플랫폼 제어가 과도하게 결합되는지 테스트에서 확인.
- 보안: 동일 출처 사용자/로그인 분리만 보조, 서버 인증 대체 아님.

## 9. 기능/효율 리팩터링 우선순위
| 분류 | 판단·근거 |
|---|---|
| 즉시 수정 | 데모 초기화와 입출고 intent의 상호 잠금 및 리셋 epoch 검증 (데이터 손상 위험) |
| 다음 TASK 전 수정 검토 | 오래된 JS 탭 혼용/수동 저장소 삭제 이후 복구 정책. 실제 위험 시에만 별도 안전 기능 |
| 2차 개선 | pendingWrite 업무 흐름 공통 composable 또는 IndexedDB intent store 통합 (테스트/마이그레이션 비용) |
| 현 시점 불필요 | Redis, 서버 캐시, WebSocket, 분산 락 (Vercel 데모/CI MySQL 분리 구조, 병목 미측정) |

## 10. 다음 TASK 및 미검증
이번 TASK는 데모 리셋 경합까지만. 실제 서버 리셋/백업, 다기기, 크래시 일관성, Windows 브라우저 수동 QA는 별도. 최초 runtime 실패 발생 시 증상/로그/원인/수정 검증을 TROUBLESHOOTING에 기록.

## 11. 20~40초 설명
> 브라우저 데모에서는 입고·출고가 미확인 상태인데 사용자가 다른 탭에서 샘플 데이터를 초기화할 수 있었습니다. Web Locks로 두 작업의 미확인 기록을 확인하는 동안 IndexedDB 초기화를 직렬화했고, 초기화 전에 세대값을 먼저 기록했습니다. 다른 탭의 오래된 요청은 세대가 다르면 전송을 거절해 새로 초기화한 재고가 다시 변경되는 상황을 방지합니다.

이해 질문: 1) IndexedDB 트랜잭션만으로 localStorage의 미확인 요청을 보호할 수 없는 이유? 2) epoch를 IDB reset 이후가 아닌 이전에 써야 하는 이유? 3) 데모 초기화와 실제 운영 DB 롤백의 차이?

## 12. 증거 추적
검증 후 [PR/CI/배포 링크]와 기능별 최종 판정을 추가할 것. 본 문서의 코드는 작성 당시 정적 검토 대상이며 실제 CI 실행 완료를 의미하지 않음.
