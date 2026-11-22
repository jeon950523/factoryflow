# ADR-014 — 다중 탭 입고·출고 쓰기 의도 공유와 Web Locks

상태: **선택 (2026-11-23), PR·main CI/실DB 검증 완료**

## 배경
TASK-022까지는 `sessionStorage`에 POST 전 key/payload를 기록하지만, 같은 브라우저의 다른 탭은 같은 요청을 공유하지 않아 서로 다른 키로 같은 입고·출고를 중복 생성할 수 있다.

## 결정
- 동종 물리 재고 작업(입고/출고)마다 미확인 요청을 최대 하나씩 공유하고, owner(로그인 사용자명)와 **API 필드로만 한정된** key/payload를 `localStorage`에 보관.
- `navigator.locks` 배타 잠금으로 claim/release **저장 연산만** 직렬화. 요청 전 shared + tab 기록의 성공을 확인해야만 POST.
- 기존 `sessionStorage` 저장을 유지해 TASK-022의 같은 탭 복원/기존 기록 마이그레이션 지원.
- 다른 key, 다른 owner, 손상된 shared record, Web Locks 부재·Storage 쓰기 실패 시 서버 호출을 진행하지 않음.
- 저장소 event는 화면 안내/복원 갱신만 수행. 완료 여부 판정은 서버 200/201 및 정합성 검증 기준.

## 검토한 대안
1. `localStorage` 선조회 후 쓰기: **반려** — 락 없이 동시에 읽으면 두 탭 모두 미확인 없음으로 판단할 수 있다.
2. `IndexedDB` 전용 intent store: 트랜잭션으로 원자적 점유 가능하지만 새로운 DB 스키마/마이그레이션/비동기 초기화 필요. 후속 요구에 적합할 수 있다.
3. 서버 requestKey 조회 API: 다중 브라우저/기기의 중앙 조회에 가장 강하지만 백엔드 인증/계정 소유권·키 만료·데이터 보존 정책을 새로 요구한다.

## 한계 및 정책
- 브라우저 로컬 공유는 서버 분산 락이 아니며 OS 충돌·저장소 삭제/복원 실패를 보증하지 않는다. 공개 Vercel은 IndexedDB 데모, 실제 MySQL 보호는 서버 UNIQUE+트랜잭션.
- 미확인 요청을 임의 삭제해 새 key를 발급하는 UI는 제공하지 않는다. 다른 로그인 owner의 데이터는 UI에서 노출하지 않지만 공유 PC의 개발자 도구에 저장소가 접근 가능하므로 보안 경계로 취급하지 않는다.
- 브라우저 잠금이 지원되지 않으면 **안전성 우선으로 입출고 전송 중단**. 호환성 요구가 생기면 서버측 조회 또는 IndexedDB 트랜잭션 재설계.

## 사후 검증
- [PR #35](https://github.com/jeon950523/factoryflow/pull/35) HEAD `328aecf` / main `b43beca`, [Frontend CI](https://github.com/jeon950523/factoryflow/actions/runs/45053092176) Node46/Chrome27/IndexedDB30 및 [실 MySQL](https://github.com/jeon950523/factoryflow/actions/runs/45053092151) 3/3 성공. Vercel production 및 [공개 Chrome 기존 8/8](https://github.com/jeon950523/factoryflow/actions/runs/43958402614) 재검증 완료.
- Web Locks가 브라우저 데모 초기화·외부 저장소 삭제 자체를 막는 것은 아님. 남은 충돌 정책은 다음 TASK 검토.
