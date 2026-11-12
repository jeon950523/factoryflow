# TASK-013 품목/BOM·완제품 출고 UI 검증 근거

- 최종 기능/테스트 기준: `6523464d946e59032980cb2106a2175a4d891111`
- [Frontend CI #43321664299](https://github.com/jeon950523/factoryflow/actions/runs/43321664299) **SUCCESS**: Node **37/37**, Vite build, Chrome fixture **14/14**, IndexedDB Demo Chrome **19/19**
- [Fullstack E2E #43321663928](https://github.com/jeon950523/factoryflow/actions/runs/43321663928) **SUCCESS**: Java21 Spring Boot/MySQL8.4 기동, 브라우저 업무 **2/2**
- 초도 UI [#43320302044](https://github.com/jeon950523/factoryflow/actions/runs/43320302044) 실패: 품목·출고 '상세 보기'가 기존 버튼과 접근성 이름 충돌; RouterLink로 역할 변경.
- 2차 UI [#43320909103](https://github.com/jeon950523/factoryflow/actions/runs/43320909103) 실패: 출고 상세에서 requestKey 누락, 원래 추적성 필드 복원.
- 3차 UI [#43321254568](https://github.com/jeon950523/factoryflow/actions/runs/43321254568) 실패: 새 품목 등록 후 토스트가 BOM 등록 버튼 위를 덮음. 토스트 z-index 낮추고 Chrome 재실행 성공.
- 최종 실DB와 demo 양쪽 테스트에서 제조 규칙 변경 없음. 실제 프로덕션 Vercel 시각 QA는 별도.
