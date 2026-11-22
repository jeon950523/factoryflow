# TASK-022 — 입고·출고 POST 사전 기록 및 새로고침 복구 강화

> 착수: 2026-11-23 · 기준 main `2d064047b2471e411f63fcdfdc8ccd70a185c3e0` · 상태: **PR·main CI/실DB·Vercel·공개 QA 검증 완료**

## 사전 시니어 코드 검수 (main)
- `InventoryView.vue#submitReceipt`와 `ShipmentsView.vue#submit`은 전송 후 **오류가 발생해야만** `savePendingWrite()`를 호출한다. 서버가 커밋하고 브라우저가 응답을 기다리는 동안 새로고침하면 복구 키가 사라질 수 있다.
- 브라우저 저장소가 거절해도 기존 코드는 먼저 POST를 보냈으며, 일부 401/403·정의되지 않은 409 등의 오류는 미확인 요청을 잠그지 않았다.
- 서버 `StockReceiptService`/`ShipmentService`는 별도 트랜잭션·UNIQUE·같은 키 재생을 이미 구현했으므로, **백엔드 신규 멱등 API/스키마 변경은 하지 않는다**.

## 작업 범위
1. 입고·출고의 payload(키/품목/LOT/수량)를 동기적으로 `sessionStorage`에 저장하고 **읽기 확인까지 성공해야만** POST 전송.
2. 저장 실패 또는 기존 미확인 payload와 다른 값 충돌 시 **아무 POST도 보내지 않음**, 안전하게 사용자에게 안내.
3. 성공으로 확인된 200/201 응답에서만 저장 기록 삭제. 명백하게 해당 명령이 거절된 400/404/지정된 409는 잠금 해제 후 **새 키** 발급. INVALID_RESPONSE, 0/5xx, 401/403, 불명확한 409는 이전 키/본문 유지.
4. 기존 브라우저 동선 및 server/demo 계약 유지. 입고·출고 중복 UI 코드의 과도한 추상화는 보류.

## 검증 계획 / 수용 기준
- Node 단위: 동일 payload 재기록 허용, 다른 pending 덮어쓰기 금지, 예외/무반영 저장 거부.
- Chrome fixture: 실제 POST를 관측한 시점에 이미 동일 key/body가 세션에 존재하며, 성공 후에만 삭제됨. 저장 거절 때 POST 0건.
- 실제 Spring Boot/MySQL Chrome: 라우트가 HTTP 요청을 받은 시점에 sessionStorage에 동일 본문이 존재하는지 검증하고, 커밋→응답 abort→재전송에 200 replay / LOT·원장 1회 유지.
- 기존 Node/Chrome fixture/IndexedDB/실DB E2E 회귀 성공 및 배포 SHA별 상태와 공개 Chrome 검증을 별개로 기록.

## 비범위 및 한계
- `sessionStorage`는 **같은 탭 세션에서의 새로고침 복구**에 한정. 탭 종료·저장소 삭제·다른 탭/장치·브라우저 강제 종료 후 복구는 보장하지 않으며 별도 TASK 설계.
- 브라우저가 성공 표시 직후 저장소 삭제에 실패하면 재시도 화면이 재등장할 수 있으나 동일 키 재생은 서버에서 중복 차감하지 않는다.
- Vercel 공개 페이지는 IndexedDB 데모이며 **실 운영 MySQL이 아니다**. 배포 비용 상승·Redis·로그인 정책 변경 없음.

## 실제 완료 근거 (2026-11-23)

- [PR #33](https://github.com/jeon950523/factoryflow/pull/33) 병합 `main 1c03e94`; 기능 코드 `b75615f`.
- [PR Frontend #45044548625](https://github.com/jeon950523/factoryflow/actions/runs/45044548625) 및 [main #45044738329](https://github.com/jeon950523/factoryflow/actions/runs/45044738329): Node 44/44, Chrome fixture 25/25, IndexedDB Chrome 30/30, 프로덕션 빌드 성공.
- [PR 실 MySQL #45044548595](https://github.com/jeon950523/factoryflow/actions/runs/45044548595) 및 [main #45044738273](https://github.com/jeon950523/factoryflow/actions/runs/45044738273): 기존 실DB E2E 3/3, POST 시점 사전 저장 확인 및 응답 유실 재생 검증 성공.
- Vercel `1c03e94` production SUCCESS, 이후 [기존 공개 Chrome 8/8 재실행](https://github.com/jeon950523/factoryflow/actions/runs/43958402614) 성공. 새 preflight 동작은 별도 fixture/실DB 테스트로 검증했고 실제 공개 UI는 IndexedDB 데모임.
- 남은 한계: 탭 종료/서버측 키 조회/다중 탭 공유, 운영 MySQL 배포, 실제 Windows 접근성 확인은 이번 TASK 범위 밖.
