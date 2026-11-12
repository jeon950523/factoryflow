# TASK-008 — 브라우저 체험 데모 CI 증거

- 날짜: 2026-11-12 KST
- 저장소: `jeon950523/factoryflow`; PR #15; 코드 기준 `063760e0f60bc1b640097c4cce4ea452402aca3b` + UX 안내 변경 `8afcd56f6cfb915390e4eae7b980bb6f2bc9aca5`.

## CI
- [Frontend CI 성공 #43232982304](https://github.com/jeon950523/factoryflow/actions/runs/43232982304) — Node **37/37**, Vite 프로덕션 build 성공, 기존 Chrome UI fixture **11/11**, 새 Chrome IndexedDB Demo **3/3**.
- [Fullstack Browser CI 성공 #43232982442](https://github.com/jeon950523/factoryflow/actions/runs/43232982442) — CI의 실제 Spring Boot/Flyway/MySQL8.4 기동 및 기존 제조 업무 **2/2**.
- 과거 1차 실패 [#43232780291](https://github.com/jeon950523/factoryflow/actions/runs/43232780291) — Node 환경에서 import.meta.env 직접 참조 → `?.` 방어로 해결.

## 아직 완료로 주장하지 않을 항목
Vercel 실제 도메인에 최신 main 커밋의 프론트 배포가 전파되었는지, 운영 브라우저별 저장 지속성, 외부 API 실제 호스팅, Oracle VM, 서버 TLS/세션, 서버 DB 백업/복원은 CI와 별개.
