# FactoryFlow 관리자 콘솔

Vue 3 + Vite + Vue Router + Pinia 기반의 관리자 화면. Odoo의 작업 중심 UI, ERPNext의 현황·이력, Katana의 모듈별 내비게이션, SAP Fiori의 목록→상세→작업 패턴을 **참고해 FactoryFlow용으로 재구성**했다. 외부 제품의 이미지나 코드를 복제하지 않는다.

## 실행

1. 프로젝트 루트 `infra`에서 MySQL 8.4 실행 및 백엔드 구동(기존 README 참고).
2. `cd frontend` → `npm install` → `npm run dev`.
3. `http://127.0.0.1:5173` 접속. 프론트엔드 `/api`는 Vite 프록시로 `127.0.0.1:8080`에 연결.
4. `npm run test` / `npm run build`로 기본 검증.

**보안:** 아직 인증/인가가 없으므로 외부 공개 배포하지 않는다. VITE_API_BASE_URL은 브라우저에 공개되는 값이므로 비밀 토큰을 절대 설정하지 않는다.

현재 메뉴: 운영 개요, 품목·BOM, 재고·입고, 생산지시·품질검사, 완제품 출고, 재고 이동 이력. **실제 API만 사용**하며 가짜 KPI·목 데이터·임시 시뮬레이션은 없다.
