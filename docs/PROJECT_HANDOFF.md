# FactoryFlow — 프로젝트 상태 및 작업 핸드오프

> 마지막 문서 정리: 2026-11-17
> GitHub: [jeon950523/factoryflow](https://github.com/jeon950523/factoryflow)
> **현재 기능/운영 배포/공개 QA 검증 완료 기준**: TASK-021 / `main a91c0cae0f14654d52e84f46f21710d16a180797` / Vercel SUCCESS / 공개 Chrome 8/8
> **TASK-021 검증:** [공개 Chrome #43958402614](https://github.com/jeon950523/factoryflow/actions/runs/43958402614) 8/8(운영 배포 이후 재실행도 8/8), [main Frontend #43958811806](https://github.com/jeon950523/factoryflow/actions/runs/43958811806) 42/21/30, [main MySQL #43958811687](https://github.com/jeon950523/factoryflow/actions/runs/43958811687) 3/3, Vercel production SUCCESS.
> 이 문서의 HEAD는 작업 시작 시 확인한 값이다. 더 최신 코드가 생기면 반드시 GitHub `main`과 PR/CI 결과를 먼저 확인한다.

## 1. 문서 우선순위와 최근 작업

1. 비즈니스 불변조건·상태 기획: [FACTORYFLOW_SSOT_v0.1.md](FACTORYFLOW_SSOT_v0.1.md) **초기 Draft**, 변경은 보고서/ADR/검증과 비교해 판단
2. 현재 구현·배포 경계: [README](../README.md) + 현재 GitHub `main` 코드, [ADR-013](adr/ADR-013-프론트데모-실백엔드분리.md)
3. 최신 작업과 검증: [TASK-021 보고서](task-reports/TASK-021-배포재시도-공개QA-검수.md), [PR #30](https://github.com/jeon950523/factoryflow/pull/30) + [PR #31](https://github.com/jeon950523/factoryflow/pull/31), [TS-010 해결 기록](TROUBLESHOOTING.md).
4. 최신 완료: TASK-021 Vercel 재배포·실제 공개 Chrome 8/8·main Frontend/실DB 검증. 다음 후보는 운영 UX/사용성 및 배포 횟수 제한 정책 점검. 실제 공용 MySQL 백엔드는 아직 공개하지 않았다.
5. 개념·선택 이유: [CORE_CONCEPTS.md](learning/CORE_CONCEPTS.md), [ADR](adr/), [TROUBLESHOOTING](TROUBLESHOOTING.md)
6. 최신 일정/보류: [로드맵](DELIVERY_ROADMAP_20261111.md)

**중요:** 오래된 보고서나 Draft SSOT가 ‘미구현’이라 하더라도 지금 구현 상태를 판단할 때는 최신 커밋의 API·테스트·ADR과 비교한다. 과거 근거를 삭제하지 말고 현행과의 차이를 표기한다.

## 2. 기술 및 데이터 경계

- Backend: Java 21, Spring Boot, Spring Data JPA, Flyway, MySQL 8.4, Testcontainers, 세션 기반 ADMIN/VIEWER 및 CSRF. 모듈형 모놀리식.
- Frontend: Vue 3, Pinia, Vue Router, 서버 검색형 `ItemSearchSelect`, URL 기반 상세/페이지 복원.
- 공개 웹: https://factoryflow.kro.kr = Vercel 정적 SPA + **브라우저별 IndexedDB 데모**. Java/MySQL·실계정 서버 인증이 아니다.
- 개발 서버: Vite `/api` → 로컬 Spring Boot 8080 → MySQL.
- 도메인: 단일 공장/창고, 정수 EA, 원자재 LOT 입고, BOM 스냅샷, 생산지시 초안→전량 예약→실소모→생산완료→QC 합격품 입고→FIFO 완제품 출고.
- 재고 불변식: `onHand >= reserved >= 0`, `available = onHand - reserved`; 물리 이동 원장은 입고/소모/합격입고/출고만 추적. 예약·취소는 물리 이동 이력이 아니다.
- 한계: BOM 수정/버전, 반품/출고 취소, 다중창고, 부분생산/재작업, 실제 운영 서버 공개 배포는 범위 밖.

## 3. TASK 이력

| TASK | 작업 요약 | PR / main 결과 |
|---|---|---|
| 014 | 재고 이동 원장·품목 전체 검색·업무 이력 링크 | [#21](https://github.com/jeon950523/factoryflow/pull/21) / `c427540` |
| 015 | 실제 공개 Chrome·1440px 스크린샷·한글 폰트 | [#22](https://github.com/jeon950523/factoryflow/pull/22) / `2899b020` |
| 016 | 공통 모달 포커스·닫힘·가독성 | [#23](https://github.com/jeon950523/factoryflow/pull/23) / `4d790666` |
| 017 | BOM/LOT/생산지시/출고 이력 왕복·URL·정합성 | [#24](https://github.com/jeon950523/factoryflow/pull/24) / `71b6ae20` |
| 018 | 공개 동선/URL/예외/모달 6/6, 기록 누락 복원 | [PR #25](https://github.com/jeon950523/factoryflow/pull/25) / `63dbfbb` · main Frontend/실DB SUCCESS |
| 019 | QC 결과 직접 입력/범위·합계 검증/불가역 확정 전 재확인 | [PR #26](https://github.com/jeon950523/factoryflow/pull/26) / `480c2a0` · 병합 main Frontend/실DB SUCCESS |
| 020 | 입고·출고 처리 결과 미확인 시 동일 키/본문 복원·멱등 재시도 | [PR #28](https://github.com/jeon950523/factoryflow/pull/28) / `8cb8617` · 실DB 성공, 공개 반영은 TASK-021에서 확인 |

## 4. TASK-020 당시 검증 이력 및 TASK-021 최신 근거

TASK-020 병합 main `8cb86179294ca702b5d0ad3154b3efca46c3052b`:
- [Frontend CI #43733088956](https://github.com/jeon950523/factoryflow/actions/runs/43733088956): Node 42/42, Chrome fixture 21/21, IndexedDB 30/30, build SUCCESS.
- [Fullstack E2E #43733088860](https://github.com/jeon950523/factoryflow/actions/runs/43733088860): 처음 job 취소 이후 **같은 실행을 재실행하여** 실 MySQL Chrome 3/3 SUCCESS.
- **당시 Vercel 배포 실패:** `Deployment rate limited — retry in 24 hours.`는 TASK-020 당시의 이력이다. **2026-11-17 TASK-021에서 신규 `main a91c0cae` Vercel success 및 공개 Chrome 8/8로 복구 확인**.
- Vercel 공개 웹은 브라우저 IndexedDB 데모, Java/MySQL 백엔드는 로컬/CI 전용.

## 5. TASK-019 완료 이력

- TASK-019는 `OrdersView.vue`에서 QC 결과를 **직접 입력**하고 안전 정수·합계 검증/최종 사용자 확인을 거쳐 검사 POST하도록 변경한 작업이다.
- 검증 증거는 [검수 보고서](task-reports/TASK-019-품질검사-명시적확정-검수.md)와 병합된 main [Frontend](https://github.com/jeon950523/factoryflow/actions/runs/43730192091)·[MySQL](https://github.com/jeon950523/factoryflow/actions/runs/43730192083). 이전 CI 테스트 선택자 충돌 및 비동기 초기화 레이스는 [TS-008](TROUBLESHOOTING.md)에 기록돼 있다.
- 다음 TASK를 시작할 때는 `main` 최신 SHA와 CI 확인 → [기록 표준](DEVELOPMENT_REPORTING_STANDARD.md) 및 신규 `tasks/` 지시서 작성 → 시니어 검수/테스트 → `docs/task-reports/` 결과 및 핸드오프 누적 원칙을 따른다.
- **우선 검토 후보:** 실제 배포 후 새 QC 화면의 사용성/확대/키보드 시험, 실패한 출고·입고의 재시도 안내/멱등성 요청 키 유지 검수. 새 API·검사 정정 기능은 별도 업무 설계가 필요하다.
- 공개 Vercel = 브라우저별 IndexedDB 데모이며, Java/MySQL 서버는 외부 공개하지 않는다. Vercel 배포 SUCCESS와 새 화면의 공개 QA 성공을 혼동하지 않는다.

## 6. 매 TASK의 완료 기준 (사용자 지정)

[작성 표준](DEVELOPMENT_REPORTING_STANDARD.md) 및 [템플릿](templates/TASK_REPORT_TEMPLATE.md)을 **매 작업에서** 지킨다.

- 작업 전 `tasks/TASK-XXX-*.md`: 목적, 범위, 제외, 검증 조건
- 작업 후 `docs/task-reports/TASK-XXX-*.md`: 호출 흐름, 변경 파일, 핵심 코드 1~3개 및 이유, 대안/장단점, 예외, 검증 근거와 CI URL, 시니어 관점, 기능/효율 리팩터링의 우선순위, 다음 작업, 20~40초 요약 및 이해 점검 질문
- 영향 시 `docs/learning/CORE_CONCEPTS.md` 누적 개념, `docs/adr/` 설계 결정, `docs/TROUBLESHOOTING.md` 재현한 문제, `docs/DELIVERY_ROADMAP_20261111.md`/README/이 핸드오프 갱신
- 구현·정적 검토·실행 검증·실배포 확인을 혼용하지 않는다. 기존 오래된 보고서는 삭제/덮어쓰기보다 차이·후속 이력 링크로 보완한다.
- 한국어 1목적 커밋, PR/실제 Chrome/실 MySQL 검증 후 병합. 원격 게시/병합은 사용자 명시적 요청에 따라 수행한다.

## 7. 새 채팅에서 재개할 때

“FactoryFlow 저장소 `jeon950523/factoryflow`의 `main` 최신 커밋과 CI를 먼저 검수해. `docs/PROJECT_HANDOFF.md`, `docs/DEVELOPMENT_REPORTING_STANDARD.md`, 최신 `tasks/` 지시서와 `docs/task-reports/` 보고서를 참고하고, 구현→시니어 검수→브라우저·MySQL E2E→PR·문서 누적 순서로 작업해. TASK-021에서 Vercel 배포 제한을 해소하고 실제 공개 Chrome 8/8·최종 main Frontend/실DB CI를 검증했어. 이제 `main` 최신 상태를 확인하고 다음 개선 항목을 결정해.”

## 8. TASK-019 인계

- 문제: `OrdersView.vue#choose`가 QC 입력을 전량 합격으로 자동 채워 검수자 입력 없이 재고를 늘릴 수 있는 상태였다.
- 개선: `qcQuantity()`에서 빈 필드와 직접 입력 0을 분리, 수량/합계 안전 정수 확인, 생산지시번호·합격·불합격 수량으로 `window.confirm` 후 검사 POST.
- 테스트: 실제 IndexedDB에서 취소 시 비반영·입력 유지·재로드 공란·승인 후 재고 변화; 실 MySQL E2E에서 명시 입력·승인 후 기존 제조 정합성 테스트.
- 서버 API/FIFO·DB·인증 미변경. 사전 검토 기준 코드는 `feature/task-019-qc-explicit-confirmation`; [PR 기능 HEAD `729e5223`](https://github.com/jeon950523/factoryflow/pull/26)에서 Frontend Node 37/37·Chrome fixture 21/21·IndexedDB 29/29·실DB 2/2 성공. [최종 PR 기능+문서 HEAD `655453af`](https://github.com/jeon950523/factoryflow/pull/26) Frontend/실DB SUCCESS 이후 [병합 main Frontend](https://github.com/jeon950523/factoryflow/actions/runs/43730192091)·[실DB](https://github.com/jeon950523/factoryflow/actions/runs/43730192083) SUCCESS 확인.
- [지시서](../tasks/TASK-019-품질검사-명시적확정-안전성.md), [시니어 검수](task-reports/TASK-019-품질검사-명시적확정-검수.md), [학습 누적](learning/CORE_CONCEPTS.md).

## 9. TASK-020 완료 인계 및 배포 차단

- 기준 main: `2b6cde9b2cb724bec512bd5ea4851a260060b4c4` (TASK-019 최종 문서 병합).
- 코드 검수로 확인한 위험: `InventoryView.vue`와 `ShipmentsView.vue`는 네트워크 쓰기 실패 후 폼을 수정·닫았다 다시 열거나 새로고침하면 새 key를 생성할 수 있어, **서버 커밋 후 응답 유실** 상황의 원래 요청을 복구하기 어렵다.
- 신규 `frontend/src/lib/pendingWrite.js`: 0/5xx/INVALID_RESPONSE 상태에서만 미확인 요청을 탭 sessionStorage로 보관. `read/save/clear` 함수는 순수한 계약 필드만 복원. 실패 후 입력 잠금·같은 payload 재시도·정상 응답 이후 삭제.
- 실제 Spring Boot/MySQL 재현: `frontend/tests-live/real-manufacturing.spec.js`에서 `route.fetch()`로 서버에 먼저 POST 커밋한 뒤 `route.abort()`로 브라우저 응답만 유실, 새로고침 후 동일 key로 재시도. 원자재 입고/완제품 출고 원장 각각 1건 검증.
- 서버 SQL/트랜잭션·스키마/FIFO/권한 미변경. 시뮬레이터(IndexeDB)와 실 MySQL CI는 별개. 다중 탭·스토리지 삭제/종료는 미보증.
- [작업지시서](../tasks/TASK-020-입출고-재시도-멱등성.md) · [검수 보고서](task-reports/TASK-020-쓰기재시도-멱등성-검수.md) · [TS-009](TROUBLESHOOTING.md) · [기술 학습 §24](learning/CORE_CONCEPTS.md).
- 기능 CI `278c60fe` 검증 성공: [Frontend #43732644025](https://github.com/jeon950523/factoryflow/actions/runs/43732644025) Node42/Chrome21/IndexedDB30, [MySQL #43732644015](https://github.com/jeon950523/factoryflow/actions/runs/43732644015) 실제 E2E 3/3, HTTP 200 replay·원장 1회·409 잠금 해제. 병합 main `8cb8617`의 Frontend/MySQL CI 성공을 확인했다. 다음 작업은 Vercel 배포 제한 해제 후 재배포와 **실제 공개 브라우저 화면 확인**이다.

### 다음 작업 착수 조건

1. Vercel 요청 제한이 해제되면 실패한 commit/배포를 재시도하고 최신 GitHub `main`과 실제 Vercel 배포 SHA를 대조한다. 자동 재시도가 될지는 알 수 없으므로 배포 상태를 직접 확인한다.
2. 공개 사이트 `factoryflow.kro.kr`는 브라우저 IndexedDB 데모. 공개 사이트의 TASK-020 재시도 UI는 현재 배포 실패로 미검증이다.
3. 다른 탭·세션 복원은 이번 멱등 보장의 범위 밖. 실제 운영을 검토한다면 서버 requestKey 조회 API 및 저장 만료·복구 정책을 별도 설계한다.
4. 다음 TASK에서도 [개발 기록 표준](DEVELOPMENT_REPORTING_STANDARD.md)의 지시서/실행 증거/시니어 검수/학습·TS·핸드오프 누적을 지킨다. TASK-021에서 배포 문제/공개 QA는 완료했다.

## 10. TASK-021 배포 제한 해제·공개 UI 검증 완료 인계

- 기능 정본: TASK-020 [PR #28](https://github.com/jeon950523/factoryflow/pull/28) 병합 `8cb8617`, 기존 실DB 응답 유실·멱등성 3/3.
- 배포 복구: [PR #30](https://github.com/jeon950523/factoryflow/pull/30) → `main 0e577136` Vercel production SUCCESS. [PR #31](https://github.com/jeon950523/factoryflow/pull/31) 테스트 추가 → `main a91c0cae` Vercel production SUCCESS.
- 공개 브라우저: [Github Public Site Smoke #43958402614](https://github.com/jeon950523/factoryflow/actions/runs/43958402614) **8/8**. PR 때 8/8 통과하고 이후 `a91c0cae` 새 production 배포 성공 뒤 **job `133620793468` 재실행도 8/8** 성공. screenshots 아티팩트(7일): `public-qa/task-021-receipt-retry.png`, `public-qa/task-021-shipment-retry.png`.
- 코드 병합 main CI: [Frontend #43958811806](https://github.com/jeon950523/factoryflow/actions/runs/43958811806) Node42/Chrome21/IndexedDB30 SUCCESS, [MySQL #43958811687](https://github.com/jeon950523/factoryflow/actions/runs/43958811687) 3/3 SUCCESS.
- 기존 실패가 실제로 해소됐다는 의미는 신규 Vercel success와 실제 공개 Chrome 성공으로 제한한다. 사용량 한도/자동 재시도 정책 및 타 브라우저의 과거 IndexedDB 데이터 상태는 조사하지 않았다.
- **TASK-021 추가 서비스 코드/DB 변경 없음.** Public Site Smoke 테스트 2건만 추가했다. 다음 TASK에서는 실운영 백엔드 보안/접근 경계 및 오류 메시지·현장 사용자 UX를 우선 검토하면 좋다.
