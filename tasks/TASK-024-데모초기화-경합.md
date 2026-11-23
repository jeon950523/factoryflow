# TASK-024 — 데모 초기화와 다중 탭 미확인 입출고 충돌 보호

> 착수 2026-11-24 · 기준 main `41eac9d5e71e2d266d32cf1e344446bf388f53b1` · 상태 **PR/main 기능 CI·실DB·Vercel 성공, 공개 Chrome 신규 9건 검사 대기**

## 1. 사전 검수로 확인한 위험
- `frontend/src/lib/demo/store.js#resetDemoData`는 현재 `IndexedDB` 스냅샷을 바로 샘플 상태로 교체한다. TASK-023에서 추가한 브라우저 공유 `localStorage`의 미확인 물리 재고 요청은 검사하지 않는다.
- 다른 탭에서 서버/데모 응답을 확정한 후 공유 intent가 지워져도 원래 탭 `sessionStorage`에는 기록이 남을 수 있다. 초기화가 그 사이에 성공하면 기존 requestKey로 재전송할 때 초기화된 데모 데이터에 새로운 입고·출고가 적용될 위험이 있다.
- IndexedDB `readwrite` 트랜잭션은 데모 상태 변경 자체를 직렬화하지만, **브라우저 공유 write-ahead intent와 데이터 초기화의 순서를 보장하지 않는다.**

## 2. 요구사항/불변조건
1. 초기화는 입고/출고 Web Locks를 **항상 동일 순서(receipt → shipment)** 로 획득하고, 해당 잠금이 해제되기 전까지 IndexedDB 재설정 트랜잭션 완료/실패까지 대기해야 한다.
2. shared receipt/shipment 원문 값이 하나라도 존재하면 **손상 기록/다른 owner 포함** 초기화 금지. 해당 탭의 유효한 `sessionStorage` 미확인 요청도 차단. 저장소 접근 차단/Web Locks 미지원도 fail-closed.
3. reset generation(세대값)을 **IndexedDB 변경 전에** `localStorage`에 기록/읽기 확인. 다른 탭에서 reset 중 대기한 옛 요청/미처리 화면의 옛 payload는 generation 불일치 시 POST하지 않는다.
4. 기존 `sessionStorage` 레거시 복구 키/본문 형태를 보존하고, 각 탭에 epoch를 별도 기록한다. 리셋 이전 탭의 복구키는 이후 자동 재시도 불가. 새 키의 정상 신규 요청은 허용.
5. UI 중복 초기화 클릭 방지, 거절 이유 알림, 기존 업무 시연 동선/성공 알림 유지.

## 3. 비범위
- 운영 Java/MySQL DB 초기화, 서버측 키 조회, 별도 계정·기기 간 동기화, Web Locks 미지원 브라우저 폴백, Redis, 기존 업무의 자동 백업·복원.
- 사용자가 개발자도구로 localStorage/IndexedDB를 임의 변경하거나 삭제하는 경우에는 안전 보장 불가. OS 강제 종료 시 두 저장소 사이 내구성은 보장하지 않음.
- 이미 완료된 다른 정상 작업을 내용만 같다고 중복 명령으로 분류하지 않음.

## 4. 테스트 계획/완료 기준
- Node 단위: 공유/탭 미확인 receipt/shipment일 때 reset 콜백 **0회**, 저장소 오류/잠금 미지원에서도 0회, 성공 시 세대값 갱신, reset이 먼저 Web Lock 획득하면 queued 이전 세대 claim 거절, 이전 세대 session 키는 재사용 불가·새 키는 허용.
- 실제 IndexedDB Chrome: 두 탭에서 미확인 기록을 가진 상태의 초기화 거절과 데이터 불변, 기록이 사라진 후 정상 초기화, 과거 키 복구 배제, 열려 있던 과거 입력 폼 제출 거절.
- 기존 프론트 Node/Chrome fixture/IndexedDB/실DB E2E + 빌드 회귀. Java/DB 변경 없음이므로 기능 검증 결과는 분리해 보고.
- PR CI 통과 전에는 완료로 기록하지 않음. PR 병합 후 main CI·배포 SHA와 공개 Chrome 별도 확인.

## 5. 저장·잠금 설계
`App.vue#resetDemo → demo/store.js#resetDemoData → pendingWrite.js#withDemoResetGuard(receipt lock→shipment lock→shared/tab 기록 검사→epoch 등록→IndexedDB readwrite reset commit) → UI reload`.
기존 일반 입고·출고: `Vue form epoch capture → claimPendingWrite`에서 lock을 취득한 뒤 예상 epoch와 현재 epoch 비교 → 다르면 `RESET_CHANGED`로 전송 금지 → 정상일 때만 기존 key/body 기록 및 POST.

## 6. 작업 기록
[시니어 검수 보고서](../docs/task-reports/TASK-024-데모초기화-경합-검수.md) · [ADR-015](../docs/adr/ADR-015-데모초기화-WebLocks-세대값.md) · [핸드오프](../docs/PROJECT_HANDOFF.md).

## 7. 후속
실제 브라우저 프로세스 종료·스토리지 삭제·구버전 탭 혼용은 후속 운영 정책으로 검토하며, 이번 작업에서 완전한 분산 트랜잭션을 구현했다고 주장하지 않는다.

## 8. 실제 CI/운영 검증 결과 (2026-11-24)

- [기능 PR #37](https://github.com/jeon950523/factoryflow/pull/37) HEAD `7de69578595d039cf98224478e9f29fb2ce5640e` → main `fe0eacbd756e3ea05c62cd45d72723e96e8ccbbd`.
- [PR Frontend #45240068073](https://github.com/jeon950523/factoryflow/actions/runs/45240068073) 및 [main Frontend #45240451984](https://github.com/jeon950523/factoryflow/actions/runs/45240451984): Node **49/49**, Chrome fixture **27/27**, IndexedDB Chrome **33/33** (TASK-024 신규 2탭 3건 포함), Vite build 성공.
- [PR 실제 MySQL #45240067965](https://github.com/jeon950523/factoryflow/actions/runs/45240067965) 및 [main MySQL #45240452073](https://github.com/jeon950523/factoryflow/actions/runs/45240452073): Spring Boot·Java21·MySQL8.4·Chrome **3/3** 성공. 서버 코드 미수정 회귀검증.
- Vercel main `fe0eacb` production SUCCESS. 공개 도메인 신규 1건 포함 Chrome 총 9건 실행 결과 대기, QA 통과 전 미확정.
- 실제 Windows DPI/키보드, 프로세스 강제 종료, 구버전 JS 탭 혼용은 미검증.
