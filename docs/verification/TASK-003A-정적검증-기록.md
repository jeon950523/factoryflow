# TASK-003A 정적 검증 기록

> 실행일: 2026-11-11 · 환경: Linux/OpenJDK 21.0.12.1 · Spring/DB 테스트 미실행

## 실제로 통과한 검사

1. JDK 21 `JavaCompiler/JavacTask.parse()`로 `backend/src/`의 Java 소스 **41개 문법 파싱**: PASS. **외부 라이브러리의 타입/메서드 호환성 검증이 아님**.
2. `javac --release 21`로 의존성이 없는 `Sku.java`와 별도 Smoke 테스트 컴파일: PASS.
3. `java -cp /tmp/factoryflow-smoke FactoryFlowSkuSmoke`: 정상 SKU 대문자화, 밑줄 허용, 공백 코드 거부: PASS.
4. PyYAML 로 `application*.yml`, Compose, GitHub Actions 총 **5개 YAML** 파싱: PASS.
5. Flyway V1의 `CREATE TABLE` 12개와 JPA `@Table` 12개 이름 일치: PASS. **실제 DDL 실행/DDL 타입 일치는 아님**.
6. Spring Boot 4의 `spring-boot-starter-webmvc-test` 및 `AutoConfigureMockMvc` 이동 경로가 코드에 반영되었는지 점검: PASS (텍스트 기준).

## 실제로 실패하거나 수행할 수 없었던 검사

- `./gradlew --offline --version`: **실패**. Gradle 9.5.1 distribution이 환경에 없어 다운로드를 시도했고 `java.net.UnknownHostException: services.gradle.org`로 중단.
- `./gradlew clean test`: 실행 불가. Gradle bootstrap 실패.
- Docker CLI / Engine: 이 환경에 없음. 따라서 MySQL 8.4/Flyway/JPA validate/Testcontainers 통합 테스트 **미실행**.
- GitHub Actions 실행: 원격에 코드 push 하지 않았으므로 **미실행**.

## 판정

**TASK-003A = 구현 초안 + 정적 검증 완료, 런타임 검증 대기**. `Gradle test PASS` 또는 `배포 성공`으로 표현하지 않는다.
