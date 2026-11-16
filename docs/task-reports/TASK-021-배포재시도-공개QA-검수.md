# TASK-021 — Vercel 재배포 및 공개 사이트 검증 보고서

> 2026-11-17 · 정적 사전 검수/재시도 준비 · **배포/공개 브라우저 검증 미완료**

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
- **새 재시도 커밋/새 Vercel 상태**: 후속 기록 필요. 새 상태가 완료될 때까지 '성공'으로 변경 금지.
- **공개 Chrome 테스트**: 후속 검증 필요. 직접 브라우저 동작이 입증되지 않았으므로 현재 완료 아님.
- **기존 기능 회귀 증거**: Node42/Chrome fixture21/IndexedDB30/실DB3 성공은 TASK-020 코드 병합 SHA `8cb8617` 기준이다.

## 5. 시니어 검수·실행 순서

배포 오류는 **서비스 코드 오류가 아니다**. 제한 해제 추정만으로 성공이라고 판단하면 배포된 이전 버전의 UI와 최신 GitHub 코드가 어긋난다. 또한 Vercel 상태 success만으로 브라우저 요청·초기화·모달 UX가 정상이라고 주장할 수 없다. 재시도 성공 이후 공용 호스트에서 실제 Chrome smoke를 반드시 실행한다.

## 6. 설명/인계

“입고·출고 재시도 코드는 서버 실DB E2E를 통과했지만 정적 사이트 배포가 Vercel 요청 제한으로 거부됐습니다. 제한 해제 이후 1회 새 커밋을 배포 트리거로 사용하고 해당 SHA의 Vercel 상태, 공개 사이트 Chrome 테스트를 따로 검증하겠습니다.”

질문: (1) GitHub CI 성공과 Vercel 배포 성공은 왜 별개의 증거인가? (2) public IndexedDB Chrome 테스트는 왜 MySQL 운영 QA를 대신할 수 없는가?

## 7. 남은 위험

- Vercel 계정/정확한 프로젝트 배포 로그는 연결되지 않아 접근 불가.
- 스크린샷/라이브 Chrome 기능 검증은 실제 실행 성공 후에만 증거로 추가.
- 재배포 요청 자체가 다시 rate limit에 걸릴 가능성은 남아 있다.
