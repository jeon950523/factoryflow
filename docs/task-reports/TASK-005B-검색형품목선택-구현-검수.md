# TASK-005B — ERP형 검색 가능 품목 선택기: 구현 및 시니어 검수

> 구현 브랜치: feature/task-005b-searchable-items
> 검증: 2026-11-11 / Frontend CI **Node 6/6 + Vite 빌드 성공**, Backend CI **Testcontainers MySQL 52/52 통과**

## 1. 기능의 목적
기존 Vue 관리자 콘솔의 품목 선택 필드가 처음 100개 품목만 로딩해 이후 등록된 원자재/완제품을 선택할 수 없던 문제를 개선했다. 제조 ERP의 검색/필터/페이지 업무 패턴에 맞춰, 제품 수가 늘어나도 입고·생산지시·출고·BOM 처리를 계속 수행할 수 있게 만드는 것이 목적이다.

## 2. 전체 호출 순서
1. 입고/생산지시/출고/BOM 생성 화면에서 ItemSearchSelect 열기.
2. 검색어·품목유형(RAW_MATERIAL/FINISHED_GOOD)·페이지 번호로 GET /api/v1/items 호출.
3. lib/api.get(path, query, { signal })가 AbortController 취소 신호를 fetch에 전달.
4. Spring ItemController → ItemService.search Specification + PageRequest → MySQL → PageResponse 반환.
5. component는 이전 응답과 신규 응답이 뒤바뀌지 않도록 요청 번호를 확인하고, 10개씩 목록과 전체 건수를 렌더링.
6. 선택한 품목 ID를 v-model로 부모 양식에 전달; 부모 View의 POST 명령은 기존 서버 상태/검증/트랜잭션을 그대로 호출.

## 3. 변경 파일과 역할
- frontend/src/components/ItemSearchSelect.vue: 품목 종류별 검색/페이지/선택/취소/요청 실패 재시도. 선택 결과는 품목 객체가 아닌 ID를 부모에 전달.
- frontend/src/lib/api.js: GET 호출 옵션을 확장해 fetch signal 전달. 기존 호출과 호환.
- frontend/src/lib/api.test.js: 검색 파라미터 전달·취소 구분 테스트 2건 추가.
- frontend/src/views/InventoryView.vue: 입고 폼 원자재 선택을 공통 컴포넌트로 교체하고 불필요한 첫 100개 원자재 조회 제거.
- frontend/src/views/OrdersView.vue: 생산지시 폼 완제품 검색으로 교체.
- frontend/src/views/ShipmentsView.vue: 출고 폼 완제품 검색으로 교체.
- frontend/src/views/ItemsView.vue: BOM 원자재 다중 선택에 검색형 컨트롤 적용; 기존 첫 100개 목록 때문에 추가 등록을 차단하지 않음.
- frontend/src/assets/base.css: 선택/검색 결과/페이지 이동/필수 레이블 스타일.
- backend/src/test/java/com/factoryflow/ItemBomApiIntegrationTest.java: 105개 동일 조건 품목 중 11페이지의 5개까지 실제 MySQL에서 조회 확인.
- docs/design/ADMIN_UI_REFERENCES.md, docs/learning/CORE_CONCEPTS.md, README.md: 업무 UX 참고, 검증 범위, 학습/리팩터링 내용 누적.

## 4. 설계 선택 이유와 대안
- **단순 select 100개 vs 서버 조회:** 고정 100개는 데이터가 늘어나는 순간 업무 대상이 누락. 전체 N개 다운로드는 대규모 데이터에 불리. 검색조건과 페이지를 서버에 맡기면 전송량과 선택 누락 문제를 함께 줄인다.
- **공통 선택 컴포넌트:** 각 폼에서 선택 로직을 복사하면 검색·취소·에러 처리의 버그가 퍼진다. 공통 컴포넌트 하나로 같은 UX를 제공한다.
- **선택 ID와 검색 목록 분리:** 다른 페이지 검색/닫기/재열기를 해도 사용자의 선택값은 명시적 변경 또는 지우기 전까지 유지한다.
- **AbortController + 요청 시퀀스:** 요청 취소가 성공하더라도 이미 처리된 응답이 도착할 수 있어 시퀀스 검사를 병행한다.
- **중첩 form 제거:** 입고 등 기존 form 내부에 검색 form을 중첩하지 않고 검색 div/버튼/Enter key 처리로 이벤트 간섭을 막는다.
- **백엔드 업무 규칙 불변:** 원자재/완제품 식별과 최종 검증은 서버에서 계속 담당. 프론트는 검색/선택의 UX만 개선한다.

## 5. 주요 예외와 검증
- 페이지 변경 도중 빠르게 다른 검색을 시작하면 오래된 요청은 취소되고 최신 조건 결과만 표시.
- 네트워크 오류 시 메시지와 재시도 버튼, 실패한 페이지 번호 유지.
- 검색에 맞는 품목이 없으면 빈 결과 표시. 취소·선택 초기화는 실제 저장 요청을 하지 않음.
- 키보드 조작은 일반 버튼/검색 입력의 자연스러운 Tab·Enter·ESC 동작을 활용.
- 실제 프론트 CI: https://github.com/jeon950523/factoryflow/actions/runs/43036690995 — Node 테스트 **6/6**, Vite 빌드 성공.
- 실제 백엔드 CI: https://github.com/jeon950523/factoryflow/actions/runs/43036048446 — JUnit XML **52/52, 실패·오류·건너뜀 0**, 신규 105건 검색 페이지 통합 검증.
- **검증하지 않은 항목:** 실제 브라우저 클릭/키보드/스크린리더·네트워크 지연 시각 QA, 운영 배포 환경, 1,000개 이상 목록의 성능 측정.

## 6. 시니어 기준 검수 — 장점/위험/리팩터링
- **잘한 점:** 100개 선택 누락 해결, 기존 페이지 API 재사용, 범용 컴포넌트, 요청 역순 응답 방지, UI에 임의 재고 데이터 추가하지 않음, 중첩 폼 및 오류 페이지 재시도 검수.
- **즉시 수정한 문제:** 중첩 form이 생기지 않도록 검색 폼 제거, 잘못된 재시도 페이지 방지, 품목 레이블 접근성 개선.
- **현재 위험:** 폼의 선택 문제는 해결했지만 기존 일부 목록/상세 이름 표시용 캐시는 첫 100건만 가져오므로 나중에 등록된 제품이 ID로 보일 수 있음. 삭제/변경된 품목 ID 복원 시 선택 레이블이 코드/이름이 아닌 ID일 수 있음.
- **다음 작업에서 개선:** 브라우저 QA와 폼 자동화 테스트 추가, 선택값의 ID→이름 재조회, 대량 품목 목록의 검색/필터 일관성.
- **2차 리팩터링:** UI fixture 공통화, 검색 입력 디바운스·데이터 캐시(성능 근거 확보 후), 폼 컴포넌트 테스트 환경 도입.
- **지금 불필요:** 관리자 UI 프레임워크 전면 교체, MSA, Redis 캐시, 외부 ERP 제품 모방.

## 7. 나중에 직접 설명할 요약
첫 100건만 내려받던 드롭다운에서 101번째 품목을 선택할 수 없는 문제가 생겨 서버 검색·페이지 방식의 공통 선택기로 변경했다. 빠른 검색의 순서 역전은 AbortController와 요청 시퀀스로 막았다. 실제 MySQL에 105개 품목을 넣어 마지막 검색 페이지를 확인했고, 기존 제조 트랜잭션 로직은 변경하지 않았다.
