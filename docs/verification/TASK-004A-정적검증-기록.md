# TASK-004A 정적 검증 기록

> 2026-11-11 · 작업 대상: TASK-002 + TASK-003A + TASK-003B 통합본

## 1. 실제로 실행한 검사

| 검사 | 실행 방식 | 결과 | 해석 |
|---|---|---|---|
| Java 구문 분석 | JDK 21 `JavaCompiler` `JavacTask.parse()` | 60개 소스 / 오류 0 | Java 문법만 확인. Spring 의존성 타입 해석·메서드 존재·바이트코드 컴파일 미검증 |
| SKU 순수 Java 실행 | `javac` + Java 21 `SkuSmoke` 임시 러너 | 6/6 조건 통과 | SKU 대문자화 및 유효하지 않은 값 거부 규칙만 확인 |
| YAML 형식 | PyYAML parser | 5개 통과 | GitHub Actions·Spring 설정·Compose의 구문 분석 통과. 실제 환경별 동작 미검증 |
| DB 매핑 구조 | V1 SQL CREATE TABLE 정규식과 JPA `@Table` 집합 비교 | 12개 테이블 일치 | 컬럼 타입·JPA Hibernate `ddl-auto=validate` 통과 의미 아님 |
| 비밀 파일명 검사 | `.env`·`.pem`·`.p12`·`.pfx` 포함 여부 | 발견 0 | 내용까지 완전한 비밀정보 누출 검증을 의미하지 않음 |
| 통합 배포 ZIP | Python zipfile `testzip()` | 오류 없음 / 93개 파일 | 압축 파일 읽기 및 무결성 확인 |

## 2. 실행할 수 없었던 검사

- `backend/gradlew clean test`: `services.gradle.org` DNS 연결 실패로 Gradle 9.5.1 다운로드 불가. 기존 실패 로그는 `factoryflow-next-gradle-attempt.log`에 있음.
- Testcontainers `mysql:8.4`: 실행 환경에 Docker Engine이 설치되어 있지 않음.
- Spring Boot 기동·Flyway·JPA 스키마 검증: Gradle 빌드/의존성 다운로드와 MySQL 컨테이너 실행이 필요.
- GitHub Actions: **이 통합 코드는 아직 원격 브랜치에 게시하지 않았으므로 실행되지 않음**.

## 3. 다음 승인 후 검증 절차

기능 브랜치에 통합본을 올리고 GitHub Actions에서 `Backend CI` 실행을 확인한다. 실패 시 `factoryflow-backend-test-results` 아티팩트 및 액션 로그를 증거로 삼아 해당 코드/DDL을 수정하고 재검증한다.

로컬 Windows PowerShell (저장소 루트에서):

```powershell
Copy-Item .\infra\.env.example .\infra\.env; notepad .\infra\.env
```

변경한 DB 비밀번호가 포함된 파일은 절대 Git에 올리지 않는다.

```powershell
docker compose --env-file .\infra\.env -f .\infra\compose.yml up -d
```

```powershell
$env:DB_PASSWORD=(Get-Content .\infra\.env | Select-String '^FACTORYFLOW_DB_PASSWORD=').ToString().Split('=',2)[1]; Set-Location .\backend; .\gradlew.bat clean test
```

## 4. 정확한 완료 판정

**TASK-004A 미완료(런타임 검증 대기)**: 위 정적 검사가 모두 통과해도 MySQL 스키마의 실제 무결성과 Spring DI/JPA/HTTP 동작은 검증되지 않았다. 녹색 CI 실행 기록을 확인하기 전까지 완료로 변경하지 않는다.
