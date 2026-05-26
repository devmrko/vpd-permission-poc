-- ============================================================
-- 04_views.sql
-- Local views over the remote (heterogeneous) DB-linked tables.
-- End-users will be granted SELECT on these views only.
-- The raw @dblink references stay inside ADMIN, invisible to users.
-- ============================================================
SET ECHO OFF
SET FEEDBACK ON

PROMPT === Creating v_customers_pg (Postgres) ===
-- Postgres is case-sensitive: schema, table, and column names must be quoted.
CREATE OR REPLACE VIEW v_customers_pg AS
SELECT "customer_id"  AS customer_id,
       "full_name"    AS full_name,
       "email"        AS email,
       "signup_date"  AS signup_date,
       "region"       AS region
FROM   "public"."customers"@RDS_POSTGRES_LINK;

PROMPT === Creating v_customers_my (MySQL) ===
-- MySQL via Oracle gateway: schema/table need quoting (lowercase preserved).
CREATE OR REPLACE VIEW v_customers_my AS
SELECT "customer_id"  AS customer_id,
       "full_name"    AS full_name,
       "email"        AS email,
       "signup_date"  AS signup_date,
       "region"       AS region
FROM   "ecommerce_poc"."customers"@RDS_LINK;

PROMPT === Views created ===
EXIT;
