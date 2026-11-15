# FactoryFlow 관리자 콘솔 — 개발과 공개 데모의 분리

Vue 3 + Vite + Vue Router + Pinia 기반의 제조 운영 UI. Odoo·ERPNext·Katana·SAP Fiori의 작업 중심 정보 구조를 참고했으며 외부 제품의 이미지/코드를 복제하지 않는다.

## 실행 모드

| 실행 | 데이터 출처 | 인증 | 공개 여부 |
|---|---|---|---|
| `npm run dev` (기본) | Vite `/api` 프록시 → `127.0.0.1:8080` Spring Boot → MySQL 8.4 | 서버 세션·CSRF·ADMIN/VIEWER | 로컬 개발 |
| `npm run build` (기본) | 브라우저 IndexedDB 제조 시뮬레이터 | 체험용 사용자 (실제 서버 인증 아님) | [factoryflow.kro.kr](https://factoryflow.kro.kr) |
| `VITE_DEMO_MODE=false` 프로덕션 빌드 | 실제 Spring Boot 서버 API | 세션·CSRF·역할 권한 | 운영 프록시·TLS·쿠키 보안 검증 전 공개하지 않음 |

## 로컬 개발

1. 루트 [README](../README.md)에 따라 MySQL과 Spring Boot 실행.
2. `frontend`에서 `npm ci`, `npm run dev` 순서로 실행하고 `http://127.0.0.1:5173` 열기.
3. `npm test`, `npm run build`, Chrome용 `npx playwright test --config playwright.config.js` 실행.
4. 데모와 실DB E2E의 실행/검증 위치는 [공통 보고 규칙](../docs/DEVELOPMENT_REPORTING_STANDARD.md)·[GitHub Actions](../.github/workflows/)·[TASK-017 보고서](../docs/task-reports/TASK-017-업무동선-데이터정합성-검수.md) 참조.

## 메뉴 및 실제 기능

- 운영 개요, 품목·BOM, 재고·입고, 생산지시·품질검사, 완제품 출고, 재고 이동 이력.
- TASK-017 기준: 실제 ID를 사용하는 BOM → LOT → 생산지시 → 완제품 출고/원장 링크, URL 페이지/상세 복원.
- 공개 데모의 모든 데이터는 **각 브라우저 IndexedDB에만** 저장. 서로 다른 사용자 간 실시간 동기화 또는 Spring/MySQL 처리로 오인하지 않는다.
- MySQL 잠금, 동시성, 트랜잭션과 권한은 **Java 백엔드와 분리된 Testcontainers/MySQL E2E**에서 검증한다.

## 보안·제약

- 서버 모드는 이미 세션 인증·ADMIN/VIEWER 권한·CSRF·로그인 제한을 구현했다. **그러나 실제 서버 공개 운영에는** TLS, 보안 쿠키, 프록시, 계정 복구 등 별도의 검증이 필요하다.
- `VITE_API_BASE_URL` 같은 공개 빌드 환경변수에는 비밀 키·비밀번호를 넣지 않는다.
- 작업 문서와 실행 증거는 [현 상태 핸드오프](../docs/PROJECT_HANDOFF.md) 및 [TASK별 보고서](../docs/task-reports/)에 연결한다.
