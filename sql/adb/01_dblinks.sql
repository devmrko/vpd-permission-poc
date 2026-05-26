-- ============================================================
-- 01_dblinks.sql
-- Run as ADMIN.
--
-- ADB 에서 외부 Postgres / MySQL 로 가는 DB Link 를 만든다.
-- DBMS_CLOUD_ADMIN.CREATE_DATABASE_LINK 를 쓰면 ADB 가 내장 게이트웨이로
-- heterogeneous (PG/MySQL) 연결을 처리해 준다.
--
-- 멱등성: 같은 이름의 link/credential 이 있으면 drop 후 재생성.
--
-- DEFINE: &DBLINK_PG_NAME, &DBLINK_MY_NAME,
--         &PG_HOST, &PG_PORT, &PG_DB, &PG_USER, &PG_PASSWORD,
--         &MY_HOST, &MY_PORT, &MY_DB, &MY_USER, &MY_PASSWORD
-- ============================================================
-- ECHO OFF: heredoc 의 DEFINE 으로 주입되는 패스워드가 stdout 으로 흐르지 않게.
-- 디버그 필요할 땐 sql 파일 단독으로만 실행하고, run.sh 경유 시는 절대 ON 하지 말 것.
SET ECHO OFF
SET VERIFY OFF
SET FEEDBACK ON
SET DEFINE ON
SET TERMOUT ON
WHENEVER SQLERROR EXIT SQL.SQLCODE

PROMPT === 1) 기존 DB link / credential 정리 (있으면 drop) ===
DECLARE
  PROCEDURE drop_link_if_exists(p_name IN VARCHAR2) IS
    l_cnt NUMBER;
  BEGIN
    SELECT COUNT(*) INTO l_cnt FROM user_db_links
     WHERE db_link = UPPER(p_name);
    IF l_cnt > 0 THEN
      DBMS_CLOUD_ADMIN.DROP_DATABASE_LINK(db_link_name => UPPER(p_name));
      DBMS_OUTPUT.PUT_LINE('dropped db_link ' || UPPER(p_name));
    END IF;
  EXCEPTION WHEN OTHERS THEN
    DBMS_OUTPUT.PUT_LINE('drop_link skip ' || p_name || ': ' || SQLERRM);
  END;

  PROCEDURE drop_cred_if_exists(p_name IN VARCHAR2) IS
    l_cnt NUMBER;
  BEGIN
    SELECT COUNT(*) INTO l_cnt FROM user_credentials
     WHERE credential_name = UPPER(p_name);
    IF l_cnt > 0 THEN
      DBMS_CLOUD.DROP_CREDENTIAL(credential_name => UPPER(p_name));
      DBMS_OUTPUT.PUT_LINE('dropped credential ' || UPPER(p_name));
    END IF;
  EXCEPTION WHEN OTHERS THEN
    DBMS_OUTPUT.PUT_LINE('drop_cred skip ' || p_name || ': ' || SQLERRM);
  END;
BEGIN
  drop_link_if_exists('&DBLINK_PG_NAME');
  drop_link_if_exists('&DBLINK_MY_NAME');
  drop_cred_if_exists('&DBLINK_PG_NAME._CRED');
  drop_cred_if_exists('&DBLINK_MY_NAME._CRED');
END;
/

PROMPT === 2) credential 등록 (원격 DB 로그인 정보) ===
BEGIN
  DBMS_CLOUD.CREATE_CREDENTIAL(
    credential_name => '&DBLINK_PG_NAME._CRED',
    username        => '&PG_USER',
    password        => '&PG_PASSWORD'
  );
  DBMS_CLOUD.CREATE_CREDENTIAL(
    credential_name => '&DBLINK_MY_NAME._CRED',
    username        => '&MY_USER',
    password        => '&MY_PASSWORD'
  );
END;
/

PROMPT === 3) DB Link 생성 — Postgres ===
BEGIN
  DBMS_CLOUD_ADMIN.CREATE_DATABASE_LINK(
    db_link_name    => '&DBLINK_PG_NAME',
    hostname        => '&PG_HOST',
    port            => &PG_PORT,
    service_name    => '&PG_DB',
    credential_name => '&DBLINK_PG_NAME._CRED',
    gateway_params  => JSON_OBJECT('db_type' VALUE 'postgres'),
    ssl_server_cert_dn => NULL,
    directory_name  => NULL
  );
END;
/

PROMPT === 4) DB Link 생성 — MySQL ===
BEGIN
  DBMS_CLOUD_ADMIN.CREATE_DATABASE_LINK(
    db_link_name    => '&DBLINK_MY_NAME',
    hostname        => '&MY_HOST',
    port            => &MY_PORT,
    service_name    => '&MY_DB',
    credential_name => '&DBLINK_MY_NAME._CRED',
    -- mysql_community: AWS RDS for MySQL, self-managed MySQL CE 등에 필요.
    -- 디폴트 'mysql' 은 MySQL Enterprise/Commercial 전용.
    gateway_params  => JSON_OBJECT('db_type' VALUE 'mysql_community'),
    ssl_server_cert_dn => NULL,
    directory_name  => NULL
  );
END;
/

PROMPT === 5) 결과 확인 — DB link 목록 ===
COL db_link FORMAT a25
COL host    FORMAT a60
SELECT db_link, host FROM user_db_links ORDER BY db_link;

PROMPT === 6) 연결 검증 — 각 link 로 가벼운 SELECT ===
WHENEVER SQLERROR CONTINUE
PROMPT -- Postgres
SELECT COUNT(*) AS pg_customers FROM "public"."customers"@&DBLINK_PG_NAME;
PROMPT -- MySQL
SELECT COUNT(*) AS my_customers FROM "&MY_DB"."customers"@&DBLINK_MY_NAME;

PROMPT === 01_dblinks done ===
EXIT
