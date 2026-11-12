# TASK-012 — 재고·LOT·원자재 입고 UI 시니어 검수

- 기능/테스트 기준 커밋: `f050aadf9e7ba6edf1203e341711906cb078b361`
- 결과: **코드·Chrome·실DB CI 검증 완료, 실제 공개 Vercel 시각/사용성 QA는 별도**
- 범위: Vue 3, CSS, Chrome 회귀 테스트. Java 21·Spring Boot·MySQL·IndexedDB 제조 엔진 불변.

## 1. 기능의 목적

기존 재고 화면은 비슷한 디자인의 카드 안에 품목 재고표와 LOT 상세표를 각각 두고, 선택 원자재 입고는 범용 팝업으로 분리되어 있었다. 새 운영 개요/생산지시 UI와 같은 **업무 표 중심 정보 구조**로 통일하며 재고 수치·예약고·가용고를 먼저 읽고 상세 LOT/입고까지 최소 클릭으로 이어지게 한다.

새로운 설비·재고 부족 임계치·실제 LOT FIFO 배분 이력을 조작하지 않고, 현재 구현된 `InventoryController`의 `itemType`, `itemId`, `page`, `size`만 활용한다.

## 2. 전체 호출 순서

### 품목 재고 확인 → LOT 상세

1. Vue Router `/inventory` → `InventoryView.vue` → `load()`.
2. `GET /api/v1/inventory?itemType=...&page=...&size=15`에서 품목명·SKU·유형·현재고·예약고·가용재고를 응답받고 한 표에 표시. 서버는 `InventoryReadService.summary()`에서 SQL SUM 집계, 브라우저 데모는 별도 IndexedDB 시뮬레이션.
3. 유형 필터를 변경하면 `/inventory?itemType=RAW_MATERIAL` 등의 쿼리로 저장. 페이징도 `?page=...`로 저장하며 선택 품목은 초기화한다.
4. 품목명/LOT 보기 클릭 시 `/inventory?itemId=ID`(기존 유형/페이지 유지)로 이동하여 `loadLots()` 호출.
5. `GET /api/v1/inventory/lots?itemId=...&page=...&size=15`로 LOT 코드를 입고일·LOT ID의 오름차순으로 보여준다. 선택 품목이 현재 목록 밖이면 별도 `GET /api/v1/inventory?itemId=ID&page=0&size=1`로 메타데이터·재고 요약을 조회.
6. 개별 LOT의 `cumulativeInQty`, `onHand`, `reserved`, `available` 값을 그대로 표시. 이는 실제 재고 이동 이력이나 생산지시별 예약 allocation의 상세 증거와 구분해야 한다.

### 원자재 입고

1. 상단 **원자재 입고** 또는 원자재 LOT 상세의 **선택 원자재 입고**에서 기존 등록 폼을 연다.
2. 선택 원자재가 있으면 `preselectedItem`으로 SKU·이름을 보여준다. 선택이 달라져도 기존 검색형 품목 선택 기능은 유지.
3. 원자재 ID, LOT 코드, 양의 정수 수량을 검증하고 기존 `POST /api/v1/stock-receipts` 호출. 기존 `requestKey`로 멱등성을 확보하며 저장 중 중복 제출/취소를 차단.
4. 성공하면 서버/데모 응답의 `created.itemId`를 신뢰해 해당 `?itemType=RAW_MATERIAL&itemId=...`로 이동. 이미 같은 경로면 목록과 LOT를 재조회.
5. 실패하면 입력값·동일 `requestKey`를 보존해 중복 입고 위험 없이 재시도를 지원한다. 실제 중복 보장은 백엔드 DB/데모 엔진에 의존한다.

## 3. 각 파일 역할

| 파일 위치 | 변경·연결 |
|---|---|
| `frontend/src/views/InventoryView.vue` | 업무 툴바·전체 재고표·LOT 상세·입고 폼 연결 및 URL 필터·비동기 응답 경쟁 방어 |
| `frontend/src/views/inventory-workbench.css` | 페이지 범위 ERP형 테이블·툴바·하단 LOT 간결한 레이아웃과 키보드 포커스 |
| `frontend/src/components/ItemSearchSelect.vue` | 선택적 `preselectedItem`으로 이미 선택된 원자재 SKU·이름 표시. 기존 미지정 호출은 영향 없음 |
| `frontend/tests-demo/browser-demo.spec.js` | 유형 필터, 상세 직접 링크 및 새로고침, 기존 LOT 추가 입고, 신규 LOT 입고 및 IndexedDB 지속 |
| `frontend/tests/security-shell.spec.js` | VIEWER가 LOT은 읽되 원자재 입고/선택 입고는 실행할 수 없는지 검증 |

## 4. 예외 상황과 테스트

- **데이터 범위:** 상단의 가용재고 0은 현재 15개 조회 페이지에 한정. 0이라고 해서 발주량이나 안전재고 기준으로 부족하다는 뜻이 아님.
- **LOT 조회 경쟁:** `createLatestRequest` 및 AbortSignal로 이전 선택 응답이 새 선택을 덮지 못하도록 방어.
- **화면 이탈/언마운트:** `mounted` 검사로 비동기 `load()` 종료 뒤 언마운트된 컴포넌트에서 `loadLots()`가 새로 시작되지 않도록 방어.
- **특정 ID 직접 링크:** 현재 타입/페이지에 없으면 `/inventory?itemId=...` 개별 집계 API로 정확한 이름/재고 요약을 읽는다. 요약 요청이 실패해도 LOT 데이터 조회 자체를 막지 않고 품목 ID로 표시 가능.
- **입고 재전송:** 저장 중 버튼·닫기를 차단하고, 네트워크 장애 시 같은 요청 키 유지. 멱등성의 최종 방어는 기존 서버/데모 엔진.
- **VIEWER:** UI 쓰기 버튼 숨김. 실제 API의 권한은 Spring Security 서버가 최종 검사.
- **데모 대 실DB:** IndexedDB 시뮬레이션의 수량 반영 검증과 실제 MySQL 트랜잭션 E2E는 별개.
- **직접 확인 방법:** `https://factoryflow.kro.kr/inventory` → '원자재' 필터 → '산업용 강판' LOT → 선택 원자재 입고 → 동일 LOT 코드로 5EA 입고 → 누적 입고/현재고 확인 → 새로고침 → IndexedDB 값 유지.

### CI 근거

- [Frontend #43313017588](https://github.com/jeon950523/factoryflow/actions/runs/43313017588): **SUCCESS**, Node 37/37, Vite build, Chrome fixture 13/13, IndexedDB Chrome 14/14.
- [Fullstack #43313017639](https://github.com/jeon950523/factoryflow/actions/runs/43313017639): **SUCCESS**, Java 21·Spring Boot·MySQL8.4 기동, 실제 업무 Chrome 2/2.
- 초기 [Frontend #43312704710](https://github.com/jeon950523/factoryflow/actions/runs/43312704710)도 Node 37/37, Chrome fixture 12/12, IndexedDB 14/14 PASS. 이후 언마운트 후 LOT 요청 방어 + VIEWER 테스트를 추가해 최종 CI 재실행.

## 5. 시니어 코드 검수

**잘한 점:** 업무 중심 표 우선 설계, 기존 API의 지원 필터만 사용, 목록 밖 선택 품목 메타데이터 직접 조회, 정확한 URL 및 새로고침 재진입, 선택 원자재 입고 기본값 처리, 요청 재시도 키 보존, 서버 데이터 정합성 불변.

**위험한 점:** 유형 필터/페이지와 선택한 상세 품목의 타입이 일치하지 않을 수 있다(직접 링크 의도). 목록과 상세의 대상 범위가 다름을 사용자에게 명확히 보여야 하며, 현재는 LOT 제목과 개별 SKU로 구분한다. LOT 코드와 이동 내역만으로 생산지시 예약 allocation 및 실제 출고 FIFO 근거를 추정하면 안 된다.

**지금 당장 고친 것:** 선택 품목이 목록 15개 밖에 있으면 이름/수량을 유실하던 기존 UI 한계를 직접 itemId 조회로 해결. 선택 원자재 입고 폼에 품목 ID 대신 SKU/이름을 보여주게 함. 비동기 언마운트 경계와 VIEWER 입고 버튼을 별도 검증.

**기능 리팩터링 후보:** `InventoryView.vue`는 목록·LOT·입고를 한 파일에서 관리한다. 유지보수 변경이 늘면 `InventoryList`·`InventoryLots`·`StockReceiptDrawer`로 분리 권장. 현재 단일 화면/API 계약이 안정적이므로 과도한 사전 분리는 보류.

**효율 리팩터링 후보:** 선택 품목이 목록에 없을 때 개별 재고 요약 GET을 한 번 더 호출한다. 이 추가 네트워크 호출은 이름/재고 정확성을 위한 비용이므로 유지한다. 실제 백엔드 성능이 문제로 측정될 경우 캐시·전용 상세 API로 최적화 가능. 측정 없는 선제 최적화는 피한다.

**운영 포트폴리오 판단:** 1차 MVP에는 충분히 실습 가능한 ERP 화면, 상용 MES/ERP 기준으로는 권한 승인 흐름·입고 검수자·발주정보·실사조정·감사 이력 등 추가 설계가 필요하다. 화면 디자인이 상용 MES 완성도까지 입증하는 것은 아님.

## 6. 요약 (직접 설명용)

“재고 화면을 카드형 UI에서 전체 재고표·LOT 상세표 중심으로 개편했습니다. 기존 재고 API의 유형 필터와 특정 품목 조회를 활용해 페이지 밖 품목도 정확히 조회하고, 원자재 입고 후 해당 LOT로 이동하도록 연결했습니다. 멱등성 요청 키와 기존 DB 처리 규칙은 유지했고, 브라우저 데모 및 MySQL E2E로 기능 회귀를 검증했습니다.”

이해 확인: (1) LOT 누적 입고량과 현재고가 달라지는 이유는? (2) 입고 실패 후 새로운 requestKey를 생성하면 어떤 문제가 생기는가?
