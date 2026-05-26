-- ============================================================
-- 07_end_users.sql
-- Create the FOUR end-user accounts with MINIMAL privileges.
-- Add a LOGON trigger that loads each user's context automatically.
-- Run as ADMIN.
--
-- DEFINE: &VPDUSER_MY_PASSWORD, &VPDUSER_PG_PASSWORD,
--         &VPDUSER_BOTH_PASSWORD, &VPDUSER_NONE_PASSWORD
-- ============================================================
SET ECHO OFF
SET FEEDBACK ON
SET DEFINE ON
SET VERIFY OFF       -- 비번이 'new 1:' 라인으로 echo 되는 것을 막음 (leak 방지)

PROMPT === Creating end-user accounts ===
-- Passwords come from .env (DEFINE) so they aren't hardcoded in source.
-- Production should use IAM / proxy auth / mTLS instead of static passwords.
CREATE USER vpduser_my   IDENTIFIED BY "&VPDUSER_MY_PASSWORD";
CREATE USER vpduser_pg   IDENTIFIED BY "&VPDUSER_PG_PASSWORD";
CREATE USER vpduser_both IDENTIFIED BY "&VPDUSER_BOTH_PASSWORD";
CREATE USER vpduser_none IDENTIFIED BY "&VPDUSER_NONE_PASSWORD";

-- Login privilege only. No QUOTA — these users never create objects.
GRANT CREATE SESSION TO vpduser_my;
GRANT CREATE SESSION TO vpduser_pg;
GRANT CREATE SESSION TO vpduser_both;
GRANT CREATE SESSION TO vpduser_none;

PROMPT === Granting SELECT on the policy-protected views ONLY ===
-- We deliberately grant SELECT on BOTH views to all four users.
-- The VPD policy decides what they actually see — including 0 rows
-- when there is no permission row. This is what makes the demo a
-- clean security boundary: revoking access doesn't mean revoking
-- the GRANT; it means removing the row in `permission`.
GRANT SELECT ON v_customers_pg TO vpduser_my;
GRANT SELECT ON v_customers_my TO vpduser_my;
GRANT SELECT ON v_customers_pg TO vpduser_pg;
GRANT SELECT ON v_customers_my TO vpduser_pg;
GRANT SELECT ON v_customers_pg TO vpduser_both;
GRANT SELECT ON v_customers_my TO vpduser_both;
GRANT SELECT ON v_customers_pg TO vpduser_none;
GRANT SELECT ON v_customers_my TO vpduser_none;

-- ctx_pkg is bound to the secure context; calling it is harmless
-- (the package always loads ONLY the caller's own permissions).
GRANT EXECUTE ON ctx_pkg TO vpduser_my;
GRANT EXECUTE ON ctx_pkg TO vpduser_pg;
GRANT EXECUTE ON ctx_pkg TO vpduser_both;
GRANT EXECUTE ON ctx_pkg TO vpduser_none;

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
  IF SYS_CONTEXT('USERENV','SESSION_USER') IN
       ('VPDUSER_MY','VPDUSER_PG','VPDUSER_BOTH','VPDUSER_NONE') THEN
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
