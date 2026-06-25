-- ============================================================
-- 25_agent_ords_security_backoffice_support.sql
-- Support objects for the Spring Boot VPD/ORDS backoffice.
--
-- Run as ADMIN after 17_agent_ords_security_local_vpd_setup.sql.
-- This script keeps the existing CB_* VPD model and adds only the
-- metadata/audit objects needed by the backoffice UI.
-- ============================================================
WHENEVER SQLERROR EXIT SQL.SQLCODE
SET ECHO ON
SET FEEDBACK ON

PROMPT === Adding optional description column to bearer keys ===
BEGIN
  EXECUTE IMMEDIATE 'ALTER TABLE cb_agent_bearer_key ADD (description VARCHAR2(200))';
EXCEPTION
  WHEN OTHERS THEN
    IF SQLCODE != -1430 THEN -- ORA-01430: column being added already exists
      RAISE;
    END IF;
END;
/

PROMPT === Creating backoffice sequences ===
DECLARE
  PROCEDURE create_seq(p_name IN VARCHAR2, p_start IN NUMBER) AS
  BEGIN
    EXECUTE IMMEDIATE
      'CREATE SEQUENCE ' || p_name || ' START WITH ' || p_start || ' INCREMENT BY 1 NOCACHE';
  EXCEPTION
    WHEN OTHERS THEN
      IF SQLCODE != -955 THEN
        RAISE;
      END IF;
  END;
BEGIN
  create_seq('cb_agent_bearer_key_seq', 1000);
  create_seq('cb_permission_seq', 1000);
  create_seq('cb_permission_rule_seq', 10000);
  create_seq('cb_ords_probe_audit_seq', 1);
END;
/

PROMPT === Creating protected object whitelist ===
BEGIN
  EXECUTE IMMEDIATE 'ALTER TABLE cb_permission_rule ADD (rule_column VARCHAR2(128))';
EXCEPTION
  WHEN OTHERS THEN
    IF SQLCODE != -1430 THEN
      RAISE;
    END IF;
END;
/

BEGIN
  EXECUTE IMMEDIATE 'ALTER TABLE cb_permission ADD (permission_effect VARCHAR2(10) DEFAULT ''ALLOW'' NOT NULL)';
EXCEPTION
  WHEN OTHERS THEN
    IF SQLCODE != -1430 THEN
      RAISE;
    END IF;
END;
/

CREATE TABLE cb_protected_object (
  object_id    NUMBER PRIMARY KEY,
  owner        VARCHAR2(128) NOT NULL,
  object_name  VARCHAR2(128) NOT NULL UNIQUE,
  ords_path    VARCHAR2(300) NOT NULL,
  enabled_yn   CHAR(1) DEFAULT 'Y' CHECK (enabled_yn IN ('Y','N')) NOT NULL
);

CREATE TABLE cb_protected_column (
  column_id        NUMBER PRIMARY KEY,
  object_id        NUMBER NOT NULL REFERENCES cb_protected_object(object_id),
  column_name      VARCHAR2(128) NOT NULL,
  sensitive_yn     CHAR(1) DEFAULT 'N' CHECK (sensitive_yn IN ('Y','N')) NOT NULL,
  visible_role_id  NUMBER,
  CONSTRAINT cb_protected_column_uk UNIQUE (object_id, column_name)
);

CREATE TABLE cb_permission_column (
  permission_id  NUMBER NOT NULL,
  column_name    VARCHAR2(128) NOT NULL,
  CONSTRAINT cb_permission_column_pk PRIMARY KEY (permission_id, column_name)
);

CREATE TABLE cb_ords_probe_audit (
  audit_id    NUMBER PRIMARY KEY,
  event_type  VARCHAR2(50) NOT NULL,
  key_id      NUMBER,
  object_id   NUMBER,
  status      VARCHAR2(50),
  row_count   NUMBER,
  error_code  VARCHAR2(100),
  message     VARCHAR2(500),
  created_at  TIMESTAMP DEFAULT SYSTIMESTAMP NOT NULL
);

PROMPT === Creating backoffice settings ===
BEGIN
  EXECUTE IMMEDIATE '
    CREATE TABLE cb_backoffice_setting (
      setting_key    VARCHAR2(100) PRIMARY KEY,
      setting_value  VARCHAR2(1000),
      updated_at     TIMESTAMP DEFAULT SYSTIMESTAMP NOT NULL
    )';
EXCEPTION
  WHEN OTHERS THEN
    IF SQLCODE != -955 THEN
      RAISE;
    END IF;
END;
/

MERGE INTO cb_backoffice_setting dst
USING (
  SELECT 'ORDS_BASE_URL' setting_key,
         'https://yh0olybn5pqce4n-d8aukro81636mon0.adb.ap-seoul-1.oraclecloudapps.com/ords' setting_value
  FROM dual
) src
ON (dst.setting_key = src.setting_key)
WHEN MATCHED THEN UPDATE SET dst.setting_value = src.setting_value
WHERE dst.setting_value IS NULL OR TRIM(dst.setting_value) IS NULL
WHEN NOT MATCHED THEN INSERT (setting_key, setting_value, updated_at)
VALUES (src.setting_key, src.setting_value, SYSTIMESTAMP);

PROMPT === Seeding default VPD ORDS protected object ===
MERGE INTO cb_protected_object dst
USING (
  SELECT 1 AS object_id,
         'ADMIN' AS owner,
         'CB_V_SEARCH_DOCUMENTS' AS object_name,
         'cb-ords/cb-agent-security/vpd/documents' AS ords_path,
         'Y' AS enabled_yn
  FROM dual
) src
ON (dst.object_id = src.object_id)
WHEN MATCHED THEN UPDATE SET
  dst.owner = src.owner,
  dst.object_name = src.object_name,
  dst.ords_path = src.ords_path,
  dst.enabled_yn = src.enabled_yn
WHEN NOT MATCHED THEN INSERT (object_id, owner, object_name, ords_path, enabled_yn)
VALUES (src.object_id, src.owner, src.object_name, src.ords_path, src.enabled_yn);

MERGE INTO cb_protected_column dst
USING (
  SELECT 1 column_id, 1 object_id, 'DOC_ID' column_name, 'N' sensitive_yn FROM dual UNION ALL
  SELECT 2, 1, 'TITLE', 'N' FROM dual UNION ALL
  SELECT 3, 1, 'OWNER_EMP_NO', 'N' FROM dual UNION ALL
  SELECT 4, 1, 'DEPT_CODE', 'N' FROM dual UNION ALL
  SELECT 5, 1, 'CONTENTS', 'Y' FROM dual
) src
ON (dst.object_id = src.object_id AND dst.column_name = src.column_name)
WHEN MATCHED THEN UPDATE SET dst.sensitive_yn = src.sensitive_yn
WHEN NOT MATCHED THEN INSERT (column_id, object_id, column_name, sensitive_yn)
VALUES (src.column_id, src.object_id, src.column_name, src.sensitive_yn);

MERGE INTO cb_permission_column dst
USING (
  SELECT 300 permission_id, 'CONTENTS' column_name FROM dual
) src
ON (dst.permission_id = src.permission_id AND dst.column_name = src.column_name)
WHEN NOT MATCHED THEN INSERT (permission_id, column_name)
VALUES (src.permission_id, src.column_name);

PROMPT === Replacing CONTENTS redaction with role column permission check ===
CREATE OR REPLACE FUNCTION cb_agent_can_read_column(
  p_target_name IN VARCHAR2,
  p_column_name IN VARCHAR2
) RETURN VARCHAR2
AUTHID DEFINER
AS
  v_allowed NUMBER;
BEGIN
  IF SYS_CONTEXT('CB_AGENT_CTX', 'USER_ID') IS NULL THEN
    RETURN 'N';
  END IF;

  SELECT COUNT(*)
  INTO   v_allowed
  FROM   cb_user_role ur
  JOIN   cb_permission p
  ON     p.role_id = ur.role_id
  JOIN   cb_permission_column pc
  ON     pc.permission_id = p.perm_id
  WHERE  ur.user_id = TO_NUMBER(SYS_CONTEXT('CB_AGENT_CTX', 'USER_ID'))
  AND    p.target_name = UPPER(p_target_name)
  AND    p.action_name = 'SELECT'
  AND    pc.column_name = UPPER(p_column_name);

  RETURN CASE WHEN v_allowed > 0 THEN 'Y' ELSE 'N' END;
END;
/

BEGIN
  DBMS_REDACT.DROP_POLICY(
    object_schema => USER,
    object_name   => 'CB_V_SEARCH_DOCUMENTS',
    policy_name   => 'CB_CONTENTS_REDACT'
  );
EXCEPTION
  WHEN OTHERS THEN NULL;
END;
/

BEGIN
  DBMS_REDACT.ADD_POLICY(
    object_schema => USER,
    object_name   => 'CB_V_SEARCH_DOCUMENTS',
    column_name   => 'CONTENTS',
    policy_name   => 'CB_CONTENTS_REDACT',
    function_type => DBMS_REDACT.NULLIFY,
    expression    => 'SYS_CONTEXT(''CB_AGENT_CTX'', ''CAN_READ_CONTENTS'') IS NULL OR SYS_CONTEXT(''CB_AGENT_CTX'', ''CAN_READ_CONTENTS'') != ''Y'''
  );
END;
/

GRANT EXECUTE ON cb_agent_can_read_column TO cb_ords;

COMMIT;

PROMPT === Backoffice support setup complete ===
EXIT;
