-- ============================================================
-- 14_tests_dds_user.sql
-- Shared verification script for all 4 DDS end-users.
-- Run as ddsuser_my / ddsuser_pg / ddsuser_both / ddsuser_none.
--
-- Expected matrix (DDS):
--   user             v_dds_customers_pg     v_dds_customers_my
--   ---------------  ---------------------  ---------------------
--   ddsuser_my       ORA-00942 (hidden)     17 rows
--   ddsuser_pg       12 rows                ORA-00942 (hidden)
--   ddsuser_both     12 rows                17 rows
--   ddsuser_none     ORA-00942 (hidden)     ORA-00942 (hidden)
--
-- Note: where VPD silently returns "0 rows", DDS returns
-- `ORA-00942 (table or view does not exist)` — the object is
-- not visible at all. `WHENEVER SQLERROR CONTINUE` is set so the
-- script keeps going past expected ORA-00942 errors.
-- ============================================================
WHENEVER SQLERROR CONTINUE
SET FEEDBACK ON
SET LINESIZE 200
SET PAGESIZE 100

PROMPT
PROMPT === Who am I? (DDS end-user context) ===
SELECT ORA_END_USER_CONTEXT.username AS end_user_name FROM dual;

PROMPT
PROMPT === Row counts on the DDS-only views ===
PROMPT (ORA-00942 here means "no DATA GRANT" — that's the expected deny path)
SELECT 'v_dds_customers_pg' AS view_name, COUNT(*) AS rows_visible
  FROM admin.v_dds_customers_pg;
SELECT 'v_dds_customers_my' AS view_name, COUNT(*) AS rows_visible
  FROM admin.v_dds_customers_my;

PROMPT
PROMPT === Sample (first 3 rows from each view, if visible) ===
COLUMN customer_id FORMAT 9999
COLUMN full_name   FORMAT A20
COLUMN email       FORMAT A30
COLUMN region      FORMAT A8
SELECT customer_id, full_name, email, region
  FROM admin.v_dds_customers_pg
  ORDER BY customer_id FETCH FIRST 3 ROWS ONLY;
SELECT customer_id, full_name, email, region
  FROM admin.v_dds_customers_my
  ORDER BY customer_id FETCH FIRST 3 ROWS ONLY;

PROMPT
PROMPT === Bypass attempts ===
PROMPT -- 1) underlying VPD view (no GRANT given) -> must fail
SELECT COUNT(*) FROM admin.v_customers_pg;
SELECT COUNT(*) FROM admin.v_customers_my;
PROMPT -- 2) raw remote table via DB Link (no GRANT on the link) -> must fail
SELECT COUNT(*) FROM "public"."customers"@RDS_POSTGRES_LINK;
SELECT COUNT(*) FROM "ecommerce_poc"."customers"@RDS_LINK;
PROMPT -- 3) permission / app_user tables (VPD plane) -> must fail
SELECT COUNT(*) FROM admin.permission;
SELECT COUNT(*) FROM admin.app_user;

EXIT;
