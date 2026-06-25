# 권한 조합/경계값/오류 메시지 테스트 체크리스트 (#459)

## 목적

VPD/ORDS 권한 백오피스에서 권한 저장, token 검증, ORDS 호출 실패가 운영자가 이해 가능한 결과로 드러나는지 확인한다.

## 자동 Smoke

| ID | 시나리오 | 실행 | 기대 결과 |
|----|----------|------|-----------|
| A-01 | HR token 문서 조회 | `./run.sh backoffice-vpd-ords-test` | 3행, `DOC_ID=[1,2,6]`, `CONTENTS` 없음 |
| A-02 | SELF token 문서 조회 | `./run.sh backoffice-vpd-ords-test` | 1행, `DOC_ID=[3]`, `CONTENTS` 없음 |
| A-03 | ALL token 문서 조회 | `./run.sh backoffice-vpd-ords-test` | 6행, `DOC_ID=[1,2,3,4,5,6]`, `CONTENTS` 있음 |
| A-04 | 미등록 bearer token 조회 | `./run.sh backoffice-vpd-ords-test` | HTTP 200 성공 rows로 처리되지 않음 |

## 권한 저장 경계값

| ID | 입력 | 기대 결과 |
|----|------|-----------|
| P-01 | `ALL` 단독 | 저장 성공 |
| P-02 | `ALL` + 다른 rule | 저장 거부, `ALL 규칙은 다른 규칙과 함께 저장할 수 없습니다.` |
| P-03 | `MY_DEPT`, 컬럼 비움 | 저장 성공, VPD 기본 컬럼 `DEPT_CODE` 사용 |
| P-04 | `SELF`, 컬럼 비움 | 저장 성공, VPD 기본 컬럼 `OWNER_EMP_NO` 사용 |
| P-05 | `DEPT`, 값 비움 | 저장 거부, 값 필요 메시지 |
| P-06 | `EMP_NO`, 값 비움 | 저장 거부, 값 필요 메시지 |
| P-07 | `=`, 컬럼 비움 | 저장 거부, 컬럼 필요 메시지 |
| P-08 | `!=`, 값 비움 | 저장 거부, 값 필요 메시지 |
| P-09 | 보호 객체에 없는 컬럼 | 저장 거부 또는 DB VPD 함수에서 fail-closed |

## 권한 조합

| ID | 조합 | 기대 결과 |
|----|------|-----------|
| C-01 | 동일 사용자에게 HR role + SELF role | 두 rule이 OR 조건으로 합쳐진 결과 |
| C-02 | 동일 사용자에게 ALL role 포함 | 전체 행 조회 |
| C-03 | 권한 삭제 후 해당 객체 권한 0개 | 보호 객체 드롭다운에서 protected 표시 제거 |
| C-04 | 민감 컬럼 예외 없음 | 민감 컬럼 NULL/미노출 |
| C-05 | 민감 컬럼 예외 있음 | 허용 role에서만 민감 컬럼 노출 |

## ORDS/환경 오류

| ID | 상황 | 기대 메시지 방향 |
|----|------|------------------|
| O-01 | ORDS base URL 미설정 | `ORDS_NOT_CONFIGURED`, 설정 조치 안내 |
| O-02 | ORDS path 불일치 | `ORDS_PATH_NOT_FOUND`, schema/module/template 확인 안내 |
| O-03 | ORDS handler PL/SQL 오류 | `ORDS_USER_DEFINED_RESOURCE_ERROR`, response body 표시 |
| O-04 | DB 연결 실패 | DB URL/사용자/비밀번호/ADB wallet 확인 안내 |
| O-05 | token 만료/폐기 | 권한 결과가 아니라 token 거부로 표시 |

## 수동 확인 절차

1. `권한` 탭에서 `MY_DEPT`, `SELF`, `DEPT`, `EMP_NO`, `=`, `!=`를 각각 저장해 본다.
2. 저장 후 권한 목록의 `적용 필터`가 실제 rule 의미와 일치하는지 확인한다.
3. `ORDS 검증` 탭에서 HR/SELF/ALL token으로 결과를 비교한다.
4. 잘못된 ORDS path를 임시로 넣어 request/response header/body가 표시되는지 확인한다.
5. 권한 삭제 후 보호 객체 표시가 갱신되는지 확인한다.
