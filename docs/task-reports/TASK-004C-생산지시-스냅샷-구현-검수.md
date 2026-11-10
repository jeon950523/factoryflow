# TASK-004C — 생산지시 DRAFT 생성 및 BOM 스냅샷 구현·검수

> 상태: **구현·정적 검사 완료 / CI 통합검증 대기**  
> 날짜: 2026-11-11 / 선행 작업: TASK-004B (17개 테스트 CI 통과)

## 1. 목적

원자재 예약과 실제 소모 전에 `DRAFT` 생산지시를 생성한다. BOM의 자재 수량(`qty_per_unit`)에 계획 생산량(`planned_qty`)을 곱한 결과를 `work_order_materials`에 **생성 당시 스냅샷**으로 고정한다. 이후 BOM이 변경되더라도 이미 생성된 생산지시의 필요 자재가 바뀌어서는 안 된다.

## 2. 전체 호출 흐름

`POST /api/v1/work-orders` → `WorkOrderController` → `WorkOrderService`(요청 정규화/멱등성 조회) → `WorkOrderWriteService @Transactional` → `ItemRepository`(완제품 확인) → `BomRepository`/`BomComponentRepository`(BOM 읽기) → 필요 수량 곱셈(`Math.multiplyExact`) → `WorkOrderRepository`(DRAFT 저장) → `WorkOrderMaterialRepository`(스냅샷 저장) → 커밋 → 201 응답.

중복 `clientRequestId`는 조회 후 처리하며 병렬 중복으로 DB UNIQUE가 충돌하면 쓰기 트랜잭션 종료 후 별도 조회에서 동일 내용이면 **200 기존 결과**, 다른 내용이면 **409 키 충돌**로 응답한다. `GET /api/v1/work-orders/{id}`는 작업/스냅샷을 함께 조회한다.

## 3. 파일별 역할과 작성 이유

| 파일 | 목적 | 왜 이렇게 작성했는가 |
|---|---|---|
| `production/WorkOrderController.java` | POST/GET와 HTTP 201·200 분기 | HTTP 계층에 업무 수량 계산을 넣지 않음 |
| `production/WorkOrderService.java` | 멱등성 오케스트레이터 | 실패한 쓰기 트랜잭션 안에서 재조회하지 않음 |
| `production/WorkOrderWriteService.java` | 실제 생성·스냅샷 저장 `@Transactional` | 작업 헤더와 모든 필요 자재를 하나의 원자적 작업으로 처리 |
| `production/WorkOrderLookupService.java` | 재조회·멱등성 충돌 검사 | 읽기 전용 트랜잭션과 DTO 변환 담당 |
| `production/validation/CanonicalWorkOrder.java` | 요청 키 정규화/해시 | 대소문자 재전송을 같은 요청으로 인식, 다른 payload 재사용 방지 |
| `production/WorkOrderEntity.java` | DRAFT 팩터리·읽기 접근자 | 임의 상태 변경을 노출하지 않음 |
| `production/WorkOrderMaterialEntity.java` | 불변 필요 자재 스냅샷 | BOM 실시간 JOIN이 아닌 확정 수량 저장 |
| `production/*Repository.java` | JPA 쿼리 | 도메인 계층에서 직접 SQL을 작성하지 않음 |
| `production/dto/*` | API 입력·출력 계약 | JPA Entity 외부 노출 방지 |
| `WorkOrderDraftIntegrationTest.java` | 실제 MySQL 연동 검증 | 상태·스냅샷·중복·동시성·오버플로·잔여 데이터 확인 |

## 4. 주요 코드 선택 이유

**`Math.multiplyExact(qtyPerUnit, plannedQty)`:** 일반 `long` 곱셈은 64비트 범위를 넘으면 음수나 잘못된 양수가 될 수 있다. 스냅샷 저장 전에 모든 곱셈을 수행하고 초과 시 400을 반환해 잘못된 필요 자재량을 막는다.

**트랜잭션을 writer Bean에 지정:** Spring AOP 프록시를 거쳐 호출하면 실제 트랜잭션이 생성된다. `WorkOrderService`는 트랜잭션 바깥에서 실패를 잡고 조회 Bean을 호출한다. 같은 Bean 내부 self-invocation으로 트랜잭션이 적용되지 않는 문제를 피한다.

**BOM 원본 대신 자재 스냅샷:** 생산지시의 역사적 의미가 현재 BOM에 의해 바뀌지 않게 한다. 단점은 BOM 정보가 중복 저장되므로 저장공간/버전 관리 비용이 생긴다.

**품목에 비관적 락을 걸지 않음:** `DRAFT`는 실제 재고를 건드리지 않는다. 잠금 비용을 미리 지불할 이유가 없으며, 실제 재고 변경은 다음 작업에서 Item/LOT 순서대로 잠근다.

## 5. 주요 예외 및 테스트

- 잘못된 완제품/비활성 원자재 → 400, 완제품 미존재/BOM 미존재 → 404.
- BOM 구성품 없음 → 409, 수량 곱셈 오버플로 → 400, DB 쓰기 없음.
- 동일 `clientRequestId` 같은 요청 → 200 기존 내용, 다른 요청 → 409.
- 병렬 동일 키 2건 → 1건 201 + 1건 200, `work_orders`와 스냅샷 1세트.
- 등록 직후 재고와 예약 수량은 변하지 않는다 (`DRAFT`는 예약 아님).
- Testcontainers MySQL 8.4 + MockMvc로 위 조건을 검증한다. **CI 실행 전까지 통과라고 말하지 않는다.**

## 6. 시니어 검수 — 장단점 및 리팩터링

| 구분 | 판단 |
|---|---|
| 장점 | 트랜잭션 원자성, 스냅샷 불변성, 멱등성, 오버플로 방어, 응답 DTO 분리 |
| 단점 | 동일 내용 중복 해시·정규화 코드가 원자재 입고 모듈과 중복, 멱등성 조회 추가 쿼리 발생 |
| 즉시 수정 | 실제 CI에서 발견되는 컴파일·JPA·테스트 오류. 중복 요청이나 잘못된 생산지시의 잔여 DB 행 방어 |
| 다음 작업 | `RESERVED` 상태 전이, 품목 ID 순서 잠금, FIFO LOT 예약, 전체 성공/실패 원자성 |
| 후속 리팩터링 | 두 멱등성 키의 공통 형식만 선별적으로 추출, Clock 주입, 테스트 DB 컨테이너 재사용 최적화 |
| 지금 불필요 | 비동기 Kafka 이벤트, Redis 락, MSA 분리, 공정 라우팅, BOM 버전 관리 |

## 7. 직접 설명할 요약

> 생산지시는 생성 시점의 BOM 필요 수량을 스냅샷으로 저장합니다. 서비스의 트랜잭션 안에서 생산지시와 자재 목록을 함께 저장하고, 수량 곱셈에 `Math.multiplyExact`를 사용해 오버플로를 방지했습니다. 중복 클라이언트 요청은 고유키와 요청 해시를 비교해 같은 요청은 재사용하고 다른 요청은 거부합니다. 아직 재고 예약은 하지 않아 `DRAFT`와 `RESERVED`를 명확히 구분했습니다.

### 스스로 답할 질문

1. 왜 생산지시를 만들 때 BOM을 그대로 참조하는 대신 자재 스냅샷을 저장할까?
2. 왜 컨트롤러에 `@Transactional`을 붙이지 않고 writer Service에 적용할까?
3. 같은 `clientRequestId`를 두 클라이언트가 동시에 요청하면 사전 조회만으로 충분할까?
