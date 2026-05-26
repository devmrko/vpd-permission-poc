-- ============================================================
-- 03_seed.sql
-- Seed FOUR end-users to demonstrate a 2x2 source-access matrix:
--
--   user           PG view      MySQL view    VPD predicate effect
--   -------------  -----------  ------------  ---------------------------
--   VPDUSER_MY     blocked      ALL rows      PG='1=0'   / MY=NULL(='*')
--   VPDUSER_PG     ALL rows     blocked       PG=NULL    / MY='1=0'
--   VPDUSER_BOTH   ALL rows     ALL rows      PG=NULL    / MY=NULL
--   VPDUSER_NONE   blocked      blocked       PG='1=0'   / MY='1=0'
--
-- The `permission` table grants per (group, source/view).
-- For this simplified demo every grant uses allowed_regions='*'
-- (full visibility within the source). Row-level region filtering
-- is still supported by the policy function — see commented examples
-- at the bottom of this file for how to layer it in.
--
-- Run as ADMIN.
-- ============================================================
SET ECHO OFF
SET FEEDBACK ON

PROMPT === Seeding permission data ===

-- Tenant (kept generic — multi-tenant hook for future use).
INSERT INTO app_customer (customer_id, customer_name) VALUES (1, 'Acme Corp');

-- Four end-users, each Oracle SESSION_USER value (uppercased).
INSERT INTO app_user (user_id, db_username, customer_id) VALUES (1, 'VPDUSER_MY',   1);
INSERT INTO app_user (user_id, db_username, customer_id) VALUES (2, 'VPDUSER_PG',   1);
INSERT INTO app_user (user_id, db_username, customer_id) VALUES (3, 'VPDUSER_BOTH', 1);
INSERT INTO app_user (user_id, db_username, customer_id) VALUES (4, 'VPDUSER_NONE', 1);

-- One group per access pattern (1:1 in this demo; in production one
-- group typically aggregates many users).
INSERT INTO app_group (group_id, customer_id, group_name) VALUES (10, 1, 'MY_ONLY');
INSERT INTO app_group (group_id, customer_id, group_name) VALUES (20, 1, 'PG_ONLY');
INSERT INTO app_group (group_id, customer_id, group_name) VALUES (30, 1, 'BOTH_SOURCES');
-- (No group is needed for VPDUSER_NONE — absence of grants == fail-closed.)

-- (VPDUSER_NONE deliberately has no user_group row -> fail-closed.)
INSERT INTO user_group (user_id, group_id) VALUES (1, 10);
INSERT INTO user_group (user_id, group_id) VALUES (2, 20);
INSERT INTO user_group (user_id, group_id) VALUES (3, 30);

-- Source registry.
INSERT INTO db_source (source_id, source_name, source_type, dblink_name)
  VALUES (100, 'RDS_POSTGRES', 'DBLINK_PG', 'RDS_POSTGRES_LINK');
INSERT INTO db_source (source_id, source_name, source_type, dblink_name)
  VALUES (200, 'RDS_MYSQL',    'DBLINK_MY', 'RDS_LINK');

-- Permissions: '*' means no row filter (full visibility on that view).
-- Mapping (group -> view):
--   MY_ONLY(10)      -> V_CUSTOMERS_MY
--   PG_ONLY(20)      -> V_CUSTOMERS_PG
--   BOTH_SOURCES(30) -> V_CUSTOMERS_PG + V_CUSTOMERS_MY
-- (VPDUSER_NONE has no group, hence no permission row.)
INSERT INTO permission (perm_id, group_id, source_id, object_name, allowed_regions)
  VALUES (1, 10, 200, 'V_CUSTOMERS_MY', '*');
INSERT INTO permission (perm_id, group_id, source_id, object_name, allowed_regions)
  VALUES (2, 20, 100, 'V_CUSTOMERS_PG', '*');
INSERT INTO permission (perm_id, group_id, source_id, object_name, allowed_regions)
  VALUES (3, 30, 100, 'V_CUSTOMERS_PG', '*');
INSERT INTO permission (perm_id, group_id, source_id, object_name, allowed_regions)
  VALUES (4, 30, 200, 'V_CUSTOMERS_MY', '*');

COMMIT;

-- ------------------------------------------------------------
-- HOW TO LAYER IN ROW-LEVEL REGION FILTERS (uncomment to try)
-- ------------------------------------------------------------
-- 'BOTH_SOURCES' showing only APAC from PG instead of '*':
--   UPDATE permission SET allowed_regions = 'APAC'
--    WHERE group_id = 30 AND object_name = 'V_CUSTOMERS_PG';
--   COMMIT;
--
-- Multi-region (CSV) example for the same group on MySQL:
--   UPDATE permission SET allowed_regions = 'APAC,EMEA'
--    WHERE group_id = 30 AND object_name = 'V_CUSTOMERS_MY';
--   COMMIT;
--
-- The policy function vpd_region_filter handles CSV → IN-list
-- conversion automatically. See sql/adb/06_policy.sql.
-- ------------------------------------------------------------

PROMPT === Seed complete ===
EXIT;
