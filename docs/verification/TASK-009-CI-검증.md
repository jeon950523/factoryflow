# TASK-009 GitHub Actions 검증 기록

- 기준 기능 브랜치 SHA: `85df1acb3a6f267cd116acd9061680911dd1eee5`.
- 주요 변경: Vue 업무 화면/공통 CSS, 데모 브라우저 테스트만 변경. Java 및 IndexedDB 제조 도메인 엔진 변경 없음.

| 실행 | 결과 | 근거 |
|---|---|---|
| 최종 Frontend CI | **SUCCESS** — Node 37/37, Vite 프로덕션 빌드, Chrome Fixture 11/11, Demo Browser 6/6 | [#43266729962](https://github.com/jeon950523/factoryflow/actions/runs/43266729962) |
| 최종 Fullstack E2E | **SUCCESS** — Spring Boot/MySQL 8.4, Chrome 2/2 | [#43266730205](https://github.com/jeon950523/factoryflow/actions/runs/43266730205) |
| 1차 Frontend CI | **FAILURE** — 기존 원자재명 버튼 선택자와 추가 LOT 버튼이 중복 매칭됨 | [#43266084592](https://github.com/jeon950523/factoryflow/actions/runs/43266084592) |

원인과 해결: 두 버튼의 접근 가능한 이름 모두 '산업용 강판'을 포함했으므로 Playwright `getByRole({name:'산업용 강판'})`가 strict mode로 실패. 품목 버튼에는 `exact:true`, LOT 버튼에는 독립 키보드 실행 테스트를 적용했다.

**검증 경계:** Chrome 테스트는 GitHub Actions 내부 Vite 빌드/실제 MySQL CI 기반이다. 운영 Vercel 도메인 최신 배포와 시각적·현장 사용자 QA는 별도 확인이 필요하다.
