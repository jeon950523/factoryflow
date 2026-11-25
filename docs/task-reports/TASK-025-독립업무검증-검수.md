# TASK-025 시니어 검수 — 독립 업무 검증

## 착수 검수
기존 UI E2E는 실제 DB의 정상 흐름과 응답 유실 재시도를 검증한다. 그러나 구현과 별개로 유지되는 업무 검증 모듈 및 독립 CI가 없다. SSOT는 초안이므로 실제 Controller 경로/상태와 대조했다. LOT API는 /inventory/lots?itemId=, 응답 페이지 totalElements를 확인한다.

## 설계 및 리팩터링 판단
- 기능: 독립 원장 계산은 이동 종류의 부호를 명시하고 알 수 없는 종류를 실패시킨다. LOT 잔량·예약·가용·누적 입고 및 요약을 비교한다. 복수 원자재 실패와 실제 경쟁 요청으로 불변조건을 검증한다.
- 효율: Node 내장 fetch/assert/test만 사용하여 의존성 설치·Chrome 실행을 제거한다. 서버/DB는 한 번 시작하며 격리된 신규 품목으로 업무 시나리오를 실행한다. 공통 검증기를 앱으로 옮겨 공유하지 않는다.
- 한계: API 관측 기반으로 물리 DB 직접 대사와 완전한 권한 매트릭스, 강제 장애 및 앱 mutation은 후속 과제. 합성 oracle 결함 주입은 앱 mutation 점수가 아니다. 같은 개발 주체가 작성한 검증이라 독립 리뷰를 대체하지 않는다.
- 기존 Java/Vue/DB 변경 없음. 별도 workflow는 모든 PR에서 실행되므로 CI 비용이 늘 수 있다. 현재 main 보호 미설정으로 필수 체크 강제는 별도 설정이 필요하다.
- 상태: 코드 정적 검토 후 PR CI 실행 대기. 테스트 성공/병합은 미확정.

## 첫 CI 실패 및 수정 (2026-11-26 KST)

- [최초 독립 CI #45646312795](https://github.com/jeon950523/factoryflow/actions/runs/45646312795): Spring Boot/MySQL 기동과 oracle 음성 대조군 성공. 실제 HTTP 테스트는 FIFO 위치(`live.test.mjs:125`)에서 `TypeError: Cannot read properties of undefined (reading 'reserved')`로 중단.
- 원인: `CanonicalReceipt`가 LOT 코드 및 요청 키를 대문자로 정규화하는데 테스트는 요청 원문으로 LOT를 검색했다. 애플리케이션 결함으로 판정하지 않는다.
- 수정: 입고 응답의 `inventoryLotId`를 이용해 원래 LOT를 조회하고 존재 여부, 정규화 계약, 재전송 시 동일 LOT를 명시적으로 검증한다. FIFO 기대 예약 수량은 그대로 유지한다. 서버/DB/기존 테스트는 수정하지 않는다.
- 최초 실패 이후의 생산·QC·출고는 당시 실행되지 않았다. 수정 커밋 전체 CI 성공 전 완료 판정 금지.
