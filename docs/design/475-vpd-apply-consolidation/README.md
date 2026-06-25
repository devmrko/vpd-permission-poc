# #475 VPD 적용 화면 통합

## 배경

VPD 적용은 보호 객체 하나를 선택해서 policy를 붙이는 개별 적용이 기본이다. 벌크 적용은 같은 filter/function을 스키마의 여러 TABLE/VIEW에 확장할 때 사용하는 보조 기능이다.

기존 화면은 `/vpd-policies`가 적용 현황 위주이고, 실제 개별/벌크 적용 폼은 `/vpd-filter-policies`에 있어 운영 흐름이 맞지 않았다.

## 결정

- `/vpd-policies`를 VPD 적용의 중심 화면으로 둔다.
- 개별 적용 폼을 먼저 배치한다.
- VPD 적용 대상은 ORDS path가 아니라 DB TABLE/VIEW다.
- ORDS는 VPD가 적용된 TABLE/VIEW를 HTTP 경로로 서빙하고 검증하는 계층으로만 표시한다.
- 벌크 적용은 같은 화면의 접힌 영역으로 둔다.
- `/vpd-filter-policies`는 filter function 등록/수정과 고급 policy 수정 화면으로 유지한다.

## 완료 기준

- 운영자가 VPD 설정 화면에서 바로 개별 적용을 수행할 수 있다.
- 같은 화면에서 필요 시 벌크 적용을 펼쳐 실행할 수 있다.
- 기존 controller/service API는 유지한다.
