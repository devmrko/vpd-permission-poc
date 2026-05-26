-- ============================================================
-- 01_perm_tables.sql
-- Permission model (the "policy plane"). All tables live in ADMIN.
-- Run as ADMIN.
-- ============================================================
SET ECHO OFF
SET FEEDBACK ON

PROMPT === Creating permission-model tables ===

CREATE TABLE app_customer (
  customer_id   NUMBER       PRIMARY KEY,
  customer_name VARCHAR2(80) NOT NULL UNIQUE
);

CREATE TABLE app_user (
  user_id       NUMBER       PRIMARY KEY,
  db_username   VARCHAR2(30) NOT NULL UNIQUE,   -- matches Oracle SESSION_USER (UPPER)
  customer_id   NUMBER       NOT NULL REFERENCES app_customer(customer_id),
  active        CHAR(1)      DEFAULT 'Y' CHECK (active IN ('Y','N'))
);

CREATE TABLE app_group (
  group_id      NUMBER       PRIMARY KEY,
  customer_id   NUMBER       NOT NULL REFERENCES app_customer(customer_id),
  group_name    VARCHAR2(60) NOT NULL,
  UNIQUE (customer_id, group_name)
);

CREATE TABLE user_group (
  user_id       NUMBER NOT NULL REFERENCES app_user(user_id),
  group_id      NUMBER NOT NULL REFERENCES app_group(group_id),
  PRIMARY KEY (user_id, group_id)
);

CREATE TABLE db_source (
  source_id     NUMBER       PRIMARY KEY,
  source_name   VARCHAR2(60) NOT NULL UNIQUE,   -- e.g. RDS_POSTGRES, RDS_MYSQL
  source_type   VARCHAR2(30) NOT NULL,          -- 'DBLINK_PG','DBLINK_MY','EXTERNAL_TABLE',...
  dblink_name   VARCHAR2(128)
);

-- Each permission row says: this group, on this object of this source,
-- is allowed to see rows where region is in `allowed_regions`.
-- Convention: '*' means no restriction (full access).
CREATE TABLE permission (
  perm_id          NUMBER        PRIMARY KEY,
  group_id         NUMBER        NOT NULL REFERENCES app_group(group_id),
  source_id        NUMBER        NOT NULL REFERENCES db_source(source_id),
  object_name      VARCHAR2(60)  NOT NULL,   -- the local view name, e.g. 'V_CUSTOMERS_PG'
  allowed_regions  VARCHAR2(200) NOT NULL,   -- CSV: 'KR,APAC' or '*'
  UNIQUE (group_id, source_id, object_name)
);

PROMPT === Permission tables created ===
EXIT;
