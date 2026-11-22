# TASK-023 — 입고·출고 다중 탭 미확인 요청 충돌 차단 및 복구

> 착수: 2026-11-23 · 기준 main `e0858f4d1f26e437d91eae99e2061b09cee1b11f` · 상태: **코드/테스트 작성, GitHub CI 검증 대기**

## 1. 사전 코드 검수
- `frontend/src/lib/pendingWrite.js`는 같은 탭의 `sessionStorage`에서만 요청을 읽는다. 다른 탭은 서로 다른 requestKey를 발급해, 사용자가 같은 의도로 입고/출고를 다시 실행할 수 있다.
- TASK-022는 POST **전** 저장 및 실제 MySQL 키 재생을 검증했다. 그러나 탭 A 미확인 동안 탭 B가 별도의 쓰기를 시작하는 것은 막지 못했다.
- 브라우저 IndexedDB 데모의 업무 상태 변경은 `store.js#requestDemo`에서 단일 readwrite 트랜잭션으로 직렬화된다. 이는 **서로 다른 key로 동일 명령을 중복 발행하는 문제를 해결하지 않는다.**
- 실 서버는 MySQL UNIQUE/requestKey로 **같은 키**를 방어하며 **다른 키의 재요청**까지 알아서 합치지 않는다.

## 2. 구현 범위
1. 원자재 입고/완제품 출고 미확인 **요청 의도**를 같은 출처 브라우저 탭들이 공유하도록, `localStorage`에 owner(로그인 사용자명) + 표준화된 key/payload를 보관한다.
2. `navigator.locks.request` 배타 잠금으로 `localStorage`의 확인→쓰기·삭제를 직렬화한다. 잠금/스토리지 미지원·저장 실패·기존 다른 미확인 명령·손상 기록 발견 시 **POST 0회**로 보수적으로 차단한다. 락 없는 localStorage '선확인→후쓰기'는 경쟁 조건 때문에 금지.
3. 기존 `sessionStorage`는 TASK-022 호환으로 유지, 다른 탭/새로고침 시 동일 사용자 소유의 shared 요청 복원 및 동일 key/body 재시도 허용.
4. 확정 성공(200/201 검증) 또는 해당 명령 확정 거절(기존 400/404/지정 409)에서만 **일치하는 공유 요청** 삭제. 401/403/불명확 409/0/5xx/INVALID_RESPONSE는 복구 가능 상태 보존.
5. 탭 간 `storage` 이벤트는 사용자 안내/화면 새로고침 **힌트**일 뿐 잠금 보장 수단이 아니다. 열린 다른 탭의 입력 폼을 갑자기 덮어쓰지 않는다.

## 3. 비범위
- 로그인 사용자 간 개인정보 공개/요청 이전, 전혀 다른 브라우저·기기간 동기화, 서버 requestKey 조회 API, 브라우저 저장소 수동 삭제/손상 복원.
- 서로 다른 사용자 의도의 **확정 완료 이후** 같은 내용으로 재입고한 작업은 자동 중복 판정하지 않는다.
- 실제 운영 MySQL 공개, 백엔드 Java·트랜잭션·Flyway·BOM/LOT/FIFO 변경, 유료 인프라/Redis.
- Web Locks 미지원 브라우저에서는 안전하게 POST를 차단하며 기능 동등성을 주장하지 않는다. 원래 탭의 `sessionStorage`만으로 다중 탭 보호를 보증하지 않는다.

## 4. 검증·수용 조건
- Node: 탭 A key claim → B의 다른 key 거절 → 같은 key 복구/재생 → 일치하는 key만 release → 새 key 가능. 타 로그인 owner 복원 제한·Web Locks 부재/저장 실패 fail-closed.
- Chrome fixture(2탭 × 입고/출고): A 미확인 요청 보존, B의 다른 key POST 0회, B 재접속 후 동일 key 복원, 성공 후 shared key 제거. 기존 4개 TASK-022 preflight 테스트 유지.
- 실제 Spring Boot/MySQL: A 서버 commit 후 응답 abort → B 별도 탭에서 같은 key/body로 HTTP 200 replay → A에서도 동일 키 재생 → LOT/원장 1회·FIFO 재고 정합성 유지. 실제 중복 차감 없는지 확인.
- 기존 Node/Chrome fixture/IndexedDB 데모 + 빌드, PR/main CI/Vercel 배포/공개 Chrome 분리 검증. **작성과 실행 통과를 혼동하지 않는다.**

## 5. 릴리스/회귀 정책
- 한 목적 브랜치 → PR 검증 후 main 병합, 문서에 각각 SHA/CI 증거 반영.
- 데모 사이트는 IndexedDB로만 운영, MySQL E2E는 격리된 CI 환경.
- 다중 탭 대기 중 사용자에게 요청을 삭제/포기하는 UI를 자동 제공하지 않는다. 임의 폐기 후 새 키 전송은 이중 재고 위험.
