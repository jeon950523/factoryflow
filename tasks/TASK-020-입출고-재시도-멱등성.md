# TASK-020 — 입고·출고 응답 유실 재시도 및 멱등성 안전성

> 착수: 2026-11-16 · 기준 main `2b6cde9b2cb724bec512bd5ea4851a260060b4c4` · 상태: 구현/실행 검증 대기

## 문제
`InventoryView.vue#submitReceipt`와 `ShipmentsView.vue#submit`은 POST 네트워크 오류가 나면 동일 요청 키를 남기는 장점이 있다. 그러나 입력 필드를 편집할 수 있고 닫기→새 모달 열기, 브라우저 새로고침→재진입 시 새 키가 발급된다. 서버가 실제 DB 트랜잭션을 커밋한 뒤 응답만 유실되면 **새 키로 같은 재고 변화를 다시 실행**할 위험이 있다. 같은 키로 다른 payload를 전송하면 서버는 `IDEMPOTENCY_KEY_CONFLICT`를 반환한다.

## 구현
1. **불확실한 결과만 보수적으로 처리:** 네트워크 실패(status 0), 5xx, 유효하지 않은 JSON 응답이면 확정 성공/실패를 단정하지 않고 제출 payload를 스냅샷으로 보관한다. 명확한 4xx(재고부족/유효성/권한)는 그 자체를 중복 성공이라고 표시하지 않는다.
2. **재시도 불변성:** pending 상태에서는 품목/LOT/수량을 변경 못하고 동일 key+payload로만 POST한다. 서버의 200 replay/201 created를 성공으로 인정한다.
3. **브라우저 세션 보호:** pending payload는 `sessionStorage`에 저장하여 같은 탭의 모달 재개/새로고침에서도 유지하고, 성공 응답 후에만 삭제한다. 다른 탭·브라우저/세션·저장소 차단 환경에는 무한 보증을 주장하지 않는다.
4. **실패 안내:** 사용자가 중복 처리 위험을 이해하도록 '처리 여부 미확인·새 요청 금지·동일 요청 재시도'를 명시하고, 모달 재개 시 이전 요청임을 표시한다. 수동 폐기·신규 요청 전환은 이번 범위에서 제공하지 않는다(사실 확인 없는 중복 키 발급 방지).
5. **기능 계약:** Spring Boot `StockReceiptService/ShipmentService`의 payload hash·유니크 제약·락·원장/FIFO는 유지한다. 데모 엔진도 현재 멱등성 로직을 재사용한다.

## 검증
- 브라우저 Chrome fixture에서 입고/출고 POST가 201로 서버 처리되었으나 응답만 유실된 상황을 모의하고, 화면·네비게이션·새로고침 뒤 동일 키와 동일 본문으로 재시도하는지 확인.
- 실제 Spring Boot/MySQL Chrome에서 `route.fetch()`로 POST를 서버에 전달한 뒤 `route.abort()`로 응답 유실을 재현한다. 이후 200 replay → 입고 현재고/원장 1회, 출고 LOT 차감/원장 1회 검증한다.
- 명백한 409 재고부족에 대한 후속 동작은 과잉 잠금하지 않으며 결과 미확인과 구분한다.
- Node·Vite·Chrome fixture·IndexedDB·실 MySQL CI를 최종 PR HEAD와 병합 main에서 검증. 실패 로그·수정 이유는 [TS](../docs/TROUBLESHOOTING.md)와 [보고서](../docs/task-reports/TASK-020-쓰기재시도-멱등성-검수.md)에 남긴다.

## 비범위/남은 위험
- 서버 API/DB 스키마 추가, 모든 과거 요청을 requestKey로 조회하는 새 검색 API, IndexedDB와 실 MySQL 상태를 혼동하는 안내, 작업자의 수동 '미확인 요청 폐기'는 보류.
- 사용자가 시크릿 모드를 닫거나 다른 탭에서 새 요청을 만들거나 저장소를 삭제한 경우는 별도 해결책이 필요하다. 다중 탭·운영 복구를 보증하지 않는다.

## 사전 시니어 검수 후 보완 (2026-11-16)

- 최초 상태 0/5xx/INVALID_RESPONSE는 처리 결과가 미확인: 동일 key/payload만 재전송.
- 이후 같은 요청의 재전송이 400/404 또는 지정된 409 재고 부족 등 **확실한 업무 거절**로 귀결되면, 영구적인 폼 잠금을 풀고 수정한 작업은 **새 요청 키**로 보낸다.
- 401/403/IDEMPOTENCY_KEY_CONFLICT/5xx에는 확정 미처리 판단을 하지 않는다. pending 유지.
- 실DB Chrome에 '서버까지 전송되지 않은 네트워크 오류 → 같은 요청 재시도 409 → 수정한 새 요청의 키 확인' 시나리오를 추가한다.

## PR 기능 코드 검증 근거

- [PR #28](https://github.com/jeon950523/factoryflow/pull/28) 코드 검증 커밋 `278c60fec74525ca029e09635f2ab052f2d67cc8`.
- [Frontend #43732644025](https://github.com/jeon950523/factoryflow/actions/runs/43732644025): Node 42/42, Chrome UI fixture 21/21, IndexedDB 30/30, 빌드 SUCCESS.
- [실제 Spring Boot/MySQL #43732644015](https://github.com/jeon950523/factoryflow/actions/runs/43732644015): E2E 3/3 SUCCESS. 커밋 후 응답 유실 → HTTP 200 replay, 원장 1회, 확정 409 잠금 해제.
- 후속 문서 커밋/병합 `main` 상태는 반드시 별도 CI로 판단하며, Vercel 실제 화면 조작·다중 탭/브라우저 세션 전반의 재시도 보장은 별도.
