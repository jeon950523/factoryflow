# FactoryFlow — 재현된 오류 및 해결 근거

> **원칙:** 이 문서는 실제 코드 검수·CI 로그·Chrome 스크린샷에서 확인한 문제만 다룬다.
> 모호한 우려/아이디어는 각 TASK 보고서의 남은 위험·리팩터링 후보에 기록한다.
> 상태: 2026-11-15 TASK-018 문서 감사에서 기존 보고서와 CI 근거를 연결해 소급 정리.

## TS-001 · 테스트 JVM JAR 이름 하드코딩으로 서버 부팅 실패 (TASK-005D)
- **증상:** Gradle 빌드 성공 후 전체 Spring Boot/MySQL 브라우저 테스트의 서버 기동이 실패했다.
- **원인:** CI 실행 스크립트가 생성 JAR 파일명을 하드코딩했다.
- **조치:** 빌드 산출물에서 JAR을 탐색하도록 수정. API 코드 오류와 빌드·실행 스크립트 실패를 분리한다.
- **검증:** [TASK-005D 보고서](task-reports/TASK-005D-실브라우저-동시성-검수.md) 및 이후 Fullstack CI 2/2.
- **재발 방지:** 앱 버전 변경 후에도 동적으로 bootJar 산출물을 찾는 흐름을 유지한다.

## TS-002 · 상세 클릭/품목 정보 추가 후 테스트 누락과 화면 가림 (TASK-013)
- **증상:** 상세 링크 테스트 fixture에서 단건 GET 미응답, 출고 상세에서 출고 요청 키 미표시, 성공 알림 토스트가 등록 버튼을 가렸다.
- **원인:** 상세 조회 API 계약과 fixture가 불일치했고 화면 레이어 우선순위가 모달보다 높았다.
- **조치:** 실제 `GET /api/v1/items/{id}` fixture를 추가, 출고 요청 키 표시 복원, 모달과 토스트 z-index 순서 조정.
- **검증:** [TASK-013 보고서](task-reports/TASK-013-품목BOM-출고UI-검수.md), 최종 CI에서 Node 37/37, Chrome fixture 14/14, IndexedDB 19/19, MySQL E2E 2/2.

## TS-003 · 공통 페이지 태그·실DB Playwright 테스트 코드 오류 (TASK-014)
- **증상:** 공통 페이지 컴포넌트 마크업 오류와 실DB 테스트 코드 중복/구문 잘림으로 CI가 실패했다.
- **원인:** UI/테스트 변경 중 문법과 누락 블록을 최종 테스트 전에 충분히 확인하지 못했다.
- **조치:** 닫는 태그와 E2E 테스트 코드를 수정하고 새 최종 PR 헤드 전체 CI를 재실행했다.
- **검증:** [TASK-014 보고서](task-reports/TASK-014-재고이동-공통UI-검수.md), [PR #21 Checks](https://github.com/jeon950523/factoryflow/pull/21/checks).
- **재발 방지:** 최종 코드 기준 `npm run build`, Chrome fixture, 실DB 테스트 결과를 확인하기 전 병합 금지.

## TS-004 · GitHub-hosted Ubuntu 스크린샷 한글이 네모로 표시됨 (TASK-015)
- **증상:** 공개 사이트 Chrome 테스트는 통과하나 시각 스크린샷의 한국어 글자가 사각형으로 표시됐다.
- **원인:** 테스트용 Ubuntu 이미지의 한글 폰트 부재. 공개 사이트의 Vue 문자열 누락이 아니었다.
- **조치:** `.github/workflows/public-site-smoke.yml`에 `fonts-noto-cjk` 설치와 `fc-cache` 갱신을 추가했다.
- **검증:** [TASK-015 보고서](task-reports/TASK-015-공개배포-Chrome-검수.md), [해당 Chrome 증거 실행](https://github.com/jeon950523/factoryflow/actions/runs/43483614978).
- **재발 방지:** 공개 사이트의 스크린샷 QA에서 러너 폰트 확인과 최종 캡처 증거를 함께 보관한다.

## TS-005 · 발급 중 버튼 접근성 이름 변경으로 테스트 선택 실패 (TASK-016)
- **증상:** 계정 발급 제출 후 `getByRole('button', { name: '계정 발급' })` 검사 대상이 사라져 Playwright가 실패했다.
- **원인:** 정상 구현에서는 진행 중 버튼 레이블이 `발급 중...`으로 바뀌어 접근성 이름이 달라진다.
- **조치:** 동일 제출 버튼 `button[type="submit"]`을 추적하도록 테스트를 수정해 비활성 상태를 검사했다. 실제 저장 로직은 변경하지 않았다.
- **검증:** [TASK-016 보고서](task-reports/TASK-016-가독성-모달접근성-검수.md), [PR #23 Checks](https://github.com/jeon950523/factoryflow/pull/23/checks) / 병합 후 CI.
- **재발 방지:** 처리 중 변하는 문구를 고정적인 요소 식별자로 사용하지 않고 안정적인 role/selector를 선택한다.

## TS-006 · 목록 URL 숫자 정규식 과잉 이스케이프 (TASK-017)
- **증상:** URL 페이지 번호 파싱 신규 코드에 `/^\\d+$/`가 들어가 숫자 쿼리를 의도대로 인식하지 못할 위험이 코드 리뷰에서 발견됐다.
- **원인:** 원격 편집용 문자열의 이스케이프가 JavaScript 정규식 리터럴까지 중첩 적용됐다.
- **조치:** 소스 정규식을 `/^\d+$/`로 바로잡았다.
- **검증:** 브랜치에서 직접 최종 구문을 재열람하고, 페이지·상세 URL 복원 Chrome fixture 및 IndexedDB 테스트와 MySQL E2E를 성공시켰다. 이 문제는 최종 CI 실패로 관측된 장애가 아닌 **사전 코드 검수 발견**이다.
- **재발 방지:** 소스 생성·치환 후 패턴을 실제 출력에서 다시 읽고, 페이지 1+ 링크 자동 테스트를 적용한다.

## 실제 CI와 공개 운영의 구분
- [TASK-014~017 보고서](task-reports/)와 [TASK-018 작업지시서](../tasks/TASK-018-공개배포-QA-문서정합성.md)를 함께 참조한다.
- Vercel 배포 성공은 개별 브라우저 동작 보증이 아니며, IndexedDB 체험은 실 MySQL 트랜잭션을 증명하지 않는다.
- 사용자의 Windows PC에서만 재현되는 오류는 해당 환경에서 확인 전까지 여기서 “해결 완료”라 선언하지 않는다.


## TS-007 · 로드맵 최상단에 잘못 들어간 문자열 발견 (TASK-018)

- **발견:** `docs/DELIVERY_ROADMAP_20261111.md` 원본 첫 줄이 실제로 `[object Object]`라는 비정상 텍스트였다.
- **영향:** 로드맵 문서 제목이 사라진 상태였으며 README의 최신 TASK 완료 현황도 TASK-006 기준 안내와 혼재됐다.
- **원인:** 정확한 입력 시점 및 이 문자열이 삽입된 도구는 이번 감사로 확인하지 못했다. 추정 원인이나 책임을 특정하지 않는다.
- **조치:** 문서 머리말을 복원하고 기존 TASK-011~013 본문을 보존한 채 TASK-014~018 기록을 추가했다. README·frontend README·핸드오프에도 현행 구현 상태를 명시했다.
- **검증:** [PR #25의 문서 변경 diff](https://github.com/jeon950523/factoryflow/pull/25/files), [TASK-018 보고서](task-reports/TASK-018-공개배포-문서정합성-검수.md). 문서 편집 직후 저장소 파일 내용을 다시 읽어 복원된 제목을 확인했다.
- **재발 방지:** 원격 파일 수정 후 문자열로 덮어쓸 때 파일 첫 줄·필수 제목·문서 링크를 확인한다.

## TS-008 · 품질검사 자동 전량 합격 위험 및 테스트 이름 충돌 (TASK-019)

- **실제 코드에서 확인:** `OrdersView.vue#choose`가 `qcForm={passedQty: detail.plannedQty, failedQty:0}`을 사전 설정하여 `qcValid=true`와 검사 확정 버튼 활성화를 사용자 입력 전에 만들어 냈다. `QualityInspectionService.confirm`은 확정 시 합격품 LOT/재고 이동을 기록하며 검사 정정 API가 없는 상태다.
- **구조적 원인:** 검사 결과를 임의의 전량 합격으로 추정하는 UI 기본값과 `Number('')===0`에 의존할 수 있는 숫자 입력 계산.
- **해결:** 두 입력 공란 시작, 명시적 0/안전 정수/합계 검증, 생산지시번호·입력 수량 확인 대화상자 승인 시에만 검사 POST. 원자재·검사·완제품 DB 트랜잭션 계약은 유지.
- **실제 CI 실패:** [Frontend 첫 PR 검사 #43729184665](https://github.com/jeon950523/factoryflow/actions/runs/43729184665)에서 신규 IndexedDB 테스트 **2건 실패**, 기존 fixture는 정상. Playwright의 `getByRole('spinbutton',{name:'합격 수량'})`가 '불합격 수량'에도 부분 일치하여 strict mode violation이 발생했다. UI 도메인 로직 오류로 보지 않는다.
- **테스트 수정:** 합격 필드 로케이터를 `getByRole('spinbutton',{name:'합격 수량',exact:true})`로 고정. 전량 불합격(합격0/불합격10) 후 재고 미반영 테스트도 추가.
- **검증 증거:** [PR #26](https://github.com/jeon950523/factoryflow/pull/26)의 [Frontend #43729409460](https://github.com/jeon950523/factoryflow/actions/runs/43729409460) Chrome fixture 21/21·IndexedDB 29/29와 [실DB #43729409394](https://github.com/jeon950523/factoryflow/actions/runs/43729409394) 2/2 성공. 병합 main [Frontend #43730192091](https://github.com/jeon950523/factoryflow/actions/runs/43730192091) 및 [MySQL #43730192083](https://github.com/jeon950523/factoryflow/actions/runs/43730192083) SUCCESS, [TASK-019 보고서](task-reports/TASK-019-품질검사-명시적확정-검수.md).
- **두 번째 실제 CI 실패:** [최종 문서 커밋 Frontend #43729693292](https://github.com/jeon950523/factoryflow/actions/runs/43729693292)에서 29개 IndexedDB 테스트 중 1개 실패. 기존 '샘플 초기화' 테스트가 `resetDemoData()` 완료를 기다리지 않고 즉시 `page.goto('/orders')`로 이동해 재조회 시점에 이전 완료 상태를 볼 수 있었다. 다른 28건 및 모든 새 QC 시나리오는 성공.
- **수정:** `App.vue#resetDemo()`의 `await resetDemoData()` 완료 후 발생하는 `ui.notify('이 브라우저의 데모 데이터를 초기화했습니다.')` 토스트를 Playwright에서 기다린 후 화면을 이동한다. 비즈니스 엔진이나 초기화 UI 로직은 수정하지 않는다.
- **재발 방지:** 유사한 한국어 레이블은 `exact:true` 또는 명시적 `label`과 안정적인 ID를 사용한다. 비동기 화면 초기화/POST 후 테스트는 **완료 신호**를 기다리고 다음 화면을 탐색한다. UI 기본값이 사용자 승인/재고 변동을 암묵적으로 수행하지 않는지 검토한다.

## TS-009 · HTTP 응답 유실 뒤 새 요청 키 생성 시 입고·출고 중복 가능 (TASK-020)

- **검수 증거:** TASK-019 완료 `main 2b6cde9`의 `InventoryView.vue#openReceipt/submitReceipt`, `ShipmentsView.vue#openCreate/submit`를 직접 확인했다. POST 실패 시 기존 폼은 유지되지만 **입력 변경이 가능**하며, 닫기→다시 열기 또는 새로고침 시 **새 UUID 요청 키**를 생성한다.
- **실제 영향:** Spring Boot `CanonicalReceipt/CanonicalShipment`가 **같은 요청 키**에만 기존 결과 반환을 보장하므로, 서버가 먼저 커밋하고 브라우저 응답만 사라진 경우 새 키로 같은 내용 재전송 시 별개의 쓰기로 처리될 수 있다. 사용자가 실제 중복 입고·출고를 했다는 운영 사고 기록은 아니다. **코드 검수로 확인된 조건부 재현 위험**이다.
- **원인:** UI는 네트워크 오류를 모두 일반 메시지로 표시했고, 멱등성이 **요청 키뿐 아니라 payload 전체가 같아야 한다**는 규칙을 화면 재진입에 적용하지 않았다.
- **수정:** 응답 미확인(status0/5xx/INVALID_RESPONSE) 시 key+품목/LOT/수량을 immutable snapshot으로 고정, 탭 sessionStorage에 보관하고 확인된 재전송(200/201)에서만 해제. 명백한 4xx는 미확인 잠금을 적용하지 않는다.
- **사전 코드 검수 추가 발견:** `requestKey()`가 UUID를 소문자로 생성하나 Java `CanonicalReceipt/CanonicalShipment`은 Locale.ROOT 대문자 정규화를 수행한다. 응답 키를 원문과 직접 비교하면 정상 결과를 잘못된 응답으로 분류하는 오류가 생기므로 **비교만 대문자 정규화**하고 요청 원문은 보존한다.
- **검증:** [PR #28](https://github.com/jeon950523/factoryflow/pull/28) Chrome/MySQL E2E에서 `route.fetch()`로 DB에 실제 반영한 뒤 `route.abort()`로 클라이언트 응답만 유실하는 테스트를 실행한다. [Frontend #43732644025](https://github.com/jeon950523/factoryflow/actions/runs/43732644025) Node42/Chrome21/IndexedDB30, [실DB #43732644015](https://github.com/jeon950523/factoryflow/actions/runs/43732644015) E2E3 모두 성공했다. [병합 main Frontend #43733088956](https://github.com/jeon950523/factoryflow/actions/runs/43733088956) 및 [실DB #43733088860](https://github.com/jeon950523/factoryflow/actions/runs/43733088860)에서도 성공했다. [TASK-020 보고서](task-reports/TASK-020-쓰기재시도-멱등성-검수.md)에 상세 근거를 보존한다.
- **한계/재발 방지:** `sessionStorage`는 같은 탭 세션에만 유효. 재고가 불확실한 네트워크 쓰기는 새 키로 자동 재발행하지 않는다. 대규모 운영 환경에서는 서버측 키로 조회/재개하는 API 및 관리 정책 필요.

### TS-009 후속 코드 검수 — 확정 실패 후 영구 잠금 방지

재전송 이전의 status 0은 미확인으로 잠가야 하지만, 이후 서버가 400/404 또는 출고재고부족 409처럼 **그 키의 미처리**를 명확히 반환한 경우에도 무한 잠금하면 사용자가 데이터를 정정할 수 없는 문제가 된다. 따라서 `isConfirmedWriteRejection()`을 별도로 두고 해당 경우에만 pending·sessionStorage를 해제하고 새 키를 부여한다. 401/403/IDEMPOTENCY_KEY_CONFLICT는 원본이 이미 처리됐을 가능성이 있으므로 해제 대상에서 제외한다. [PR #28](https://github.com/jeon950523/factoryflow/pull/28)의 [실 MySQL Chrome #43732644015](https://github.com/jeon950523/factoryflow/actions/runs/43732644015)에서 미전송→409 확정 거절→새 키 발급을 3번째 E2E로 재현·검증했다.

## TS-010 · Vercel 신규 배포 거절: 요청 제한 (TASK-020)

- **재현/증거:** TASK-020 코드 [PR #28](https://github.com/jeon950523/factoryflow/pull/28)을 `main 8cb86179294ca702b5d0ad3154b3efca46c3052b`에 병합한 뒤 [해당 커밋 상태](https://github.com/jeon950523/factoryflow/commit/8cb86179294ca702b5d0ad3154b3efca46c3052b)는 Vercel **failure**, 설명은 **`Deployment rate limited — retry in 24 hours.`**이다.
- **원인 판정:** Vercel 배포 요청 제한으로 새로운 정적 웹 배포가 거부됐다. **정확한 요금제·쿼터 잔여량·원인 제공 파일/요청 수는 확인되지 않았다.** GitHub CI 성공이나 코드 결함으로 취급하지 않는다.
- **기능 검증은 별개:** [병합 main Frontend #43733088956](https://github.com/jeon950523/factoryflow/actions/runs/43733088956) Node42/Chrome21/IndexedDB30 성공, [MySQL #43733088860](https://github.com/jeon950523/factoryflow/actions/runs/43733088860) 3/3 성공(취소 job 재실행 이후). Vercel 배포 실패 상태에서 **공개 UI 최신 버전의 동작은 보증하지 않는다.**
- **미해결/복구:** 제한 해제 후 Vercel에서 실패한 최신 commit 배포를 다시 시도해 배포 SHA 및 공개 사이트 HTTP/Chrome 동선을 확인해야 한다. 자동 복구/재시도 여부는 미확인. 현재 배포 성공으로 표시하지 않는다.
- **재발 완화:** PR·브랜치 커밋을 불필요하게 자주 만들지 않고 작업과 문서 갱신을 배치한다. 배포 빈도/트리거·할당량을 실제 Vercel 설정에서 검토하고, 인프라 재설계는 별도 결정한다.
