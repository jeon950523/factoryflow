# TASK-005A — Vue 3 제조 관리자 화면과 API 목록 계약 확장

> 상태: 2026-11-11 기능 브랜치 구현 · CI 검증 결과는 `docs/verification/TASK-005A-CI-검증.md`의 실제 실행 링크 기준으로 판단.

## 1. 목적

제조 업무 전 과정이 실제 API로 검증됐지만 명령/조회 조작은 Postman·테스트 코드로만 가능했다. 담당자가 품목·BOM·LOT 입고·생산지시·QC·출고를 화면에서 처리할 수 있도록 **데스크톱 중심 관리자 콘솔**을 만든다. 외부 ERP UI의 탐색과 데이터 표현 패턴을 참고하되 이 프로젝트에 없는 업무 기능은 표시하지 않는다.

## 2. 전체 호출 흐름

`Vue Router → 해당 View → lib/api.js(fetch, 표준 응답.data 반환) → Vite 개발 프록시(/api) → Spring Boot Controller → Service/Repository → MySQL → API 응답 → Vue 상태 업데이트 → 표/상세/알림`

- **조회**: GET 응답의 `data.content` 및 `totalElements`/`totalPages`로 목록과 페이지를 보여준다.
- **저장**: Vue `@submit.prevent` → `post` → Spring Bean Validation/업무 검증 → 201/200/409/400을 구분해 처리.
- **상태 변경**: 선택된 생산지시의 현재 상태에 따라 가능한 작업만 표시. 최종 허용 여부는 백엔드가 다시 검사.
- **실패**: `ApiError`가 서버의 `errorCode`, `message`, `traceId`, HTTP status 보존. 요청 중 로딩·빈 화면·재시도 버튼 지원.

## 3. 파일별 주요 역할

| 위치 | 역할 |
|---|---|
| `frontend/src/App.vue` | 사이드바, 상단바, 페이지 공통 레이아웃 및 알림 렌더링 |
| `frontend/src/router/index.js` | 6개 업무 화면 라우팅, 브라우저 히스토리 URL |
| `frontend/src/views/DashboardView.vue` | 실제 품목/생산지시 상태별 `totalElements`, 최근 작업 및 재고 요약 |
| `frontend/src/views/ItemsView.vue` | 품목 등록·필터·상세와 BOM 생성/조회 |
| `frontend/src/views/InventoryView.vue` | 품목별 현재고/예약/가용고, LOT 조회, 원자재 입고 |
| `frontend/src/views/OrdersView.vue` | 생산지시 목록/상세 및 reserve/start/finish/cancel/QC 명령 |
| `frontend/src/views/ShipmentsView.vue` | 완제품 출고 생성/목록/LOT 배분 상세 |
| `frontend/src/views/MovementsView.vue` | 품목별 재고 이동 장부 조회 |
| `frontend/src/components/*` | 상태 배지, 서버 상태 표시/재시도, 공통 페이지 이동 |
| `frontend/src/lib/api.js` | JSON 전송·오류 정보·쿼리 파라미터 정규화 |
| `frontend/src/lib/format.js` | 숫자·날짜·상태 한국어 표시, 요청 키(UUID) 생성 |
| `frontend/src/stores/ui.js` | Pinia 전역 알림 상태 |
| `frontend/src/assets/base.css` | 공통 디자인 토큰·반응형 레이아웃·명확한 표/폼 |
| `backend/.../WorkOrderLookupService` | 생산지시 상태 필터·페이지별 목록. 구성품 조회 N+1을 피하려고 상세 응답과 목록 DTO 분리 |
| `backend/.../ShipmentLookupService` | 출고 목록 조회. LOT 배분은 개별 상세에서 조회 |
| `.github/workflows/frontend-ci.yml` | Node 22 `npm ci` + API 클라이언트 테스트 + Vite build |

## 4. 코드·설계 선택 이유

1. **업무 중심 정보 구조:** Odoo·Katana·ERPNext의 모듈 분리 관점을 채택하되 판매/구매/설비처럼 현재 백엔드에 없는 메뉴는 제외. `docs/design/ADMIN_UI_REFERENCES.md` 참조.
2. **목록과 상세 API 분리:** 생산지시/출고가 `GET /{id}`만 있었다. 목록을 위해 N개의 상세 조회를 하는 대신 `GET /api/v1/work-orders`와 `GET /api/v1/shipments`를 추가했다. 목록 DTO는 자재 구성·LOT 배분을 빼 N+1/과도한 응답 방지.
3. **정직한 KPI:** 생산 중·검사 대기·완료 수치는 서버의 상태 필터 결과 `totalElements`만 사용. 재고 첫 8개는 *일부 품목*임을 표시. 임의 수치/목 데이터 없음.
4. **트랜잭션·멱등성은 백엔드:** 프론트에서 자재 수량을 계산·차감하지 않고 기존 서버 명령 호출. 입고·생산지시·출고 요청 키는 폼 생성 시 한 번 발급하고 실패 시 그대로 보유해 재시도 중복 방어.
5. **개발용 same-origin 프록시:** Vite가 `/api`를 `127.0.0.1:8080`으로 프록시하므로 로컬 개발에서 CORS 설정을 불필요하게 변경하지 않음. 운영은 인증·인가를 구현한 후 실제 HTTPS API URL을 환경 변수로 지정할 계획.
6. **컴포넌트 적정 추출:** 상태·빈화면·페이지 이동처럼 반복되는 UI만 재사용 컴포넌트로 추출. 개별 업무 양식은 각 View에 둬서 작업 흐름을 읽기 쉽게 유지.

## 5. 검증 및 테스트 시나리오

- `backend/src/test/java/com/factoryflow/AdminListApiIntegrationTest.java`: 실제 MySQL에서 상태 필터/페이지/출고 최근순/잘못된 페이지 값/목록의 과다 데이터 반환 방지.
- `frontend/src/lib/api.test.js`: URL query 값 누락·0 허용, 성공 data unwrap, 업무 오류 status/code/traceId, 연결 실패 오류.
- GitHub Actions: Backend CI(Gradle clean test + Testcontainers), Frontend CI(Node 22 npm ci/test/build).
- 미실시: 실제 웹 브라우저와 로컬 DB 연결 클릭 시나리오, Vercel/Oracle 공개 배포, 인증/역할 테스트. CI가 통과해도 이 항목까지 완료했다고 주장하지 않음.

## 6. 시니어 검수 — 장점/문제/리팩터링

**잘한 점:** 실제 API만 사용, 데이터 조회/쓰기 분리, 오류와 빈 상태 처리, 상태 전이 버튼의 맥락화, 서버 데이터·프런트 전역 알림 책임 분리, 목록 페이지 계약 추가, 프론트 단위 테스트.

**즉시 주의할 점:** 인증 없이 쓰기 API가 공개된 상태이므로 **외부에 배포하지 않는다**. API URL을 브라우저용 환경 변수에 두므로 비밀 키를 절대 포함하지 않는다.

**한계:** 현재 품목 선택 드롭다운은 첫 100건만 대상으로 하며 페이지/검색형 선택 지원 부족. 대량 품목 환경에서 개선 필요. 생산지시 상태를 선택하면 상세에서 동작만 제한하고 서버 권한 검사를 대체할 수는 없다. Vite CSS와 Vue 컴파일은 CI에서 검증하되 접근성/시각적 QA는 추후 검증.

**2차 리팩터링:** fetch 공통 AbortController/요청 취소, 검색형 서버 사이드 품목 선택, 재고 이동 이력의 복합 필터, 폼 입력 재사용 컴포넌트, 중복 데이터 조회 최소화(캐싱). 필요성이 확인된 후 도입. 서버 목록 DTO는 필요한 추가 열이 생기면 versioning/응답 계약 검토.

**지금 불필요:** 대형 UI 프레임워크 전체 도입, 고가 라이선스 관리자 템플릿, 가짜 차트·OEE, WebSocket/Kafka/Redis 캐시, MSA 구조 변경.

## 7. 후속 순서

1. Vue 3 실제 브라우저 API 연동 스모크 QA 및 UI 피드백 반영.
2. 인증/인가(역할 분리, 쓰기 제한, CORS/CSRF 정책) 구축 후 외부 접근 설계.
3. Vercel(정적 프론트) + Oracle Cloud Always Free(Spring Boot/MySQL) 배포 검증. Oracle 무료 VM 확보/상시 운영 가능 여부 별도 확인.

## 8. 나중에 네가 설명할 핵심

FactoryFlow 관리자 콘솔은 생산/재고 업무를 작업 흐름별로 구성하고 목록→상세→상태 변경으로 이어지도록 설계했다. 프론트는 표시와 입력만 담당하고 핵심 수량 검증/동시성/트랜잭션은 Spring Boot 서비스와 MySQL이 책임지게 유지했다. 대량 목록을 위해 생산지시·출고에 페이지 조회 API와 목록 전용 DTO를 추가했다. 외부 ERP의 인터페이스 패턴은 참고했으나 미구현 기능이나 KPI를 실제처럼 꾸미지 않았다.
