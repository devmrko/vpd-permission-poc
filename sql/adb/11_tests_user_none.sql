-- ============================================================
-- 11_tests_user_none.sql
-- Run as VPDUSER_NONE — the "default deny" case.
--
-- This user has CREATE SESSION and SELECT on both views, but ZERO
-- rows in the permission table. The LOGON trigger still fires and
-- ctx_pkg.init still runs — it just finds nothing to load.
--
-- Expected:
--   - regions_pg = NULL AND regions_my = NULL
--   - Both views return 0 rows (policy returns '1=0' / fail-closed)
--   - This proves the model is "deny by default" — adding a user
--     without a permission row is automatically safe.
-- ============================================================
SET FEEDBACK ON
SET LINESIZE 200
SET PAGESIZE 100

PROMPT
PROMPT === Who am I, and what context did the LOGON trigger load? ===
PROMPT === (expect: regions_pg and regions_my both NULL) ===
SELECT USER                                  AS session_user,
       SYS_CONTEXT('VPD_CTX','USER_ID')      AS app_user_id,
       SYS_CONTEXT('VPD_CTX','V_CUSTOMERS_PG') AS regions_pg,
       SYS_CONTEXT('VPD_CTX','V_CUSTOMERS_MY') AS regions_my
FROM dual;

PROMPT
PROMPT === Row counts (expect: PG=0, MY=0 — fail-closed) ===
SELECT 'V_CUSTOMERS_PG' AS view_name, COUNT(*) AS rows_visible FROM admin.v_customers_pg
UNION ALL
SELECT 'V_CUSTOMERS_MY',                COUNT(*)                FROM admin.v_customers_my;

PROMPT
PROMPT === Confirm no rows leak through despite the GRANT on the view ===
SELECT * FROM admin.v_customers_pg WHERE ROWNUM <= 1;
SELECT * FROM admin.v_customers_my WHERE ROWNUM <= 1;

PROMPT
PROMPT === BYPASS: even with no permission row, all 4 bypass surfaces blocked ===
WHENEVER SQLERROR CONTINUE;
SELECT COUNT(*) FROM "public"."customers"@RDS_POSTGRES_LINK;
SELECT COUNT(*) FROM admin.permission;
BEGIN
  DBMS_SESSION.SET_CONTEXT('VPD_CTX','V_CUSTOMERS_PG','*');
END;
/
BEGIN
  DBMS_RLS.DROP_POLICY('ADMIN','V_CUSTOMERS_PG','CUSTOMERS_PG_POLICY');
END;
/

EXIT;
