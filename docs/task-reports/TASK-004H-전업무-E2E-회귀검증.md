# TASK-004H — 전 업무 API E2E 회귀 검증·시니어 리뷰

> 상태: 2026-11-11 **GitHub Actions / MySQL 8.4 Testcontainers 49 tests, 0 failures, 0 errors, 0 skipped**.

## 1. 목적
원자재 입고 → BOM → 생산지시 DRAFT → LOT 전량 예약 → 실제 자재 소모 → 생산 완료 → 품질검사 → 합격품 입고 → 출고를 단일 통합 시나리오에서 순차 호출한다. 개별 API만 테스트해서 놓칠 수 있는 단계 간 재고 수량 불일치, 중복 반영, 이력 누락을 검증한다.

## 2. 전체 호출 순서와 파일 역할
- FactoryFlowEndToEndIntegrationTest: MockMvc로 Controller를 통해서만 품목/입고/생산/검사/출고 데이터를 생성한다. JdbcTemplate은 커밋된 DB 불변조건 조회에만 사용한다.
- ItemController/BomController → ItemService/BomService → JPA: 품목과 BOM 준비.
- StockReceiptController → ReceiptService/Writer → LOT/RAW_RECEIPT: 원자재 입고.
- WorkOrderController → WorkOrderService/ReservationService/ProductionService: 생산지시 스냅샷, FIFO 예약, 생산 시작 원자재 소모, 검사 대기.
- QualityInspectionController → QualityInspectionService: 합격 수량만 QC_PASS LOT 및 FINISHED_RECEIPT로 확정.
- ShipmentController → ShipmentWriteService: 합격품 FIFO LOT 차감 및 출고/LOT 배분/FINISHED_SHIPMENT 이력.
- InventoryController 및 JDBC 조회: 각 시점 onHand/reserved/available, 이동 이력과 실제 물리 재고 대조.

## 3. 테스트 4건
1. Golden path: 철판 100(30+70 LOT), 볼트 200, BOM(철판 1/볼트 2) → 생산 50 예약·소모 → QC 47 합격·3 불합격 → 10개 출고. 완제품 재고 37, RAW_RECEIPT 3행 + RAW_CONSUMPTION 3행 + FINISHED_RECEIPT 1행 + FINISHED_SHIPMENT 1행 = 8개 이동 이력.
2. 부족 재고 복구: 두 번째 자재 부족 시 409와 모든 예약의 원자적 롤백, 추가 입고 후 예약·취소. 현재고 보존, 예약 0, 소모 이력 0.
3. 멱등성: 원자재 입고·생산지시·출고 동일 요청 재전송 200, 다른 수량에 같은 출고 키 사용 시 409, 품질검사 재확정 409. 재고와 이동 이력은 한 번만 반영.
4. 전량 불합격: 검사 합격 0/불합격 전량 → 업무 상태 COMPLETED지만 완제품 LOT 0, 출고 거절 409.

## 4. 핵심 코드 선택 이유
- 서비스 트랜잭션 내부 DB를 직접 변경하는 테스트가 아니다. 모든 쓰기 요청을 MockMvc HTTP 경유로 실행하여 Controller의 Bean Validation, 서비스 규칙, DB 제약을 함께 통과시킨다.
- 테스트 클래스에 Transactional을 붙이지 않는다. 그래야 API마다 실제로 커밋/롤백한 결과를 관찰할 수 있다.
- JDBC는 조회와 수량 불변식 계산에만 사용해 JPA 1차 캐시나 영속성 컨텍스트에 가려진 잘못된 DB 상태를 놓치지 않는다.
- 재고 이동 이력 합계와 LOT 현재고 합계가 일치하는지 실제 계산한다. 예약은 물리 재고 이동이 아니라 별도로 검증한다.
- UUID 기반 품목 SKU·요청 키로 테스트의 실행 순서·과거 데이터에 의존하지 않게 한다.

## 5. 실제 CI 실패와 해결
- 최초 실패: https://github.com/jeon950523/factoryflow/actions/runs/42987199491
- 증상: E2E 테스트에서 com.fasterxml.jackson.databind 패키지를 찾지 못해 컴파일 실패.
- 원인: Spring Boot 4.1.2의 Jackson 3 구성과 달리 Jackson 2 패키지를 import함.
- 수정: JsonNode/ObjectMapper를 tools.jackson.databind로 변경. 운영 비즈니스 로직 변경 없음. 수정 커밋 2117793c6fe7ad23e7f969989c49626a31e99735.
- 재검증: https://github.com/jeon950523/factoryflow/actions/runs/42987324239 — BUILD SUCCESSFUL in 2m 11s.
- JUnit XML 원본 11개 스위트를 합산하여 **49개 실행, 실패/오류/건너뜀 모두 0개**. E2E 신규 4개 성공.

## 6. 시니어 검수: 장점, 위험, 리팩터링
- 유지: 비즈니스 변경은 API로 수행, DB 커밋 결과는 별도 읽기로 검증, 정상·복구·재전송·불량 시나리오 포함.
- 즉시 수정: Jackson 3 import 문제 수정 완료. 현재 CI 기준 남은 실패 없음.
- 단점: 컨테이너 기동 시간과 중복 테스트 데이터 헬퍼가 존재하며, MockMvc는 실제 네트워크·Vue 화면·공개 배포를 보증하지 않는다.
- 2차 리팩터링: 여러 테스트 클래스의 API fixture 중복을 재사용 유틸로 정리; Testcontainers 공유 전략으로 실행시간 최적화(테스트 간 상태 격리 우선); SQL/인덱스/잠금 실측.
- 후속 필수 검증: Vue UI 실제 브라우저 E2E, 로그인과 권한, TLS/CORS·환경변수, Oracle/Vercel 실제 배포와 MySQL 백업·복원.
- 현재 불필요: Kafka·Redis·MSA 추가. 측정 전 성능 개선 완료 주장 금지.

## 7. 나중에 직접 설명할 핵심
개별 API 테스트 외에 입고부터 생산과 품질검사, 출고까지 묶은 백엔드 통합 E2E를 작성했다. 입력은 실제 Controller를 통해 보내고, MySQL 커밋 결과의 현재고·예약고·가용고와 이동 이력을 교차 확인해 전체 업무 수량 정합성을 검증했다. Java 21/Testcontainers MySQL CI의 전체 49개 테스트가 통과했다. 이는 Vue와 공개 배포까지 검증했다는 의미는 아니다.
