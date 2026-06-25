# 설계서: ORDS handler와 VPD filter 적용 상태 관측성 강화 (#461)

> **상태**: Approved
> **작성**: [AI] Architect · **최종수정**: 2026-06-25
> **추적성** — Redmine: #461 · 관련 ADR: 없음
> · 구현 파일: `OperationStatusMapper`, `OperationStatusService`, `OperationStatusController`, `operation-status.html` · 테스트: `mvn test`, Spring Boot 기동

## 1. 목적 (Why)

보호 객체별 ORDS path, handler 존재 여부, VPD policy/function 상태, 권한 rule 수, 최근 ORDS 검증 결과를 한 화면에서 확인하게 한다.

## 2. 범위 (Scope)

- **포함**: 운영 상태 탭 추가, 보호 객체 기준 상태 집계, 상태 badge/조치 문구 표시.
- **제외**: handler source inline 편집, 자동 probe 실행, 장기 검증 로그 검색.

## 3. 인수조건 (Acceptance Criteria)

- [ ] 보호 객체마다 ORDS path와 handler 매칭 상태를 보여준다.
- [ ] VPD policy enabled 여부와 policy function status를 보여준다.
- [ ] permission/rule count와 최근 probe 상태/row count를 보여준다.
- [ ] 문제가 있는 항목에는 조치 문구를 표시한다.
- [ ] 운영 메뉴에서 접근 가능하다.

## 4. 컨텍스트 & 제약

- ORDS metadata는 기존 `dba_ords_*` view를 조회한다.
- 최근 검증은 `cb_ords_probe_audit`의 최신 row를 사용한다.
- VPD status는 `all_policies`, `all_objects`를 사용한다.

## 5. 아키텍처 개요

```
/operation-status
  -> OperationStatusController
  -> OperationStatusService
  -> OperationStatusMapper.findRows()
      cb_protected_object
      + all_policies/all_objects
      + dba_ords_* metadata
      + cb_permission/cb_permission_rule
      + cb_ords_probe_audit latest row
```

## 6. 데이터 모델

- `OperationStatusRow`: 한 보호 객체의 end-to-end 상태.
- health:
  - `OK`: handler, enabled policy, valid function이 모두 확인됨.
  - `WARN`: 일부 미설정 또는 최근 검증 실패.
  - `ERROR`: function invalid 등 즉시 조치 필요.

## 7. 함수 명세 (Function Specs)

| 함수 | 책임(1줄) | 시그니처(잠정) | 입력 | 출력 | 에러/실패 | 복잡? |
|------|-----------|----------------|------|------|-----------|-------|
| `findRows` | 보호 객체별 운영 상태를 DB에서 집계 | mapper select | 없음 | rows | DB 예외 | **복잡** |
| `healthLevel` | row 상태를 OK/WARN/ERROR로 분류 | Java method | row fields | string | 없음 | 단순 |
| `actionText` | 운영자가 볼 조치 문구 생성 | Java method | row fields | string | 없음 | 단순 |

## 8. 흐름 / 알고리즘

1. enabled 보호 객체를 조회한다.
2. object별 permission/rule count를 집계한다.
3. object별 VPD policy와 function status를 left join한다.
4. ORDS path와 metadata full path를 비교해 handler 상태를 찾는다.
5. 최신 audit row를 붙인다.
6. 화면에서 health와 action text를 계산해 표시한다.

## 9. 엣지케이스 & 에러 처리

- ORDS metadata 권한이 없으면 페이지 전체가 DB 예외를 받을 수 있다. 기존 ORDS handler 화면과 같은 전제다.
- policy가 여러 개면 policy 이름을 `LISTAGG`로 보여준다.
- 최근 probe가 없으면 `검증 이력 없음`으로 표시한다.

## 10. 테스트 계획

- `mvn test`
- Spring Boot 재기동
- `/operation-status` HTTP 200 확인

## 11. 리스크 & 대안 검토

- 선택: DB join 기반 read-only 화면. 실행 비용이 낮고 운영자가 한 번에 상태를 볼 수 있다.
- 대안: 각 화면 fragment를 iframe/htmx로 모으기. 중복 조회가 많고 상태 판정이 분산된다.

## 12. 미해결 질문 (Open Questions)

- 최신 probe 상세 request/response 저장은 현재 audit 테이블에 없으므로 별도 이슈로 확장할 수 있다.
