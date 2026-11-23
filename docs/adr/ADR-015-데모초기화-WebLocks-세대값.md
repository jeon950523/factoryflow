# ADR-015 — 데모 초기화 Web Locks 잠금 순서와 세대값 도입

> 선택안: 2026-11-24 · 코드 구현/CI 검증 대기 · 선행 [ADR-013](ADR-013-프론트데모-실백엔드분리.md), [ADR-014](ADR-014-다중탭-쓰기의도-WebLocks.md)

## 배경
IndexedDB snapshot 초기화는 원자적이지만, TASK-023의 `localStorage` write-ahead intent는 별도 저장소에 존재한다. 공유 미확인 요청이 남은 초기화는 결과/원장 판단을 망가뜨린다. 공유 intent를 다른 탭이 정상 삭제한 후에도 기존 탭 세션에 요청 키가 남을 수 있어, 초기화 후 옛 키 재사용이라는 두 번째 위험이 존재한다.

## 선택
1. `resetDemoData`는 `withDemoResetGuard`를 사용해 기존 receipt Web Lock → shipment Web Lock 순서대로 획득하고 IndexedDB reset **트랜잭션 완료까지 유지**. claim/release는 각자 자기 kind 락만 쓰므로 역방향 중첩 락이 없어 교착을 피한다.
2. 락 아래에서 공유 기록 raw 존재 여부(손상/타 owner 포함)와 현재 탭의 유효 pending 여부를 확인한다. 원문 미확인이 있으면 초기화 차단하고 사용자가 원래 명령 결과를 먼저 확인하도록 한다.
3. DB 변경 이전 `localStorage`에 랜덤 reset epoch를 동기 기록/읽기 확인한다. 각 탭은 현재 폼의 epoch를 저장하며, 제출 시 Web Lock 내부에서 예상/실제 epoch를 비교해 옛 요청을 차단한다. 세션 복구 기록의 epoch는 별도 키로 저장하여 기존 key/body 계약 유지.
4. 읽기/잠금/쓰기 실패는 **초기화 거절**. 별도 서버 API/스키마 변경은 불필요.

## 대안 검토
- 기존 IndexedDB reset만 수행: 단순하지만 공유 intent와 원자적 순서를 보장하지 못하므로 반려.
- 사용자 확인 대화상자에서 localStorage만 읽고 reset: 다른 탭이 확인/초기화 사이에 claim할 수 있으므로 반려.
- reset 시 모든 pending 키 자동 삭제: 서버 commit이 미확인인 상황에서 새 key를 만들 위험이 있어 반려.
- IndexedDB에 intent/epoch를 통합: 단일 트랜잭션 일관성이 더 강하나 레거시 sessionStorage 마이그레이션·별도 DB 스키마·이벤트 정책이 추가됨. **2차 고도화 후보**.
- 서버측 요청 조회/복구: 다른 브라우저/기기에 유용하지만 실제 Spring 서비스를 공개하기 전 범위 밖.

## 한계와 트레이드오프
- **저장소 간 완전한 ACID 트랜잭션은 아니다.** reset epoch 기록 후 DB reset이 실패하면 epoch만 변할 수 있다(보수적 무효화, 잠정 지연). 반대로 DB commit 후 epoch를 적으면 오래된 키가 재전송될 수 있으므로 안전 우선으로 epoch를 먼저 기록한다.
- 같은 origin·동일 브라우저의 **협조적 Web Locks 사용 탭**만 통제한다. 옛 배포된 JS 탭, 외부 localStorage 수정/삭제, 다른 프로필·기기, OS 크래시, 브라우저 저장소 손상까지 보장하지 않는다.
- Web Locks 미지원 시 **초기화 중단**. 브라우저 데모는 MySQL 운영 서비스가 아니며 서버 DB/FIFO/트랜잭션은 변경하지 않는다.

## 검증
CI 통과 전에는 결과를 기재하지 않는다. PR Node/Chrome IndexedDB/실 MySQL 회귀·배포/공개 Chrome을 구분해 추가할 것.
