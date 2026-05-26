-- ============================================================
-- 08_tests_user_b.sql
-- Run as VPDUSER_B (group GLOBAL_ADMINS, allowed_regions=*).
-- Expected: ALL regions visible (no row filter).
-- ============================================================
SET FEEDBACK ON
SET LINESIZE 200
SET PAGESIZE 100

PROMPT
PROMPT === Who am I, and what context did the LOGON trigger load? ===
SELECT USER AS session_user,
       SYS_CONTEXT('VPD_CTX','USER_ID')         AS app_user_id,
       SYS_CONTEXT('VPD_CTX','V_CUSTOMERS_PG')  AS regions_pg,
       SYS_CONTEXT('VPD_CTX','V_CUSTOMERS_MY')  AS regions_my
FROM dual;

PROMPT
PROMPT === Distinct regions visible from Postgres view (expect: all regions) ===
SELECT DISTINCT region FROM admin.v_customers_pg ORDER BY 1;

PROMPT
PROMPT === Distinct regions visible from MySQL view (expect: all regions) ===
SELECT DISTINCT region FROM admin.v_customers_my ORDER BY 1;

PROMPT
PROMPT === Row counts (expect higher than VPDUSER_A) ===
SELECT 'V_CUSTOMERS_PG' AS view_name, COUNT(*) AS rows_visible FROM admin.v_customers_pg
UNION ALL
SELECT 'V_CUSTOMERS_MY',                COUNT(*)                FROM admin.v_customers_my;

PROMPT
PROMPT === PII REDACTION (expect REAL email/full_name — GLOBAL_ADMINS has '*' so no masking) ===
COLUMN customer_id FORMAT 9999
COLUMN full_name   FORMAT A20
COLUMN email       FORMAT A30
COLUMN region      FORMAT A8
SELECT customer_id, full_name, email, region
FROM   admin.v_customers_pg
ORDER  BY customer_id;

SELECT customer_id, full_name, email, region
FROM   admin.v_customers_my
ORDER  BY customer_id;

EXIT;
