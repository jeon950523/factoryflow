# TASK-021 — Vercel 재배포 및 공개 사이트 검증 보고서

> 2026-11-17 · **Vercel 운영 배포·공개 Chrome 8/8·병합 main Frontend/실DB E2E 검증 완료** · 문서 전용 최종 병합 SHA는 별도 확인

## 1. 목표와 현재 근거

TASK-020은 코드와 MySQL에서 입고·출고 200 replay가 검증됐지만 Vercel Git 통합 배포가 rate limit에 막혔다. 기존 GitHub 상태 `Deployment rate limited — retry in 24 hours.` (2026-11-15T16:06:50Z) 이후 24시간 초과 시점에 새 Git 연동 배포를 **1회** 재유도하고 그 결과를 확인한다.

## 2. 코드·배포 경계

- 현재 업무 앱 코드와 DB·검사 API를 변경하지 않는다.
- `main 7cd77be`의 Vercel 상태가 실패한 것은 GitHub Actions Node/Chrome/MySQL 실패와 다르다. [기능 CI Frontend](https://github.com/jeon950523/factoryflow/actions/runs/43733088956) 42/21/30 성공·[실DB](https://github.com/jeon950523/factoryflow/actions/runs/43733088860) 3/3 성공.
- 공개 도메인은 IndexedDB 기반 정적 프론트엔드로서, 화면 검증만으로 MySQL 동시성 보장을 주장하지 않는다.

## 3. 접근·대안·리팩터링 검토

- 선택: GitHub 원격에 **문서만 포함된 1회 PR/병합**으로 Vercel의 새 production build를 유도. 새 SHA의 commit status를 기준으로 판단.
- 대안 A: Vercel 대시보드/앱으로 Redeploy. 연결되어 있다면 가장 직접적이고 명확하지만 이번 작업 시점에는 Vercel 연결이 없어서 실행할 수 없다.
- 대안 B: GitHub 재배포 전용 CI 추가. Vercel 토큰/프로젝트 정보와 권한 관리가 필요하고, 기존 통합과 이중 배포 위험이 있으므로 불필요.
- 대안 C: 배포 횟수 제한을 유료 플랜으로 해제. 사용자 동의 없는 비용 발생 및 대시보드 설정 변경 불가. 금지.
- **즉시**: Vercel 배포 status 확인/공개 동작과 CI 동작을 분리 보고.
- **2차**: 운영 배포 트리거/브랜치별 preview 활성화 및 빌드 빈도 정책 검토. 프론트 앱 성능 리팩터는 근거가 없어 현 시점 불필요.

## 4. 실제 수행/검증 추적

- 2026-11-17 오전, [코드 병합 커밋](https://github.com/jeon950523/factoryflow/commit/8cb86179294ca702b5d0ad3154b3efca46c3052b) 및 문서 병합 `7cd77be` 모두 Vercel `failure` 상태를 재확인. 신규 자동 재시도 성공을 확인하지 못함.
- **새 재시도 커밋/새 Vercel 상태**: [PR #30](https://github.com/jeon950523/factoryflow/pull/30) 문서 커밋 `61185a7` Vercel preview SUCCESS → `main 0e57713` production SUCCESS. 새 공개 QA 추가 [PR #31](https://github.com/jeon950523/factoryflow/pull/31) 병합 `main a91c0ca`도 production SUCCESS. 자세한 검증은 §8.
- **공개 Chrome 테스트**: [기존 6/6](https://github.com/jeon950523/factoryflow/actions/runs/43727222108), [신규 포함 8/8](https://github.com/jeon950523/factoryflow/actions/runs/43958402614) 성공. 신규 운영 배포 `a91c0ca` 이후에도 **같은 8개를 재실행해 8/8 성공**. 
- **기존 기능 회귀 증거**: Node42/Chrome fixture21/IndexedDB30/실DB3 성공은 TASK-020 코드 병합 SHA `8cb8617` 기준이다.

## 5. 시니어 검수·실행 순서

배포 오류는 **서비스 코드 오류가 아니다**. 제한 해제 추정만으로 성공이라고 판단하면 배포된 이전 버전의 UI와 최신 GitHub 코드가 어긋난다. 또한 Vercel 상태 success만으로 브라우저 요청·초기화·모달 UX가 정상이라고 주장할 수 없다. 재시도 성공 이후 공용 호스트에서 실제 Chrome smoke를 반드시 실행한다.

## 6. 설명/인계

“입고·출고 재시도 코드는 서버 실DB E2E를 통과했지만 정적 사이트 배포가 Vercel 요청 제한으로 거부됐습니다. 제한 해제 이후 1회 새 커밋을 배포 트리거로 사용하고 해당 SHA의 Vercel 상태, 공개 사이트 Chrome 테스트를 따로 검증하겠습니다.”

질문: (1) GitHub CI 성공과 Vercel 배포 성공은 왜 별개의 증거인가? (2) public IndexedDB Chrome 테스트는 왜 MySQL 운영 QA를 대신할 수 없는가?

## 7. 남은 위험

- Vercel 계정의 프로젝트 세부 로그/요금제는 연결되지 않아 직접 분석할 수 없었지만, GitHub 커밋에 연결된 Vercel build 상태와 공개 Chrome E2E로 신규 배포 성공을 확인했다.
- 실제 공개 Chrome에서 8/8 검증 완료. 원자재·출고 재시도 결과 스크린샷은 [Actions 실행](https://github.com/jeon950523/factoryflow/actions/runs/43958402614)의 `factoryflow-public-site-evidence` 아티팩트 내 `public-qa/task-021-receipt-retry.png`와 `public-qa/task-021-shipment-retry.png`에 보관(설정상 7일 유지).
- 일일 배포/preview 횟수 초과 시 동일 제한이 다시 발생할 가능성이 있다. 요금제/정확한 제한값은 확인하지 못했다. 재발 시 GitHub Git 통합 상태와 Vercel 빌드 로그를 구분해야 한다.

## 8. 최종 실행·배포·검수 증거

### 8.1 Vercel 재배포 — 실제 성공

- 2026-11-16T16:06:50Z가 아니라 **2026-11-15T16:06:50Z** 마지막 실패 status 확인. 24시간 이상 경과한 2026-11-16T21:14Z(한국 시간 11월 17일 06:14)에 새 배포 요청.
- [PR #30](https://github.com/jeon950523/factoryflow/pull/30) 배포 유도 단일 문서 커밋 `61185a79`: Vercel preview 상태 **SUCCESS**. [병합 production commit `0e577136`](https://github.com/jeon950523/factoryflow/commit/0e5771364ecdf963c1774d23c550f731a8b7af30) Vercel **SUCCESS / Deployment has completed**.
- 실제 공개 Chrome 테스트를 추가한 [PR #31](https://github.com/jeon950523/factoryflow/pull/31) `1e2e6513`에서 [공개 smoke #43958402614](https://github.com/jeon950523/factoryflow/actions/runs/43958402614) **8/8 PASS**. [병합 production commit `a91c0cae`](https://github.com/jeon950523/factoryflow/commit/a91c0cae0f14654d52e84f46f21710d16a180797) Vercel **SUCCESS**.
- 운영 코드 commit `a91c0cae`에서 배포가 완료된 뒤 같은 [공개 Chrome 8/8 스모크를 다시 실행](https://github.com/jeon950523/factoryflow/actions/runs/43958402614)하여 **8/8 PASS** (재실행 job `133620793468`). 공개 URL의 과거 샘플 동선 6건 및 TASK-020 미확인 요청 동선 2건.
- CI와 Vercel 상태를 혼합하지 않는다. 운영 배포 성공은 GitHub 커밋의 Vercel success, 공개 UI 성공은 실제 공개 Chrome의 8/8로 별도 입증.

### 8.2 새 공개 Chrome 테스트 계약 (실제 확인)

`frontend/tests-public/public-site.spec.js`에 신규 2건을 포함했다.

1. 원자재 `REC-T021-PUBLIC-01`: 브라우저 `sessionStorage`에 **미확인 입고 요청을 시뮬레이션하는 동일 key/payload** 저장 → 새로고침 복원·입력 잠금·닫기/재열기 → 동일 요청 재시도. 데모 샘플 강판 현재고 **40→42 EA**, 원장 **+2 EA 1건**. 동일 key 재생에서도 42 EA 유지. screenshot `public-qa/task-021-receipt-retry.png`.
2. 완제품 `SHIP-T021-PUBLIC-01`: 출고 복원·입력 잠금·재전송 → 샘플 완제품 현재고 **3→2 EA**, 원장 **−1 EA 1건**. 동일 key 재생에서 추가 차감 없음. screenshot `public-qa/task-021-shipment-retry.png`.
3. **안전성:** 두 테스트 모두 독립 Chromium 브라우저 컨텍스트에서만 실행. `/api/v1/` 백엔드 네트워크 호출 **0건**, 브라우저 `pageerror` **0건**. 실제 MySQL에 쓰지 않는다.
4. **엄밀한 구분:** 공개 스모크는 sessionStorage에 pending marker를 직접 만들어 UI 복원/멱등성을 검증한 것이며, **실제 HTTP 응답 유실을 전송 계층에서 주입한 것은 아니다**. 실제 서버가 커밋한 뒤 브라우저 응답만 유실시키는 `route.fetch() → route.abort()` 검증은 TASK-020의 [실 MySQL E2E #43733088860](https://github.com/jeon950523/factoryflow/actions/runs/43733088860)이 담당한다.

### 8.3 최신 main 프론트엔드·실DB 회귀

- 테스트 병합 `main a91c0cae0f14654d52e84f46f21710d16a180797` [Frontend CI #43958811806](https://github.com/jeon950523/factoryflow/actions/runs/43958811806): Node **42/42**, Chrome fixture **21/21**, IndexedDB demo **30/30**, 빌드 SUCCESS.
- [실DB E2E #43958811687](https://github.com/jeon950523/factoryflow/actions/runs/43958811687): Java 21/Spring Boot/MySQL + Chrome **3/3** SUCCESS.
- 공개 Chrome **8/8**는 위 두 빌드/실DB CI와는 별개로 Vercel URL을 대상으로 실행했다.
- PR #30은 문서·배포 트리거, PR #31은 공개 QA 테스트 2개. **서비스 코드·스키마·멱등 API는 변경하지 않았다.**

## 9. 시니어 최종 판정 및 리팩터링

- **TASK-021 배포 차단 해결/공개 사이트 QA 완료:** 실제 Vercel 커밋 상태와 공개 Chromium 실행·실DB CI의 서로 다른 증거가 모두 있다.
- **즉시 리팩터링:** 없음. Vercel 제한 때문에 검증되지 않은 성능 튜닝·Redis·배포용 인증 토큰 추가는 과설계다.
- **2차 개선:** GitHub PR preview 빌드 빈도/필터링과 Vercel 사용량 대시보드를 확인해 rate limit 재발 가능성을 낮출 것. 실제 재시도 운영 요구가 커지면 requestKey 서버 조회/다중 탭 복구는 별도 TASK로 설계.
- **범위 밖:** Windows 사용자 PC에서의 글꼴·확대·접근성 수동 시험, 실 운영 MySQL 공개 배포, Vercel 계정 내부 빌드 제한 상세 정책, 24시간 이상 장기 복구/브라우저 다중 탭 일관성.
- **문서 완료:** 본 보고서, TASK 지시서, TS-010, 핵심 개념, README, 로드맵, 데모 가이드, 핸드오프. 최종 문서 PR의 `main` 병합 SHA는 해당 PR에서 별도 확인해야 하며, 문서 전용 변경에 새로운 UI/DB 테스트 성공을 허위 기재하지 않는다.
