# TASK-005C — 품목 ID 묶음 조회·관리자 UI 실명 표시·Chromium 스모크 검증

> 상태: 기능/테스트 구현 및 실제 CI 검증. Java 21 + Testcontainers MySQL **53/53 성공**, Node **10/10 성공**, GitHub Chrome 스모크 **5/5 성공**.
> 날짜: 2026-11-11 / 작업 브랜치 feature/task-005c-batch-item-labels

## 1. 목적과 이전 코드 문제
TASK-005B에서 등록 폼의 첫 100개 품목 제한은 제거했지만, 생산지시·출고·BOM의 *표시 이름*은 여전히 GET /items?page=0&size=100 기반 캐시를 사용했다. 101번째 뒤에 등록된 품목을 검색해서 작업에 사용할 수 있어도 나중에 상세 조회 시 "완제품 #105"로만 보일 수 있었다. 원자재 ID가 많아지면 작업자가 원자재를 알아보기 어려운 실제 UX 결함이다.

**이번 목표:** 임의의 기존 품목 ID 여러 개를 DB에서 효율적으로 resolve하고, 업무 이름 표시만 실패했을 때도 원래의 조회/조작이 중단되지 않게 한다. 동시에 실제 Chrome에서 Vue 렌더링과 작업 흐름 스모크를 자동화한다.

## 2. 전체 호출 순서

- 생산지시/출고 목록 GET → 표시되는 행들의 finishedItemId 수집 → frontend/src/lib/itemNames.js의 lookupItemNames → GET /api/v1/items/lookup?ids=105,205 → ItemController.lookup → ItemService.lookup → ItemRepository.findAllById → MySQL 한 번의 IN 조회 → ItemResponse 목록 → { id: item } 맵 → 각 Vue 목록/상세에 실제 이름 표시.
- 생산지시 상세 GET /work-orders/{id} → finishedItemId 및 materials[].materialItemId를 함께 resolve → 완제품명과 자재명·SKU 표시.
- BOM 상세 GET /boms/{id} → components[].rawItemId resolve → 원자재명·SKU 표시.
- 이름 API만 실패하면 상태/수량/업무 목록은 유지, "원자재 #205" 등 fallback과 경고 표시.

## 3. 변경 파일별 역할

- backend/.../item/ItemController.java: 정적 GET /lookup 라우팅. 기존 GET /{id}와 별개.
- backend/.../item/ItemService.java: 1~100개 양수 ID 검증, 입력 중복 제거, findAllById 결과 매핑, 요청 순서 유지. 누락된 ID는 생략.
- backend/src/test/.../ItemBomApiIntegrationTest.java: 105개 품목 생성 후 105번째 품목과 첫 번째 품목을 한 호출로 조회, 중복 요청 제거/입력 오류 검증.
- frontend/src/lib/itemNames.js + itemNames.test.js: 50개씩 HTTP 요청 묶음, id→sku/name 사전, 빈/중복/취소 신호 테스트.
- frontend/src/views/{Orders,Shipments,Items,Dashboard}View.vue: 필요 ID만 묶음 조회, 목록/상세에 이름·코드 노출, 조회 실패해도 원래 업무 데이터는 표시하는 예외 경계.
- frontend/tests/item-labels.spec.js + playwright.config.js: 실제 Chrome에서 UI 구성/클릭/품목명 표시/서버검색/장애 대체를 테스트.
- .github/workflows/frontend-ci.yml: Ubuntu 24.04 호스팅 러너의 내장 Google Chrome을 사용하는 Playwright 단계 및 실패 시 증거 보존 설정.
- docs/learning/CORE_CONCEPTS.md, docs/design/ADMIN_UI_REFERENCES.md, README.md, 이 보고서: 핵심 이유·검수·후속 개선사항 누적.

## 4. 핵심 구현 판단 및 장단점

| 대안 | 장점 | 단점 | 판단 |
|---|---|---|---|
| 기존 첫 100개 목록 | 초기 간단 | 101번째부터 실제 이름 누락·불필요한 데이터 전송 | 폐기 |
| 행마다 GET /items/{id} | 엔드포인트 추가 불필요 | N행당 N번 호출, 네트워크/DB 부담 | 비추천 |
| **일괄 GET /lookup** | 필요한 ID만 1번 IN 조회, 여러 UI 재사용, REST 계약 단순 | 목록 API 후 추가 HTTP 왕복, ID 최대 100개 제한 | 현재 채택 |
| 생산지시/출고 DTO에 itemName JOIN | 한 HTTP 요청으로 완료 | 생산 도메인의 품목 의존 증가, 여러 DTO 변경·N+1 성능 점검 필요 | 추후 비교 |

- 요청에 중복 ID가 있어도 결과에 하나만 내보내며 요청 순서를 유지한다.
- 백엔드 API는 한 요청 최대 100개, Vue는 쿼리 길이를 줄이기 위해 50개씩 분할한다. batch 간 Promise.all로 병렬 호출 가능.
- 실측 부하 테스트는 아직 없으므로 "성능 최적화 완료"가 아니라 **N+1 요청을 방지하는 구조를 검증**했다고 기록한다.
- 중요: 재고 수량·예약·품질검사 상태 전이 및 MySQL 트랜잭션 로직은 전혀 변경하지 않았다.

## 5. 테스트: 실제 실행과 한계

**백엔드:** GitHub Actions https://github.com/jeon950523/factoryflow/actions/runs/43042942803 — Java 21, MySQL 8.4 Testcontainers 실제 JUnit XML 12개 스위트 합계 **53 tests / 0 failures / 0 errors / 0 skipped**. 105건 품목 데이터를 준비해 첫 페이지 이후 ID를 요청한 새 lookup 통합 테스트 포함.

**프론트:** GitHub Actions https://github.com/jeon950523/factoryflow/actions/runs/43044876163 — Node 테스트 **10/10 통과**, Vite 프로덕션 빌드 성공, 실제 GitHub 호스팅 Ubuntu 24.04에 설치된 Google Chrome 156으로 Playwright UI 테스트 **5/5 통과**.
- 생산지시 목록에서 101번째 이후 완제품 이름 노출, 상세에서 구성 원자재명·SKU 노출.
- 출고 목록/상세에서 동일 품목명 확인.
- BOM 상세에서 원자재명·코드 확인.
- 생산지시 생성 폼에서 검색형 선택.
- lookup API가 503이면 목록·작업을 유지한 채 완제품 ID 대체 표시.

**실제 실패·해결:** 최초 UI CI https://github.com/jeon950523/factoryflow/actions/runs/43043829212 에서 Playwright 1.56.1이 GitHub 최신 Ubuntu 26.04 Chromium 설치를 지원하지 않아 실행 전 실패. Ubuntu 24.04 러너로 고정하고, 오래 걸리는 브라우저 설치 대신 러너가 기본 제공하는 Chrome을 사용하도록 변경해 재검증 성공.

**반드시 구분:** Chrome 스모크는 *실제 Chrome 브라우저 + 고정 API 응답 Fixture* 테스트이며, MySQL 통합 테스트는 *Spring Boot MockMvc + 실제 Testcontainers MySQL*이다. 두 환경을 한 번에 연결한 배포형 E2E, 사용자 로그인/권한, Vercel/Oracle 운영망을 검증한 것은 아니다.

## 6. 시니어 기준 코드 검수

**잘한 점:** 대량 품목의 정확한 이름/코드 표시, DB 1회 IN 조회 및 프론트 묶음 요청, 빈·중복 ID 방어, optional 이름 정보 장애가 핵심 업무 UI를 막지 않게 오류 경계 분리, 실제 Chrome 클릭 회귀 테스트와 실제 DB 테스트 병행.

**주의/위험:** 목록 API 이후 이름 API가 추가되므로 화면 최초 렌더링과 전체 왕복은 다소 느려질 수 있다. 현재 각 View가 별도 이름 맵을 보유해 화면 이동시 중복 요청이 가능. itemName이 이후 변경되면 같은 화면 캐시가 오래될 수 있음(현재 품목 수정 미지원). 백엔드 조회 API는 인증/인가 전 외부 공개 금지.

**즉시 수정한 문제:** 이름 조회 실패 시 목록 자체가 오류로 바뀌는 경로를 제거해 ID fallback과 오류 안내를 유지했다. Ubuntu 26.04 Playwright 설치 호환성도 CI 환경에서 수정.

**다음 작업 전/다음 UI 개선:** Mock Fixture가 아닌 실제 Spring Boot+MySQL 연결에서 브라우저 전체 업무 동선 검증, 리스트 전환 연속 클릭 시 요청 취소/최신 응답 우선 처리, 키보드/스크린리더 접근성 점검.

**2차 리팩터링 후보:** 목록 DTO에 itemName 직결 방식과 REST batch 방식의 DB/네트워크 비용 비교; 여러 페이지에서 공유하는 품목명 캐시(무효화 정책 필요); Playwright 의존성은 현재 CI에서 고정 버전을 임시 설치하므로 package.json/package-lock.json에 개발 의존성으로 기록해 재현성을 더욱 강화.

**지금 불필요:** 메시지 브로커/Redis/MSA/별도 검색엔진. 단순 ID 조회에 과도한 인프라 추가는 학습/운영 비용을 높임.

## 7. 본인이 나중에 설명할 핵심

"입고·생산지시 폼의 100개 제한을 해결한 다음에도 목록/상세의 이름 캐시가 앞 100개에 묶여 있음을 발견했습니다. 화면에 실제 필요한 품목 ID만 모아서 조회하는 전용 API를 구현하고, Java Spring의 JPA IN 쿼리로 여러 개를 한 번에 읽도록 했습니다. UI는 실명 매핑에 실패하더라도 업무 자체는 계속 가능하도록 ID fallback을 적용했습니다. MySQL 53개, 프론트 단위 테스트 10개, 실제 Chrome UI 5개가 통과했습니다."
