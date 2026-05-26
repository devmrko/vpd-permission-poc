-- ============================================================
-- 07_end_users.sql
-- Create the two end-user accounts with MINIMAL privileges.
-- Add a LOGON trigger that loads each user's context automatically.
-- Run as ADMIN.
--
-- DEFINE: &VPDUSER_A_PASSWORD, &VPDUSER_B_PASSWORD
-- ============================================================
SET ECHO OFF
SET FEEDBACK ON
SET DEFINE ON

PROMPT === Creating end-user accounts ===
-- Passwords come from .env (DEFINE) so they aren't hardcoded in source.
-- Production should use IAM / proxy auth / mTLS instead of static passwords.
CREATE USER vpduser_a IDENTIFIED BY "&VPDUSER_A_PASSWORD";
CREATE USER vpduser_b IDENTIFIED BY "&VPDUSER_B_PASSWORD";

-- ADB requires a tablespace quota even for read-only users in some setups; we
-- skip QUOTA since these users won't create objects.
GRANT CREATE SESSION TO vpduser_a;
GRANT CREATE SESSION TO vpduser_b;

PROMPT === Granting SELECT on the policy-protected views ONLY ===
GRANT SELECT ON v_customers_pg TO vpduser_a;
GRANT SELECT ON v_customers_my TO vpduser_a;
GRANT SELECT ON v_customers_pg TO vpduser_b;
GRANT SELECT ON v_customers_my TO vpduser_b;

-- Allow them to call ctx_pkg.init (the logon trigger needs this, and a manual
-- re-init is sometimes useful). The package is bound to vpd_ctx so calling it
-- is harmless: it only ever loads the caller's OWN permissions.
GRANT EXECUTE ON ctx_pkg TO vpduser_a;
GRANT EXECUTE ON ctx_pkg TO vpduser_b;

-- NOTE on what we are deliberately NOT granting:
--   * NO grant on app_user / permission / etc.    -> users can't read who-can-see-what
--   * NO grant on the underlying @dblink tables    -> can't bypass the view
--   * NO grant on DBMS_RLS                         -> can't alter/drop the policy
--   * NO EXEMPT ACCESS POLICY                      -> can't escape VPD
--   * NO CREATE TABLE / CREATE MATERIALIZED VIEW   -> can't snapshot filtered data
--   * NO DBA / PDB_DBA / RESOURCE roles            -> least privilege

PROMPT === Creating LOGON trigger that auto-loads context ===
CREATE OR REPLACE TRIGGER vpd_logon_trg
AFTER LOGON ON DATABASE
BEGIN
  -- Only fire for our application end-users. ADMIN logons keep normal behavior.
  IF SYS_CONTEXT('USERENV','SESSION_USER') IN ('VPDUSER_A','VPDUSER_B') THEN
    admin.ctx_pkg.init;
  END IF;
EXCEPTION
  WHEN OTHERS THEN
    -- Never block login on a context-loading error; fail closed (no perms loaded
    -- means policy returns 1=0, i.e. no rows). Log if you have an audit table.
    NULL;
END;
/

PROMPT === End-users ready ===
EXIT;
