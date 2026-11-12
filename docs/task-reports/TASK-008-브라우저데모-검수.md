# TASK-008 — Vercel 브라우저 제조 데모 시니어 검수

상태: 구현, 테스트 CI 진행 전. Oracle 가입 문제가 있어 실제 백엔드 배포는 보류.

## 1. 목적
FactoryFlow의 Java21/Spring Boot + MySQL 로직과 테스트를 유지하면서 Vercel에 공개된 UI를 체험 가능하게 만든다. 브라우저 데이터는 서버 DB가 아니므로 **실DB를 대체했다는 표현을 사용하지 않는다.**

## 2. 전체 호출 흐름
- 개발 모드: Vue → `src/lib/api.js` → Spring Boot 세션·CSRF·역할 검증 → JPA/MySQL.
- Vercel 프로덕션 기본: Vue → `src/lib/api.js` → 데모 분기 → `demo/store.js` IndexedDB 트랜잭션 → `demo/engine.js` 업무 상태 변경 → 화면 업데이트.
- 브라우저 데모는 로그인 없이 샘플과 독립적인 가상 체험 사용자로 진입한다. 실제 계정 발급·비밀번호 검증·세션 인증은 하지 않는다.
- `VITE_DEMO_MODE=false` 설정 후 빌드하면 실제 API 경로 사용. 서버·프록시 준비 전에는 변경하지 않는다.

## 3. 파일 역할
- `frontend/src/lib/demo/engine.js`: 샘플 생성, Spring DTO 호환 조회, 품목/BOM 등록, 입고 멱등성, 전량 FIFO 예약·취소·소모, QC 합격품 LOT, 출고 FIFO·멱등성.
- `frontend/src/lib/demo/store.js`: IndexedDB 상태 스냅샷 1개를 `readwrite` 트랜잭션에서 읽고/커밋. 새로고침 후 유지, 사용자 명시적 초기화.
- `frontend/src/lib/api.js`: Vite `PROD` 빌드 데모 분기, 로컬 개발 시 기존 HTTP API 유지.
- `frontend/src/App.vue`, `router/index.js`: 데모 안내·초기화 버튼, 로그인·계정관리 미표시.
- `frontend/vercel.json`: 직접 URL 방문 및 새로고침 시 Vue SPA 라우팅.
- `frontend/src/lib/demo/engine.test.js`: 순수 도메인 시뮬레이션 테스트.
- `frontend/tests-demo/browser-demo.spec.js`: 실제 Chrome/IndexedDB 데이터를 생성·영속·초기화하는 화면 검증.

## 4. 예외/테스트 계획
- 최초 방문 시 샘플 원자재·완제품·BOM·LOT·완료/초안 생산지시·출고 예시 생성.
- 재고 부족 시 예약 상태·LOT 수량은 하나도 변경되지 않아야 한다.
- 생산 시작 시 예약된 LOT를 소모하며, 검사가 끝나야 합격품 LOT가 만들어진다.
- 출고는 QC_PASS LOT만 FIFO로 사용하고 부족할 경우 전량 실패해야 한다.
- 입고·생산·출고의 요청 키 반복은 이중 재고 변경을 만들어선 안 된다.
- 브라우저 저장소 접근 차단·데이터 용량 부족 시 오류를 노출하며 서버 전송으로 조용히 변경하지 않는다.
- 프론트 로컬 개발 모드는 기존 Testcontainers/Chrome E2E 검증을 계속 사용한다.

## 5. 시니어 검수
- **잘한 점:** 서버/데모 코드 경계 분리, 실패 시 기존 snapshot 원본 보존, IndexedDB `readwrite` 직렬화, 비밀번호/공개 데모 사용자 계정 미사용, 지속적 화면 안내.
- **위험한 점:** IndexedDB는 브라우저별로 격리되며 삭제·시크릿 모드에서 소실될 수 있다. 서버 간 데이터 동기화나 MySQL ACID 트랜잭션의 증거가 될 수 없다.
- **즉시 고칠 것:** 단위·브라우저 CI 실패 시 수정. 데모 mode가 실서버로 fetch하지 않는지 점검.
- **2차 개선:** 실 API 계약 변화에 따른 샘플 엔진 차이 관리, 체험 UX 순서 안내와 데이터 스키마 마이그레이션.
- **보류:** Oracle VM 및 PR #14는 미병합; 공개 사이트의 운영 API 준비 전까지 `VITE_DEMO_MODE=false` 지정 금지.

## 6. 직접 설명 요약
서버 기반 제조 시스템 코드는 유지하고 포트폴리오 웹에서는 IndexedDB와 같은 업무 DTO를 사용하는 브라우저 전용 체험 환경을 구현했습니다. FIFO·재고 부족 롤백 등을 데모 상태에서도 재현했지만 MySQL 동시성·서버 인증의 증거는 별도의 백엔드 CI 테스트로 구분합니다.
