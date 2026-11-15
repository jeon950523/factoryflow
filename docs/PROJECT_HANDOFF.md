# FactoryFlow — 프로젝트 상태 및 작업 핸드오프

> 마지막 문서 정리: 2026-11-15
> GitHub: [jeon950523/factoryflow](https://github.com/jeon950523/factoryflow)
> **현재 완료된 기준**: TASK-017 / main `71b6ae204cf2948414da8fe67aa544f5dab9baf9`
> **TASK-018 PR 검증 상태**: [PR #25](https://github.com/jeon950523/factoryflow/pull/25)의 공개 Chrome 6/6·Frontend/실DB E2E 성공. 최신 main 병합·CI 여부는 GitHub에서 확인
> 이 문서의 HEAD는 작업 시작 시 확인한 값이다. 더 최신 코드가 생기면 반드시 GitHub `main`과 PR/CI 결과를 먼저 확인한다.

## 1. 문서 우선순위와 최근 작업

1. 비즈니스 불변조건·상태 기획: [FACTORYFLOW_SSOT_v0.1.md](FACTORYFLOW_SSOT_v0.1.md) **초기 Draft**, 변경은 보고서/ADR/검증과 비교해 판단
2. 현재 구현·배포 경계: [README](../README.md) + 현재 GitHub `main` 코드, [ADR-013](adr/ADR-013-프론트데모-실백엔드분리.md)
3. 최신 작업과 검증: [TASK-017 보고서](task-reports/TASK-017-업무동선-데이터정합성-검수.md) / [PR #24](https://github.com/jeon950523/factoryflow/pull/24)
4. 진행 중 작업: [TASK-018 작업지시서](../tasks/TASK-018-공개배포-QA-문서정합성.md), [TASK-018 보고서](task-reports/TASK-018-공개배포-문서정합성-검수.md)
5. 개념·선택 이유: [CORE_CONCEPTS.md](learning/CORE_CONCEPTS.md), [ADR](adr/), [TROUBLESHOOTING](TROUBLESHOOTING.md)
6. 최신 일정/보류: [로드맵](DELIVERY_ROADMAP_20261111.md)

**중요:** 오래된 보고서나 Draft SSOT가 ‘미구현’이라 하더라도 지금 구현 상태를 판단할 때는 최신 커밋의 API·테스트·ADR과 비교한다. 과거 근거를 삭제하지 말고 현행과의 차이를 표기한다.

## 2. 기술 및 데이터 경계

- Backend: Java 21, Spring Boot, Spring Data JPA, Flyway, MySQL 8.4, Testcontainers, 세션 기반 ADMIN/VIEWER 및 CSRF. 모듈형 모놀리식.
- Frontend: Vue 3, Pinia, Vue Router, 서버 검색형 `ItemSearchSelect`, URL 기반 상세/페이지 복원.
- 공개 웹: https://factoryflow.kro.kr = Vercel 정적 SPA + **브라우저별 IndexedDB 데모**. Java/MySQL·실계정 서버 인증이 아니다.
- 개발 서버: Vite `/api` → 로컬 Spring Boot 8080 → MySQL.
- 도메인: 단일 공장/창고, 정수 EA, 원자재 LOT 입고, BOM 스냅샷, 생산지시 초안→전량 예약→실소모→생산완료→QC 합격품 입고→FIFO 완제품 출고.
- 재고 불변식: `onHand >= reserved >= 0`, `available = onHand - reserved`; 물리 이동 원장은 입고/소모/합격입고/출고만 추적. 예약·취소는 물리 이동 이력이 아니다.
- 한계: BOM 수정/버전, 반품/출고 취소, 다중창고, 부분생산/재작업, 실제 운영 서버 공개 배포는 범위 밖.

## 3. TASK 이력

| TASK | 작업 요약 | PR / main 결과 |
|---|---|---|
| 014 | 재고 이동 원장·품목 전체 검색·업무 이력 링크 | [#21](https://github.com/jeon950523/factoryflow/pull/21) / `c427540` |
| 015 | 실제 공개 Chrome·1440px 스크린샷·한글 폰트 | [#22](https://github.com/jeon950523/factoryflow/pull/22) / `2899b020` |
| 016 | 공통 모달 포커스·닫힘·가독성 | [#23](https://github.com/jeon950523/factoryflow/pull/23) / `4d790666` |
| 017 | BOM/LOT/생산지시/출고 이력 왕복·URL·정합성 | [#24](https://github.com/jeon950523/factoryflow/pull/24) / `71b6ae20` |
| 018 | 공개 동선/URL/예외/모달 6/6, 기록 누락 복원 | [PR #25](https://github.com/jeon950523/factoryflow/pull/25) · PR Chrome/Frontend/실DB 3종 SUCCESS, main 병합 여부 별도 확인 |

## 4. 최종 확인된 자동 검증

TASK-017 병합 `main 71b6ae20`에 대해:
- [Frontend CI](https://github.com/jeon950523/factoryflow/actions/runs/43725889140): Node 37/37, Chrome fixture 21/21, IndexedDB Chrome 27/27, 빌드 성공
- [Fullstack UI E2E](https://github.com/jeon950523/factoryflow/actions/runs/43725889199): 실제 Spring Boot/MySQL+Chrome E2E 2/2
- GitHub Vercel status: 성공. **TASK-017의 공개 도메인 새 동선을 직접 검증했다는 주장은 TASK-018 Chrome 증거 이후만 가능.**

## 5. TASK-018 범위 및 다음 시작점

- `frontend/tests-public/public-site.spec.js`에 **현재 공개 배포**의 새 업무 연결·URL 상태·잘못된 ID·모달 키보드·CSS zoom(Windows OS 배율과 구분)을 검증한다.
- GitHub Actions `FactoryFlow Public Site Smoke`는 PR 확인 커밋 `8a1e6c2`에서 **6/6 성공**했다. [증거 실행 #43726967372](https://github.com/jeon950523/factoryflow/actions/runs/43726967372) · [PNG artifact #14166568094](https://github.com/jeon950523/factoryflow/actions/runs/43726967372/artifacts/14166568094). 후속 문서만 수정한 최종 PR HEAD 및 병합된 main CI를 별도 확인한다.
- 프론트 CI·실DB E2E는 그대로 실행하고, 업무 로직/DB 스키마·데모 상태 엔진 변경 금지.
- README/로드맵/학습 누적/실제 오류 원인 보고서를 GitHub 기준 최신화한다.
- 이후 새로운 TASK를 시작할 때 최종 main SHA·CI 성공 여부·미해결 리팩터링 후보를 확인한다.

## 6. 매 TASK의 완료 기준 (사용자 지정)

[작성 표준](DEVELOPMENT_REPORTING_STANDARD.md) 및 [템플릿](templates/TASK_REPORT_TEMPLATE.md)을 **매 작업에서** 지킨다.

- 작업 전 `tasks/TASK-XXX-*.md`: 목적, 범위, 제외, 검증 조건
- 작업 후 `docs/task-reports/TASK-XXX-*.md`: 호출 흐름, 변경 파일, 핵심 코드 1~3개 및 이유, 대안/장단점, 예외, 검증 근거와 CI URL, 시니어 관점, 기능/효율 리팩터링의 우선순위, 다음 작업, 20~40초 요약 및 이해 점검 질문
- 영향 시 `docs/learning/CORE_CONCEPTS.md` 누적 개념, `docs/adr/` 설계 결정, `docs/TROUBLESHOOTING.md` 재현한 문제, `docs/DELIVERY_ROADMAP_20261111.md`/README/이 핸드오프 갱신
- 구현·정적 검토·실행 검증·실배포 확인을 혼용하지 않는다. 기존 오래된 보고서는 삭제/덮어쓰기보다 차이·후속 이력 링크로 보완한다.
- 한국어 1목적 커밋, PR/실제 Chrome/실 MySQL 검증 후 병합. 원격 게시/병합은 사용자 명시적 요청에 따라 수행한다.

## 7. 새 채팅에서 재개할 때

“FactoryFlow 저장소 `jeon950523/factoryflow`의 `main` 최신 커밋과 CI를 먼저 검수해. `docs/PROJECT_HANDOFF.md`, `docs/DEVELOPMENT_REPORTING_STANDARD.md`, 최신 `tasks/` 지시서와 `docs/task-reports/` 보고서를 참고하고, 구현→시니어 검수→브라우저·MySQL E2E→PR·문서 누적 순서로 작업해. 현재 작업은 TASK-018 공개 도메인 스모크/기록 감사였으니 PR/CI를 먼저 확인해.”
