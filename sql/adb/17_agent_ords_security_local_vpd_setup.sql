-- ============================================================
-- 17_agent_ords_security_local_vpd_setup.sql
-- Local-only executable VPD example for Agent ORDS security.
--
-- Scenario:
--   * ORDS connects as CB_ORDS.
--   * ORDS receives Authorization: Bearer <key>.
--   * ORDS handler calls ADMIN.CB_AGENT_CTX_PKG.SET_USER_BY_BEARER.
--   * VPD reads SYS_CONTEXT and filters ADMIN.CB_V_SEARCH_DOCUMENTS.
--   * DBMS_REDACT masks the CONTENTS column unless the key user is allowed.
-- ============================================================
WHENEVER SQLERROR EXIT SQL.SQLCODE
SET ECHO OFF
SET FEEDBACK ON
SET DEFINE OFF

PROMPT === 1. Creating local base table and sample documents ===
CREATE TABLE cb_search_documents (
  doc_id        NUMBER PRIMARY KEY,
  title         VARCHAR2(100) NOT NULL,
  owner_emp_no  VARCHAR2(20)  NOT NULL,
  dept_code     VARCHAR2(20)  NOT NULL,
  contents      VARCHAR2(4000),
  created_at    DATE DEFAULT SYSDATE NOT NULL
);

INSERT INTO cb_search_documents VALUES (1, 'HR payroll guide',       'E1001', 'HR',    'Payroll policy and HR guide', SYSDATE);
INSERT INTO cb_search_documents VALUES (2, 'HR recruiting plan',     'E1002', 'HR',    'Recruiting plan for HR team', SYSDATE);
INSERT INTO cb_search_documents VALUES (3, 'Finance close checklist','E2001', 'FIN',   'Monthly close checklist', SYSDATE);
INSERT INTO cb_search_documents VALUES (4, 'Finance audit memo',     'E2002', 'FIN',   'Audit memo for finance team', SYSDATE);
INSERT INTO cb_search_documents VALUES (5, 'Sales forecast',         'E3001', 'SALES', 'Quarterly sales forecast', SYSDATE);
INSERT INTO cb_search_documents VALUES (6, 'HR benefits notice',     'E1003', 'HR',    'Benefits notice for employees', SYSDATE);

CREATE OR REPLACE VIEW cb_v_search_documents AS
SELECT doc_id,
       title,
       owner_emp_no,
       dept_code,
       contents,
       created_at
FROM   cb_search_documents;

PROMPT === 2. Creating user/role/permission tables ===
CREATE TABLE cb_app_user (
  user_id            NUMBER PRIMARY KEY,
  user_name          VARCHAR2(50) NOT NULL,
  employee_no        VARCHAR2(20) NOT NULL,
  dept_code          VARCHAR2(20) NOT NULL,
  can_read_contents  CHAR(1) DEFAULT 'N' CHECK (can_read_contents IN ('Y','N')) NOT NULL,
  active             CHAR(1) DEFAULT 'Y' CHECK (active IN ('Y','N')) NOT NULL
);

CREATE TABLE cb_app_role (
  role_id               NUMBER PRIMARY KEY,
  role_name             VARCHAR2(50) NOT NULL,
  max_sensitivity_level VARCHAR2(20) DEFAULT 'PUBLIC' NOT NULL
);

CREATE TABLE cb_user_role (
  user_id  NUMBER NOT NULL REFERENCES cb_app_user(user_id),
  role_id  NUMBER NOT NULL REFERENCES cb_app_role(role_id),
  CONSTRAINT cb_user_role_pk PRIMARY KEY (user_id, role_id)
);

CREATE TABLE cb_permission (
  perm_id      NUMBER PRIMARY KEY,
  role_id      NUMBER NOT NULL REFERENCES cb_app_role(role_id),
  target_name  VARCHAR2(128) NOT NULL,
  action_name  VARCHAR2(30)  NOT NULL,
  permission_effect VARCHAR2(10) DEFAULT 'ALLOW' NOT NULL
);

CREATE TABLE cb_permission_rule (
  rule_id     NUMBER PRIMARY KEY,
  perm_id     NUMBER NOT NULL REFERENCES cb_permission(perm_id),
  rule_column VARCHAR2(128),
  rule_type   VARCHAR2(30) NOT NULL,
  rule_value  VARCHAR2(100)
);

CREATE TABLE cb_permission_column (
  permission_id  NUMBER NOT NULL REFERENCES cb_permission(perm_id),
  column_name    VARCHAR2(128) NOT NULL,
  CONSTRAINT cb_permission_column_pk PRIMARY KEY (permission_id, column_name)
);

CREATE TABLE cb_agent_bearer_key (
  key_id      NUMBER PRIMARY KEY,
  user_id     NUMBER NOT NULL REFERENCES cb_app_user(user_id),
  key_hash    VARCHAR2(128) NOT NULL UNIQUE,
  key_prefix  VARCHAR2(16),
  issued_at   DATE DEFAULT SYSDATE NOT NULL,
  expires_at  DATE,
  revoked_at  DATE,
  active      CHAR(1) DEFAULT 'Y' CHECK (active IN ('Y','N')) NOT NULL
);

PROMPT === 3. Seeding permissions ===
INSERT INTO cb_app_user VALUES (101, 'agent_hr',       'E10234', 'HR',  'N', 'Y');
INSERT INTO cb_app_user VALUES (102, 'agent_fin_self', 'E2001',  'FIN', 'N', 'Y');
INSERT INTO cb_app_user VALUES (103, 'agent_all',      'E99999', 'HQ',  'Y', 'Y');

INSERT INTO cb_app_role VALUES (10, 'HR_DEPT_ROLE', 'INTERNAL');
INSERT INTO cb_app_role VALUES (20, 'FIN_SELF_ROLE', 'INTERNAL');
INSERT INTO cb_app_role VALUES (30, 'ALL_DOC_ROLE', 'CONFIDENTIAL');

INSERT INTO cb_user_role VALUES (101, 10);
INSERT INTO cb_user_role VALUES (102, 20);
INSERT INTO cb_user_role VALUES (103, 30);

INSERT INTO cb_permission VALUES (100, 10, 'CB_V_SEARCH_DOCUMENTS', 'SELECT', 'ALLOW');
INSERT INTO cb_permission VALUES (200, 20, 'CB_V_SEARCH_DOCUMENTS', 'SELECT', 'ALLOW');
INSERT INTO cb_permission VALUES (300, 30, 'CB_V_SEARCH_DOCUMENTS', 'SELECT', 'ALLOW');

INSERT INTO cb_permission_rule VALUES (1000, 100, 'MY_DEPT', NULL);
INSERT INTO cb_permission_rule VALUES (2000, 200, 'SELF',    NULL);
INSERT INTO cb_permission_rule VALUES (3000, 300, 'ALL',     NULL);

INSERT INTO cb_permission_column VALUES (300, 'CONTENTS');

INSERT INTO cb_agent_bearer_key(key_id, user_id, key_hash, key_prefix)
VALUES (1, 101, STANDARD_HASH('cb_hr_key',  'SHA256'), 'cb_hr');
INSERT INTO cb_agent_bearer_key(key_id, user_id, key_hash, key_prefix)
VALUES (2, 102, STANDARD_HASH('cb_fin_key', 'SHA256'), 'cb_fin');
INSERT INTO cb_agent_bearer_key(key_id, user_id, key_hash, key_prefix)
VALUES (3, 103, STANDARD_HASH('cb_all_key', 'SHA256'), 'cb_all');

COMMIT;

PROMPT === 4. Creating secure application context package ===
CREATE OR REPLACE PACKAGE cb_agent_ctx_pkg AUTHID DEFINER AS
  PROCEDURE clear_user;
  PROCEDURE set_user(p_user_id IN NUMBER);
  PROCEDURE set_user_values(
    p_user_id           IN NUMBER,
    p_emp_no            IN VARCHAR2,
    p_dept_code         IN VARCHAR2,
    p_can_read_contents IN VARCHAR2
  );
  PROCEDURE set_user_by_bearer(p_bearer_key IN VARCHAR2);
END;
/

CREATE OR REPLACE CONTEXT cb_agent_ctx USING cb_agent_ctx_pkg;

CREATE OR REPLACE PACKAGE BODY cb_agent_ctx_pkg AS
  PROCEDURE clear_user AS
  BEGIN
    DBMS_SESSION.SET_CONTEXT('CB_AGENT_CTX', 'USER_ID', NULL);
    DBMS_SESSION.SET_CONTEXT('CB_AGENT_CTX', 'EMP_NO', NULL);
    DBMS_SESSION.SET_CONTEXT('CB_AGENT_CTX', 'DEPT_CODE', NULL);
    DBMS_SESSION.SET_CONTEXT('CB_AGENT_CTX', 'CAN_READ_CONTENTS', NULL);
  END;

  PROCEDURE set_user_values(
    p_user_id           IN NUMBER,
    p_emp_no            IN VARCHAR2,
    p_dept_code         IN VARCHAR2,
    p_can_read_contents IN VARCHAR2
  ) AS
  BEGIN
    DBMS_SESSION.SET_CONTEXT('CB_AGENT_CTX', 'USER_ID', TO_CHAR(p_user_id));
    DBMS_SESSION.SET_CONTEXT('CB_AGENT_CTX', 'EMP_NO', p_emp_no);
    DBMS_SESSION.SET_CONTEXT('CB_AGENT_CTX', 'DEPT_CODE', p_dept_code);
    DBMS_SESSION.SET_CONTEXT('CB_AGENT_CTX', 'CAN_READ_CONTENTS', p_can_read_contents);
  END;

  PROCEDURE set_user(p_user_id IN NUMBER) AS
    v_emp_no            cb_app_user.employee_no%TYPE;
    v_dept_code         cb_app_user.dept_code%TYPE;
    v_can_read_contents cb_app_user.can_read_contents%TYPE;
  BEGIN
    SELECT employee_no,
           dept_code,
           CASE
             WHEN EXISTS (
               SELECT 1
               FROM   cb_user_role ur
               JOIN   cb_permission p
               ON     p.role_id = ur.role_id
               JOIN   cb_permission_column pc
               ON     pc.permission_id = p.perm_id
               WHERE  ur.user_id = p_user_id
               AND    p.target_name = 'CB_V_SEARCH_DOCUMENTS'
               AND    p.action_name = 'SELECT'
               AND    pc.column_name = 'CONTENTS'
             ) THEN 'Y'
             ELSE 'N'
           END
    INTO   v_emp_no, v_dept_code, v_can_read_contents
    FROM   cb_app_user
    WHERE  user_id = p_user_id
    AND    active = 'Y';

    set_user_values(p_user_id, v_emp_no, v_dept_code, v_can_read_contents);
  EXCEPTION
    WHEN NO_DATA_FOUND THEN
      clear_user;
      RAISE_APPLICATION_ERROR(-20003, 'Mapped application user not found');
  END;

  PROCEDURE set_user_by_bearer(p_bearer_key IN VARCHAR2) AS
    v_user_id           cb_app_user.user_id%TYPE;
    v_emp_no            cb_app_user.employee_no%TYPE;
    v_dept_code         cb_app_user.dept_code%TYPE;
    v_can_read_contents cb_app_user.can_read_contents%TYPE;
  BEGIN
    IF p_bearer_key IS NULL THEN
      clear_user;
      RAISE_APPLICATION_ERROR(-20001, 'Authorization Bearer key is required');
    END IF;

    SELECT u.user_id,
           u.employee_no,
           u.dept_code,
           CASE
             WHEN EXISTS (
               SELECT 1
               FROM   cb_user_role ur
               JOIN   cb_permission p
               ON     p.role_id = ur.role_id
               JOIN   cb_permission_column pc
               ON     pc.permission_id = p.perm_id
               WHERE  ur.user_id = u.user_id
               AND    p.target_name = 'CB_V_SEARCH_DOCUMENTS'
               AND    p.action_name = 'SELECT'
               AND    pc.column_name = 'CONTENTS'
             ) THEN 'Y'
             ELSE 'N'
           END
    INTO   v_user_id, v_emp_no, v_dept_code, v_can_read_contents
    FROM   cb_agent_bearer_key k
    JOIN   cb_app_user u
    ON     u.user_id = k.user_id
    WHERE  k.key_hash = STANDARD_HASH(p_bearer_key, 'SHA256')
    AND    k.active = 'Y'
    AND    k.revoked_at IS NULL
    AND    (k.expires_at IS NULL OR k.expires_at > SYSDATE)
    AND    u.active = 'Y';

    set_user_values(v_user_id, v_emp_no, v_dept_code, v_can_read_contents);
  EXCEPTION
    WHEN NO_DATA_FOUND THEN
      clear_user;
      RAISE_APPLICATION_ERROR(-20002, 'Invalid or expired Bearer key');
  END;
END;
/

SHOW ERRORS

PROMPT === 5. Creating VPD policy function ===
CREATE OR REPLACE FUNCTION cb_agent_doc_vpd_filter(
  p_schema IN VARCHAR2,
  p_object IN VARCHAR2
) RETURN VARCHAR2
AUTHID DEFINER
AS
  v_user_id    NUMBER;
  v_target     VARCHAR2(128);
  v_allow_predicate VARCHAR2(32767);
  v_deny_predicate  VARCHAR2(32767);

  FUNCTION quote_literal(p_value IN VARCHAR2) RETURN VARCHAR2 IS
  BEGIN
    RETURN '''' || REPLACE(NVL(p_value, ''), '''', '''''') || '''';
  END;

  FUNCTION safe_column(
    p_owner       IN VARCHAR2,
    p_table_name  IN VARCHAR2,
    p_column_name IN VARCHAR2
  ) RETURN VARCHAR2 IS
    v_column VARCHAR2(128);
    v_count  NUMBER;
  BEGIN
    IF p_column_name IS NULL THEN
      RETURN NULL;
    END IF;

    v_column := DBMS_ASSERT.SIMPLE_SQL_NAME(UPPER(TRIM(p_column_name)));

    SELECT COUNT(*)
    INTO   v_count
    FROM   all_tab_columns
    WHERE  owner = UPPER(p_owner)
    AND    table_name = UPPER(p_table_name)
    AND    column_name = v_column;

    IF v_count = 0 THEN
      RETURN NULL;
    END IF;

    RETURN v_column;
  EXCEPTION
    WHEN OTHERS THEN
      RETURN NULL;
  END;

  PROCEDURE append_or(p_effect IN VARCHAR2, p_clause IN VARCHAR2) IS
  BEGIN
    IF p_clause IS NULL THEN
      RETURN;
    END IF;

    IF p_effect = 'DENY' THEN
      IF v_deny_predicate IS NULL THEN
        v_deny_predicate := '(' || p_clause || ')';
      ELSE
        v_deny_predicate := v_deny_predicate || ' OR (' || p_clause || ')';
      END IF;
    ELSIF v_allow_predicate IS NULL THEN
      v_allow_predicate := '(' || p_clause || ')';
    ELSE
      v_allow_predicate := v_allow_predicate || ' OR (' || p_clause || ')';
    END IF;
  END;
BEGIN
  BEGIN
    v_user_id := TO_NUMBER(SYS_CONTEXT('CB_AGENT_CTX', 'USER_ID'));
  EXCEPTION
    WHEN OTHERS THEN
      RETURN '1 = 0';
  END;

  IF v_user_id IS NULL OR p_schema IS NULL OR p_object IS NULL THEN
    RETURN '1 = 0';
  END IF;

  v_target := UPPER(TRIM(p_object));

  FOR r IN (
    SELECT UPPER(TRIM(r.rule_type)) AS rule_type,
           UPPER(TRIM(r.rule_column)) AS rule_column,
           NVL(UPPER(TRIM(p.permission_effect)), 'ALLOW') AS permission_effect,
           r.rule_value
    FROM   cb_user_role ur
    JOIN   cb_permission p
    ON     p.role_id = ur.role_id
    JOIN   cb_permission_rule r
    ON     r.perm_id = p.perm_id
    WHERE  ur.user_id = v_user_id
    AND    p.target_name = v_target
    AND    p.action_name = 'SELECT'
    ORDER BY r.rule_id
  ) LOOP
    DECLARE
      v_column VARCHAR2(128);
    BEGIN
      IF r.rule_type = 'ALL' THEN
        IF r.permission_effect = 'DENY' THEN
          RETURN '1 = 0';
        END IF;
        v_allow_predicate := '(1 = 1)';
      ELSIF r.rule_type = 'MY_DEPT' THEN
        v_column := safe_column(p_schema, p_object, NVL(r.rule_column, 'DEPT_CODE'));
        IF v_column IS NOT NULL THEN
          append_or(r.permission_effect, v_column || ' = SYS_CONTEXT(''CB_AGENT_CTX'', ''DEPT_CODE'')');
        END IF;
      ELSIF r.rule_type = 'SELF' THEN
        v_column := safe_column(p_schema, p_object, NVL(r.rule_column, 'OWNER_EMP_NO'));
        IF v_column IS NOT NULL THEN
          append_or(r.permission_effect, v_column || ' = SYS_CONTEXT(''CB_AGENT_CTX'', ''EMP_NO'')');
        END IF;
      ELSIF r.rule_type = 'DEPT' THEN
        v_column := safe_column(p_schema, p_object, NVL(r.rule_column, 'DEPT_CODE'));
        IF v_column IS NOT NULL THEN
          append_or(r.permission_effect, v_column || ' = ' || quote_literal(r.rule_value));
        END IF;
      ELSIF r.rule_type = 'EMP_NO' THEN
        v_column := safe_column(p_schema, p_object, NVL(r.rule_column, 'OWNER_EMP_NO'));
        IF v_column IS NOT NULL THEN
          append_or(r.permission_effect, v_column || ' = ' || quote_literal(r.rule_value));
        END IF;
      ELSIF r.rule_type = '=' THEN
        v_column := safe_column(p_schema, p_object, r.rule_column);
        IF v_column IS NOT NULL THEN
          append_or(r.permission_effect, 'TO_CHAR(' || v_column || ') = ' || quote_literal(r.rule_value));
        END IF;
      ELSIF r.rule_type IN ('!=', '<>') THEN
        v_column := safe_column(p_schema, p_object, r.rule_column);
        IF v_column IS NOT NULL THEN
          append_or(r.permission_effect, 'TO_CHAR(' || v_column || ') <> ' || quote_literal(r.rule_value));
        END IF;
      END IF;
    END;
  END LOOP;

  IF v_allow_predicate IS NULL THEN
    RETURN '1 = 0';
  END IF;

  IF v_deny_predicate IS NULL THEN
    IF v_allow_predicate = '(1 = 1)' THEN
      RETURN '1 = 1';
    END IF;
    RETURN '(' || v_allow_predicate || ')';
  END IF;

  RETURN '((' || v_allow_predicate || ') AND NOT (' || v_deny_predicate || '))';
END;
/

SHOW ERRORS

PROMPT === 6. Attaching VPD policy to the protected view ===
BEGIN
  DBMS_RLS.ADD_POLICY(
    object_schema   => USER,
    object_name     => 'CB_V_SEARCH_DOCUMENTS',
    policy_name     => 'CB_AGENT_DOC_POLICY',
    function_schema => USER,
    policy_function => 'CB_AGENT_DOC_VPD_FILTER',
    statement_types => 'SELECT',
    enable          => TRUE
  );
END;
/

PROMPT === 7. Adding column redaction on CONTENTS ===
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

SHOW ERRORS

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

PROMPT === 8. Creating ORDS runtime schema ===
CREATE USER cb_ords IDENTIFIED BY "CbOrdS#2026Local1";
GRANT CREATE SESSION TO cb_ords;
GRANT CREATE PROCEDURE TO cb_ords;
GRANT SELECT ON cb_v_search_documents TO cb_ords;
GRANT EXECUTE ON cb_agent_ctx_pkg TO cb_ords;
GRANT EXECUTE ON cb_agent_can_read_column TO cb_ords;

PROMPT === Local VPD setup complete ===
EXIT;
