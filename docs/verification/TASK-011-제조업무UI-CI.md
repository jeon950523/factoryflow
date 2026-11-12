# TASK-011 — 생산지시/품질검사 ERP형 UI 검증

- 최종 기능/테스트 커밋: `2dc531f5c8b821ae9bae7c8e33a94a0b39fbc7a4` (문서 수정 전).
- [Frontend CI #43306931753](https://github.com/jeon950523/factoryflow/actions/runs/43306931753): SUCCESS — Node **37/37**, Vite build, fixture Chrome **12/12**, IndexedDB Chrome **10/10**.
- [Fullstack E2E #43306931723](https://github.com/jeon950523/factoryflow/actions/runs/43306931723): SUCCESS — Java21 Spring Boot/MySQL8.4 기동, Chrome **2/2**.
- 초도 실패 [#43306300619](https://github.com/jeon950523/factoryflow/actions/runs/43306300619): locator strict match 중복(생산지시번호·합격/불합격 라벨). `exact:true` 및 고유 요소 선택으로 수정한 [재검증 #43306625731](https://github.com/jeon950523/factoryflow/actions/runs/43306625731) PASS.
- 코드 범위: OrdersView.vue, orders-workbench.css, tests-demo/browser-demo.spec.js, tests/security-shell.spec.js.
- 서버 API·인증 필터·생산·재고·검사·출고·IndexedDB 업무 엔진은 미변경.
- 실 Vercel 최신 배포와 현장 사용성은 별도 점검 대상.
