# FactoryFlow — 진행 로드맵 및 TASK 상태

> 기준일: 2026-11-15 · 실제 정본: [GitHub main](https://github.com/jeon950523/factoryflow) 및 TASK별 CI/PR 보고서
> 이 문서는 기존 TASK-011~013 기록을 보존한 뒤 TASK-014 이후 상태를 추가했다. TASK-001~010의 과거 상세 근거는 [README](../README.md), [task-reports](task-reports/) 및 [SSOT](FACTORYFLOW_SSOT_v0.1.md) 참조.
> 주의: Vercel 공개 브라우저는 IndexedDB 데모이며 Spring Boot + MySQL 운영 서버 배포가 아니다.

## TASK-011 — 생산지시·품질검사 화면 ERP형 전환

- 기존 2열 카드형 작업 공간을 제거하고, 생산지시 전체 너비 목록과 별도 하단 상세 처리 영역으로 변경.
- 상태별 필터는 기존 쿼리와 연동; 지시번호/상세 보기에서 자재 소요량과 실제 실행 가능한 단계 표시.
- 검사 입력은 계획량 대비 합격·불합격 합계를 검증하고, 불일치 시 확정 버튼을 비활성화한다. 기존 API 검증 유지.
- VIEWER 읽기 권한 및 관리자 쓰기 버튼 숨김을 Chrome에서 확인.
- **CI 통과:** Frontend Node 37/37, Chrome fixture 12/12, IndexedDB Chrome 10/10, Spring Boot·MySQL 실DB E2E 2/2.
- **다음 순서:** TASK-012 재고·LOT·입고 UI → TASK-013 품목/BOM·출고 UI → TASK-014 공통 디자인/이력·전체 QA.
- **남은 위험:** 현장 UI 시각·접근성 평가, QC 전량 합격 사전 입력에 따른 실수 가능성, 실제 서버 미배포.

## TASK-012 — 재고·LOT·원자재 입고 UI 업무형 전환

- 전체 너비 재고 목록/품목 유형 필터와 선택 품목의 하단 LOT 표를 도입. 조회·입고가 실제 구현된 API에서만 동작하게 한다.
- 재고 페이지 URL 쿼리 `itemType/page/itemId`로 필터/선택을 유지하고 목록 밖 품목의 요약 정보를 직접 조회한다.
- 입고 화면은 기존 멱등성 요청 키 유지, 중복 클릭 방지, 저장 중 폼 닫기 방지, 선택 원자재 사전 표시. 성공 후 처리 품목 LOT로 복귀한다.
- **검증:** Frontend Node 37/37, fixture Chrome 13/13, 브라우저 데모 Chrome 14/14, Spring Boot·MySQL E2E 2/2.
- **후속:** TASK-013 품목·BOM/출고 화면, TASK-014 재고 이동 이력과 전체 디자인 일관성 및 PC 실제 배포 QA.
- **주의:** 실DB 권한·멱등성/동시성 규칙은 변경하지 않았으며 UI 검사가 상용 재고관리 성능 검증을 의미하지는 않는다.

## TASK-013 — 품목·BOM·완제품 출고 UI 업무형 전환

- 기존 품목/BOM, 출고 화면의 좌우 2열 카드 구조를 제거하고, 전체 너비 목록 → 하단 상세/구성표로 재구성.
- 품목 필터와 상세 선택은 `itemType/keyword/page/itemId` URL을 사용. 직접 URL에서 `GET /items/{id}`를 추가로 조회하는 비용은 복원 정확성을 위한 선택.
- 출고는 `GET /shipments/{id}`의 실제 LOT 배분 ID·수량·합계, 출고 요청 키를 표시하고 `/inventory?itemType=FINISHED_GOOD&itemId=...`에서 현행 LOT 잔량 확인.
- 기존 POST /items, POST /boms, POST /shipments와 DB·IndexedDB 도메인 규칙은 미수정.
- **검증 통과:** Node 37/37, fixture Chrome 14/14, demo Chrome 19/19, 실DB Chrome E2E 2/2.
- **발견·수정:** 역할이 겹치는 상세 버튼 → 링크로 분리; 선택 품목 단건 GET fixture 추가; 출고 요청 키 누락 복원; 토스트가 모달 하단 확정 버튼 클릭을 가리는 스택 순서 개선.
- **남은 일:** TASK-014 이동 이력 및 공통 스타일 통합, 실제 Vercel 화면 PC 시각·키보드 QA. BOM 수정/버전·반품은 MVP 밖.

## TASK-014 — 재고 이동 이력/공통 UI 보강 · 완료

- `GET /api/v1/stock-movements`를 이용한 이력 업무표, 입고·소모/출고의 부호 구분, 품목 검색·페이지/ID URL 복원, 원본 업무 참조 링크 도입.
- [PR #21](https://github.com/jeon950523/factoryflow/pull/21), `main` `c427540`. Node 37/37, Chrome fixture 18/18, IndexedDB 21/21, 실DB E2E 2/2.
- [구현·검수 보고서](task-reports/TASK-014-재고이동-공통UI-검수.md).

## TASK-015 — 공개 배포 Chrome 시각·접속 검수 · 완료

- 실제 운영 도메인 `factoryflow.kro.kr`의 품목·BOM/출고/이동 업무 화면을 GitHub Ubuntu Chrome에서 직접 로딩하고 증거 PNG 확보. CI의 한글 폰트 문제를 `fonts-noto-cjk`로 해결.
- [PR #22](https://github.com/jeon950523/factoryflow/pull/22), `main` `2899b020`. 공개 Chrome 3/3, Frontend/Fullstack E2E 성공. 1440px PC 브라우저 캡처는 검수했지만 Windows 실제 디스플레이 확대는 별개.
- [검수 보고서](task-reports/TASK-015-공개배포-Chrome-검수.md).

## TASK-016 — 공통 UI 가독성과 모달 접근성 · 완료

- 다섯 업무 모달에 공통 포커스 관리, 열린 품목 검색 Escape 우선 소비, 저장 중 모달 닫힘 방어; 보조 글씨 크기·명암 대비 개선.
- [PR #23](https://github.com/jeon950523/factoryflow/pull/23), `main` `4d790666`. Node 37/37, Chrome fixture 19/19, IndexedDB 25/25, 실DB E2E 2/2.
- [검수 보고서](task-reports/TASK-016-가독성-모달접근성-검수.md).

## TASK-017 — 제조업무 전체 추적 동선 및 정합성 · 완료

- BOM → 원자재 LOT → 물리 이동 → 생산지시 → 완제품 LOT/이동 → 출고 배분 및 원장 왕복, 생산지시·출고 목록의 `page/id` URL 복원.
- `원자재 현재고=입고25−소모20=5`, `완제품 현재고=합격8−출고3=5`, 출고 LOT 배분 합계=3을 실제 MySQL Chrome에서 검증.
- [PR #24](https://github.com/jeon950523/factoryflow/pull/24), `main` `71b6ae20`. Node 37/37, Chrome fixture 21/21, IndexedDB 27/27, 실DB E2E 2/2; Vercel 배포 상태 성공.
- [검수 보고서](task-reports/TASK-017-업무동선-데이터정합성-검수.md).

## TASK-018 — 최신 배포 Chrome 검증 및 작업 기록 정합성 · PR 검증 완료

- 공개 도메인의 TASK-017 링크 왕복, 상태/ID URL 새로고침, 잘못된 이동 이력 쿼리, 키보드 모달·CSS zoom을 읽기 전용 Chrome으로 검증.
- 기록 누락을 소급 확인: 최근 TASK별 지시서·누적 기술 개념·트러블슈팅·로드맵/README·핸드오프와 TASK 검수 보고서의 연결을 보강한다.
- **검증:** 공개 Vercel Chrome **6/6**, Frontend Node **37/37**, Chrome fixture **21/21**, IndexedDB **27/27**, 실제 MySQL E2E **2/2** 성공. [공개 실행](https://github.com/jeon950523/factoryflow/actions/runs/43726967372) · [Frontend](https://github.com/jeon950523/factoryflow/actions/runs/43726967329) · [MySQL](https://github.com/jeon950523/factoryflow/actions/runs/43726967349). 최종 PR HEAD·병합 후 main CI는 별도 확인.
- [작업지시서](../tasks/TASK-018-공개배포-QA-문서정합성.md) · [작업 기록 규정](DEVELOPMENT_REPORTING_STANDARD.md).

## 현재 결정/보류

- 현행 범위: 단일 공장·단일 창고·EA, BOM/LOT/FIFO/생산·검사·출고; Vercel 브라우저 데모와 실제 MySQL 통합 테스트는 분리한다.
- 보류: BOM 버전·수정, 출고 취소/반품, 다중 창고, 서버의 원장 전체 기간/유형 검색, 실DB 공개 운영 보안·복구, 실제 Windows 현장 확대/사용성 시험.
- 문서 정본: [SSOT v0.1](FACTORYFLOW_SSOT_v0.1.md) (초기 설계 초안), [핵심 개념](learning/CORE_CONCEPTS.md) (누적), [장애 기록](TROUBLESHOOTING.md), [현 상태·핸드오프](PROJECT_HANDOFF.md).
