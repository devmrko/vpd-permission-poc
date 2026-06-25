# #477 Oracle VPD/ORDS와 권한 테이블 제어 구조 표시

## 배경

이 백오피스는 Oracle Database의 VPD와 ORDS 기능을 새로 구현하는 도구가 아니다. Oracle이 제공하는 기능을 사용하되, 애플리케이션 권한 테이블로 제어하고 운영자가 결과를 확인할 수 있게 만든 도구다.

## 구조

1. Backoffice Tables
   - 사용자, 역할, 권한, 행 규칙, 컬럼 원문 허용을 저장한다.
   - VPD policy function이 이 테이블을 조회해 predicate를 만든다.
2. Oracle Database VPD
   - TABLE/VIEW에 policy function을 붙인다.
   - SELECT 시점에 DB가 행 접근을 제한한다.
3. ORDS
   - VPD가 적용된 TABLE/VIEW를 HTTP API로 서빙한다.
   - Bearer Token을 받아 DB session context를 세팅하고 결과를 확인한다.

## UX 결정

- 대시보드에 전체 구조를 표시한다.
- 권한 관리 화면은 Backoffice Tables 레이어를 강조한다.
- VPD 설정 화면은 Oracle Database VPD 레이어를 강조한다.
- ORDS Handler 생성과 ORDS 검증 화면은 ORDS 서빙/검증 레이어를 강조한다.
- VPD 적용 대상은 TABLE/VIEW로 표기하고, ORDS path는 서빙/검증 화면에서만 노출한다.
