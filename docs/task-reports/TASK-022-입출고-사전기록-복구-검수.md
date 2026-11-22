# TASK-022 — 입출고 POST 사전 기록·복구 시니어 검수 보고서

> 작성일: 2026-11-23 · 기준 `main 2d064047b2471e411f63fcdfdc8ccd70a185c3e0` · 상태: **PR + 병합 main CI/실DB·Vercel 배포·공개 Chrome 검증 완료**
> 관련 지시서: [TASK-022](../../tasks/TASK-022-입출고-사전기록-복구.md)

## 1. 목적과 배경
TASK-020은 **오류를 받은 뒤** 미확인 요청을 저장했지만, POST가 이미 서버로 전달된 상태에서 브라우저 새로고침으로 JS 콜백이 중단되면 동일 키를 보존하지 못할 수 있었다. TASK-022에서는 POST의 사전조건으로 안전한 `sessionStorage` 기록을 요구한다.

## 2. 구현 범위·상태
| 대상 | 변경 | 검증 상태 |
|---|---|---|
| `frontend/src/lib/pendingWrite.js` | 동일 payload 저장 확인, 기존 다른 payload 덮어쓰기 차단, readback 검사 | PR/main 검증 성공 |
| `InventoryView.vue` | 입고 POST 전 기록; 실패 시 POST 중단; 불확실한 오류 전체에 잠금 적용 | PR/main 검증 성공 |
| `ShipmentsView.vue` | 출고 POST 전 기록 및 동일한 오류 정책 | PR/main 검증 성공 |
| `pendingWrite.test.js` | 덮어쓰기/저장 거절 단위 테스트 | PR/main 검증 성공 |
| `tests/write-preflight.spec.js` | POST 경계와 저장 실패 Chrome fixture 4건 | PR/main 검증 성공 |
| `tests-live/real-manufacturing.spec.js` | 기존 서버 응답 유실 테스트에 실제 POST 경계의 저장 확인 추가 | PR/main 검증 성공 |

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
- [PR #33](https://github.com/jeon950523/factoryflow/pull/33) 기능 HEAD `b75615f` → main 병합 `1c03e94`.
- [PR Frontend #45044548625](https://github.com/jeon950523/factoryflow/actions/runs/45044548625) 및 [main Frontend #45044738329](https://github.com/jeon950523/factoryflow/actions/runs/45044738329): **Node 44/44, Chrome fixture 25/25, IndexedDB Chrome 30/30, 프로덕션 빌드 SUCCESS**. 새 preflight fixture 4건 포함.
- [PR MySQL #45044548595](https://github.com/jeon950523/factoryflow/actions/runs/45044548595) 및 [main MySQL #45044738273](https://github.com/jeon950523/factoryflow/actions/runs/45044738273): Java 21/Spring Boot/MySQL + Chrome **3/3 PASS**. 라우트의 전송 시점에 sessionStorage와 POST 본문이 같음을 확인하고, 서버 commit → response abort → 200 replay 및 이동 원장 1회 검증.
- GitHub `main 1c03e94` Vercel status SUCCESS. 운영 배포 이후 [공개 Chrome 재실행 #43958402614](https://github.com/jeon950523/factoryflow/actions/runs/43958402614) run attempt 4: 기존 공개 8건 모두 PASS. 공개 사이트는 IndexedDB 데모이고 **이 8건은 TASK-021 기준 스크립트**다.
- 실행 환경: GitHub Actions Ubuntu 24.04/Node 22/Chrome, 실DB runner Java 21 + MySQL 8.4. 운영 시스템 성능 수치는 미측정.

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
- PR #33과 병합 main의 기능 CI·실 MySQL E2E·Vercel 운영 배포·공개 Chrome 재실행은 성공. 문서 전용 후속 PR에서 새 기능/DB를 추가하지 않는다.
- 멀티탭/브라우저 종료 후 조회는 다음 TASK 후보. 실제 Windows 접근성은 별도 수동 QA.

## 11. 직접 설명 가능한 20~40초 요약
> 서버가 입고나 출고를 이미 확정했는데 브라우저가 결과를 받기 전에 새로고침하면 새 요청 키가 생겨 중복 재고 이동이 일어날 위험이 있었습니다. 그래서 프론트에서 전송 전에 키와 내용을 sessionStorage에 저장하고 저장 여부까지 확인한 뒤 POST를 보내도록 변경했습니다. 서버의 멱등 로직은 그대로 두고, 실제 Spring Boot·MySQL과 Chrome CI에서 동일 요청의 저장·재생 및 원장 1회만 반영되는 것을 검증했습니다.

이해 질문: 1) 사전 기록이 사후 오류 기록보다 안전한 이유는? 2) 왜 401/403은 처리 실패로 확정할 수 없는가? 3) sessionStorage만으로 해결할 수 없는 장애는 무엇인가?

## 12. 최종 시니어 판정·검증 경계

- **기능/데이터 안전성 통과:** 사전 기록된 key+payload가 실제 HTTP POST와 같음을 프론트 fixture 및 실DB 브라우저 경계에서 검증. 저장 실패 시 POST 0회, 같은 키 재송신 시 원장 1회라는 서로 다른 보장을 확인했다.
- **CI·운영 구분:** PR/병합 main의 Node 44/Chrome fixture 25/IndexedDB 30/실 MySQL E2E 3은 모두 성공. Vercel production 성공과 공개 Chrome 8/8은 별도 증거이며, 공개는 IndexedDB 데모다. 공개 Chrome 8건은 TASK-021 기존 스크립트의 재실행으로 이번 새 사전 기록 직전 인터셉트 자체를 직접 검사하지 않는다.
- **남은 미검증:** 실제 브라우저 프로세스 강제 종료 또는 OS crash 시 sessionStorage의 디스크 flush, 탭 완전 종료, 다중 탭/기기간 복원, Windows 실환경 DPI·키보드 사용성. 실제 호스트에 MySQL 운영 백엔드 배포 여부는 이번 작업에서 변하지 않았다.
- **리팩터링:** 즉시 수정은 모두 반영. 입력 잠금/동일 키 보존은 회귀 검증됨. 공통 Vue composable 및 서버측 key 조회는 **2차 개선**, 실측 없는 캐시/성능 튜닝은 **현 시점 불필요**. 과거 미확인 장애를 TROUBLESHOOTING에 새 사고처럼 소급하지 않았다.
