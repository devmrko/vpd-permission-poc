# #467 컬럼 민감도와 redaction 정책 분리

## 배경

기존 모델은 `cb_permission_column`에 포함된 컬럼을 "NULL 처리 예외"로 해석했다. 이 방식은 역할별 표시 예외는 표현할 수 있지만, 컬럼 자체의 데이터 민감도와 masking 방식을 별도로 관리하기 어렵다.

## 결정

- `cb_protected_column`을 컬럼 정책의 기준 테이블로 유지한다.
- `sensitivity_level`을 추가해 컬럼 데이터 등급을 관리한다.
  - `PUBLIC`
  - `INTERNAL`
  - `CONFIDENTIAL`
  - `RESTRICTED`
- `redaction_method`를 추가해 masking 방식을 관리한다.
  - `NONE`
  - `NULLIFY`
  - `PARTIAL`
  - `FULL`
- `cb_app_role.max_sensitivity_level`을 추가해 역할별 민감도 허용 상한을 설정한다.
- `cb_permission_column`은 기존 호환을 위해 "해당 권한에서 redaction 예외로 표시 가능한 컬럼" 의미로 유지한다.

## Migration

기존 `sensitive_yn = 'Y'` 컬럼은 `CONFIDENTIAL/NULLIFY`로 보정한다. 기존 `sensitive_yn = 'N'` 컬럼은 `PUBLIC/NONE`을 기본값으로 둔다. 운영자가 실제 업무 기준에 따라 `INTERNAL/NONE`, `RESTRICTED/FULL` 등으로 조정한다.

## UI 반영

- ORDS 조회 Handler 대상 화면에서 객체별 기본 컬럼 정책을 조회/수정한다.
- 역할 화면에서 `max_sensitivity_level`을 조회/수정한다.
- 권한 관리 wizard에서 역할이 해당 보호 객체를 조회할 때 원문 표시를 허용할 마스킹 컬럼을 선택한다.
- ORDS 검증 결과의 masked 컬럼은 `contents [CONFIDENTIAL/NULLIFY]`처럼 정책 라벨을 같이 표시한다.

## 권한 부여 시 컬럼 마스킹 처리

컬럼 마스킹의 기준 정보는 보호 객체 컬럼에 둔다. 하지만 실제 원문 표시 허용은 역할과 보호 객체를 묶는 권한에 붙인다. 따라서 운영자는 권한을 줄 때 다음 순서로 판단한다.

1. 보호 객체의 기본 컬럼 민감도/마스킹 정책을 확인한다.
2. 역할의 민감도 허용 상한을 확인한다.
3. 권한 wizard에서 해당 역할에 원문 표시를 허용할 컬럼만 선택한다.
4. 선택하지 않은 마스킹 대상 컬럼은 기본 정책대로 NULL 처리 또는 마스킹된다.

## 남은 판단

`cb_permission_column`과 `max_sensitivity_level`의 최종 런타임 결합 방식은 다음 단계에서 결정한다. 현재는 기존 Oracle Redaction expression과 호환되도록 표시 예외 구조를 유지한다.
