# TASK-023 — 다중 탭 입출고 쓰기 의도 공유·복구 시니어 검수

> 2026-11-23 · 기준 `main e0858f4d1f26e437d91eae99e2061b09cee1b11f` · **정적 검수/구현안 작성, 실행 증거 확인 전**
> [작업지시서](../../tasks/TASK-023-입출고-다중탭-복구.md) · [ADR-014](../adr/ADR-014-다중탭-쓰기의도-WebLocks.md)

## 1. 목적·배경과 문제
TASK-020/022는 탭 내 요청 키 보존과 전송 전 영속 기록을 개선했지만, 다른 탭이 독립된 키를 만들어 같은 물리 재고 작업을 중복 반영할 위험이 있었다. 실 MySQL의 유니크 key는 *같은 key*만 보호하므로 클라이언트의 동일 의도 복원 정책이 필요하다.

## 2. 구현 목록·정적 상태
| 파일 | 수정 목적 | 근거 | 상태 |
|---|---|---|---|
| `frontend/src/lib/pendingWrite.js` | Web Locks 배타 잠금, owner 분리, shared/local 두 기록 확인, 일치하는 요청만 삭제 | 타 탭의 다른 키 덮어쓰기 방지 | 코드 작성, 실행 미검증 |
| `InventoryView.vue` / `ShipmentsView.vue` | async claim/release, 공유 복원·경합 안내, 탭 storage event | 기존 사용자 동선 유지 | 코드 작성, 실행 미검증 |
| `pendingWrite.test.js` | 키 경합/동일 키 재생/owner 분리/Web Locks 미지원 | 단위 계약 | 작성, 실행 미검증 |
| `tests/write-preflight.spec.js` | 실제 두 탭의 서로 다른 POST 차단과 재접속 복원 | fixture Chrome | 작성, 실행 미검증 |
| `tests-live/real-manufacturing.spec.js` | 실제 DB commit 뒤 두 번째 탭 200 replay와 재고 불변식 | 실제 Spring Boot/MySQL | 작성, 실행 미검증 |

## 3. 호출 흐름·트랜잭션·책임
`Vue submit → 값 검증 → navigator.locks.request('factoryflow:stock-write:'+kind) → localStorage 기존 owner/key/body 원자적 검사 → 같은 탭 sessionStorage 기록 확인 → POST → Spring Boot Service(@Transactional writer) → MySQL UNIQUE·LOT/원장 → 검증된 200/201 → 동일 공유 key/body만 해제`.

- `navigator.locks`는 **같은 출처 브라우저 탭의 프론트 사전 기록 경쟁만** 직렬화한다. 네트워크 POST 전체 기간에는 잠금 유지하지 않는다. 저장된 intent가 다른 키를 거부한다.
- `localStorage`는 동시 쓰기 원자성이 없으므로 Web Locks 아래에서만 claim/release 한다. `storage` 이벤트는 알림용이며 성능 최적화나 정합성 잠금으로 사용하지 않는다.
- 실제 서버 API·트랜잭션·DB 스키마는 변경되지 않았다.

## 4. 코드 심층 설명
### A. 공유 claim — `pendingWrite.js#claimPendingWrite`
저장소를 읽기 전에 브라우저 Web Lock을 획득. 기존 기록이 다른 명령이면 거절, 같은 key+본문이면 동일 요청 재시도 허용. 동일 owner이어도 내용이 다르면 거절. 공유 기록·탭 기록을 POST **전에** 읽기 확인한다. Web Locks/스토리지 제한 시 HTTP 쓰기를 진행하지 않는다.

### B. 공유 release — `pendingWrite.js#releasePendingWrite`
서버 응답이 실제 명령과 일치하고 성공이 확인됐을 때, 또는 확정된 업무 거절 후에만 호출한다. 기존에 저장된 owner/key/body가 정확히 일치하는 경우에만 제거하여 다른 탭의 새 명령을 삭제하지 않는다. 완료된 결과 자체는 서버 트랜잭션 기준으로 판정한다.

### C. UI — 두 Vue 파일의 `refreshOtherTabPending`
원격 탭의 저장/삭제 알림은 단순 UX 힌트. 다른 탭에서 쓰기를 시작했는데 사용자가 이미 폼을 편집 중이라면 임의 덮어쓰지 않고 충돌 안내; 실제 제출 시 `claimPendingWrite`가 최종 확인/거절. 다른 탭에서 기록이 사라졌다고 **성공 처리하지 않고** 목록 확인 유도.

## 5. 예외·권한·운영 위험
- 로그인 owner가 다른 shared 기록은 복구 UI에 payload를 노출하지 않지만 새 명령은 차단. 공동 PC에서 서로 다른 계정으로 로그인한 후 잠금 해제 절차는 별도 정책 필요. localStorage의 owner 문자열은 JS에서 접근 가능하므로 인증 보안 경계로 사용하지 않는다.
- sessionStorage 예전 기록과 shared 기록이 다른 경우 자동 덮어쓰기하지 않는다. 신규 key 대신 재고 담당자의 확인이 필요하다.
- localStorage 영속 기록은 탭 종료 후에도 남을 수 있지만 **삭제/시크릿 모드/브라우저 프로필 변경/다른 기기**에서는 보장하지 않는다. 브라우저 저장 실패는 fail-closed. XSS 발생 시 모든 프론트 저장소를 조작할 수 있으므로 서버 검증이 최종 방어.
- Web Locks는 지원 브라우저에서만 동작한다. 잠금 API 미지원 시 작업이 중단될 수 있으며, 범용 브라우저 지원이 필요하면 서버측 상태 조회 API/DB 기반 설계를 다음 ADR로 검토.
- 서버 세션이 만료/로그아웃되는 도중에는 401/403을 '명령 미처리'로 확정하지 않고 동일 key 보존. 다른 owner로 재전송 허용하지 않음.

## 6. 장점·대안·리팩터링 판정
| 선택 | 기능/효율 | 비용·한계 | 판정 |
|---|---|---|---|
| Web Locks + localStorage | 같은 출처 탭의 key 경쟁 차단, 별도 서버 변경 없음 | Web Locks 필수, 저장 기록 관리 필요 | 이번 즉시 개선 |
| localStorage 선확인-후쓰기만 | 코드 단순 | 두 탭 동시 읽기 시 다른 key 덮어쓰기 경쟁 | 사용 금지 |
| 전용 IndexedDB intent 트랜잭션 | 단일 저장소에서 원자적 key claim | 기존 탭 sessionStorage 마이그레이션·DB 스키마·이벤트 처리 추가 | 2차 대안 |
| 서버 requestKey 조회·보존 정책 | 여러 브라우저/계정의 중앙 복구·감사 | 인증, 테넌트/계정 격리, 보존기한·권한 설계 | 2차 개선 |
| Redis/범용 분산락 | 현재 요구보다 과함 | 서버 비용/장애 지점 증가 | 현 시점 불필요 |

**기능적 리팩터링:** submit 함수 두 곳에 동일한 claim/release 계약 적용. **효율적 리팩터링:** 서버 조회와 캐시 추가 대신 브라우저 lock/스토리지 활용. UI composable 공통화는 실제 중복 유지비가 증가할 때 재검토.

## 7. 테스트·증거
- 사전 정적 검수: 코드 흐름과 JS API 계약 확인. 현재 **CI 실행 전**, 테스트 통과 숫자/실DB 결과/배포 성공을 미리 단정하지 않는다.
- 계획: `cd frontend && npm ci && npm test`, Chrome fixture `npx playwright test --config playwright.config.js`, demo `npx playwright test --config playwright.demo.config.js`, Java21/Spring Boot/MySQL 8.4 `npx playwright test --config playwright.live.config.js`.
- PR 및 main CI 링크, QA 스크린샷·새 코드 기준 Chrome 결과는 실행된 후 첨부한다. 성능 실측 없음.

## 8. 시니어 검수 판정
- **정확성: 검증 대기.** 탭 경쟁의 실제 동작과 저장 API 장애 주입 필수.
- **원장/FIFO: 서버 코드 미변경.** 실제 DB E2E에서 두 탭 replay 이후 원장 1회 검증 필요.
- **접근성: 주의.** 충돌 경고는 role=alert, 다른 탭 변경은 성공으로 오인하면 안 됨.
- **보안: 주의.** owner는 편의 구분이며 진짜 인증/인가 경계가 아님, cross-user browser shared storage 위험 설명.
- **효율: 무측정.** 전역잠금은 저장 검사 구간에만 적용, 실제 POST 진행 중 lock 점유 없음.
- **유지보수: 조건부.** 기존 helper 호환 유지, API 변경은 Vue 두 군데로 한정.

## 9. 다음 TASK/검증 경계
PR 통과 → 병합 main CI → 정확한 SHA의 Vercel production → **실제 공개 IndexedDB 데모** Chrome 재실행/신규 다중 탭 QA를 구분한다. Windows 실사용 환경, 서버조회/삭제 정책, 다기기 동기화는 별도.

## 10. 직접 설명할 수 있는 20~40초 요약
> 서버는 같은 키의 중복 요청을 방어하지만, 브라우저 탭마다 다른 키로 같은 재고 이동을 보내면 그 보호가 무력해질 수 있습니다. 그래서 Web Locks로 브라우저 내 요청 의도의 등록과 삭제를 직렬화하고, localStorage에서 같은 로그인 사용자의 미확인 요청을 공유하도록 설계했습니다. 이미 처리됐는지는 최종적으로 MySQL에서 동일 키 재생과 원장 1회로 검증합니다.

**이해 점검:** 1) localStorage만 쓰면 왜 충돌이 날 수 있나? 2) Web Locks를 POST 동안 잡지 않는 이유? 3) 동일 브라우저 멀티탭과 다기기 복구 차이?


## 11. 최초 PR CI 발견·수정 이력 (2026-11-23)

- [Frontend CI #45052685004](https://github.com/jeon950523/factoryflow/actions/runs/45052685004)에서 Node 단위 테스트/빌드는 성공했고 기존 fixture 포함 **25/27 PASS, 신규 다중 탭 Chrome 2건 FAIL**. 실패 단정이 아니라 실제 테스트 로그 `expect(secondPosts).toBe(1)`의 클릭 직후 검사 때문일 가능성이 높아 검사 종료 신호를 기다리도록 변경.
- 수정 후 `await expect(recovered).toHaveCount(0)` 및 `expect.poll`를 이용해 비동기 claim→CSRF→POST→확정 응답까지 확인한다. 두 탭의 다른 기존 요청이 충돌할 경우 remote storage 이벤트가 탭 로컬 키를 덮어쓰지 않도록 보호했다.
- [TS-011](../TROUBLESHOOTING.md)에 증상·실패 로그·해결 방향을 남겼다. 수정 버전의 실제 CI 성공 여부는 별도로 확인 후 판정한다.
