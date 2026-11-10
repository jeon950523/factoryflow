# FactoryFlow

> **설계 진행 중 · 구현 전 (2026-11-11)**

Java 21 / Spring Boot 기반으로 제조업의 **원자재 입고 → BOM → 생산지시 → 재고 예약·소모 → 품질검사 → 완제품 입고·출고** 흐름을 구현하는 개인 백엔드 포트폴리오 프로젝트입니다.

## 프로젝트 목적

- 제조 업무의 데이터 모델과 상태 전이 설계
- 원자재 **현재고(on-hand)**, **예약고(reserved)**, **가용재고(available)**의 구분
- MySQL 트랜잭션·잠금과 멱등성 키를 통한 동시성 및 중복 요청 처리
- JUnit 5 + Testcontainers(MySQL)로 재고 정합성 검증
- SQL 실행 계획 및 측정 결과로 성능 개선의 근거 제시

## 현재 진행 상태

| 작업 | 상태 | 문서 |
|---|---|---|
| TASK-001 · 요구사항·도메인·기술 설계 | 설계 초안 작성 | [SSOT v0.1](docs/FACTORYFLOW_SSOT_v0.1.md) |
| TASK-002 · 개발 기반 구축 | 미구현 / 다음 작업 | [작업지시서](tasks/FACTORYFLOW_TASK_002_개발기반구축.md) |
| TASK-003 이후 · 품목·재고·생산·품질 구현 | 계획 | [SSOT의 구현 범위](docs/FACTORYFLOW_SSOT_v0.1.md) |

**현재는 기능 구현, 빌드, 통합 테스트, 배포를 완료한 상태가 아닙니다.** 문서에 적힌 API·테이블·테스트 항목은 구현 예정 사양입니다.

## 기술 방향 (계획)

| 영역 | 기술 |
|---|---|
| Backend | Java 21, Spring Boot, Spring Data JPA |
| Database | MySQL 8.4, Flyway |
| Frontend | Vue 3, Pinia |
| Testing | JUnit 5, Testcontainers(MySQL) |
| Infrastructure | Docker Compose, GitHub Actions |

첫 버전은 **모듈형 모놀리식**으로 구현합니다. Redis·Kafka·MSA·Kubernetes·RAG는 1차 범위에서 제외합니다.

## 핵심 업무 흐름

```text
품목 및 BOM 등록
    ↓
원자재 LOT 입고
    ↓
생산지시 생성 (DRAFT)
    ↓
원자재 전량 예약 (RESERVED)
    ↓
생산 시작: 예약 원자재 소모 (IN_PROGRESS)
    ↓
생산 완료 (AWAITING_QC)
    ↓
품질검사: 합격품만 재고 반영 (COMPLETED)
    ↓
완제품 출고
```

## 설계 원칙

1. 재고 예약 시 실제 수량을 차감하지 않고, 생산 시작 시 예약 물량을 소모합니다.
2. 생산지시에 필요한 자재가 부족하면 부분 예약 없이 전체 작업을 취소합니다.
3. LOT 단위 추적과 재고 이동 이력을 남깁니다.
4. 동일한 입고·출고 요청의 재전송이 수량을 중복 변경하지 않도록 설계합니다.
5. 구현 전에 테스트할 불변조건과 예외 시나리오를 먼저 작성합니다.

## 문서

- [제품·도메인·기술 설계 SSOT v0.1](docs/FACTORYFLOW_SSOT_v0.1.md)
- [TASK-002 초기 개발 기반 구축 작업지시서](tasks/FACTORYFLOW_TASK_002_개발기반구축.md)

## 공개 범위 및 보안

설계 문서만 공개합니다. 추후 구현 시 DB 비밀번호, `.env`, 실제 개인정보, 내부 인프라 접속 정보를 Git에 올리지 않습니다. 인증·인가가 구현되기 전의 쓰기 API는 인터넷에 공개하지 않습니다.