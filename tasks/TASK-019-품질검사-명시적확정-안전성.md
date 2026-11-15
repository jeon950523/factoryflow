# TASK-019 — 품질검사 결과의 명시적 입력 및 확정 안전성

> 작성일: 2026-11-16
> 기준: `main` `63dbfbb61682cc1bb1cc4ea032c343f7d0d7f051` / TASK-018 완료
> 작업 상태: **완료** — PR #26 병합·`main` Frontend/실DB CI 성공, Vercel 배포 완료
> 범위: Vue `OrdersView.vue` 품질검사 UI, Chrome 데모/실DB 회귀 테스트, 기록 문서

## 문제와 근거

현재 `OrdersView.vue`의 `choose(id)`는 AWAITING_QC 상태의 생산지시를 선택하면 `qcForm = {passedQty: detail.plannedQty, failedQty: 0}`을 채운다. `qcValid`가 즉시 true가 되어 검사 결과를 **명시적으로 입력하지 않아도 전량 합격 처리 버튼이 활성화**된다. `QualityInspectionService.confirm`은 검사 결과/합격품 LOT/완제품 재고 이동을 동일 MySQL 트랜잭션으로 확정하며, API의 검사는 생성 후 수정 기능이 없다. 이것은 실제 재고에 반영되는 업무 실수 위험이다.

## 구현 범위

1. 생산지시 상세 조회 및 재조회 때 QC 두 수량을 빈 값으로 시작한다. 0개 합격 또는 0개 불합격도 **사용자가 명시적으로 입력한 0**만 인정한다.
2. 브라우저의 `v-model.number`는 빈 input을 문자열 `''`로 반환하므로 `Number('') === 0`에 의존하지 않고 **입력 여부/안전 정수/0 이상/합계의 안전 정수 범위**를 검증한다.
3. 계획 수량과 입력한 합격+불합격의 합계가 정확히 일치해야만 품질검사 확정 버튼을 활성화한다.
4. 실제 POST 전 사용자가 대상 생산지시번호·합격·불합격 수량을 다시 확인한다. 취소 시 요청하지 않고 입력값을 유지한다. 반복 클릭·처리 중 중복 요청 방지.
5. 검사 API `POST /api/v1/work-orders/{id}/inspection` payload와 서버 정합성 규칙·DB 스키마·데모 엔진은 변경하지 않는다.

## 검증 조건

- IndexedDB Chrome: 검사 대기 상태 최초 선택 시 필드 공란/확정 비활성; 0을 직접 입력하면 유효; 잘못된 합계/소수/음수/안전 범위 초과 차단; 확인 대화상자 취소 후 상태 변경 없음; 승인 후 QC 성공·새로고침에도 결과 유지.
- Spring Boot/MySQL Chrome E2E: 수량 입력 후 명시적 확인, QC 완료, 원자재/완제품 현재고 및 출고 기존 검증 2/2 유지.
- Node/빌드·Chrome fixture·IndexedDB/실DB CI 통과, PR HEAD과 병합 main CI 모두 확인한다.
- Chrome/테스트 오류가 실제 발견되면 `docs/TROUBLESHOOTING.md`에 증상-원인-대책-실행 링크 기록.
- [보고 규칙](../docs/DEVELOPMENT_REPORTING_STANDARD.md)의 설명·트레이드오프·우선순위·검증/핸드오프를 동일 TASK에 작성한다.

## 비범위 / 선택 이유

- 검사 결과 수정/취소·품질 승인 워크플로·검사자 서명·검사 상세 첨부는 신규 도메인 계약과 감사 이력이 필요해 범위 밖.
- 모든 생산 단계에 확인 팝업을 추가하면 작업량/실수 위험의 비율이 달라진다. 우선 **되돌릴 수 없는 재고 입고가 발생하는 QC 확정**에 한정한다.
- 기본 0/0으로 채우는 것도 `0`의 직접 입력 여부를 구분하지 못하므로 빈 값 초기화가 타당하다.

## 실행 근거

- PR 검증 커밋: `729e5223869c013f1dd918dc806afc768f83dcf9`, [PR #26](https://github.com/jeon950523/factoryflow/pull/26)
- [Frontend CI](https://github.com/jeon950523/factoryflow/actions/runs/43729409460): Node 37/37, Chrome fixture 21/21, IndexedDB 29/29 성공
- [실DB E2E](https://github.com/jeon950523/factoryflow/actions/runs/43729409394): 실제 MySQL 2/2 성공
- 첫 [Frontend CI 실패](https://github.com/jeon950523/factoryflow/actions/runs/43729184665)는 `합격 수량` 로케이터가 `불합격 수량`에도 매칭된 Playwright selector 문제로, `exact:true` 수정 후 최종 CI 성공
- 공개 Vercel에서의 실제 검사 입력/확정과 Windows PC 수동 사용성은 **별개·미검증**으로 추적. 배포 성공만 확인.

## 완료 확인 (2026-11-16)

- 코드 병합 [PR #26](https://github.com/jeon950523/factoryflow/pull/26) → `main 480c2a058cfdbb3bf3bef4657bb3e04e97671883`.
- [병합 main Frontend CI](https://github.com/jeon950523/factoryflow/actions/runs/43730192091): Node 37/37, Chrome fixture 21/21, IndexedDB 29/29 성공.
- [병합 main MySQL E2E](https://github.com/jeon950523/factoryflow/actions/runs/43730192083): 2/2 성공. 배포 상태 Vercel success.
- 세부 실패 분석·코드 설계/트레이드오프·잔여 항목은 [시니어 검수](../docs/task-reports/TASK-019-품질검사-명시적확정-검수.md), [TS-008](../docs/TROUBLESHOOTING.md) 참조.
