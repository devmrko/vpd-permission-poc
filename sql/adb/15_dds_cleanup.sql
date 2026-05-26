-- ============================================================
-- 15_dds_cleanup.sql
-- Idempotent teardown for the DDS variant (13_dds_variant.sql).
-- Safe to run before 13_dds_variant.sql to wipe partial state.
-- Errors are ignored (objects may not exist on first run).
-- Run as ADMIN.
-- ============================================================
WHENEVER SQLERROR CONTINUE NONE;
SET ECHO OFF
SET FEEDBACK OFF

PROMPT === Dropping DDS data grants (if present) ===
BEGIN EXECUTE IMMEDIATE 'DROP DATA GRANT admin.dds_my_only_grant_mysql'; EXCEPTION WHEN OTHERS THEN NULL; END;
/
BEGIN EXECUTE IMMEDIATE 'DROP DATA GRANT admin.dds_pg_only_grant_pg';    EXCEPTION WHEN OTHERS THEN NULL; END;
/
BEGIN EXECUTE IMMEDIATE 'DROP DATA GRANT admin.dds_both_grant_pg';       EXCEPTION WHEN OTHERS THEN NULL; END;
/
BEGIN EXECUTE IMMEDIATE 'DROP DATA GRANT admin.dds_both_grant_mysql';    EXCEPTION WHEN OTHERS THEN NULL; END;
/

PROMPT === Dropping DDS end users (no CASCADE — END USERs own no objects) ===
BEGIN EXECUTE IMMEDIATE 'DROP END USER "ddsuser_my"';   EXCEPTION WHEN OTHERS THEN NULL; END;
/
BEGIN EXECUTE IMMEDIATE 'DROP END USER "ddsuser_pg"';   EXCEPTION WHEN OTHERS THEN NULL; END;
/
BEGIN EXECUTE IMMEDIATE 'DROP END USER "ddsuser_both"'; EXCEPTION WHEN OTHERS THEN NULL; END;
/
BEGIN EXECUTE IMMEDIATE 'DROP END USER "ddsuser_none"'; EXCEPTION WHEN OTHERS THEN NULL; END;
/

PROMPT === Dropping DDS data roles (if present) ===
BEGIN EXECUTE IMMEDIATE 'DROP DATA ROLE my_only_role';      EXCEPTION WHEN OTHERS THEN NULL; END;
/
BEGIN EXECUTE IMMEDIATE 'DROP DATA ROLE pg_only_role';      EXCEPTION WHEN OTHERS THEN NULL; END;
/
BEGIN EXECUTE IMMEDIATE 'DROP DATA ROLE both_sources_role'; EXCEPTION WHEN OTHERS THEN NULL; END;
/
BEGIN EXECUTE IMMEDIATE 'DROP DATA ROLE connect_only_role'; EXCEPTION WHEN OTHERS THEN NULL; END;
/

PROMPT === Dropping DDS regular role (session-carrier) ===
BEGIN EXECUTE IMMEDIATE 'DROP ROLE dds_db_role'; EXCEPTION WHEN OTHERS THEN NULL; END;
/

PROMPT === Dropping DDS-only views ===
BEGIN EXECUTE IMMEDIATE 'DROP VIEW v_dds_customers_pg'; EXCEPTION WHEN OTHERS THEN NULL; END;
/
BEGIN EXECUTE IMMEDIATE 'DROP VIEW v_dds_customers_my'; EXCEPTION WHEN OTHERS THEN NULL; END;
/

PROMPT === DDS cleanup complete ===
EXIT;
