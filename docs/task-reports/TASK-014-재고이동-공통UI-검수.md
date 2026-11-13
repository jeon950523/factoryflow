# TASK-014 — 재고 이동 이력 및 공통 UI 통합 시니어 검수

- 작업 브랜치: `feature/task-014-movement-audit-erp-ui`
- PR: https://github.com/jeon950523/factoryflow/pull/21
- 기준: TASK-013 병합 `main` / `c726c86`
- 검사 증거: [PR #21 Checks](https://github.com/jeon950523/factoryflow/pull/21/checks)
- 실제 도메인: https://factoryflow.kro.kr (최신 Vercel 배포와 PC 육안 검수는 CI와 별도)

## 1. 작업 범위와 계약

백엔드 제조·재고 도메인 로직, JPA, MySQL 스키마, 세션 인증, 브라우저 IndexedDB 제조 엔진을 변경하지 않았다.

- 재고 이동 이력 조회: `GET /api/v1/stock-movements?itemId=...&page=...&size=20`
- 품목 선택: `GET /api/v1/items?keyword=...&page=...&size=10` (품목 유형 미지정 시 전체 검색)
- 직접 접근 선택 품목 정보: `GET /api/v1/items/{id}`
- 서버 계약: `StockMovementResponse` = `id`, `itemId`, `inventoryLotId`, `movementType`, `quantity`, `referenceType`, `referenceId`, `createdAt`.
- `ReferenceType`: `RECEIPT`/`WORK_ORDER`/`INSPECTION`/`SHIPMENT`; `MovementType`: `RAW_RECEIPT`/`RAW_CONSUMPTION`/`FINISHED_RECEIPT`/`FINISHED_SHIPMENT`.

## 2. 화면·공통 UX 변경

1. 이동 이력을 전체 너비의 업무표로 변경해 처리 시각·이력 ID·이동 유형·LOT ID·증감 수량·발생 업무·참조 ID를 표시한다.
2. 기존 최초 100건만 조회하던 목록 선택을 서버 검색·페이지 이동을 사용하는 공통 `ItemSearchSelect`로 교체한다. 컴포넌트의 `itemType`을 생략하면 전체 유형 검색, 기존 RAW/FINISHED 지정 시 종전처럼 해당 유형만 선택 가능하다.
3. `/movements?itemId=ID&page=N`으로 품목/페이지를 복원한다. 유효하지 않은 품목 ID는 API 조회에 사용하지 않는다. 마지막 페이지를 벗어난 URL은 유효 마지막 페이지로 자동 복귀한다.
4. `createLatestRequest`를 통해 늦게 도착한 품목/이력 응답의 덮어쓰기를 방지하고 언마운트 시 GET을 취소한다.
5. 저장된 원본 수량은 양수로 유지한다. 화면에서만 `RAW_RECEIPT`, `FINISHED_RECEIPT`는 '+', `RAW_CONSUMPTION`, `FINISHED_SHIPMENT`는 '−'로 표현한다.
6. `WORK_ORDER` 참조는 `/orders?id=...`, `SHIPMENT`는 `/shipments?id=...`로 연결한다. `RECEIPT`, `INSPECTION`의 참조 ID는 표시만 한다. 품목의 현행 LOT 화면은 `/inventory?itemId=...`로 연결하되 과거 재고와 혼동하지 않도록 명시한다.
7. `AsyncState`의 로딩/빈/오류 상태에 보조기기 상태 알림을 적용하고, `PageControls`의 페이지 이동 영역을 내비게이션으로 표기한다.

## 3. 변경 파일

| 파일 | 책임 |
|---|---|
| `frontend/src/views/MovementsView.vue` | 재고 이동 이력 표, URL 복원, 검색, 업무 참조 |
| `frontend/src/views/movements-workbench.css` | 재고 이력 전용 ERP 레이아웃·포커스·강조 |
| `frontend/src/components/ItemSearchSelect.vue` | 유형 미지정 전체 품목 검색 허용 |
| `frontend/src/components/AsyncState.vue` | 비동기 상태 접근성 역할 |
| `frontend/src/components/PageControls.vue` | 페이지 이동 의미 구조 |
| `frontend/tests/movements.spec.js` | Chrome API fixture 조회·복원·링크·잘못된 URL |
| `frontend/tests-demo/movements-demo.spec.js` | IndexedDB 실데모 입출고·출고 연결 |
| `frontend/tests-live/real-manufacturing.spec.js` | 기존 Spring Boot/MySQL 제조 E2E 후 이력 UI 실데이터 검증 |

## 4. 테스트/회귀 검사

GitHub CI에서 다음 항목을 검증한다.

- `npm test` (Node 단위 테스트)
- `npm run build` (Vite 프로덕션 빌드)
- `npx playwright test --config playwright.config.js` (Chrome fixture, 이동 이력 신규 테스트 포함)
- `npx playwright test --config playwright.demo.config.js` (프로덕션 IndexedDB 실데모)
- `npx playwright test --config playwright.live.config.js` (Java 21, Spring Boot, MySQL 8.4 기동 후 실DB 제조·화면 E2E 2건)

최종 판정은 PR #21의 해당 헤드 커밋 Frontend/Fullstack 워크플로가 모두 성공한 결과를 기준으로 한다. CI 실행 상세 링크는 상단 PR Checks를 확인한다.

## 5. 시니어 코드·설계 검수

**안전성:** 이동 이력은 조회 전용. 모든 재고 차감·FIFO·멱등성·생산·검사·출고 업무 규칙은 기존 서버/데모 엔진이 소유한다. 과거 이동 수량을 화면에서 재계산해 잔량으로 주장하지 않는다. 존재하지 않는 LOT 코드/업무 상세 링크는 꾸며내지 않는다.

**동시성/회귀:** 조회 전환 시 AbortController + 최신 요청 검사. 직접 URL 새로고침, 잘못된 ID, 페이지 이탈, 품목 페이지 제한과 조회 실패를 고려한다. 기존 원자재/완제품 전용 검색의 유형 검증은 유지한다.

**수정 중 발견한 문제:** 공통 페이지 이동의 닫는 태그 오류와 실DB Playwright 검증 코드의 잘린 구문·중복 블록을 CI 로그로 찾아 수정했다. 기능 코드가 정상이어도 테스트 파일 구문 오류가 실DB 검증을 막을 수 있으므로, 최종 CI 성공 전 병합 금지 원칙을 유지한다.

**향후 기능적 개선:** 서버의 이동 유형·기간 검색 API가 생긴 후에만 전체 이력 필터를 도입한다. 출고 LOT 코드까지 보여주려면 명시적인 API 응답 계약이 필요하다. 입력 모달의 포커스 트랩·키보드 포커스 복귀는 별도 접근성 작업으로 검토한다.

**향후 효율 리팩터링:** 직접 URL에서 품목 단건 GET이 발생하며, 여러 화면의 URL 파서가 분산되어 있다. 조회량과 빈도를 계측한 뒤 품목 조회 캐시 및 URL 상태 파서 공통화를 고려한다. 단일 창고 포트폴리오 MVP에서는 추가 상태관리 추상화를 도입하지 않는다.

## 6. 시연 설명

“재고 이동 이력은 특정 품목이 어떤 LOT에서 얼마만큼 입고·소모·출고됐는지 추적하는 읽기 전용 기록입니다. 기존 100개 품목 선택 제한과 URL 복원 문제를 해결하고 실제 업무의 참조 ID를 연결했습니다. 재고 계산 자체는 변경하지 않고 브라우저 데모와 Spring Boot/MySQL E2E를 분리 검증했습니다.”
