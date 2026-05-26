-- ============================================================
-- 08_tests_user_my.sql
-- Run as VPDUSER_MY (group MY_ONLY).
--
-- Expected:
--   - regions_pg = NULL  -> policy returns '1=0' -> 0 rows from PG view
--   - regions_my = '*'   -> policy returns NULL  -> ALL rows from MySQL view
--   - email/full_name on MySQL view are unmasked ('*' bypasses redaction)
--   - all five bypass attempts fail (this is the full bypass-attempt suite;
--     the other three user tests rerun the most relevant subset only)
-- ============================================================
SET FEEDBACK ON
SET LINESIZE 200
SET PAGESIZE 100

PROMPT
PROMPT === Who am I, and what context did the LOGON trigger load? ===
SELECT USER                                  AS session_user,
       SYS_CONTEXT('VPD_CTX','USER_ID')      AS app_user_id,
       SYS_CONTEXT('VPD_CTX','V_CUSTOMERS_PG') AS regions_pg,
       SYS_CONTEXT('VPD_CTX','V_CUSTOMERS_MY') AS regions_my
FROM dual;

PROMPT
PROMPT === Row counts (expect: PG=0, MY=17) ===
SELECT 'V_CUSTOMERS_PG' AS view_name, COUNT(*) AS rows_visible FROM admin.v_customers_pg
UNION ALL
SELECT 'V_CUSTOMERS_MY',                COUNT(*)                FROM admin.v_customers_my;

PROMPT
PROMPT === MySQL view sample (expect: ALL regions, UNMASKED email/full_name) ===
COLUMN customer_id FORMAT 9999
COLUMN full_name   FORMAT A20
COLUMN email       FORMAT A30
COLUMN region      FORMAT A8
SELECT customer_id, full_name, email, region
FROM   admin.v_customers_my
ORDER  BY customer_id
FETCH FIRST 5 ROWS ONLY;

PROMPT
PROMPT === PG view sample (expect: NO ROWS — fail closed) ===
SELECT customer_id, full_name, email, region
FROM   admin.v_customers_pg
ORDER  BY customer_id;

PROMPT
PROMPT === BYPASS 1: query remote tables directly (expect ORA-00942 / privilege error) ===
WHENEVER SQLERROR CONTINUE;
SELECT COUNT(*) FROM "public"."customers"@RDS_POSTGRES_LINK;
SELECT COUNT(*) FROM "ecommerce_poc"."customers"@RDS_LINK;

PROMPT
PROMPT === BYPASS 2: try to spoof the context (expect ORA-01031) ===
BEGIN
  DBMS_SESSION.SET_CONTEXT('VPD_CTX','V_CUSTOMERS_PG','*');
END;
/

PROMPT
PROMPT === BYPASS 3: try to drop the policy (expect ORA-00942 / privilege error) ===
BEGIN
  DBMS_RLS.DROP_POLICY('ADMIN','V_CUSTOMERS_PG','CUSTOMERS_PG_POLICY');
END;
/

PROMPT
PROMPT === BYPASS 4: try to read the permission table (expect ORA-00942) ===
SELECT COUNT(*) FROM admin.permission;

PROMPT
PROMPT === BYPASS 5: try to read app_user (expect ORA-00942) ===
SELECT COUNT(*) FROM admin.app_user;

EXIT;
