# FactoryFlow — 진행 로드맵 및 TASK 상태

> 기준일: 2026-11-24 · 실제 정본: [GitHub main](https://github.com/jeon950523/factoryflow) 및 TASK별 CI/PR 보고서
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

## TASK-018 — 최신 배포 Chrome 검증 및 작업 기록 정합성 · 완료

- 공개 도메인의 TASK-017 링크 왕복, 상태/ID URL 새로고침, 잘못된 이동 이력 쿼리, 키보드 모달·CSS zoom을 읽기 전용 Chrome으로 검증.
- 기록 누락을 소급 확인: 최근 TASK별 지시서·누적 기술 개념·트러블슈팅·로드맵/README·핸드오프와 TASK 검수 보고서의 연결을 보강한다.
- **검증:** 공개 Vercel Chrome **6/6**, Frontend Node **37/37**, Chrome fixture **21/21**, IndexedDB **27/27**, 실제 MySQL E2E **2/2** 성공. [공개 실행](https://github.com/jeon950523/factoryflow/actions/runs/43726967372) · [Frontend](https://github.com/jeon950523/factoryflow/actions/runs/43726967329) · [MySQL](https://github.com/jeon950523/factoryflow/actions/runs/43726967349). 최종 PR 헤드와 병합 `main 63dbfbb`의 [Frontend](https://github.com/jeon950523/factoryflow/actions/runs/43727388011) 및 [실DB](https://github.com/jeon950523/factoryflow/actions/runs/43727388049) CI 모두 성공 확인.
- [작업지시서](../tasks/TASK-018-공개배포-QA-문서정합성.md) · [작업 기록 규정](DEVELOPMENT_REPORTING_STANDARD.md).

## 현재 결정/보류

- 현행 범위: 단일 공장·단일 창고·EA, BOM/LOT/FIFO/생산·검사·출고; Vercel 브라우저 데모와 실제 MySQL 통합 테스트는 분리한다.
- 보류: BOM 버전·수정, 출고 취소/반품, 다중 창고, 서버의 원장 전체 기간/유형 검색, 실DB 공개 운영 보안·복구, 실제 Windows 현장 확대/사용성 시험.
- 문서 정본: [SSOT v0.1](FACTORYFLOW_SSOT_v0.1.md) (초기 설계 초안), [핵심 개념](learning/CORE_CONCEPTS.md) (누적), [장애 기록](TROUBLESHOOTING.md), [현 상태·핸드오프](PROJECT_HANDOFF.md).

## TASK-019 — 품질검사 명시적 입력·최종 확인 안전장치 · 완료

- 검사 대기 생산지시가 기본 합격 `plannedQty`/불합격 0을 자동 기입하여 사용자 검사 없이 전량 합격 승인 가능했던 UI를 개선한다.
- 공란과 명시적 0의 차이를 보존하고, 안전 정수·합계 일치·계획 수량 검증, 확인 대화상자에 생산지시번호/합격·불합격 수량 표시, 취소 시 POST 미실행.
- 실제 `POST /api/v1/work-orders/{id}/inspection` 및 서버의 검사/LOT/재고 트랜잭션은 미수정.
- **PR 검사 성공:** [Frontend #43729409460](https://github.com/jeon950523/factoryflow/actions/runs/43729409460) Node 37/37·Chrome fixture 21/21·IndexedDB 29/29, [실DB #43729409394](https://github.com/jeon950523/factoryflow/actions/runs/43729409394) Chrome 2/2. [PR #26](https://github.com/jeon950523/factoryflow/pull/26) 병합 `480c2a0`, [main Frontend #43730192091](https://github.com/jeon950523/factoryflow/actions/runs/43730192091) 및 [main 실DB #43730192083](https://github.com/jeon950523/factoryflow/actions/runs/43730192083) SUCCESS로 완료. Vercel 배포 상태 성공, 공개 QC 직접 조작/Windows 실제 환경은 별도.
- [지시서](../tasks/TASK-019-품질검사-명시적확정-안전성.md) · [시니어 검수 보고서](task-reports/TASK-019-품질검사-명시적확정-검수.md).

## TASK-020 — 입고·출고 응답 유실 시 동일 키/본문 재전송 · 코드/실DB 검증 완료 (공개 배포·QA는 TASK-021에서 복구)

- 서버의 receipt/shipment 요청 키와 payloadHash 멱등성·MySQL 락/트랜잭션/원장을 **유지**한다.
- 네트워크(status 0), 5xx, 응답 형식 오류에서 처리 결과를 단정하지 않고 최초 key+품목+LOT+수량을 고정, 동일 요청으로만 재시도. 같은 탭에서 모달 닫기·새로고침 시 sessionStorage 복원.
- 반환 키는 서버의 대문자 정규화 규칙에 따라 비교. 성공한 POST 이후 목록 이동 오류는 별도 안내. 명백한 재고 부족 409는 일반 업무 오류로 표시한다.
- **기능 CI 성공:** [Frontend #43732644025](https://github.com/jeon950523/factoryflow/actions/runs/43732644025) Node 42/42·Chrome fixture 21/21·IndexedDB 30/30, [실DB #43732644015](https://github.com/jeon950523/factoryflow/actions/runs/43732644015) 3/3. 커밋 후 응답 유실→같은 key+본문 HTTP 200 replay/원장 1회, 재시도 후 확정 409 잠금 해제까지 검증. [PR #28](https://github.com/jeon950523/factoryflow/pull/28) 병합 `main 8cb8617`의 [Frontend #43733088956](https://github.com/jeon950523/factoryflow/actions/runs/43733088956) 및 [실DB #43733088860](https://github.com/jeon950523/factoryflow/actions/runs/43733088860) SUCCESS 확인. 단, Vercel은 `Deployment rate limited — retry in 24 hours`로 배포 실패해 공개 UI 반영/QA는 미완료.
- **범위 밖:** 새 서버 키 조회 API, 여러 탭/세션 자동 복구, 실제 공개 Java/MySQL 배포.
- [지시서](../tasks/TASK-020-입출고-재시도-멱등성.md) · [시니어 검수 보고서](task-reports/TASK-020-쓰기재시도-멱등성-검수.md) · [PR #28](https://github.com/jeon950523/factoryflow/pull/28).

## TASK-021 — Vercel 배포 요청 제한 복구·공개 Chrome QA · 완료

- 최초 실패 Vercel 상태는 `2026-11-15T16:06:50Z` rate limit. 24시간 초과 후 [PR #30](https://github.com/jeon950523/factoryflow/pull/30) 새 Git 커밋으로 재유도하여 `main 0e57713` Vercel SUCCESS 확인.
- [PR #31](https://github.com/jeon950523/factoryflow/pull/31) 공개 재시도 검사 2건 추가, `main a91c0cae` Vercel production SUCCESS. 기능/DB 코드는 변경하지 않았다.
- [공개 Chrome #43958402614](https://github.com/jeon950523/factoryflow/actions/runs/43958402614) 기존 6+신규 2 = **8/8** 통과, 운영 재배포 후 8/8 재실행 성공. [main Frontend #43958811806](https://github.com/jeon950523/factoryflow/actions/runs/43958811806) Node42/Chrome21/IndexedDB30 및 [실DB #43958811687](https://github.com/jeon950523/factoryflow/actions/runs/43958811687) E2E3 모두 SUCCESS.
- [지시서](../tasks/TASK-021-배포재시도-공개검증.md) · [검수 보고서](task-reports/TASK-021-배포재시도-공개QA-검수.md).


## TASK-022 — 입출고 POST 사전 기록 및 재시도 복구 강화 · 완료

- 사전 검수: TASK-020의 네트워크 실패 후 저장에는 **서버 commit 이후 브라우저 새로고침 전에 기록되지 않는 틈**이 있다.
- 브라우저 사전 동기 저장/읽기 확인 성공 시에만 POST, 저장 실패·다른 미확인 payload 충돌 시 전송하지 않음. 401/403·불명확한 409는 pending 유지.
- [지시서](../tasks/TASK-022-입출고-사전기록-복구.md) · [코드·검수 보고서](task-reports/TASK-022-입출고-사전기록-복구-검수.md).
- **상태:** [PR #33](https://github.com/jeon950523/factoryflow/pull/33) 병합 `main 1c03e94`, [Frontend CI #45044738329](https://github.com/jeon950523/factoryflow/actions/runs/45044738329) Node44/Chrome25/IndexedDB30 + [실DB #45044738273](https://github.com/jeon950523/factoryflow/actions/runs/45044738273) E2E3 성공. Vercel `1c03e94` 운영 배포 SUCCESS 이후 [기존 공개 Chrome 8/8 재실행](https://github.com/jeon950523/factoryflow/actions/runs/43958402614) SUCCESS. 공개는 IndexedDB 데모, 실DB 운영 배포 아님.
- **비범위:** 실제 실행 중 강제 탭 종료/브라우저 프로세스 크래시 및 다른 탭 복원은 보장하지 않음. 성능 수치 미측정.


## TASK-023 — 입고·출고 다중 탭 공유/충돌 차단 · 완료
- 진단: TASK-022의 `sessionStorage`만으로 다른 탭이 독립 요청 키를 생성하는 것을 막을 수 없음. 서버는 같은 키 재생만 보장.
- 구현: Web Locks 배타 잠금 + 공유 `localStorage` intent와 기존 탭 세션 복원, 성공/확정 거절 때 일치한 기록만 삭제. 미지원 브라우저는 fail-closed.
- [PR #35](https://github.com/jeon950523/factoryflow/pull/35) / main `b43beca` 병합. 수정 후 PR Frontend Node46/Chrome27/IndexedDB30 및 MySQL3 성공, 병합 [Frontend #45053092176](https://github.com/jeon950523/factoryflow/actions/runs/45053092176)·[MySQL #45053092151](https://github.com/jeon950523/factoryflow/actions/runs/45053092151) 성공. Vercel production SUCCESS 후 [공개 Chrome 8/8](https://github.com/jeon950523/factoryflow/actions/runs/43958402614) run attempt 6도 성공.
- 최초 fixture 2건 실패→비동기 검사 수정 [TS-011](TROUBLESHOOTING.md). 공개 8건은 기존 스모크이며 새 다중 탭 2건은 fixture/실DB에서 검증. 다음 위험: 데모 초기화·미확인 요청 경합.
- [지시서](../tasks/TASK-023-입출고-다중탭-복구.md) · [검수 보고서](task-reports/TASK-023-다중탭-입출고-복구-검수.md) · [ADR-014](adr/ADR-014-다중탭-쓰기의도-WebLocks.md).


## TASK-024 — 데모 초기화·미확인 입출고 경합 차단 · 기능·공개 QA 검증 완료

- 사전 검수: `resetDemoData`가 `pendingWrite.js`의 receipt/shipment Web Locks와 무관하게 실행됨. 공유 기록 삭제 후 다른 탭에 낡은 복구키가 남아 초기화된 데모에 새로 반영될 수 있음.
- 변경: 원자재 입고→완제품 출고 잠금 순서로 두 잠금을 잡고 shared/tab 미확인 의도 검사, 두 락을 쥔 채 reset IDB 트랜잭션 완료. `localStorage` reset epoch는 DB 변경 전 저장. 입력 폼 epoch mismatch는 POST 거절.
- **기능 회귀 성공:** [PR #37](https://github.com/jeon950523/factoryflow/pull/37) → main `fe0eacb`, [PR Frontend #45240068073](https://github.com/jeon950523/factoryflow/actions/runs/45240068073) 49/27/33 + [PR 실DB #45240067965](https://github.com/jeon950523/factoryflow/actions/runs/45240067965) 3, [main Frontend #45240451984](https://github.com/jeon950523/factoryflow/actions/runs/45240451984) 49/27/33 + [main 실DB #45240452073](https://github.com/jeon950523/factoryflow/actions/runs/45240452073) 3 통과. Vercel production `fe0eacb` SUCCESS. 공개 사이트 Chrome 9건(기존8 + 신규 TASK-024 1)은 [공개 QA #45241375567](https://github.com/jeon950523/factoryflow/actions/runs/45241375567)에서 **9/9 통과**. 기존 8건과 신규 실 배포 다중 탭 리셋 거절/복구 1건을 구분해 기록.
- [TASK-024](../tasks/TASK-024-데모초기화-경합.md) · [보고서](task-reports/TASK-024-데모초기화-경합-검수.md) · [ADR-015](adr/ADR-015-데모초기화-WebLocks-세대값.md).

## TASK-025 — 독립 업무 검증 모듈 (PR 검증 대기)

- SSOT 불변조건을 기준으로 별도 Node HTTP 검증과 oracle 음성 대조군을 추가한다. 실제 Spring Boot/MySQL 전용 CI로 실행하며 기존 테스트는 유지한다. 앱 mutation/필수 병합 체크는 아직 미확정. [TASK-025](https://github.com/jeon950523/factoryflow/blob/feature/task-025-independent-verification/tasks/TASK-025-독립업무검증.md) 및 ADR-016/검수 보고서 참조.
