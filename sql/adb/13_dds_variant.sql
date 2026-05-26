-- ============================================================
-- 13_dds_variant.sql  (OPTIONAL — Oracle 26ai Deep Data Security)
-- ============================================================
-- Reimplements the same 4-user source-access matrix as the VPD
-- path (06_policy.sql + 07_end_users.sql), but using Oracle Deep
-- Data Security (DDS) — the declarative SQL successor to VPD/RAS
-- introduced in Oracle AI Database 26ai (announced 2026-04-09).
--
-- Matrix (identical to VPD demo):
--   user            PG view      MySQL view
--   --------------  -----------  -----------
--   DDSUSER_MY      blocked      ALL rows
--   DDSUSER_PG      ALL rows     blocked
--   DDSUSER_BOTH    ALL rows     ALL rows
--   DDSUSER_NONE    blocked      blocked       (no data role = default deny)
--
-- ============================================================
-- PREREQUISITES (verify before running)
-- ------------------------------------------------------------
--   * Oracle AI Database 23.26.2 or later
--   * COMPATIBLE init parameter >= 20.0
--   * ADMIN holds: CREATE END USER, CREATE DATA ROLE, GRANT DATA
--     ROLE, CREATE DATA GRANT system privileges (ADMIN gets them
--     by default on ADB; on on-prem you may need to grant).
--
-- Check version + COMPATIBLE before running:
--   SELECT banner_full FROM v$version;
--   SELECT value FROM v$parameter WHERE name = 'compatible';
-- ============================================================
-- COEXISTENCE NOTE
-- ------------------------------------------------------------
-- This script creates *new* end users with the `ddsuser_` prefix
-- so it does NOT collide with the VPD demo users (`vpduser_*`).
-- Both paths can coexist on the same ADB. The shared objects
-- (views v_customers_pg / v_customers_my, db_source table, etc.)
-- are reused — DDS adds a parallel access path, it does not
-- replace anything.
--
-- We deliberately do NOT enable `SET USE DATA GRANTS ONLY` on
-- the views — doing so would block the existing VPD users who
-- rely on the regular GRANT SELECT path. The DDS users get their
-- access exclusively through DATA GRANTs (no regular SELECT
-- grant is issued), so MAC is not required for this demo.
--
-- DEFINE: &DDSUSER_MY_PASSWORD, &DDSUSER_PG_PASSWORD,
--         &DDSUSER_BOTH_PASSWORD, &DDSUSER_NONE_PASSWORD
-- ============================================================
SET ECHO OFF
SET FEEDBACK ON
SET DEFINE ON

PROMPT === 1. Creating local END USERs (schemaless, no objects) ===
-- End users in DDS do not own a schema and cannot create objects.
-- They authenticate, then receive access purely via DATA GRANTs.
CREATE END USER "ddsuser_my"   IDENTIFIED BY "&DDSUSER_MY_PASSWORD";
CREATE END USER "ddsuser_pg"   IDENTIFIED BY "&DDSUSER_PG_PASSWORD";
CREATE END USER "ddsuser_both" IDENTIFIED BY "&DDSUSER_BOTH_PASSWORD";
CREATE END USER "ddsuser_none" IDENTIFIED BY "&DDSUSER_NONE_PASSWORD";

PROMPT === 2. Creating DATA ROLEs + connection role for direct logon ===
-- Per the DDS direct-logon pattern: a standard ROLE carries
-- CREATE SESSION, then that standard role is granted to each
-- data role so end users can actually connect.
CREATE ROLE dds_db_role;
GRANT CREATE SESSION TO dds_db_role;

CREATE DATA ROLE my_only_role;
CREATE DATA ROLE pg_only_role;
CREATE DATA ROLE both_sources_role;
-- (No role for the "none" user — absence of a data role = default deny.)

GRANT dds_db_role TO my_only_role;
GRANT dds_db_role TO pg_only_role;
GRANT dds_db_role TO both_sources_role;

PROMPT === 3. Mapping end users to data roles ===
GRANT DATA ROLE my_only_role       TO "ddsuser_my";
GRANT DATA ROLE pg_only_role       TO "ddsuser_pg";
GRANT DATA ROLE both_sources_role  TO "ddsuser_both";
-- ddsuser_none gets nothing — they can authenticate (no, actually
-- they cannot, because they have no data role carrying dds_db_role).
-- Grant CREATE SESSION directly so they can prove "logged in but
-- denied at the data layer" — same UX as VPDUSER_NONE in the VPD demo.
GRANT dds_db_role TO "ddsuser_none";

PROMPT === 4. Creating DATA GRANTs (the declarative equivalent of the VPD policy) ===
-- These five lines replace the entire vpd_region_filter PL/SQL
-- function + the per-row lookups against the `permission` table.
-- Each grant is a single declarative SQL statement.

-- DDSUSER_MY -> ALL rows from v_customers_my, no PG access.
CREATE DATA GRANT admin.dds_my_only_grant_mysql
  AS SELECT
  ON admin.v_customers_my
  TO my_only_role;

-- DDSUSER_PG -> ALL rows from v_customers_pg, no MY access.
CREATE DATA GRANT admin.dds_pg_only_grant_pg
  AS SELECT
  ON admin.v_customers_pg
  TO pg_only_role;

-- DDSUSER_BOTH -> both views.
CREATE DATA GRANT admin.dds_both_grant_pg
  AS SELECT
  ON admin.v_customers_pg
  TO both_sources_role;

CREATE DATA GRANT admin.dds_both_grant_mysql
  AS SELECT
  ON admin.v_customers_my
  TO both_sources_role;

-- DDSUSER_NONE: NO data grant -> default deny.

PROMPT === 5. (Optional) Region row-level filter — DDS-style ===
-- The VPD path stores allowed_regions in a `permission` table and
-- builds the predicate at query time. DDS makes this a declarative
-- WHERE clause directly on the grant. Example: restrict
-- both_sources_role to APAC only on the PG view. Uncomment to try.
--
--   CREATE OR REPLACE DATA GRANT admin.dds_both_grant_pg
--     AS SELECT
--     ON admin.v_customers_pg
--     WHERE region = 'APAC'
--     TO both_sources_role;
--
-- For multi-region, use IN-list (no CSV-splitting helper needed,
-- unlike the VPD policy):
--
--   CREATE OR REPLACE DATA GRANT admin.dds_both_grant_mysql
--     AS SELECT
--     ON admin.v_customers_my
--     WHERE region IN ('APAC','EMEA')
--     TO both_sources_role;

PROMPT === 6. (Optional) Column masking — DDS-style ===
-- The VPD path uses a separate Data Redaction policy
-- (06a_redaction.sql) to NULL out the email column. DDS folds this
-- into the grant itself with ALL COLUMNS EXCEPT — one statement
-- handles both row filter AND column mask.
--
--   CREATE OR REPLACE DATA GRANT admin.dds_both_grant_pg
--     AS SELECT (ALL COLUMNS EXCEPT email)
--     ON admin.v_customers_pg
--     TO both_sources_role;
--
-- Excluded columns return NULL (same UX as redaction), but it's
-- one declarative grant instead of (policy function + redaction
-- policy + permission table row).

PROMPT === DDS variant ready ===
-- ------------------------------------------------------------
-- Verify with:
--   sqlplus '"ddsuser_pg"'/<pw>@<service>
--   SQL> SELECT ORA_END_USER_CONTEXT.username FROM dual;
--   SQL> SELECT COUNT(*) FROM admin.v_customers_pg;   -- expect 12
--   SQL> SELECT COUNT(*) FROM admin.v_customers_my;   -- expect 0 (no grant)
--
-- Audit the grants:
--   SELECT * FROM dba_data_grants;
--   SELECT * FROM dba_data_roles;
--   SELECT * FROM dba_end_users;
-- ------------------------------------------------------------
EXIT;
