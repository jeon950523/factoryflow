# TASK-018 — 공개 배포 업무 추적·예외 QA 및 기록 누락 보완

> 기준: `main` `71b6ae204cf2948414da8fe67aa544f5dab9baf9` (TASK-017)
> 시작: 2026-11-15
> 상태: 검증 중. 실제 공개 Chrome 및 PR/main CI가 모두 성공하기 전 완료로 표기하지 않음
> 작업 방식: 최신 main 검수 → 작업 브랜치 → 코드/문서 리뷰 → 공개 Chrome + 기존 CI 검증 → PR → main 병합 확인

## 목적과 문제

TASK-017에서 업무 ID 기반 BOM·LOT·생산지시·출고 추적 링크를 추가했지만 **공개 Vercel에서 이 동선을 Chrome으로 확인한 결과**는 없었다. 이전 TASK-014~017 보고서가 작성되었으나 [개발 기록 표준](../docs/DEVELOPMENT_REPORTING_STANDARD.md)에서 요구한 핵심 코드 선택 이유·대안의 장단점·리팩터링 판정·누적 학습·README 최신화·장애 기록·TASK별 지시서 일부가 누락되었다.

## 구현 및 비범위

- 공개 URL `https://factoryflow.kro.kr`를 Linux Google Chrome 1440×900으로 조회한다.
- 실제 데모 IndexedDB를 이용해 BOM → 원자재 LOT → 물리 이동 → 생산지시 → 완제품 LOT → 이동 → 출고 배분 → 이동 경로, 생산지시 상태/ID URL 새로고침 복원을 검증한다.
- 잘못된 품목 ID/페이지 쿼리를 이용해 조회 오류 표시를 점검하고, 모달의 키보드 Escape·포커스 복귀, 125% CSS zoom 스트레스 캡처를 수집한다.
- 공개 사이트 **쓰기 요청은 수행하지 않는다**. 방문자 데이터는 각 Chrome 테스트 컨텍스트의 IndexedDB에 한정한다.
- `docs/learning/CORE_CONCEPTS.md`, `README.md`, 로드맵, `docs/TROUBLESHOOTING.md`, `docs/PROJECT_HANDOFF.md`를 실제 확인된 상태로 최신화한다.
- API, Java 백엔드, MySQL, IndexedDB 엔진, 제조 FIFO/멱등성 비즈니스 로직에는 손대지 않는다.
- OS Windows 125%/150% 글꼴 검증은 Linux CSS zoom과 별개이며 검증했다고 주장하지 않는다.

## 완료 기준

1. `frontend/tests-public/public-site.spec.js`의 기존 3건 + TASK-018 추가 3건이 공개 도메인 Chrome에서 통과하고 증거 PNG가 GitHub Actions 아티팩트에 남는다.
2. PR 헤드에서 Frontend CI와 Spring Boot + MySQL E2E가 모두 성공한다.
3. 작업 지시서·결과 보고서·핵심 누적 개념·문서 인덱스/핸드오프·실제 트러블슈팅·README/로드맵의 서로 다른 상태가 정리된다.
4. 시니어 검수 항목(구현 이유, 핵심 코드 흐름, 대안/트레이드오프, 보안/예외, 검증 로그, 기능/효율 리팩터링 판정, 후속 작업, 설명 연습)을 `docs/task-reports/TASK-018-공개배포-문서정합성-검수.md`에 기록한다.
5. 동일 커밋 PR CI가 성공하고 병합 후 `main` CI도 성공한 뒤 작업 완료로 판단한다.

## 기존 미흡한 기록 소급 정책

- TASK-014~017의 원본 작성 이력은 삭제·재작성하지 않고, 기존 보고서 링크와 누락 항목을 이번 TASK의 **문서 감사 기록**에서 재검증·보충한다.
- 미확인 과거 실행 결과·운영 PC 사용성·비용/성능 수치는 만들지 않는다.
- 아키텍처 변경은 없으므로 새 ADR은 작성하지 않고 `ADR-013`의 “Vercel IndexedDB 대 실DB” 구분을 유지한다.
