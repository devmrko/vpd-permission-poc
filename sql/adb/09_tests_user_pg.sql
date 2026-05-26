-- ============================================================
-- 09_tests_user_pg.sql
-- Run as VPDUSER_PG (group PG_ONLY).
--
-- Expected (mirror of 08):
--   - regions_pg = '*'   -> ALL rows from PG view, unmasked
--   - regions_my = NULL  -> 0 rows from MySQL view
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
PROMPT === Row counts (expect: PG=12, MY=0) ===
SELECT 'V_CUSTOMERS_PG' AS view_name, COUNT(*) AS rows_visible FROM admin.v_customers_pg
UNION ALL
SELECT 'V_CUSTOMERS_MY',                COUNT(*)                FROM admin.v_customers_my;

PROMPT
PROMPT === PG view sample (expect: ALL regions, UNMASKED email/full_name) ===
COLUMN customer_id FORMAT 9999
COLUMN full_name   FORMAT A20
COLUMN email       FORMAT A30
COLUMN region      FORMAT A8
SELECT customer_id, full_name, email, region
FROM   admin.v_customers_pg
ORDER  BY customer_id
FETCH FIRST 5 ROWS ONLY;

PROMPT
PROMPT === MySQL view sample (expect: NO ROWS — fail closed) ===
SELECT customer_id, full_name, email, region
FROM   admin.v_customers_my
ORDER  BY customer_id;

EXIT;
