-- ============================================================
-- 02_seed.sql
-- Seed two end-users with different permissions to demonstrate VPD.
--
--   VPDUSER_A -> group KR_ANALYSTS   -> allowed_regions = 'APAC'
--   VPDUSER_B -> group GLOBAL_ADMINS -> allowed_regions = '*'  (all rows)
--
-- Run as ADMIN.
-- ============================================================
SET ECHO OFF
SET FEEDBACK ON

PROMPT === Seeding permission data ===

INSERT INTO app_customer (customer_id, customer_name) VALUES (1, 'Acme Corp');

INSERT INTO app_user (user_id, db_username, customer_id) VALUES (1, 'VPDUSER_A', 1);
INSERT INTO app_user (user_id, db_username, customer_id) VALUES (2, 'VPDUSER_B', 1);

INSERT INTO app_group (group_id, customer_id, group_name) VALUES (10, 1, 'KR_ANALYSTS');
INSERT INTO app_group (group_id, customer_id, group_name) VALUES (20, 1, 'GLOBAL_ADMINS');

-- A -> KR_ANALYSTS
INSERT INTO user_group (user_id, group_id) VALUES (1, 10);
-- B -> GLOBAL_ADMINS
INSERT INTO user_group (user_id, group_id) VALUES (2, 20);

INSERT INTO db_source (source_id, source_name, source_type, dblink_name)
  VALUES (100, 'RDS_POSTGRES', 'DBLINK_PG', 'RDS_POSTGRES_LINK');
INSERT INTO db_source (source_id, source_name, source_type, dblink_name)
  VALUES (200, 'RDS_MYSQL',    'DBLINK_MY', 'RDS_LINK');

-- Permissions: KR analysts see APAC only; Global admins see everything.
INSERT INTO permission (perm_id, group_id, source_id, object_name, allowed_regions)
  VALUES (1, 10, 100, 'V_CUSTOMERS_PG', 'APAC');
INSERT INTO permission (perm_id, group_id, source_id, object_name, allowed_regions)
  VALUES (2, 10, 200, 'V_CUSTOMERS_MY', 'APAC');
INSERT INTO permission (perm_id, group_id, source_id, object_name, allowed_regions)
  VALUES (3, 20, 100, 'V_CUSTOMERS_PG', '*');
INSERT INTO permission (perm_id, group_id, source_id, object_name, allowed_regions)
  VALUES (4, 20, 200, 'V_CUSTOMERS_MY', '*');

COMMIT;

PROMPT === Seed complete ===
EXIT;
