# TASK-025 — 독립 업무 검증 모듈 1차

- 착수 기준 main `7a02c72efcf50ec7329017a4bd0e9aacd52a6e64` (TASK-024 완료).
- 기존 테스트는 유지. 구현체·Vue·기존 테스트 헬퍼를 import하지 않는 Node 22 HTTP 검증 모듈을 verification/에 추가한다.
- 근거: SSOT v0.1 INV-01~11 및 실제 Controller/API 계약. 실제 MySQL8.4와 Spring Boot를 격리된 CI에서 기동. API 결과와 LOT/이동 원장/요약을 독립 대사.
- 범위: 복수 BOM 원자재 부족 원자성, 경쟁 예약/출고, FIFO 예약, 취소, 상태 전이, 품질 수량, 동시 동일 키/재전송, 익명·CSRF 거절. 기존 UI 장애복구 테스트를 유지한다.
- oracle 자체의 합성 오류 주입 검증을 추가. 이는 애플리케이션 mutation testing이나 모든 권한/장애 검증이 아니다.
- 모든 PR/main에 별도 CI를 실행. main protected=false, Rulesets 없음 확인. Required check 설정 전에는 자동 병합 차단을 보장하지 않는다.
- 실행/병합/배포 결과는 확인 이후에만 기록한다.
