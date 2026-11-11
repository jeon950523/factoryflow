# FactoryFlow 관리자 UI — 외부 프로그램 참고 및 적용 근거

> 2026-11-11 확인. Odoo/ERPNext/Katana/SAP Fiori **업무 구조와 UI 패턴을 참고**했으며 각 제품의 로고·이미지·코드·텍스트를 복제하지 않았다. 공개 제품의 기능이 FactoryFlow에 존재한다고 주장하지 않는다.

## 참조 제품과 차용한 UI 패턴

| 참조 | 공식 근거 | FactoryFlow에서 반영한 점 | 제외한 점 |
|---|---|---|---|
| **Odoo Manufacturing / Shop Floor** | https://www.odoo.com/documentation/19.0/applications/inventory_and_mrp/manufacturing/shop_floor/shop_floor_overview.html | 생산 업무 중심의 작업 화면, 지시 상태에 따른 다음 액션 | 작업장/작업자별 실시간 화면·설비·작업시간 집계(백엔드에 없음) |
| **ERPNext 제조 대시보드·Work Order** | https://docs.frappe.io/erpnext/manufacturing-dashboard · https://docs.frappe.io/erpnext/work-order | 현황 요약 + 생산지시 목록·상세, BOM→생산 흐름 | ERPNext의 원가 계산·멀티창고·Job Card는 범위 밖 |
| **Katana MRP 화면 안내** | https://support.katanamrp.com/en/articles/5908373-understanding-the-basics-of-each-katana-screen | Items / Stock / Make 영역을 업무별 메뉴로 구분 | 판매/구매/수요예측·비즈니스 인사이트 기능은 미구현 |
| **SAP Fiori 디자인 가이드** | https://learning.sap.com/courses/ui-development-with-sap-fiori/working-with-sap-fiori-design-guidelines_ab11c169-54de-4f51-87b9-f61c8a5198be | Overview → List Report → Object Detail/Actions 패턴, 필터·상태 배지·명확한 작업 피드백 | SAP UI5 코드·디자인 시스템을 그대로 가져오지 않음 |

## 구체적 화면 방향

- **왼쪽 고정 사이드바**: 업무 범주별 메뉴. 현재 위치는 활성 메뉴로 강조.
- **상단 요약**: 실제 API 숫자만 표시. 생산지시 상태별 카드와 품목 합계는 백엔드 `totalElements` 기반.
- **목록→상세**: 품목/BOM, 생산지시, 출고. 목록의 행 클릭 시 오른쪽에 상세와 실행 가능한 작업 노출.
- **행동의 제한**: 생산지시는 DRAFT→RESERVED→IN_PROGRESS→AWAITING_QC→COMPLETED의 상태에 맞게 버튼만 노출. 상태와 정합성의 최종 검증은 항상 서버가 수행.
- **색상 및 정보 밀도**: 네이비 사이드바·화이트 데이터 카드·청록색 주요 액션, 숫자는 오른쪽 정렬, 상태 배지/로딩/실패/빈 화면 구분.
- **데이터 정직성**: 실제 API 응답 외에 가상의 생산량·매출·공정 가동률 등은 표시하지 않음. 기획 범위 밖 모듈(작업장·OEE·원가)은 UI에 넣지 않음.

## 현재 한계 및 2차 보완

- **목록용 조회 API**는 TASK-005A에서 생산지시와 출고에 보완. 상세 전용 API로 목록을 반복 조회하지 않도록 데이터 계약 분리.
- 품목 선택 드롭다운은 첫 100건을 조회. 100건 이상이면 검색형 비동기 선택 위젯/서버 측 검색이 필요.
- 예외/빈 화면은 구현했지만 **브라우저 수동 사용성 평가 및 키보드·스크린리더 QA**는 추가 필요.
- 인증·역할별 메뉴 제어가 없으므로 외부 공개·시연 배포 전 인증/인가 단계 필수.
