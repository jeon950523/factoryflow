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

## 15. 로그인 시도 제한과 배포 안전 경계 — TASK-007A

- **호출 흐름:** 로그인 요청 → `LoginRateLimitFilter`의 제한 확인 → BCrypt 검증 → 성공 시 계정 버킷 정리. 5분 기준 계정별 8회/접속원별 120회, 제한 시 HTTP 429 및 Retry-After. 세부 설정은 [TASK-007A 보고서](../task-reports/TASK-007A-로그인제한-검수.md) 참조.
- **장점:** 인증 이전에 무차별 시도 부담 완화 / **단점:** 단일 JVM 메모리 상태이므로 여러 인스턴스 분산 제한은 보장하지 않으며 프록시 IP 공유와 사용자 방해 위험이 있다.
- **운영 주의:** 로그인 제한을 구현했다는 사실이 HTTPS, 보안 쿠키, 복구 절차를 확인한 공개 서버 배포 승인을 의미하지 않는다.

## 16. Vercel IndexedDB와 실제 MySQL 기능의 분리 — TASK-008

- **문제:** 무료 서버 배포 제약 때문에 공개 체험을 먼저 제공해야 하지만 무인증 DB 백엔드를 외부에 노출하면 안 된다.
- **방법:** `VITE_DEMO_MODE`와 `requestDemo`를 통해 Vercel 브라우저에서 동일 API 형식의 로컬 IndexedDB 시뮬레이터를 호출한다. 쓰기는 트랜잭션 하나에서 snapshot을 읽고 쓰며 `requestKey` 충돌을 모델링하지만 **실제 Spring/MySQL의 락·롤백 증거로 취급하지 않는다.**
- **장점:** 계정 없이 안전하게 개인 브라우저에서 시연 / **단점:** 실제 서버 간 동기화, 운영 계정 권한, 분산 쓰기와 MySQL 잠금은 증명하지 못함.
- **근거:** [ADR-013](../adr/ADR-013-프론트데모-실백엔드분리.md), [TASK-008 보고서](../task-reports/TASK-008-브라우저데모-검수.md).

## 17. 업무형 UI 설계 — TASK-009~013

- **업무 판단:** 화면을 KPI 장식보다 처리 대기 생산지시·품목 재고·선택 상세/명령에 집중한다. 실제 백엔드 없는 OEE·설비 지표·경보 전체 건수를 꾸며내지 않는다.
- **호출 흐름:** Vue Router URL 쿼리 → 해당 View의 `load()`/조회 게이트 → `GET /api/v1/...` → 페이지/상세 렌더링 → ADMIN 전용 쓰기 버튼 → 별도 `POST`.
- **선택 이유:** 목록 DTO와 상세 DTO를 분리하고 `lookupItemNames`로 현재 화면의 품목 ID만 일괄 조회한다. 페이지/URL 직접 접근 후 필요한 상세만 재조회한다.
- **대안 및 비용:** 모든 데이터를 처음에 가져오면 탐색은 단순해도 규모에 따라 전송 낭비가 발생한다. 단건 GET을 여러 번 호출하면 N+1 네트워크가 된다. 서버 검색·페이지·ID lookup은 추가 왕복이 있지만 데이터량을 제한한다.
- **표시 계약:** QC 불량품은 출고 가능한 완제품에 포함하지 않고, LOT 배분 API가 반환하지 않는 LOT 코드를 만들어 표시하지 않는다. 읽기 전용 VIEWER UI에서 버튼을 숨기더라도 실제 쓰기 권한은 Spring Security에서 결정한다.
- **근거:** [TASK-009](../task-reports/TASK-009-제조업무형UI-검수.md) → [TASK-013](../task-reports/TASK-013-품목BOM-출고UI-검수.md).

## 18. 읽기 전용 재고 이동 원장 — TASK-014

- `MovementsView.vue`: `/movements?itemId=205&page=1` → `GET /api/v1/items/{id}` + `GET /api/v1/stock-movements?itemId=...&page=...&size=20` → 실제 `StockMovementResponse` 표.
- **표현 vs 데이터:** DB의 `quantity`는 양수이며 화면에서만 `RAW_RECEIPT/FINISHED_RECEIPT` 앞에 `+`, `RAW_CONSUMPTION/FINISHED_SHIPMENT`에 `−`를 붙인다. 예약·해제는 물리 이동 이력이 아니므로 제외한다.
- **예외:** 직접 URL ID 유효성, 역순 응답 `createLatestRequest`, 저장한 페이지의 범위 변경 시 실제 마지막 페이지로 복원, 선택한 품목 단건 조회 실패에도 ID 기준 원장 조회는 유지.
- **트레이드오프:** 서버 측 타입/기간 검색 없이 전체 이력 검색 UI를 구현하지 않음. 품목 직접 진입 시 추가 GET 1건은 URL 복원 정확성을 위해 수용.
- **검증:** [TASK-014 보고서](../task-reports/TASK-014-재고이동-공통UI-검수.md), PR #21.

## 19. 배포 성공과 Chrome 화면 검증은 다른 주장 — TASK-015

- Vercel GitHub 상태가 `success`여도 실제 사용자 도메인의 SPA 라우팅·글꼴·IndexedDB 렌더링까지 보증하지 않는다.
- `frontend/playwright.public.config.js`는 공개 `https://factoryflow.kro.kr`에 접근하는 Google Chrome 테스트이고, CI의 실제 Spring/MySQL E2E와 분리한다.
- Ubuntu 러너에 한글 글꼴이 없으면 화면이 네모 글자로 캡처될 수 있으므로 `fonts-noto-cjk` 설치 후 증거를 보관한다.
- **한계:** GitHub Ubuntu Chrome에서 정상 렌더링 = 모든 Windows PC·확대/보조기기에서 정상 표시라는 결론은 아니다.
- **근거:** [TASK-015 보고서](../task-reports/TASK-015-공개배포-Chrome-검수.md), PR #22.

## 20. Vue 모달 수명주기·접근성 — TASK-016

- `v-dialog-focus`는 모달 마운트 때 이전 활성 요소를 기억하고 첫 입력에 초점 → 문서 keydown Tab/Shift+Tab 트랩 → Escape 닫기 → 언마운트 시 리스너 해제·트리거 복귀를 처리한다.
- 열린 `ItemSearchSelect`는 첫 Escape를 사용해 검색 팝업을 닫고, 닫힌 상태는 Escape를 상위 모달에 전달한다.
- `saving` 중에는 Escape/백드롭/닫기 버튼으로 모달을 숨기지 않는다. **장점:** 폼 처리 상태 오인 감소 / **단점:** 공통 지시어와 페이지별 저장 상태·닫기 함수를 함께 유지해야 한다.
- 보조 텍스트의 글씨 크기·대비와 `prefers-reduced-motion`을 개선하되 전체 WCAG 인증이나 Windows OS 확대 검증은 주장하지 않는다.
- **근거:** [TASK-016 보고서](../task-reports/TASK-016-가독성-모달접근성-검수.md), PR #23.

## 21. URL 상태 복원과 제조업무 데이터 출처 — TASK-017

- `OrdersView`와 `ShipmentsView`의 페이지·선택 ID를 URL에 남겨 번호 클릭·링크 클릭·새로고침의 일관성을 유지한다. 목록 페이지 변경은 오래된 선택 ID를 제거한다.
- **업무 왕복:** BOM 원자재 → 원자재 LOT 현행 재고 → 입고/소모 이동 이력 → `WORK_ORDER` 원본 → 완제품 LOT 현재고 → `SHIPMENT` 출고 LOT 배분 → 해당 완제품 이동 원장.
- **정합성:** `현재고 = 물리 입고(원자재/검사 합격품) − 물리 소모/출고`; `출고량 = 출고 당시 각 LOT 배분량 합`. 생산 예약은 현재고 차감 사건이 아니다.
- **설계 대안:** 새 전역 캐시/프런트 추정 재고를 만들지 않고 서버가 반환한 ID로 각 화면을 재조회한다. 별도 요청은 들지만 데이터 출처를 단일화하고 서버의 최신 상태를 사용한다.
- **검증:** [TASK-017 보고서](../task-reports/TASK-017-업무동선-데이터정합성-검수.md), `main` `71b6ae2`, Node 37/37·Chrome fixture 21/21·IndexedDB 27/27·실 MySQL 브라우저 E2E 2/2.

## 22. 공개 배포 추가 QA·문서 정합성 — TASK-018

- **진행 중:** 실제 운영 도메인 직접 접근, 업무 링크 왕복, `?itemId=oops&page=-1` 예외 쿼리, 키보드 포커스 복귀, 125% CSS zoom 스트레스 검증을 공개 Chrome에서 확인한다.
- **구분:** CSS zoom 스크린샷은 Windows OS 글꼴/디스플레이 125~150% 확대 검증이 아니다. CI 성공·배포 상태·실사용 환경을 별도 증거로 남긴다.
- **작업 규정:** [기록 표준](../DEVELOPMENT_REPORTING_STANDARD.md) → [TASK-018 작업지시서](../../tasks/TASK-018-공개배포-QA-문서정합성.md) → 결과 보고서·핸드오프·트러블슈팅 동시 갱신.

### TASK-014~018 이해 점검 질문

1. 재고 예약은 물리 이동 이벤트가 아닌데도 재고 가용량에 영향을 주는 이유는?
2. URL에 `?itemId=...`를 저장할 때 품목 이름을 전역 캐시에서 복원하는 대신 단건 GET을 이용한 이유는?
3. Vercel 배포 완료, 공개 Ubuntu Chrome 스모크 통과, 실DB MySQL E2E 성공은 각각 무엇을 입증하며 무엇을 입증하지 못하는가?
4. `ItemSearchSelect`의 Escape와 부모 모달 Escape를 분리한 이유, 그리고 저장 중 닫힘을 제한하는 이유는?
5. 입고/소모 합계를 현재고와 대조할 때 과거 원장의 수량 부호를 프런트에서 표시와 구분해야 하는 이유는?

## 23. 품질검사 UX 입력 검증과 서버 트랜잭션의 역할 분리 — TASK-019

- **검수에서 발견:** `OrdersView.vue#choose(id)`가 검사 수량을 `passedQty=plannedQty`, `failedQty=0`으로 미리 채워 **입력하지 않았는데 전량 합격을 확정할 수 있는 UI**였다. `AWAITING_QC → COMPLETED` 이후 검사는 API에서 재수정할 수 없고 합격품이 물리 재고로 들어간다.
- **변경 흐름:** 상세 GET → QC 입력 2칸 공란 → `qcQuantity()`로 각각 명시적 비음수 안전 정수 확인 → `qcTotal/qcValid`에서 합계/계획수량 일치 → 품질검사 확정 버튼 → 작업번호/합격/불합격 수량 표준 확인창 → 승인 후 POST.
- **JavaScript 주의:** `Number('') === 0`이므로 `v-model.number`만 믿으면 입력하지 않은 불합격 수량이 0으로 계산될 수 있다. `''`은 입력 누락을 의미하는 `null`로 처리하며, 사용자가 직접 입력한 `0`은 유효 값으로 인정한다. 합계도 `Number.isSafeInteger` 검증으로 표현 범위 초과를 막는다.
- **실제 서버는 마지막 방어선:** `QualityInspectionService.confirm`은 완제품 Item→WorkOrder 잠금, 검사 상태/수량 재검증, 검사 이력·합격품 LOT·FINISHED_RECEIPT·생산지시 완료를 한 트랜잭션으로 확정한다. UI 확인/계산은 이 서버 계약을 대체할 수 없다.
- **대안/트레이드오프:** 기본 0/0은 ‘직접 입력 0’과 미입력을 구분하기 어려움. 모든 단계에 추가 확인을 강제하면 클릭 피로가 커지므로 재고 반영이 확정되는 QC에만 확인창을 추가한다. 브라우저 `window.confirm`은 간결하지만 시각 디자인 제어는 제한된다.
- **회귀/한계:** [TASK-019 보고서](../task-reports/TASK-019-품질검사-명시적확정-검수.md)의 Chrome/IndexedDB/MySQL 실행 결과를 확인. 검사 결과 변경·취소/재작업 API는 현 MVP 범위 밖.
- **생각해 볼 질문:** 전량 불합격은 왜 유효하고 자동 전량 합격은 왜 위험한가? QC 화면 검증 성공 이후에도 왜 MySQL 트랜잭션/DB 제약이 필요한가?

## 24. 입고·출고 결과 미확인과 요청 키 기반 멱등성 — TASK-020

- **문제의 본질:** 클라이언트가 HTTP 실패를 경험했다고 서버 트랜잭션까지 실패한 것은 아니다. DB COMMIT 이후 응답이 유실되면 UI에서 *미확인*으로 판단하고 **동일 key + 동일 payload**로 재시도해야 한다. 다른 UUID는 신규 업무로 간주되어 재고가 두 번 변경될 수 있다.
- **서버 구현 경로:** `StockReceiptService.receive` / `ShipmentService.ship` → `CanonicalReceipt.of` / `CanonicalShipment.from` (case 정규화·SHA-256 payload hash) → 사전 기존 키 조회/해시 비교 → 새 키만 `@Transactional READ_COMMITTED` writer 호출. 같은 키·같은 내용은 200 기존 결과, 다른 내용이면 409 충돌. DB UNIQUE와 Item 선잠금이 최종 정확성 책임.
- **브라우저 보강:** `pendingWrite.js`가 status 0/5xx/INVALID_RESPONSE를 불확실한 결과로 구분하고, `InventoryView`와 `ShipmentsView`는 스냅샷된 키·품목·LOT·수량을 고정한 채 탭 `sessionStorage`로 저장/복원/200 replay 성공 시 삭제한다. 서버가 대문자 UUID를 응답으로 돌려주므로 응답 검증에서 케이스 정규화 비교를 적용하며, **실제 재전송 본문은 원문 그대로 보존**한다.
- **테스트 방법:** 브라우저 `route.fetch()`로 실제 Spring Boot/MySQL POST를 먼저 서버에 전달해 성공 커밋시킨 뒤 `route.abort()`로 브라우저에서만 실패하게 만들고, 재시도 후 DB의 물리 원장·현재고가 단 한 번 변경됐는지 검사한다. 명확한 409 재고 부족은 신규 작업 실패로 별도 안내한다.
- **선택 이유/대안:** 네트워크 재시도 자동화보다 사용자의 명시적 동일 요청 재전송이 MVP에서 안전·단순하다. 서버 키 조회 API는 처리상태·보관기간·권한을 함께 설계해야 하므로 추후. `localStorage` 영구 보관은 탭 동기화와 stale 데이터 문제가 있어 채택하지 않았고, 탭 `sessionStorage`는 새 탭/브라우저 세션 종료 시에는 보존하지 못한다.
- **성능/보안:** 오류 시 소량 JSON만 브라우저에 기록하고 새로운 서버 조회는 하지 않는다. 저장소가 거부되면 그 사실을 UI에 표시해 복원을 보장하지 않는다. 사용자 비밀번호/인증 토큰은 저장하지 않는다. `sessionStorage`는 멱등성의 핵심 방어가 아닌 UI 복원 수단이다.
- **복습 질문:** 200 replay/201 create가 물리 재고를 한 번만 바꿨다는 증거로 왜 DB 원장 수 검증이 필요한가? 서로 다른 탭에서 생성된 요청 키는 어떻게 다뤄야 하나?
- **근거:** [TASK-020 지시서](../../tasks/TASK-020-입출고-재시도-멱등성.md) · [검수 보고서](../task-reports/TASK-020-쓰기재시도-멱등성-검수.md) · [TS-009](../TROUBLESHOOTING.md). 테스트 성공 여부는 PR CI와 최종 main 기록을 확인할 것.

### TASK-020 후속 검수 — 확정 거절이면 신규 수정 요청 가능

- 최초 네트워크 오류는 서버가 처리했는지 모르므로 pending으로 유지한다. 그러나 **같은 키 재전송**이 명확한 400/404 또는 재고 부족 409를 반환하면 원래 커밋된 동일 요청은 정상 200 replay로 반환됐어야 한다. 이 경우 현재 요청이 처리되지 않은 것으로 판단하고 폼 잠금/세션 pending을 해제한 후 수정 요청에 **새 키를 발급**한다.
- 반대로 401/403(인증 상황 변화), 다른 payload의 같은 키로 이미 사용된 409(충돌), 5xx는 원 커밋 상태에 대한 충분한 증거가 아니므로 pending을 유지한다.
- **주의:** 이는 해당 코드의 서버 계약에 기반한 클라이언트 동작이며, 미확인 상태에서 모든 4xx를 무조건 처리 실패로 간주해 키를 새로 발행한다는 일반 법칙이 아니다.

## 25. GitHub CI·Vercel 배포·공개 Chrome의 3단계 검증 (TASK-021)

- **GitHub CI:** `frontend-ci.yml`과 `fullstack-ui-ci.yml`의 Node/Chrome/실 MySQL 검증은 코드가 테스트 환경에서 정상 작동한다는 증거다. *그 자체로 사용자에게 배포됐다*는 뜻은 아니다.
- **Vercel Git 배포:** 커밋 `8cb8617`·`7cd77be`는 기능 CI가 성공했지만 GitHub Commit Status가 `Deployment rate limited — retry in 24 hours`였다. 24시간 초과 이후 새 문서 커밋 `61185a7`를 1회 배포 트리거로 사용해 `main 0e57713`, 이어서 `main a91c0cae`의 Vercel **success**를 확인했다. 이전 실패 상태가 success로 소급 변경된 것이 아니다.
- **실제 공개 Chrome QA:** Vercel success는 브라우저 JavaScript 실행·IndexedDB·모달 UI까지 보장하지 않으므로 `factoryflow.kro.kr`를 Playwright가 직접 탐색해 기존 동선 6개와 TASK-020 pending 복원·멱등 재전송 2개를 **8/8** 통과했다. 최신 운영 배포 후에도 재실행 8/8 PASS. 원장 중복/현재고는 **브라우저 데모의 격리된 데이터** 기준이다.
- **서버 멱등성 증명은 별개:** 실제 MySQL 서버가 먼저 커밋한 다음 HTTP 응답이 유실될 때 같은 키의 200 replay 및 원장 1회 보장은 [TASK-020 실DB E2E](https://github.com/jeon950523/factoryflow/actions/runs/43733088860)가 증명한다. 웹 데모 QA로 실제 백엔드 트랜잭션을 대체하지 않는다.
- **장애 운영 원칙:** Vercel 요청 제한은 GitHub CI 실패가 아니다. 원인은 Vercel의 rate limit 상태까지만 확인하고 세부 요금제/쿼터를 추측하지 않는다. 무분별한 push/preview 재배포는 위험하고, 초과 이후 **통제된 재시도 + commit SHA 확인 + 공개 브라우저 검사**가 올바른 복구 절차다.
- **근거:** [TASK-021 작업지시서](../../tasks/TASK-021-배포재시도-공개검증.md) · [시니어 검수 보고서](../task-reports/TASK-021-배포재시도-공개QA-검수.md) · [TS-010](../TROUBLESHOOTING.md).
