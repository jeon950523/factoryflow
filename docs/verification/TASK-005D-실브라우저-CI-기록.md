# TASK-005D — 실제 CI 결과 기록

## 빠른 프론트 QA
- https://github.com/jeon950523/factoryflow/actions/runs/43065947057 — **success**
- npm/Node 22 단위 테스트 **12개 성공**, Vite 프로덕션 빌드 성공, Chrome 고정 API Fixture 브라우저 **8개 성공**(기존 5 + 연속 클릭 경쟁 3).

## 실제 브라우저 + Spring Boot + MySQL 8.4
- 첫 정상 업무 브라우저 성공 https://github.com/jeon950523/factoryflow/actions/runs/43066243956 — **1/1 success**.
- 정상 업무+재고 부족 테스트 https://github.com/jeon950523/factoryflow/actions/runs/43066642137 — **2/2 success**.
- MySQL 8.4 CI 서비스, Java 21 Spring Boot 4.1.2/Flyway, Vite 프록시, Chrome. 공개 배포 아님.
- 원자재 입고25 → 자재 예약/소모20 → QC 합격8/불합격2 → 완제품 출고3, 원자재와 완제품 재고 각각5·예약0, 물리 이동 이력 4종 검증.
- 부족 재고: UI 예약 시도 후 DRAFT 유지, 현재고3/예약0, 오류 메시지 출력.
- 기존 백엔드 Testcontainers 전체 53/53 https://github.com/jeon950523/factoryflow/actions/runs/43046278405 (기존 main 기준, 신규 Java 변경 없음).

## CI 장애 이력
- 최초 https://github.com/jeon950523/factoryflow/actions/runs/43065947121 — Gradle bootJar 성공 후 하드코딩된 JAR 이름 불일치로 실행 실패.
- 산출물 디렉터리에서 실행 JAR 탐색하도록 수정(63222a1), 이후 MySQL+Spring Boot 정상 기동 및 테스트 통과.

## 주의
- Chrome Fixture 테스트는 진짜 브라우저지만 가짜 API 응답. 실DB 테스트는 실제 API/DB이나 일회용 CI 계정, 한 브라우저 기준.
- Vercel/Oracle 네트워크·로그인/인가·모바일/스크린리더·실운영 부하 테스트 미수행.
