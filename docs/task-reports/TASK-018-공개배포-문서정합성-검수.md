# FactoryFlow TASK-018 — 공개 배포·업무 추적 QA 및 문서 감사·검수

> 날짜: 2026-11-15
> 상태: **PR 검증 완료** — 공개 Chrome 6/6, Frontend 및 Spring Boot/MySQL E2E 성공. 병합 후 `main` CI는 별도 재확인
> 기준 SSOT: [FACTORYFLOW_SSOT_v0.1.md](../FACTORYFLOW_SSOT_v0.1.md) (초기 Draft), 운영 모드 결정: [ADR-013](../adr/ADR-013-프론트데모-실백엔드분리.md)
> 기준 `main`: `71b6ae204cf2948414da8fe67aa544f5dab9baf9` (TASK-017)
> 작업 브랜치: `qa/task-018-public-smoke-docs-audit`
> 작성/검수: AI 작성·코드 정적 검수. GitHub Actions 공개 Chrome 및 CI 실행 증거로 별도 확인, 실제 사용자 Windows PC 수동 시연은 미검증.

## 1. 목적·배경과 완료 기준

TASK-017에서 새로 만든 BOM → 원자재 LOT → 원자재 원장 → 생산지시 → 완제품 LOT → 완제품 원장 → 출고 배분 동선은 실제 IndexedDB 빌드와 실 MySQL E2E로 검증했지만, **Vercel 공개 도메인에서 새 링크가 렌더링되는지 확인한 별도 Chrome 증거는 없었다.** 또한 TASK-014~017의 개별 보고서는 존재했으나 사용자가 정한 [DEVELOPMENT_REPORTING_STANDARD](../DEVELOPMENT_REPORTING_STANDARD.md)의 누적 개념·실제 장애 기록·작업지시서·README/로드맵 최신 상태·상세 코드/대안 검수가 일괄 유지되지는 않았다.

완료 기준: 공개 Chrome의 기존 3건+신규 3건 통과, PNG/로그 증거, 최종 PR 헤드 Frontend + MySQL 전체 CI 성공, 보고서/학습/트러블슈팅/핸드오프/README 상태 일치, PR 병합 후 main 재검증.

**비범위:** Spring Boot, MySQL, Vue 기능·도메인 API, 제조 FIFO·멱등성·품질/출고 엔진 수정, 실제 서버 운영 배포. 공개 브라우저에서는 **데이터 생성·수정·삭제 없이 조회/모달 열고 닫기만** 수행한다.

## 2. 변경 파일별 역할·근거·대안

| 경로 | 역할과 코드 연결 | 선택 이유/다른 방법 |
|---|---|---|
| `frontend/tests-public/public-site.spec.js` | 공개 Vercel `/items?itemId=4`, `/orders?status=DRAFT`, `/shipments?id=1`, `/movements?itemId=...`를 `page.goto`, `getByRole`/링크 클릭으로 왕복; 새로고침·잘못된 URL·모달 초점·125% CSS zoom 검사, PNG 증거 | 로컬 fixture만 추가하면 공개 배포 회귀를 놓침. 외부 도메인 실 GET을 사용하되 공용 데모 쓰기는 하지 않음 |
| `tasks/TASK-018-공개배포-QA-문서정합성.md` | 작업 목적·완료 조건과 비범위 | 규정상 작성해야 했던 구현 전 지시서 구조 복원 |
| `docs/learning/CORE_CONCEPTS.md` | TASK-007A~018의 API/코드 원리·선택 이유·장단점·질문 누적 | 작업별 보고서만 있으면 개념 복습·서술이 분산됨 |
| `docs/TROUBLESHOOTING.md` | 실제 CI/시각/테스트 장애 발생·원인·수정·근거 링크 통합 | 단순 아이디어가 아닌 재현한 문제만 기록. 오래된 보고서의 원래 증거는 유지 |
| `docs/PROJECT_HANDOFF.md` | 정본 코드/SSOT/ADR/PR/main CI, 작업 이유·다음 작업 상태 연결 | 다른 채팅이나 작업자가 최신 상태를 빠르게 파악할 수 있도록 만듦 |
| `docs/DELIVERY_ROADMAP_20261111.md` | TASK-014~018 정리; 잘못된 문서 머리말 문자열 수선 | 이전 문서의 실제 첫 줄 `[object Object]`는 문서 데이터 손상으로 판단해 제목 복원; TASK-011~013 이력 유지 |
| `README.md` | 과거 TASK-006 수치를 최신으로 오인할 수 있던 맨 위 상태와 주요 TASK 인덱스 최신화 | 역사적 이전 TASK 상세·검증 수치를 삭제하지 않고 ‘과거 결과’로 구분 |
| `frontend/README.md` | ‘인증 미구현 / 실제 API 전용’ 오설명을 현재 세션 보안과 IndexedDB 데모의 분리 상태에 맞게 수정 | 실서버 운영 검증 완료라 주장하지 않음 |
| `docs/DEVELOPMENT_REPORTING_STANDARD.md` | 차기 TASK별 지시서+누적 기술+실패 기록+보고서+핸드오프 갱신 체크리스트 | 구체적인 후속 작업 기준으로 누락 재발 방지 |
| `docs/task-reports/TASK-018-공개배포-문서정합성-검수.md` | 이 작업의 근거·설계·코드·검증 결과·한계·리팩터링·설명 | 보고서 하나 작성하고 다른 정본이 불일치하는 관행을 보완 |

**아키텍처 영향:** 신규 서비스·캐시·DB 스키마 없음. 기존 `.github/workflows/public-site-smoke.yml`의 Playwright+Chrome/한글 글꼴 설치 및 아티팩트 수집을 재사용한다. 신규 ADR 불필요: 배포 모드 결정은 ADR-013 재사용.

## 3. 전체 호출 흐름 및 실제 트랜잭션

```text
GitHub Actions pull_request (tests-public 변경)
  → Ubuntu 24.04 / Node 22 / Chrome / fonts-noto-cjk 설치
  → npx playwright test --config playwright.public.config.js
  → Chrome page.goto('https://factoryflow.kro.kr/items?itemId=4')
  → Vercel index.html + 정적 Vue 자산
  → 브라우저 독립 IndexedDB createSeedState() / requestDemo() 조회
  → Vue RouterLink 실제 품목 ID로 /inventory → /movements → /orders → /shipments
  → DOM role/표·URL·화면 캡처·pageerror 검사
  → Actions artifact 보관 / PR CI 판정
```

데모 조회는 `requestDemo(... method='GET')`의 IndexedDB readonly 트랜잭션을 통해 기존 샘플 snapshot을 읽는다. 새 Chrome 테스트는 `POST` 업무 요청·서버 DB 쓰기·샘플 초기화를 호출하지 않는다. 실제 백엔드 제조/출고 트랜잭션의 분리 검증은 기존 `.github/workflows/fullstack-ui-ci.yml`이 책임지며 이 공개 스모크의 결과로 대신하지 않는다.

## 4. 핵심 테스트 코드/구현 이해

**코드 위치:** `frontend/tests-public/public-site.spec.js`, `TASK-017 published trace links...` 테스트.

```js
const bom = page.getByRole('table', { name: '완제품 BOM 구성표' })
await bom.getByRole('link', { name: '산업용 강판 LOT 재고 확인' }).click()
await expect(page).toHaveURL(/\/inventory\?itemType=RAW_MATERIAL&itemId=1$/)
await page.getByRole('link', { name: '선택 품목 이동 이력' }).click()
await expect(page).toHaveURL(/\/movements\?itemId=1$/)
```

- 입력: 공개 사이트의 자체 IndexedDB 샘플 품목 `id=4`, 강판 `id=1`.
- 처리: DOM에 **실제로 렌더링된** 이름/역할의 링크를 클릭해 Vercel의 Vue Router를 거친다. 직접 URL만 입력하는 테스트와 달리 끊어진 UI 버튼을 발견할 수 있다.
- 출력: URL 쿼리의 `itemId`가 원본 데이터 ID를 보존하는지, 재고 이동표가 `+50` 및 `−10`을 표시하는지 검증한다.
- 보안/부작용: 모든 동작이 공개 브라우저의 조회이며 데모에 POST하지 않는다.
- 대안: 서버 코드만 grep·DOM 텍스트만 검사하면 실제 공개 배포의 연결 오류와 JS 실행 문제를 확인하지 못한다. 실제 Chrome E2E가 더 무겁지만 TASK-018 목적에 부합한다.

**예외 테스트:** `/movements?itemId=oops&page=-1`에서 잘못된 ID를 백엔드로 전달하지 않고 품목 미선택 안내를 표시하는지 검사한다. 모달 열기→초기 입력 focus→Escape→기존 버튼 focus를 확인한다. `document.documentElement.style.zoom='125%'`는 **CSS 확대 스트레스**이지 Windows OS 배율 검증이 아니다.

## 5. 예외·경계조건·보안

| 상황 | 처리 원칙 | 판정 |
|---|---|---|
| 공개 도메인에서 404/500, SPA 직접 URL 오류 | 해당 테스트 실패/trace 저장, Vercel status 성공과 구분 | 공개 Chrome에서 검증 |
| URL 잘못된 `itemId`·페이지 | 품목 미선택 UI 표시, 불필요한 실서버 API 요청 금지 | 공개 Chrome에서 검증 |
| 공개 데모 IndexedDB 과거 상태 | Playwright 테스트별 새로운 브라우저 컨텍스트 사용, 샘플 초기화/POST하지 않음 | Playwright 설정·실행 로그 |
| 라우팅 클릭 후 비동기 렌더링 | `getByRole`과 auto-retry `expect` 사용, 임의 sleep 없음 | 코드 정적 검수 및 Chrome |
| UI 오류 | `pageerror` 수집, 런타임 예외 발생 시 실패 처리 | 공개 Chrome |
| 개인정보·실 DB | 공인 사이트에 인증/실DB 연결 없음, 테스트용 신원·비밀번호를 저장하지 않음 | 소스/테스트 정적 검수 |
| 사이트 개인 OS/폰트 | GitHub Ubuntu Noto 한글 검사. Windows 확대/스크린리더 사용성 주장 금지 | 미검증, 후속 |

## 6. 실행 명령/환경 및 검증 증거

- 검증 플랫폼: GitHub Actions `ubuntu-24.04`, `node-version:22`, Google Chrome, `fonts-noto-cjk`.
- 공개 사이트: `https://factoryflow.kro.kr`; `frontend/playwright.public.config.js`, `frontend/tests-public/public-site.spec.js`.
- CI 명령: `cd frontend && npm ci && npm test && npm run build`; `npx playwright test --config playwright.config.js`; `npx playwright test --config playwright.demo.config.js`; `npx playwright test --config playwright.public.config.js`.
- 실제 서버: GitHub Actions의 Java 21/Spring Boot/MySQL 8.4 + Chrome `playwright.live.config.js`.
- **테스트 결과:** PR #25의 검사 커밋 `8a1e6c2f0322a4181abcf72fa1ea75d7579f6285`에서 **공개 Chrome 6/6 성공** (HTTP 200), **Frontend Node 37/37·Chrome fixture 21/21·IndexedDB Chrome 27/27·Vite build 성공**, **Spring Boot/MySQL 실DB E2E 2/2 성공**.
- **실제 증거:** [공개 Chrome #43726967372](https://github.com/jeon950523/factoryflow/actions/runs/43726967372), [공개 PNG/HTML artifact #14166568094](https://github.com/jeon950523/factoryflow/actions/runs/43726967372/artifacts/14166568094), [Frontend #43726967329](https://github.com/jeon950523/factoryflow/actions/runs/43726967329), [MySQL E2E #43726967349](https://github.com/jeon950523/factoryflow/actions/runs/43726967349).
- **스크린샷 육안 검수:** 1440×900 Noto CJK에서 한글 정상 표시, 실제 생산지시 상세/LOT·이력 표 정상 표시, 125% CSS 확대 상태에서 등록 모달의 입력·닫기/취소·등록 버튼이 화면 안에 표시됨. CSS 확대 스크린샷의 `position:fixed` backdrop이 fullPage 이미지의 화면 아래 부분을 덮지 않는 현상은 **현재 뷰포트만 고정 오버레이를 갖는 캡처 표현**으로, 뷰포트에서 모달 외부 클릭을 허용한다는 증거는 아니다.
- **남은 외부 조건:** 실제 Windows OS 확대·접근성 스크린리더 사용성은 미검증이며, GitHub Actions 증거 artifact 보관 기간 7일.
- 성능 지표/접속 지연 수치: **미측정**.
- 공개 Chrome 원본 PNG와 실패 trace는 7일 보관 artifact. 추후 만료 가능하므로 실행 링크와 의미를 보고서에 기록한다.

## 7. 시니어 코드 검수

| 관점 | 정적 검수 판단 | 근거/잔여 |
|---|---|---|
| 기능 | **공개 Chrome 통과** | TASK-017 DOM 링크 업무 왕복, 상태/ID URL 복원, 잘못된 쿼리 처리 6/6 |
| 데이터 | 백엔드/엔진 미변경 | 실제 MySQL E2E를 별도 CI에서 유지 |
| 보안 | 데이터 조회만 사용 | 쓰기 버튼 클릭하지 않음, 테스트 간 독립 BrowserContext |
| 예외 | **Chrome 통과** | 잘못된 쿼리→조회 안내 및 JS pageerror 없음; 등록 모달 focus/Escape 복귀 |
| 효율 | 읽기 E2E 3개 추가, 공통 fixture/추상화 추가 없음 | 외부 테스트가 느릴 수 있으므로 수동/PR 실행 유지 |
| 유지보수 | 마법 숫자 1·4는 **기존 데모 seed fixture의 고정 ID** | 공개 사이트 시드 변경 시 테스트/시연 자료 동기화 필요 |
| 검증 | **PR 3종 CI 통과** | 공개 Chrome 6/6, Frontend Node/fixture/demo, 실제 MySQL E2E 2/2를 독립 실행 |

### 장점/단점·대안

- **선택:** 공개 Chrome의 직접 DOM 상호작용 테스트 + 문서 사후 감사. 장점은 실제 배포 경로/사용자 동선 증명, 단점은 외부 네트워크·CDN 상태에 따라 플래키하고 CI 비용 발생.
- **대안 A:** Vite 로컬 데모만 테스트. 빠르고 안정적이나 Vercel 반영 확인 불가.
- **대안 B:** 매 푸시마다 공개 테스트 자동 실행. 실시간성은 높으나 Vercel 배포 완료 전에 실행되면 거짓 실패 가능. 현 단계에서 `workflow_dispatch` + 테스트 변경 PR 트리거를 유지.
- **문서 대안:** 지난 TASK별 원본을 대량 덮어쓰기보다 누적 개념/오류/핸드오프에 **출처를 연결해 소급 보완**. 오래된 검증 시점 사실을 훼손하지 않는 편이 낫다.

## 8. 리팩터링 우선순위 (기능 및 효율)

| 항목/파일 | 판단 | 사유·완료 조건 |
|---|---|---|
| 공개 사이트에서 업무 링크/SPA 오류가 발견되면 | **즉시 수정** | 실제 실패 재현·동일 PR 헤드 Chrome 재검증 후 병합 |
| README/핸드오프의 과거 실행 상태 오인 | **즉시 수정** | 현재와 과거 CI 기록 구분·서로 연결 확인 |
| `docs/learning/CORE_CONCEPTS.md` 누적 누락 | **즉시 수정** | TASK-007A~017 근거+대안+학습 질문 보강 |
| 문서 체크리스트/자동 강제 | **현 시점 텍스트 체크리스트**, 정적 CI 강제는 2차 개선 | 무관한 DOC/README PR까지 빌드 실패하지 않도록 정책부터 운영해 보고 판단 |
| `frontend/tests-public`의 리다이렉트/시드/조회 공통화 | **현 시점 불필요** | 테스트가 6개 규모이므로 오히려 추상화가 복잡성 증가 |
| Windows 운영 PC 125~150%/스크린리더 QA | **다음 TASK 전 검토** | Linux CSS zoom과 OS DPI는 달라 실제 환경 재현 필요 |
| 공개 Vercel 새 Chrome 증거의 CI 보관 기간 확대 | **2차 개선** | 7일 이후 감사 필요성 생길 때 별도 영구 보관 방안 검토 |

## 9. 변경 이력·과거 보고서의 미흡점 및 보완

- [TASK-014](TASK-014-재고이동-공통UI-검수.md): 구현 계약·변경파일·검증·판단이 있으나 템플릿의 코드 심층 설명/우선순위 표는 간략 → `CORE_CONCEPTS §18`에서 흐름/대안 보강.
- [TASK-015](TASK-015-공개배포-Chrome-검수.md): 공개 시각 증거/문제는 있으나 작업지시/누적 학습 누락 → `CORE_CONCEPTS §19`, `TROUBLESHOOTING TS-004` 보강.
- [TASK-016](TASK-016-가독성-모달접근성-검수.md): 접근성 판단·검증 항목은 있으나 진행 중 테스트 식별자 오류는 통합 장애 보고서에 미반영 → `CORE_CONCEPTS §20`, `TROUBLESHOOTING TS-005`.
- [TASK-017](TASK-017-업무동선-데이터정합성-검수.md): 데이터/URL 검증 계획과 CI는 있으나 상세 학습·정본/로드맵 반영 누락 → `CORE_CONCEPTS §21`, `PROJECT_HANDOFF`, README, 로드맵 보강.
- `docs/DELIVERY_ROADMAP_20261111.md` 앞에 실제로 `[object Object]` 문자열이 들어가 있었음. 추정 원인을 단정하지 않고 머리말과 최신 TASK 상태를 복원한다.
- **완료 판정:** 과거 모든 TASK 보고서가 템플릿 12항목 전체를 충족했다고 주장하지 않음. TASK-018에서 부족한 누적 문서를 감사·보완했으며 이후 작업에서 동일 누락을 막도록 작성 표준 §9 추가.

## 10. 남은 위험과 다음 TASK

- PR #25의 공개 Chrome/Frontend/MySQL 3종 CI는 성공했다. 병합 후 **최신 `main` CI를 별도 확인**하고 그 결과를 완료 판단에 포함한다.
- Vercel 공개 사이트의 개인 브라우저 저장/확대/스크린리더는 Ubuntu Chrome 자동 검사만으로 완전한 사용성 평가가 되지 않는다.
- 현 시점 신규 설계 ADR 없음(기존 ADR-013 유지). 기능 API·DB·도메인 로직을 임의 수정하지 않는다.
- 후속: 공개 QA에서 발견한 사용성/예외 문제를 실제 화면 기준 우선순위로 분리해 단일 목적 TASK로 작업한다. `PROJECT_HANDOFF.md`를 다음 작업의 시작점으로 유지한다.

## 11. 20~40초 설명

“공개 Vercel 체험이 실제 제조 서버가 아니라 IndexedDB라는 점을 유지하면서, TASK-017에서 구현한 BOM·LOT·생산·출고 이력의 화면 이동이 공개 주소에서도 연결되는지 Chrome으로 확인했습니다. 기능 코드 수정 없이 운영 배포와 실DB E2E를 분리 검증하고, 이전 작업의 보고서만 남기고 학습 개념·트러블슈팅·README와 작업지시가 누락되던 문제를 작업 기준 문서와 핸드오프로 보완했습니다.”

## 12. 직접 설명해 볼 질문

1. Vercel `success` 상태와 실제 공개 Chrome 스모크 통과는 각각 어떤 차이를 검증하나?
2. URL 이동 테스트에서 `page.goto('/movements?itemId=1')`만 검증하는 것보다 DOM 링크를 클릭하는 방식의 장점과 단점은?
3. IndexedDB 시연의 FIFO/멱등성 동작을 실제 MySQL 트랜잭션 증명이라고 주장하면 안 되는 이유는?

**보고서 업데이트 정책:** PR 최종 헤드·main CI/실행 결과는 근거 확인 후 갱신하고, 일치하지 않은 이력은 무리하게 완료로 바꾸지 않는다.
