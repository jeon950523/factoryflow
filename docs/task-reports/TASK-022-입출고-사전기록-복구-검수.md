# TASK-022 — 입출고 POST 사전 기록·복구 시니어 검수 보고서

> 작성일: 2026-11-23 · 기준 `main 2d064047b2471e411f63fcdfdc8ccd70a185c3e0` · 상태: **코드/테스트 작성, 실제 CI·배포 결과 확인 전**
> 관련 지시서: [TASK-022](../../tasks/TASK-022-입출고-사전기록-복구.md)

## 1. 목적과 배경
TASK-020은 **오류를 받은 뒤** 미확인 요청을 저장했지만, POST가 이미 서버로 전달된 상태에서 브라우저 새로고침으로 JS 콜백이 중단되면 동일 키를 보존하지 못할 수 있었다. TASK-022에서는 POST의 사전조건으로 안전한 `sessionStorage` 기록을 요구한다.

## 2. 구현 범위·상태
| 대상 | 변경 | 검증 상태 |
|---|---|---|
| `frontend/src/lib/pendingWrite.js` | 동일 payload 저장 확인, 기존 다른 payload 덮어쓰기 차단, readback 검사 | 실행 전 |
| `InventoryView.vue` | 입고 POST 전 기록; 실패 시 POST 중단; 불확실한 오류 전체에 잠금 적용 | 실행 전 |
| `ShipmentsView.vue` | 출고 POST 전 기록 및 동일한 오류 정책 | 실행 전 |
| `pendingWrite.test.js` | 덮어쓰기/저장 거절 단위 테스트 | 작성, 실행 전 |
| `tests/write-preflight.spec.js` | POST 경계와 저장 실패 Chrome fixture 4건 | 작성, 실행 전 |
| `tests-live/real-manufacturing.spec.js` | 기존 서버 응답 유실 테스트에 실제 POST 경계의 저장 확인 추가 | 작성, 실행 전 |

## 3. 호출 순서와 트랜잭션
`Vue submit → 값 검증 → payload 스냅샷 → savePendingWrite(동기 setItem+getItem 확인) → POST → 서버 Service+UNIQUE+트랜잭션 commit → 검증된 결과 200/201 → clearPendingWrite`.
서버 결과가 불확실하면 기존 키/본문으로만 재전송. 서버 DB 트랜잭션 및 원장·FIFO는 수정하지 않는다.

## 4. 변경 파일과 설계 근거
- `pendingWrite.js#savePendingWrite`: 이미 기록된 다른 명령을 덮어쓰면 복구 키가 소실되므로 보호. `getItem` 재조회로 조용한 저장 실패도 차단.
- `InventoryView.vue#submitReceipt`, `ShipmentsView.vue#submit`: 양쪽 폼 모두 `saving = true` 이후 전송 전에 동기 기록. 실패 시 버튼 잠금을 해제하고 저장소 설정 안내. 서로 다른 네트워크 예외를 명확하게 분류.
- `tests/write-preflight.spec.js`: transport 경계에서 실제 브라우저의 저장값/POST 본문 동일성을 검사한다.

## 5. 핵심 코드 및 이유
```js
if (!savePendingWrite('receipt', payload)) {
  saving.value = false
  ui.notify('입고 요청을 브라우저에 안전하게 저장하지 못해 전송하지 않았습니다. 저장소 설정을 확인하세요.', 'error')
  return
}
created = await post('/api/v1/stock-receipts', payload)
```
쓰기 작업의 필수 선행조건을 동기 저장으로 옮겼다. `setItem`이 throw하거나 readback이 다르면 `post`에 도달하지 않는다. 서버 commit 이후 응답 abort에서도 같은 key와 body가 남는다.

## 6. 예외·경계·보안
- `INVALID_RESPONSE`는 HTTP 400이어도 확정 거절보다 **우선** 결과 미확인으로 처리.
- `401/403` 및 알 수 없는 409 충돌은 커밋 여부를 증명하지 못하므로 pending 유지.
- 400/404/지정된 409 재고 부족은 특정 명령을 실제로 거절한 결과로 취급해 pending 해제. 단, 최종 적용 전 CI에서 재확인.
- `sessionStorage` 비활성·quota·쓰기 거부 시 서버 전송 0회. 사용자 탭 종료/스토리지 삭제/기기간 동기화는 범위 밖.

## 7. 검증 증거
- 제출 전 정적 코드 검토: 완료. 원격 PR 및 CI/실DB/공개 사이트 검증: **아직 실행/확인하지 않음**.
- 예정: `cd frontend && npm ci && npm test`, `npx playwright test --config playwright.config.js`, `npx playwright test --config playwright.demo.config.js` (CI).
- 실제 Java 21 + MySQL 8.4 E2E는 PR CI `FactoryFlow Fullstack Browser E2E`에서 확인 예정.
- 실제 테스트 숫자·빌드 시간·성능 향상은 검증 근거가 생긴 뒤 기록한다.

## 8. 시니어 검수
| 영역 | 정적 판정 | 근거·후속 |
|---|---|---|
| 기능 | 개선안 합리적·런타임 미검증 | POST 이전 저장 조건과 UI 잠금, 오류 해제 순서 |
| 데이터 정합성 | 서버 계약 보존 | 기존 Service/Repository/UNIQUE/FIFO 변경 없음 |
| 보안 | 주의 | 민감정보 저장 금지; key/품목/수량만 보관 |
| 효율 | 단순 저장 1회 readback | 별도 서버 조회/DB/schema 작업 불필요 |
| 유지보수 | 주의 | 두 submit 메서드가 일부 중복되지만 지금 추상화하지 않음 |
| 테스트 | 작성·실행 전 | Chrome/DB 실행 근거 확인 필요 |

## 9. 리팩터링 판단·대안
| 대안/대상 | 장점 | 비용 및 판정 |
|---|---|---|
| 선택: sessionStorage write-ahead | 현 기능을 최소 변경하면서 새로고침 틈새 차단 | 탭 종료는 미보증. **즉시 수정** |
| 기존 오류 이후 저장 | 추가 저장 비용이 적음 | 서버 commit 직후 페이지 종료 창구. **채택하지 않음** |
| 서버 requestKey 조회 API | 다른 세션에서 상태 확인 가능 | 인증·보존기간·권한·DB 설계 필요. **2차 개선** |
| 입고·출고 공통 composable | 코드 중복 감소 | 서로 다른 payload/화면 흐름 혼동 우려. **2차 개선** |
| Redis/외부 캐시 | 대량 분산 시스템에 유용할 수 있음 | 성능 병목 근거 없음. **현 시점 불필요** |

ADR: 이번 변경은 기존 ADR-013 경계를 유지하므로 신규 ADR 불필요.

## 10. 다음 작업
- PR CI에서 테스트 실패가 나오면 수정 후 재실행. 성공 증거를 본 보고서와 로드맵/README/핸드오프에 남긴 뒤 병합·Vercel 공개 QA 판정.
- 멀티탭/브라우저 종료 후 조회는 다음 TASK 후보. 실제 Windows 접근성은 별도 수동 QA.

## 11. 직접 설명 가능한 20~40초 요약
> 서버가 입고나 출고를 이미 확정했는데 브라우저가 결과를 받기 전에 새로고침하면 새 요청 키가 생겨 중복 재고 이동이 일어날 위험이 있었습니다. 그래서 프론트에서 전송 전에 키와 내용을 sessionStorage에 저장하고 저장 여부까지 확인한 뒤 POST를 보내도록 변경했습니다. 서버의 멱등 로직은 그대로 두고, 실제 DB/브라우저 테스트로 검증할 계획입니다.

이해 질문: 1) 사전 기록이 사후 오류 기록보다 안전한 이유는? 2) 왜 401/403은 처리 실패로 확정할 수 없는가? 3) sessionStorage만으로 해결할 수 없는 장애는 무엇인가?
