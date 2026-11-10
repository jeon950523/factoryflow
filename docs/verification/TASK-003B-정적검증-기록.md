# TASK-003B 정적·독립 실행 검증 기록

> 작성일: 2026-11-11  
> **전체 Gradle 빌드와 실제 MySQL·Docker 통합 테스트는 수행되지 않음**

## 실행 환경

- OpenJDK 21.0.12.1 (`javac` / `java`)
- Gradle 실행 파일 없음; Wrapper 배포 ZIP `gradle-9.5.1-bin.zip` 네트워크 DNS 해석 실패
- Docker CLI/Engine 없음

## 실제 실행 결과

1. Java Compiler API(`JavacTask.parse()`)로 `backend/src` 하위 Java 파일 **60개 문법 분석** → **문법 오류 0개**. 이는 타입해석·외부 라이브러리 의존성 검사 및 `test` 실행이 아님.
2. `CanonicalReceipt` 실제 소스를 JDK 21로 컴파일(외부 Spring 의존성은 테스트 전용 최소 스텁으로 대체)한 뒤 **11개 정규화·해시·잘못된 입력 조건 PASS**.
3. `InventoryLotEntity` 실제 소스를 JDK 21로 컴파일(JPA 어노테이션 등은 테스트 전용 최소 스텁)한 뒤 **7개 수량 누적·오버플로·부분 대입 방지 조건 PASS**.
4. Flyway V1의 `CREATE TABLE` 12개와 `@Table(name=...)` 엔티티 12개 이름 집합 일치. `UNIQUE(request_key)`, `reserved_qty <= on_hand_qty` 명세가 SQL에 존재함. **실제 제약 작동 검증이 아님**.
5. Spring YAML, 테스트 YAML, Compose, GitHub Actions를 포함한 **YAML 파일 5개 정적 파싱 PASS**. 실제 CI/Docker 실행 아님.
6. `./gradlew --offline clean test` 실행 → **Gradle 배포 파일 미존재 및 `services.gradle.org` DNS 해석 실패**. 테스트 코드는 실행되지 않음.

## 통합 테스트 실행 대기 목록

- `DatabaseBaselineIntegrationTest` — Flyway/JPA/MySQL 제약
- `ItemBomApiIntegrationTest` — 품목·BOM API
- `StockReceiptApiIntegrationTest` — 신규/기존 LOT, 멱등성 재전송, 동일 키/다른 키 경쟁, 오버플로/404/400/409
- `StockReceiptRollbackIntegrationTest` — 이력 저장 실패 시 원자적 롤백

**완료 기준:** 사용자 PC/CI에서 `./gradlew clean test` 실행 PASS와 실제 DB 레코드 검증 결과를 확보하기 전까지 '통합 검증 완료'로 표시하지 않는다.
