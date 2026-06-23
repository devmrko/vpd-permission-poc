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
  role_id    NUMBER PRIMARY KEY,
  role_name  VARCHAR2(50) NOT NULL
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
  action_name  VARCHAR2(30)  NOT NULL
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

INSERT INTO cb_app_role VALUES (10, 'HR_DEPT_ROLE');
INSERT INTO cb_app_role VALUES (20, 'FIN_SELF_ROLE');
INSERT INTO cb_app_role VALUES (30, 'ALL_DOC_ROLE');

INSERT INTO cb_user_role VALUES (101, 10);
INSERT INTO cb_user_role VALUES (102, 20);
INSERT INTO cb_user_role VALUES (103, 30);

INSERT INTO cb_permission VALUES (100, 10, 'CB_V_SEARCH_DOCUMENTS', 'SELECT');
INSERT INTO cb_permission VALUES (200, 20, 'CB_V_SEARCH_DOCUMENTS', 'SELECT');
INSERT INTO cb_permission VALUES (300, 30, 'CB_V_SEARCH_DOCUMENTS', 'SELECT');

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
  v_user_id     VARCHAR2(30);
  v_target_name VARCHAR2(128);
BEGIN
  v_user_id := SYS_CONTEXT('CB_AGENT_CTX', 'USER_ID');

  IF v_user_id IS NULL THEN
    RETURN '1 = 0';
  END IF;

  v_target_name := REPLACE(UPPER(p_object), '''', '''''');

  RETURN
    'EXISTS (
       SELECT 1
       FROM   admin.cb_user_role ur
       JOIN   admin.cb_permission p
       ON     p.role_id = ur.role_id
       JOIN   admin.cb_permission_rule r
       ON     r.perm_id = p.perm_id
       WHERE  ur.user_id = TO_NUMBER(SYS_CONTEXT(''CB_AGENT_CTX'', ''USER_ID''))
       AND    p.target_name = ''' || v_target_name || '''
       AND    p.action_name = ''SELECT''
       AND   (
               r.rule_type = ''ALL''
               OR (
                    r.rule_type = ''MY_DEPT''
                    AND dept_code = SYS_CONTEXT(''CB_AGENT_CTX'', ''DEPT_CODE'')
                  )
               OR (
                    r.rule_type = ''DEPT''
                    AND r.rule_value = dept_code
                  )
               OR (
                    r.rule_type = ''SELF''
                    AND owner_emp_no = SYS_CONTEXT(''CB_AGENT_CTX'', ''EMP_NO'')
                  )
               OR (
                    r.rule_type = ''EMP_NO''
                    AND r.rule_value = owner_emp_no
                  )
               OR (
                    r.rule_type = ''=''
                    AND (
                         (r.rule_column = ''DOC_ID'' AND TO_CHAR(doc_id) = r.rule_value)
                         OR (r.rule_column = ''TITLE'' AND title = r.rule_value)
                         OR (r.rule_column = ''OWNER_EMP_NO'' AND owner_emp_no = r.rule_value)
                         OR (r.rule_column = ''DEPT_CODE'' AND dept_code = r.rule_value)
                    )
                  )
               OR (
                    r.rule_type = ''!=''
                    AND (
                         (r.rule_column = ''DOC_ID'' AND TO_CHAR(doc_id) != r.rule_value)
                         OR (r.rule_column = ''TITLE'' AND title != r.rule_value)
                         OR (r.rule_column = ''OWNER_EMP_NO'' AND owner_emp_no != r.rule_value)
                         OR (r.rule_column = ''DEPT_CODE'' AND dept_code != r.rule_value)
                    )
                  )
             )
     )';
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
