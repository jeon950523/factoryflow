# FactoryFlow 핵심 기술 개념 모음

> 개발 완료 후 복습·면접 준비용으로 누적하는 문서. 지금은 **구현·검증 우선**이며, 이 문서는 진행 중인 코드의 학습 목차 및 근거를 보존한다. 기술적 설명은 실제 CI/DB 테스트를 통과한 경우에만 '검증 완료'로 적는다.

## 1. 도메인·불변조건 — TASK-001
- **현재고 / 예약고 / 가용고:** `available = onHand - reserved`, `0 <= reserved <= onHand`.
- **업무 흐름:** 원자재 입고 → 생산지시 생성(DRAFT) → 예약(RESERVED) → 생산 시작 시 실재고 차감 → 품질검사 합격분만 완제품 재고 반영.
- **의미:** 상태 전이와 물리 재고 변화를 같은 사건으로 취급하지 않는다.
- **주의:** 산업용 MES 전체를 구현했다고 주장하지 않는다. 단일 공장·단일 창고의 축약 모델이다.

## 2. 스키마 소유권과 검증 — TASK-002
- **Flyway**: 테이블·FK·UNIQUE·CHECK·인덱스를 명시적인 마이그레이션으로 관리.
- **JPA `ddl-auto=validate`**: 엔티티가 실제 MySQL 스키마와 호환되는지 확인, 자동 변경 금지.
- **Testcontainers MySQL**: H2 대체 검증이 아닌 실제 DB 종류로 제약·트랜잭션 테스트.
- **장점/단점**: 재현성·이력 관리 강화 / SQL과 엔티티 매핑을 함께 관리해야 함.

## 3. 계층·트랜잭션과 DB UNIQUE — TASK-003A
- `Controller`=HTTP, `Service`=규칙/트랜잭션, `Repository`=영속성, `DTO`=API 계약.
- `existsBySku()` 확인만으로 동시 중복 등록을 막을 수 없으므로 DB `UNIQUE`가 최종 안전망.
- BOM 및 구성품의 저장을 하나의 트랜잭션에 묶는다.

## 4. 입고 멱등성·비관적 락 — TASK-003B
- **Idempotency key + payload hash**: 같은 요청 재전송이면 재반영 금지, 다른 내용의 키 재사용은 충돌.
- **Item `PESSIMISTIC_WRITE`**: 같은 원자재의 쓰기를 순서대로 처리, 동시 갱신 분실 방지.
- **같은 트랜잭션**: LOT 수량+입고 기록+물리 이동 이력; 일부 실패하면 전체 롤백.
- **트레이드오프**: 정합성 설명이 쉬움 / 품목 단위 잠금 경합 증가 가능.

## 5. BOM 스냅샷·DRAFT 생성 — TASK-004C
- BOM 구성 수량 × 생산지시 계획 수량을 생성 시점에 저장하므로 과거 지시가 기준 BOM 변경의 영향을 받지 않는다.
- `Math.multiplyExact`로 수량 오버플로 차단.
- DRAFT 생성은 재고를 예약·차감하지 않는다. 재고 경쟁은 별도 명령에서 통제한다.

## 6. FIFO LOT 전량 예약·취소 — TASK-004D
- **잠금 순서**: 자재 Item ID 오름차순 → WorkOrder → LOT (수량 변경 명령마다 통일).
- **FIFO 할당**: 오래된 LOT부터 필요한 수량을 배정, `reserved`만 증가.
- **전량 성공/전체 롤백**: 일부 자재의 재고 부족이 확인되면 다른 LOT의 예약 변경도 롤백.
- **예약 취소**: 상태 `RESERVED→CANCELED`, 할당 내역 `RESERVED→RELEASED`, LOT 예약량 감소. 물리 현재고는 그대로.
- **한계/후속**: 동일 품목 쓰기는 직렬화돼 처리량 병목이 생길 수 있음. 인덱스/행 락 최소화는 수치 측정 후 검토. 이 동작의 완료 판정은 TASK-004D CI 결과를 따른다.

## 개발 후 스스로 답할 질문(추후 학습용)
1. API Service의 사전 검사 외에 DB UNIQUE/CHECK를 넣는 이유는?
2. 입고와 원자재 예약 모두 Item 행을 먼저 잠그는 이유는?
3. 원자재 2종 중 두 번째가 부족할 때 첫 번째 예약도 롤백되는 이유는?
4. 생산지시 DRAFT 생성 때 LOT을 잠그지 않고, 예약할 때 잠그는 이유는?
5. 비관적 잠금의 장점과 병목 발생 조건은 무엇인가?

> **판정:** 실제 검증 결과와 대응하는 각 TASK 보고서를 확인한 후 학습용 결론을 확정한다.

## 7. 예약 소모·생산 완료 상태 전이 — TASK-004E
- **생산 시작**: `RESERVED → IN_PROGRESS`. 해당 생산지시의 `RESERVED` 예약만 실제 자재 소모로 확정한다.
- **원자성**: LOT `on_hand`/`reserved`의 동시 차감, 예약 상태 `CONSUMED`, `RAW_CONSUMPTION` 재고 이동 기록, 생산지시 상태 변경이 **같은 MySQL 트랜잭션**에서 commit/rollback.
- **다른 생산지시 예약 보존**: 전체 LOT 예약을 초기화하지 않고 해당 작업 예약 수량만 차감한다.
- **상태 사전 검증**: `IN_PROGRESS`로 두 번 시작할 수 없고, `DRAFT`에서 바로 시작할 수 없다.
- **생산 완료**: `IN_PROGRESS → AWAITING_QC`, 물리 재고 변동 없음. **합격품 입고는 품질검사 확정(TASK-004F) 후**.
- **한계/후속**: 1차 MVP는 계획 수량이 전량 생산 완료된다고 가정한다. 중도 폐기·부분 생산·재작업은 별도 업무 정책 필요.

## 8. 품질검사·합격품 입고 — TASK-004F
- 검사 결과는 생산지시가 AWAITING_QC일 때만 확정한다.
- 합격 + 불합격 = 생산 계획 수량, 각 수량은 0 이상, Math.addExact로 오버플로 확인.
- 합격품이 1개 이상이면 QC_PASS 완제품 LOT 및 FINISHED_RECEIPT 이력을 생성한다. 전량 불합격 시 LOT 없음.
- 검사·LOT·이동 이력·COMPLETED 상태 변경은 하나의 MySQL 트랜잭션.
- 완제품 Item → WorkOrder 잠금 순서와 UNIQUE 제약으로 중복 검사와 재고 이중 반영을 방어한다.
- 장점: 품질검사 전 출고 차단, 생산지시별 추적. 단점: 재작업/결과 정정 미지원·동일 품목 잠금 경합. 실제 MySQL CI 40/40 통과(2026-11-11). 잠금 전 엔티티 로딩으로 오래된 상태를 재사용하면 중복 동시 검사에서 500이 발생할 수 있어, **잠금 전에는 finishedItemId 스칼라만 조회**하고 잠금 후 findForUpdate로 WorkOrder를 로딩하도록 수정했다. 영속성 컨텍스트의 1차 캐시와 비관적 잠금의 관계를 후속 학습 대상으로 보존한다.


## 9. 완제품 FIFO 출고·멱등성 — TASK-004G
- **출고 대상은 QC_PASS LOT만:** 검사 전 원자재 또는 불합격품은 출고 경로에서 제외된다.
- **Item → LOT 순서의 비관적 잠금:** 동일 완제품을 두 요청이 동시에 출고해도 초과 출고하지 않도록 재고 접근을 직렬화한다.
- **FIFO 전량 출고:** 오래된 LOT부터 차감하고, 필요한 수량이 모자라면 모든 LOT 차감·출고 기록을 롤백한다.
- **멱등성:** requestKey + payloadHash, DB UNIQUE. 같은 키로 재전송하면 200으로 기존 출고 반환; 동일 키의 다른 내용은 409.
- **원자성:** LOT.onHand 감소, shipments, shipment_allocations, stock_movements(FINISHED_SHIPMENT) 모두 같은 DB 트랜잭션.
- **장점/단점:** 데이터 정합성 및 LOT 추적이 명확 / 동일 품목 선잠금으로 경합 가능, 부분 출고·반품 미지원.
- **실측:** GitHub Actions MySQL CI 45/45 통과. 대량 트래픽 성능은 측정하지 않았으며 후속 검증 과제다.


## 10. 전체 제조 업무 API E2E — TASK-004H
- **목적:** 단위/개별 API 테스트 통과만으로는 업무 단계 간 수량 정합성이 검증되지 않는다. 원자재 입고부터 검사·출고까지 실제 HTTP 컨트롤러를 순서대로 호출하고, 커밋된 DB 상태를 대조한다.
- **도구:** MockMvc는 Spring Controller/Service/Repository 경로를 호출하지만 실제 외부 네트워크·브라우저는 포함하지 않는다. Testcontainers MySQL 8.4로 DB 종류와 제약을 일치시킨다.
- **검증 책임:** 업무 데이터 생성·변경은 모두 API 호출로 처리. JdbcTemplate은 결과 DB 조회에만 사용한다.
- **트랜잭션:** 테스트 클래스에서 Transactional로 전체 실행을 묶지 않는다. 각 HTTP 요청의 트랜잭션 커밋을 개별적으로 검증한다.
- **재고 불변식:** available = onHand - reserved. 물리 현재고 = RAW_RECEIPT + FINISHED_RECEIPT - RAW_CONSUMPTION - FINISHED_SHIPMENT. 예약은 물리 이동에 포함하지 않는다.
- **정상 흐름:** 철판 LOT 30+70, 볼트 200 → 제품 50 생산(철판 50/볼트 100 소모) → QC 합격 47·불합격 3 → 완제품 10 출고 → 최종 완제품 37.
- **보호 시나리오:** 두 번째 재료 부족 시 전량 예약 롤백; 추가 입고 후 예약·취소; 중복 요청 재전송/키 충돌; 전량 불합격 LOT 미생성·출고 불허.
- **검증:** CI 49개 테스트 성공. 신규 E2E 4개. 브라우저/로그인/Oracle 배포 경로는 아직 미검증.
- **장단점:** 실제 MySQL 트랜잭션/제약과 통합 흐름의 재현성 향상 / 컨테이너 구동비용과 테스트별 반복 준비 부담. 안정적으로 반복 확인한 뒤 Fixture/컨테이너 공유 최적화.
- **학습 질문:** API 경유로 데이터 준비하는 이유는? 테스트에 Transactional을 붙이면 왜 커밋 검증을 왜곡할 수 있나? MockMvc 통합 E2E와 실제 브라우저 E2E는 무엇이 다른가?


## 11. Vue 3 관리자 콘솔과 목록 조회 API — TASK-005A

- **제품 참고:** Odoo 제조 작업 화면, ERPNext 생산 현황·Work Order, Katana 모듈별 메뉴, SAP Fiori 목록→상세→업무 구조를 참고. 실제 백엔드 없는 설비/OEE/원가 화면은 만들지 않음.
- **목록 vs 상세:** 생산지시/출고 목록에는 요약 DTO만 반환하고 BOM 스냅샷/LOT 배분은 상세에서만 조회하여 N+1과 네트워크 낭비를 방지.
- **책임:** Vue는 화면·요청·상태 표시, Spring Boot와 MySQL은 재고 정합성·락·트랜잭션·멱등성을 담당. 화면 버튼 숨김은 보안 검사가 아님.
- **정직한 지표:** 품목 및 업무 상태 건수는 API totalElements 기반. 존재하지 않는 통계와 목 데이터는 사용하지 않음.
- **개발 프록시:** Vite /api는 localhost 8080으로 프록시. 공개 배포는 인증/인가·TLS·CORS 정책을 별도 검증한 후에만 수행.
- **후속:** 검색형 품목 선택(현재 첫 100건 제한), 폼 컴포넌트 정리, 브라우저 QA·접근성·E2E 자동화.
- 자세한 설명: [TASK-005A 학습 핵심](TASK-005A-관리자UI-학습핵심.md).


## 11. 서버 검색형 품목 선택기와 비동기 레이스 방어 — TASK-005B
- **문제:** 첫 100개만 불러오는 select는 101번째부터 존재하는 품목을 선택 불가능하게 만든다. UI 문제이면서 실제 업무 처리 불가를 야기한다.
- **해결:** 공통 ItemSearchSelect가 GET /api/v1/items?itemType=...&keyword=...&page=...&size=10 형태로 서버에서 해당 검색 조건만 조회한다. 기존 ItemService의 검색/페이지 계약을 재사용한다.
- **데이터 흐름:** 품목 선택 버튼 → 검색/페이지 요청 → API의 data.content, totalElements → 사용자가 선택 → v-model이 품목 ID 저장 → 입고/생산지시/출고/BOM 제출 시 기존 서버 검증 진행.
- **중복/역순 응답 방지:** AbortController가 이전 fetch를 취소하고 requestNumber로 취소 불가능하게 늦게 도착한 오래된 응답도 무시한다. 검색 결과와 현재 선택값을 분리한다.
- **실제 오류 예방:** 선택기를 저장 form 안에 구성할 때 또 다른 form을 중첩하면 제출 이벤트가 꼬인다. 검색 영역은 div + type=button + Enter prevent로 구현.
- **재시도:** 실패한 검색 페이지 번호를 유지해 같은 조건으로 다시 조회한다.
- **실제 검증:** frontend CI Node 테스트 6/6 + Vite build, backend CI MySQL 52/52, 105개 등록 후 11번째 페이지 확인. 실제 브라우저·보조기기 UX는 아직 미검증.
- **한계:** 목록/상세의 일부 이름 표시는 앞 100개 캐시 사용으로 이름 대신 ID로 나올 수 있다. 탭/포커스 이동·브라우저 사용성 추가 QA 필요.
- **장단점:** 모든 품목 접근 가능·대용량 전송 절감 / 검색 클릭과 페이지 이동이 추가되며 여러 UI에서 일부 캐시가 중복됨.
- **후속 리팩터링:** 페이지 전역 품목 이름 조회 전략, 선택 상태 라벨 재조회, 컴포넌트 단위 브라우저 테스트, 필요 확인 후 입력 자동완성/디바운스 도입.


## 12. 품목 식별자 일괄 조회와 실제 브라우저 QA — TASK-005C
- **원인:** 폼의 100개 제한은 TASK-005B에서 해결했지만, 생산지시·출고·BOM 상세는 이름 표시를 위해 첫 100개 품목 목록을 다시 조회했다. 뒤쪽 ID는 존재해도 이름 대신 #105로만 보였다.
- **서버 API:** GET /api/v1/items/lookup?ids=105,205. ItemController → ItemService.lookup → ItemRepository.findAllById(IN 1번 조회). 입력 1~100개·양수 검증, 중복 ID 제거, 요청 순서 유지, 없는 ID는 응답에서 생략.
- **클라이언트 흐름:** 작업 목록 조회 → 현재 페이지에 표시할 finishedItemId만 수집 → lookupItemNames가 50개 단위로 요청 → 응답을 { id: { sku, name } } 매핑 → list와 detail의 이름·SKU 렌더링. BOM 구성 원자재 ID도 같은 방식.
- **선택 대안:** 각 행 GET /items/{id} = 네트워크 N+1; 전 품목 다운로드 = 데이터 증가 시 낭비; 한 번의 일괄 조회는 네트워크 요청을 최소화하면서 독립적인 API 계약을 유지. 대안으로 백엔드 목록 DTO에 품목명 필드 포함 시 추가 왕복을 줄일 수 있지만 여러 도메인 변경과 결합도를 먼저 검토해야 한다.
- **장애 허용:** 목록 API 성공 후 품목명 API만 실패하면 업무 목록을 지우지 않는다. "완제품 #105"로 대신 표시하고 오류 알림 제공. 이 때문에 이름 조회는 부가적인 표시 데이터로 취급한다.
- **실제 테스트:** MySQL Testcontainers 53/53, Node 10/10, 실제 GitHub Actions Ubuntu 24.04의 Google Chrome에서 Playwright 스모크 5/5. Chrome에는 테스트 Fixture API를 사용하므로 실 DB 브라우저 통합 검증이라고 말하지 않는다.
- **시니어 검수:** 현재 ID 맵이 각 화면별로 분리되어 불필요한 API 호출이 있을 수 있음. 캐싱/요청 취소/DTO 확장은 실측 뒤에; 재고 정합성 로직은 손대지 않음.
- **다시 공부할 질문:** 왜 레코드 15개마다 GET /items/{id}를 15번 호출하는 방식이 좋지 않은가? 이름 API 장애 시에도 생산지시 목록을 보여줘야 하는 이유는? MockMvc 실제 MySQL 테스트와 Chrome Fixture 테스트 각각 무엇을 보증하는가?


## 13. 최신 응답 우선 처리 및 실제 MySQL 브라우저 E2E — TASK-005D
- **원인:** 목록에서 A→B 연속 클릭 시 늦게 도착한 A GET 응답이 B의 최신 상세를 덮어쓸 수 있음. BOM/LOT에서도 재현 가능한 비동기 경쟁 조건.
- **해결:** latestRequest.js의 begin()은 AbortController로 이전 요청을 중단하고 revision을 증가시킨다. isCurrent()가 true인 응답만 상태에 반영하고, unmount 시 cancel() 한다. 실제 취소와 이미 완료된 응답의 오래된 수정 방지를 동시에 수행.
- **상태 변경 주의:** 생산지시 작업 전 대상 orderId를 캡처해, 다른 행으로 이동한 후 이전 작업 성공이 현재 선택을 되돌리지 않게 함.
- **빠른 회귀:** Chrome Fixture 테스트에서 생산지시·출고·BOM의 느린 첫 응답/빠른 두 번째 응답을 재현. Node 12개, 브라우저 8개 성공.
- **실DB 회귀:** MySQL 8.4 서비스 컨테이너, 실제 Spring Boot+Flyway 기동, Vue Vite 프록시, Chrome Playwright를 같은 CI 내에서 연결하여 업무 버튼으로 입고→생산→QC→출고. 2/2 통과.
- **재고 결과:** 원자재 현재고5, 예약0, 완제품 현재고5, 예약0. RAW_RECEIPT25, RAW_CONSUMPTION20, FINISHED_RECEIPT8, FINISHED_SHIPMENT3. 부족 자재 예약은 409, DRAFT/현재고 유지.
- **실제 CI 오류:** Gradle bootJar는 성공했지만 실행 스크립트에서 JAR 이름을 하드코딩해 서버 기동 실패. 빌드 산출물 검색으로 수정 후 통과. 앱 오류와 CI 환경 오류를 구분.
- **대안/장단점:** AbortController만 사용하면 응답 완료 직전 타이밍 방어가 모호하고, revision만 사용하면 불필요한 네트워크가 지속될 수 있어 둘 다 사용. 반면 각 Vue View에 요청 상태 코드가 반복됨.
- **효율:** MySQL+Spring+브라우저 전체 CI는 상대적으로 무거워 PR/main에서만 실행하고 빠른 Chrome Fixture/Node 검사는 기능 브랜치에서 유지.
- **추후:** Vue composable로 요청 관리 중복 제거, Playwright 버전을 package-lock에 고정, 접근성·다중 사용자·운영 배포 테스트. 인증 없는 공용 배포는 하지 않음.
- **나중에 설명할 문장:** "연속 클릭 시 오래된 응답이 최신 상세를 덮어쓰는 문제를 취소+버전 검사로 방지하고, 실제 Vue–Spring–MySQL 경로로 생산 전 과정의 재고 정합성을 검증했습니다."


## 14. 세션 인증·역할 인가·CSRF — TASK-006
- 목적: 무인증 쓰기 API를 **백엔드에서** 차단. Vue의 버튼 감춤은 접근 제어가 아니라 UX 보조.
- 호출: Vue /login → GET /auth/csrf → form POST /auth/login(X-CSRF-TOKEN) → Spring Security Filter → DaoAuthenticationProvider → AppUserDetailsService → MySQL BCrypt 비교 → JSESSIONID(HttpOnly) → GET /auth/me로 이후 세션 확인.
- 역할: ADMIN 제조 GET/POST 및 계정 관리, VIEWER 제조 GET 전용. admin/users 경로는 GET이어도 ADMIN만 허용.
- POST 보안: HttpSessionCsrfTokenRepository, Spring XOR 요청 토큰. 로그인/로그아웃으로 CSRF secret이 바뀌므로 재조회.
- 세션: 30분, HttpOnly, SameSite=Lax. 운영 HTTPS에서는 SESSION_COOKIE_SECURE=true 및 동일 출처 /api 프록시 필요.
- 부트스트랩: 빈 DB에서 환경변수로만 초기 ADMIN 생성; 미설정 시 기동 거부. BCrypt 해시 저장 및 비밀번호 응답 제거.
- 검증 계획: 기존 제조 MockMvc 테스트는 보안 필터 없이 비즈니스 회귀 확인, 보안 Testcontainers 전용 클래스는 필터 포함, 실제 Vue/Chrome/MySQL 통합은 로그인 후 사용.
- 남은 위험: rate limit/계정 잠금/비밀번호 재설정/운영 TLS·프록시 확인, 접근성/세션 고장 회귀, 공인 배포 전 필수 검증.
- 나중에 설명: 서버 세션과 CSRF로 안전한 상태 변경을 검증하고 ADMIN/VIEWER RBAC를 백엔드 필터에서 강제했습니다.
