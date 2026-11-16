# TASK-021 — Vercel 배포 요청 제한 해제 후 운영 배포 재시도 및 공개 Chrome QA

> 착수: 2026-11-17 · 기준 `main 7cd77beb1bccebce5ce227f2ec11d1ee2389f682` · **배포 성공 전 미완료**

## 문제·검증된 사실

- TASK-020 기능 코드 [PR #28](https://github.com/jeon950523/factoryflow/pull/28)은 `8cb8617`로 병합. 실제 [main Frontend](https://github.com/jeon950523/factoryflow/actions/runs/43733088956) Node42/Chrome21/IndexedDB30 및 [실DB E2E](https://github.com/jeon950523/factoryflow/actions/runs/43733088860) 3/3 모두 성공.
- 하지만 코드 병합과 문서 병합 [PR #29](https://github.com/jeon950523/factoryflow/pull/29)의 Vercel 상태는 `failure`: `Deployment rate limited — retry in 24 hours.`. 마지막 실패 시각은 **2026-11-15 16:06:50 UTC** (= 11월 16일 01:06:50 KST)이다.
- 2026-11-17 오전 확인 시까지 **해당 GitHub 커밋 상태는 실패 그대로**였고 Vercel 새 배포 성공 상태를 확인하지 못했다. GitHub 커밋 상태가 이전 실패 기록을 자동으로 성공으로 바꿀 것이라고 가정하지 않는다.

## 수행

1. 마지막 제한 발생으로부터 24시간이 지난 후 **배포 재시도 목적의 단일 문서 커밋**을 GitHub PR로 병합해 Vercel Git 통합의 새 배포를 유도한다. 추가 커밋 폭주·비용 유발을 방지하기 위해 재시도 횟수를 통제한다.
2. 배포 완료 여부는 새 `main` **정확한 SHA**의 Vercel GitHub commit status로 확인한다. `success`가 아니면 완료 처리하지 않고 원인과 차단 사항을 기록한다.
3. 새 빌드 성공 이후에만 공용 URL `https://factoryflow.kro.kr`의 Chrome 브라우저에서 TASK-020 입고·출고 재시도 인터페이스를 테스트한다. 기존 공개 smoke 6건 및 새로운 사용자 흐름(문제 발생시 잠금/다시 열기/새로고침/재전송)을 구분해서 검증한다.
4. PR/CI 결과, 배포 상태와 실제 공개 Chrome QA를 개별 근거로 `docs/task-reports/TASK-021-배포재시도-공개QA-검수.md`·README·로드맵·핸드오프에 누적한다. 공개 사이트는 **개인별 IndexedDB 데모**이고 실제 운영 MySQL 서버가 아니다.

## 범위·비범위/보안

- **코드·DB 변경 없음**(배포 재시도 단계). 운영 환경 변수·토큰·Vercel 과금 설정·빌드 제한 상향을 임의로 변경하지 않는다.
- 직접 Vercel 계정 연결 없이 GitHub Git 통합으로만 재배포를 유도할 수 있다. 이 방법은 최종 배포를 보장하지 않는다.
- 빈 PR/기능 무관 커밋을 반복하며 쿼터를 소비하지 않는다. 추가 미해결 실패 시 배포 로그 확인/계정 연결 후 결정.
- 나중에 추가할 공개 Chrome 테스트는 **실제 데이터/실DB 쓰기가 없도록** 독립된 브라우저 데모 컨텍스트에 한정한다.

## 완료 판정

- 신규 main SHA의 Vercel `success`(정확한 commit/deployment 증거)
- **실제 공개 사이트**에서 새 UI가 로드됨을 보여주는 Chrome 실행 로그/화면/링크 근거
- 기존 공개 smoke/신규 TASK-020 검사 통과 및 문서 최신화. 실행하지 않은 항목은 미검증으로 남긴다.
