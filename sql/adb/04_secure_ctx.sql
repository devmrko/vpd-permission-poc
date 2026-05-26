-- ============================================================
-- 03_secure_ctx.sql
-- Secure application context. ONLY ctx_pkg can SET vpd_ctx.*
-- End-users cannot spoof their identity / region list.
--
-- The package is AUTHID DEFINER so it can read permission tables
-- without granting end-users direct SELECT on those tables.
-- It uses SYS_CONTEXT('USERENV','SESSION_USER') to identify the
-- *invoker*, so even if an end-user calls it directly they only
-- ever load their OWN permissions.
-- ============================================================
SET ECHO OFF
SET FEEDBACK ON

PROMPT === Creating context package spec ===
CREATE OR REPLACE PACKAGE ctx_pkg AUTHID DEFINER AS
  -- Called from the LOGON trigger (or manually).
  -- Loads the connecting user's allowed regions per object into vpd_ctx.
  PROCEDURE init;
END ctx_pkg;
/

PROMPT === Creating context package body ===
CREATE OR REPLACE PACKAGE BODY ctx_pkg AS
  PROCEDURE init IS
    v_user app_user.db_username%TYPE := SYS_CONTEXT('USERENV','SESSION_USER');
    v_uid  app_user.user_id%TYPE;
  BEGIN
    -- Look up app_user by Oracle session username (always uppercased).
    BEGIN
      SELECT user_id INTO v_uid
      FROM   app_user
      WHERE  db_username = v_user
      AND    active = 'Y';
    EXCEPTION WHEN NO_DATA_FOUND THEN
      -- Not an app user; leave context empty -> policy will return impossible predicate.
      RETURN;
    END;

    -- For each object the user has permission on, aggregate allowed_regions
    -- across all groups the user belongs to.
    --   '*' wins over any specific list.
    FOR r IN (
      SELECT p.object_name,
             CASE WHEN MAX(CASE WHEN p.allowed_regions='*' THEN 1 ELSE 0 END) = 1
                  THEN '*'
                  ELSE LISTAGG(p.allowed_regions, ',') WITHIN GROUP (ORDER BY p.allowed_regions)
             END AS regions
      FROM   permission p
      JOIN   user_group ug ON ug.group_id = p.group_id
      WHERE  ug.user_id = v_uid
      GROUP  BY p.object_name
    ) LOOP
      -- Attribute name = object name (e.g. V_CUSTOMERS_PG), value = CSV of regions.
      DBMS_SESSION.SET_CONTEXT('VPD_CTX', r.object_name, r.regions);
    END LOOP;

    -- Also store the user_id for auditing / future use.
    DBMS_SESSION.SET_CONTEXT('VPD_CTX', 'USER_ID', TO_CHAR(v_uid));
  END init;
END ctx_pkg;
/

PROMPT === Binding secure context to ctx_pkg ===
-- This is the critical line: only code running INSIDE admin.ctx_pkg
-- can call DBMS_SESSION.SET_CONTEXT on the VPD_CTX namespace.
CREATE OR REPLACE CONTEXT vpd_ctx USING ctx_pkg;

PROMPT === Secure context ready ===
EXIT;
