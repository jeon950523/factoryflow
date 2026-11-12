# TASK-012 — 재고·LOT·원자재 입고 ERP형 UI CI 기록

- 기능·최종 테스트 기준 SHA: `f050aadf9e7ba6edf1203e341711906cb078b361`.
- [Frontend Actions #43313017588](https://github.com/jeon950523/factoryflow/actions/runs/43313017588): **SUCCESS** — Node 37/37; Vite production build; fixture Chrome 13/13; IndexedDB demo Chrome 14/14.
- [Fullstack Actions #43313017639](https://github.com/jeon950523/factoryflow/actions/runs/43313017639): **SUCCESS** — Spring Boot + MySQL 8.4 browser E2E 2/2.
- 브라우저 검증 항목: 유형 필터 URL/새로고침, 목록 밖 직접 품목 LOT 조회, 기존 LOT 추가 입고/수량 갱신, 새 LOT 등록 후 브라우저 데이터 지속, 조회자 입고 버튼 비노출.
- 실제 API/서버 권한·DB 입고 로직·IndexedDB 엔진은 변경하지 않음.
- Vercel 실배포 반영·실무자 화면 사용성/가독성 QA는 별도 확인 대상.
