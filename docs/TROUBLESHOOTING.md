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
- **검증 증거:** [PR #26](https://github.com/jeon950523/factoryflow/pull/26)의 최종 HEAD Frontend/실 MySQL E2E 성공을 확인한 후 완료 판정, [TASK-019 보고서](task-reports/TASK-019-품질검사-명시적확정-검수.md).
- **재발 방지:** 유사한 한국어 레이블은 `exact:true` 또는 명시적 `label`과 안정적인 ID를 사용하고, UI 기본값이 사용자 승인/재고 변동을 암묵적으로 수행하지 않는지 검토한다.
