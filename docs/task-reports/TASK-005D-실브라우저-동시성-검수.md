# TASK-005D — 실DB 브라우저 업무 E2E·연속 조회 경쟁 수정 시니어 검수

> 검증: Node 12/12, Vite build 성공, Chrome Fixture 8/8, 실제 Chrome+Spring Boot+MySQL 2/2. 기준일 2026-11-12.

## 1. 목적
관리자 화면에서 사용자가 작업 A를 선택한 다음 B를 빠르게 선택했을 때 느린 A API 응답이 최신 B 상세를 덮어쓸 수 있는 비동기 경쟁 조건을 수정한다. 또한 TASK-005C의 Chrome 검사는 고정 API Fixture였으므로, 실제 Vue→Spring Boot→Flyway→MySQL 연결로 제조 업무 전 과정을 입증한다.

## 2. 호출 흐름
- 연속 상세 선택: A 클릭 → latestRequest.begin(A) → A GET → B 클릭 → A AbortController 중단/revision 증가 → B GET → 현재 revision과 동일한 응답만 Vue selected에 반영. AbortError는 사용자 오류로 표시하지 않는다.
- 목록과 상세는 별도 요청 Gate 사용. BOM과 LOT에도 같은 규칙. onBeforeUnmount에서 남은 요청을 취소.
- 상태 변경 버튼은 시작 당시 생산지시 ID를 따로 캡처하여, 작업 처리 중 다른 지시를 선택해도 이전 지시를 다시 열지 않는다.
- 실제 업무: GitHub Actions Ubuntu 24.04 + MySQL 8.4 서비스 → Java 21 Gradle bootJar → Spring Boot 로컬 기동(Flyway/JPA 검증) → Vite /api 프록시 → Playwright Chrome에서 입고/생산/품질검사/출고 폼 클릭 → REST 조회 결과 재고·이력 대조.

## 3. 파일 책임
- frontend/src/lib/latestRequest.js: AbortController와 sequence Gate, 최신 요청 여부 판정.
- frontend/src/lib/latestRequest.test.js: 취소·revision 및 AbortError 분리 Node 테스트 2개.
- frontend/src/views/OrdersView.vue: 생산지시 목록/상세 최신 조회, 작업 대상 ID 캡처.
- frontend/src/views/ShipmentsView.vue: 출고 목록/상세 경합 차단.
- frontend/src/views/ItemsView.vue: 품목 필터/선택 및 BOM 지연 응답 방어.
- frontend/src/views/InventoryView.vue: 목록/선택 LOT의 이전 응답 차단.
- frontend/tests/latest-selection.spec.js: 생산지시/출고/BOM 연속 A→B 선택의 실제 Chrome+Fixture 3개.
- frontend/tests-live/real-manufacturing.spec.js: 실제 MySQL 업무 정상 흐름과 재고 부족 오류 시나리오 2개.
- frontend/playwright.live.config.js: 실서비스 전용 Playwright 분리 구성.
- .github/workflows/fullstack-ui-ci.yml: 일회용 MySQL, Java 21, bootJar/health, Vite, Chrome 통합과 실패 증거 업로드. 무인증 서버를 외부에 공개하지 않음.

## 4. 실제 검증
- Frontend Fixture CI https://github.com/jeon950523/factoryflow/actions/runs/43065947057 : Node **12/12**, Vite production build, Chrome **8/8**.
- 실 MySQL 브라우저 정상 업무 https://github.com/jeon950523/factoryflow/actions/runs/43066243956 : Chrome 1/1.
- 실 MySQL 브라우저 확장 https://github.com/jeon950523/factoryflow/actions/runs/43066642137 : **Chrome 2/2**.
- 기존 main Testcontainers MySQL 53/53 https://github.com/jeon950523/factoryflow/actions/runs/43046278405 (이번 업무 로직 Java 코드는 미변경).
- 실제 재고: 철판 25입고 → 20예약/소모 → 잔량5, 검사 합격8·불합격2 → 완제품 3출고 → 잔량5. RAW_RECEIPT25, RAW_CONSUMPTION20, FINISHED_RECEIPT8, FINISHED_SHIPMENT3 확인.
- 오류: 재고3에서 필요량4 예약 → 409 안내, DRAFT 유지, 현재고3/예약고0 보존.
- CI 최초 실패 https://github.com/jeon950523/factoryflow/actions/runs/43065947121 : bootJar 성공 후 JAR 파일 이름 고정 가정으로 실행 실패. 산출물 찾기 명령으로 수정(커밋 63222a1) 후 통과.

## 5. 코드 선택·대안·장단점
- AbortController 단독: 네트워크는 중단하지만 이미 resolve된 비동기 단계의 상태 업데이트는 별도 제어가 필요할 수 있음.
- revision 단독: 늦은 응답을 무시할 수 있지만 취소 가능한 GET을 계속 수행하므로 낭비.
- **현재 채택:** AbortController + isCurrent() 이중 방어. 별도 View별 Gate로 업무 목록 조회와 상세 조회 간 간섭 방지.
- 브라우저 Fixture 검증은 지연·역순 응답을 빠르고 결정적으로 재현하지만 DB 계약을 보증하지 못함. 실 MySQL E2E는 비용이 더 들지만 Controller·Service·트랜잭션·DB 및 실제 사용자 버튼 흐름을 한 번에 검사.
- MySQL 서버 및 백엔드는 runner 로컬에서만 기동하고 일회용 DB 사용. 공개 서비스/로그인된 사용자 시연 검증으로 표현하지 않는다.

## 6. 시니어 검수
- **잘한 점:** 실제 DB/화면 통합 재고 수량 검증, 경합을 의도적으로 재현하는 자동화, 이전 선택 결과 무시, 오류 처리 시 트랜잭션 원자성 확인, 테스트 범위 구분.
- **고친 문제:** 잘못된 JAR 파일명 가정 및 CI 실행 환경. 요청 취소/최신 선택 우선 로직.
- **위험:** 4개 Vue 화면에 반복된 try/catch/finally와 Gate 전달, 이름 조회 장애 토스트 중복 가능성. 부분 네트워크 실패와 다중 사용자 동시 수정, 스크린리더/모바일은 미검증.
- **2차 리팩터링:** Vue composable로 비동기 조회 상태 재사용, Playwright를 devDependencies/package-lock에 고정, 에러 토스트 통합, 상태 전이 동시 클릭 회귀 확대. 실측 없는 Redis/MSA 도입 보류.
- **보안 게이트:** 외부 사용자 인증·권한, CORS/CSRF, 비밀 보호와 백업·복원 검증 이전 Vercel/Oracle 공개 배포 금지.

## 7. 나중에 설명할 요약
"사용자가 A를 선택한 직후 B를 선택할 때 A의 느린 API 응답이 B 화면을 덮어쓸 수 있다는 점을 발견해 요청 취소와 버전 판정을 적용했습니다. 또한 일회용 MySQL 컨테이너와 Spring Boot를 GitHub Actions에서 띄우고 Chrome으로 실제 입고·생산·검사·출고를 조작하여 최종 재고와 이동 기록을 검증했습니다."
