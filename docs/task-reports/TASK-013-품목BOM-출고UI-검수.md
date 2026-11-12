# TASK-013 — 품목·BOM 및 완제품 출고 UI 시니어 검수

검수 기준 기능 SHA: `6523464d946e59032980cb2106a2175a4d891111`  
상태: **Vue UI·Chrome·MySQL E2E PASS / Vercel 운영 반영·사용성 시각 검수는 별도**  
실제 도메인: https://factoryflow.kro.kr

## 1. 기능 목적

TASK-010~012에서 대시보드, 생산지시, 재고·LOT 화면을 업무형으로 전환한 이후 남은 품목/BOM 및 출고 화면의 **좌우 카드 2개 중심 레이아웃**을 수정한다. 품목·원자재 구성과 실제 출고 기록에 필요한 데이터를 우선 표시하고, 원래 도메인/멱등성 규칙을 보존하는 것이 범위다.

실제 SAP/Odoo 특정 화면을 복제한 것이 아니라, 단일 공장·단일 창고 MVP가 지원하는 **조회 → 선택 → 상세/업무 처리** 흐름을 데이터 표로 구성했다.

## 2. 전체 호출 흐름

### 품목/BOM

1. Vue Router `/items` 진입 → `ItemsView.vue` → `GET /api/v1/items?itemType=...&keyword=...&page=...&size=15`로 품목 마스터 목록.
2. 유형·검색·페이지와 선택 품목 ID를 URL 쿼리로 보존하여 브라우저 새로고침/직접 접근 가능.
3. 품목 선택 시 `/items?itemId=ID`로 이동 → 기존 실제 `GET /api/v1/items/{id}` 단건 API로 등록 속성 확인. 선택이 빠르게 바뀌면 이전 상세 조회는 `detailGate`에서 무효화.
4. 완제품은 `GET /api/v1/boms/{itemId}`로 BOM 구성 조회 후, `GET /api/v1/items/lookup?ids=...` 배치 조회로 원자재 SKU/이름 표기. 없으면 BOM 미등록 안내, 404 외 오류는 표시.
5. ADMIN이 품목 등록하면 기존 `POST /api/v1/items` 성공 후 `?itemId=created.id`로 복귀. BOM은 기존 `POST /api/v1/boms` 규칙 재사용하며 선택 원자재와 양의 정수 수량/중복 재료를 UI에서도 검증.
6. 원자재 선택 시 `/inventory?itemType=RAW_MATERIAL&itemId=...`로 이동하여 TASK-012의 실제 재고·LOT 조회와 연결.

### 완제품 출고

1. Vue Router `/shipments` → `GET /api/v1/shipments?page=...&size=15` 조회. `lookupItemNames`로 완제품명 표시.
2. 출고 선택 시 `/shipments?id=ID` → `GET /api/v1/shipments/{id}`에서 `requestKey`, `quantity`, `allocations: [{lotId, quantity}]` 등의 실제 기록 조회.
3. 상세에 **출고 요청 키, 완제품, 출고 수량, 실제 LOT ID별 차감량과 합계** 표시. 합계가 출고 총량과 다른 경우 경고를 노출하지만, 저장 로직을 UI에서 임의 변경하지 않는다.
4. `/inventory?itemType=FINISHED_GOOD&itemId=...` 바로가기는 해당 완제품의 **현재 LOT 잔량**을 보여준다. 출고 당시 배분 기록과 혼동하지 않는다.
5. ADMIN이 신규 출고를 실행하면 기존 `POST /api/v1/shipments`로 전송, 성공 후 `/shipments?id=created.id`에서 상세 조회. 요청 수량 부족·멱등성 중복·FIFO는 기존 백엔드/데모 엔진이 검사한다.

## 3. 수정 파일과 책임

| 위치 | 책임 |
|---|---|
| `frontend/src/views/ItemsView.vue` | 전체 너비 품목 마스터·선택 상세·BOM 구성표, 직접 링크/필터 복원, 기존 품목/BOM 등록 |
| `frontend/src/views/items-workbench.css` | 품목·BOM 전용 데이터 표·상태·포커스·업무 패널 |
| `frontend/src/views/ShipmentsView.vue` | 출고 이력/요청 키/LOT 배분 수량표, 상세 URL 복원, 기존 출고 폼 유지 |
| `frontend/src/views/shipments-workbench.css` | 출고 전용 테이블/상세/배분 영역 |
| `frontend/src/assets/base.css` | 토스트 z-index 60→35, 모달 backdrop z-index 40보다 낮춰 입력 클릭 가림 문제 해결 |
| `frontend/tests-demo/browser-demo.spec.js` | 품목/BOM 조회·신규 등록, 출고 LOT 배분·직접 URL, 새로고침 후 IndexedDB 저장 지속 |
| `frontend/tests/item-labels.spec.js` | 단건 품목 GET fixture와 원자재명 배치 조회 정확도 |
| `frontend/tests/latest-selection.spec.js` | 빠른 전환 시 마지막 품목/출고 선택 결과만 표시, 단건 품목 GET fixture 추가 |
| `frontend/tests/security-shell.spec.js` | VIEWER의 품목/BOM/출고 조회 허용·쓰기 버튼 비노출 |

Java·JPA·MySQL 스키마, 인증 경계, 데모 IndexedDB 제조 처리 엔진은 변경하지 않았다.

## 4. 주요 예외·검증

- **없는 BOM:** 해당 완제품에 등록된 BOM이 없다면 새 BOM 등록 안내. 서버 404를 구성표 '빈 결과'로 처리하며 기타 오류는 경고.
- **잘못된 품목 ID:** URL `itemId`는 안전한 양의 정수만 직접 조회 대상으로 수락.
- **연속 선택/느린 조회:** `createLatestRequest`로 이전 요청 무효화. Chrome fixture는 2번째 품목/출고 선택이 첫 번째 지연 응답에 덮이지 않는지 재확인.
- **출고 재고 부족:** UI가 출고 가능 수량을 임의로 보장하지 않고 기존 서버/데모의 전체 롤백 규칙 유지.
- **출고 요청 키/멱등성:** 기존 API의 `requestKey` 유지. 상세에서 다시 표시해 이력 추적성 복원.
- **조회 전용 사용자:** 읽기 화면은 제공하지만 ADMIN만 품목/BOM 등록·출고 확정 버튼이 보이도록 한다. 실제 쓰기 거부는 Spring Security.
- **BOM 수정/버전 관리·출고 취소/반품·LOT 코드 배분:** 미구현. 출고 응답은 LOT ID만 제공하며 코드/이력은 임의 생성하지 않음.
- **데모와 운영:** Vercel은 IndexedDB 체험 모드이며, CI의 실제 MySQL 검증과 동일한 서버 배포를 뜻하지 않음.

### 최종 CI 증거

| 검사 | 증거와 결과 |
|---|---|
| [Frontend #43321664299](https://github.com/jeon950523/factoryflow/actions/runs/43321664299) | **SUCCESS**, Node **37/37**, Vite production build, Chrome fixture **14/14**, IndexedDB Chrome **19/19** |
| [Fullstack #43321663928](https://github.com/jeon950523/factoryflow/actions/runs/43321663928) | **SUCCESS**, Spring Boot+MySQL 8.4 실제 기동, Chrome E2E **2/2** |

## 5. 시니어 검수 — 판단

**잘한 점:** 화면을 정보/업무 순서로 정렬, 기존 API의 실제 계약만 사용, BOM 자재 소요량과 출고 LOT allocation 구분, 원래 멱등성·FIFO·트랜잭션 보존, 검증 가능한 상세 링크.

**위험한 점:** `ItemsView.vue`와 `ShipmentsView.vue`가 조회·상세·등록 팝업을 모두 소유하므로 향후 업무 규칙이 커질 경우 상태가 비대해질 수 있다. 또한 직접 품목 상세 링크는 목록과 별도로 GET을 한 번 더 호출한다.

**즉시 수정:** (1) 새 '상세 보기' 버튼이 기존 품목명/출고 키 버튼과 접근성 이름 중복 → **RouterLink**로 역할 분리; (2) 상세 링크 단건 GET을 추가했지만 브라우저 fixture에 누락되어 선택 테스트 실패 → 실제 API와 동일한 단건 GET fixture/레이스 테스트 보완; (3) 출고 요청 키를 새 상세에서 빠뜨림 → 복구; (4) 품목 등록 성공 토스트(z-index60)가 BOM 등록 모달(z-index40) 버튼을 막음 → 토스트 레이어를 35로 낮춰 실제 클릭 동선 수정.

**기능·효율 리팩터링:** 기존 API/DB에 없는 출고 LOT 코드 동시 조회 또는 BOM 버전 관리를 임시 구현하지 않는다. 사용자 수·데이터량 증가 시 품목/BOM 목록·상세 컴포넌트 분리와 상세 API 호출 캐싱을 측정 후 검토한다.

**실무 기준:** 현장 작업자의 검수·권한 승인·조달·BOM 버전·출고 취소 등은 추가 설계가 필요하다. 1차 MVP/포트폴리오는 기능과 검증 근거를 설명하는 수준으로 적절하나 **상용 ERP 구현 완료라고 주장하면 안 된다.**

## 6. 테스트 명령 / 직접 확인

- `cd frontend && npm ci && npm test && npm run build`
- `npx playwright test --config playwright.config.js` — fixture Chrome 14건
- `npx playwright test --config playwright.demo.config.js` — 프로덕션 번들/실제 IndexedDB Chrome 19건
- 실제 서버: `.github/workflows/fullstack-ui-ci.yml`의 Java21·MySQL8.4 + Chrome 2건
- 배포 확인: `https://factoryflow.kro.kr/items`에서 완제품 → BOM 표 → 필터/새로고침, `https://factoryflow.kro.kr/shipments`에서 출고 키 → LOT 배분표 → 현행 LOT 재고 이동.
- **Vercel CDN의 최신 커밋 반영과 PC의 육안·키보드 사용성은 CI와 별도로 확인해야 한다.**

## 7. 30초 직접 설명 요약

“기존 품목/BOM과 출고 화면은 좌우 카드로 나뉘어 데이터가 압축되어 있었습니다. 품목 기준정보·BOM 구성·출고 이력을 전체 너비 업무 표로 정리하고, 선택 상세와 LOT별 실제 배분 수량을 하단에 표시하도록 개선했습니다. 품목·출고는 URL로 직접 다시 열 수 있고, 기존 Spring Boot/MySQL 도메인 규칙과 브라우저 데모는 변경하지 않은 채 Chrome·실DB 회귀검사를 통과했습니다.”

이해 점검: (1) `BOM.qtyPerUnit`과 실제 생산지시 `requiredQty`는 어떻게 다른가? (2) 출고 시 LOT allocation 수량 기록과 현재 LOT 재고를 왜 구분해야 하는가?
