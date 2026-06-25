-- ============================================================
-- 27_agent_ords_security_dynamic_vpd_filter_test.sql
-- Unit-style checks for cb_agent_doc_vpd_filter.
--
-- Run after:
--   @sql/adb/26_agent_ords_security_dynamic_vpd_filter.sql
-- ============================================================
WHENEVER SQLERROR EXIT SQL.SQLCODE
SET ECHO OFF
SET FEEDBACK ON
SET DEFINE OFF
SET SERVEROUTPUT ON

PROMPT === Preparing dynamic VPD filter unit test data ===

BEGIN
  cb_agent_ctx_pkg.clear_user;

  DELETE FROM cb_permission_rule
  WHERE perm_id BETWEEN 456000 AND 456999;

  DELETE FROM cb_permission
  WHERE perm_id BETWEEN 456000 AND 456999;

  DELETE FROM cb_user_role
  WHERE role_id BETWEEN 456000 AND 456999;

  DELETE FROM cb_app_role
  WHERE role_id BETWEEN 456000 AND 456999;

  DELETE FROM cb_app_user
  WHERE user_id BETWEEN 456000 AND 456999;

  INSERT INTO cb_app_user(user_id, user_name, employee_no, dept_code, can_read_contents, active)
  VALUES (456101, 'rule_test_hr', 'E456101', 'HR', 'N', 'Y');

  INSERT INTO cb_app_user(user_id, user_name, employee_no, dept_code, can_read_contents, active)
  VALUES (456102, 'rule_test_self', 'E2001', 'FIN', 'N', 'Y');

  INSERT INTO cb_app_user(user_id, user_name, employee_no, dept_code, can_read_contents, active)
  VALUES (456103, 'rule_test_all', 'E456103', 'HQ', 'Y', 'Y');

  INSERT INTO cb_app_user(user_id, user_name, employee_no, dept_code, can_read_contents, active)
  VALUES (456104, 'rule_test_eq', 'E456104', 'QA', 'N', 'Y');

  INSERT INTO cb_app_user(user_id, user_name, employee_no, dept_code, can_read_contents, active)
  VALUES (456105, 'rule_test_ne', 'E456105', 'QA', 'N', 'Y');

  INSERT INTO cb_app_user(user_id, user_name, employee_no, dept_code, can_read_contents, active)
  VALUES (456106, 'rule_test_bad_column', 'E456106', 'QA', 'N', 'Y');

  INSERT INTO cb_app_user(user_id, user_name, employee_no, dept_code, can_read_contents, active)
  VALUES (456107, 'rule_test_quote_value', 'E456107', 'QA', 'N', 'Y');

  INSERT INTO cb_app_user(user_id, user_name, employee_no, dept_code, can_read_contents, active)
  VALUES (456108, 'rule_test_or_injection', 'E456108', 'QA', 'N', 'Y');

  INSERT INTO cb_app_user(user_id, user_name, employee_no, dept_code, can_read_contents, active)
  VALUES (456109, 'rule_test_column_injection', 'E456109', 'QA', 'N', 'Y');

  INSERT INTO cb_app_user(user_id, user_name, employee_no, dept_code, can_read_contents, active)
  VALUES (456110, 'rule_test_target_mismatch', 'E456110', 'QA', 'N', 'Y');

  INSERT INTO cb_app_role(role_id, role_name) VALUES (456101, 'RULE_TEST_MY_DEPT');
  INSERT INTO cb_app_role(role_id, role_name) VALUES (456102, 'RULE_TEST_SELF');
  INSERT INTO cb_app_role(role_id, role_name) VALUES (456103, 'RULE_TEST_ALL');
  INSERT INTO cb_app_role(role_id, role_name) VALUES (456104, 'RULE_TEST_EQ');
  INSERT INTO cb_app_role(role_id, role_name) VALUES (456105, 'RULE_TEST_NE');
  INSERT INTO cb_app_role(role_id, role_name) VALUES (456106, 'RULE_TEST_BAD_COLUMN');
  INSERT INTO cb_app_role(role_id, role_name) VALUES (456107, 'RULE_TEST_QUOTE_VALUE');
  INSERT INTO cb_app_role(role_id, role_name) VALUES (456108, 'RULE_TEST_OR_INJECTION');
  INSERT INTO cb_app_role(role_id, role_name) VALUES (456109, 'RULE_TEST_COLUMN_INJECTION');
  INSERT INTO cb_app_role(role_id, role_name) VALUES (456110, 'RULE_TEST_TARGET_MISMATCH');

  INSERT INTO cb_user_role(user_id, role_id) VALUES (456101, 456101);
  INSERT INTO cb_user_role(user_id, role_id) VALUES (456102, 456102);
  INSERT INTO cb_user_role(user_id, role_id) VALUES (456103, 456103);
  INSERT INTO cb_user_role(user_id, role_id) VALUES (456104, 456104);
  INSERT INTO cb_user_role(user_id, role_id) VALUES (456105, 456105);
  INSERT INTO cb_user_role(user_id, role_id) VALUES (456106, 456106);
  INSERT INTO cb_user_role(user_id, role_id) VALUES (456107, 456107);
  INSERT INTO cb_user_role(user_id, role_id) VALUES (456108, 456108);
  INSERT INTO cb_user_role(user_id, role_id) VALUES (456109, 456109);
  INSERT INTO cb_user_role(user_id, role_id) VALUES (456110, 456110);

  INSERT INTO cb_permission(perm_id, role_id, target_name, action_name)
  VALUES (456101, 456101, 'CB_V_SEARCH_DOCUMENTS', 'SELECT');

  INSERT INTO cb_permission(perm_id, role_id, target_name, action_name)
  VALUES (456102, 456102, 'CB_V_SEARCH_DOCUMENTS', 'SELECT');

  INSERT INTO cb_permission(perm_id, role_id, target_name, action_name)
  VALUES (456103, 456103, 'CB_V_SEARCH_DOCUMENTS', 'SELECT');

  INSERT INTO cb_permission(perm_id, role_id, target_name, action_name)
  VALUES (456104, 456104, 'CB_V_SEARCH_DOCUMENTS', 'SELECT');

  INSERT INTO cb_permission(perm_id, role_id, target_name, action_name)
  VALUES (456105, 456105, 'CB_V_SEARCH_DOCUMENTS', 'SELECT');

  INSERT INTO cb_permission(perm_id, role_id, target_name, action_name)
  VALUES (456106, 456106, 'CB_V_SEARCH_DOCUMENTS', 'SELECT');

  INSERT INTO cb_permission(perm_id, role_id, target_name, action_name)
  VALUES (456107, 456107, 'CB_V_SEARCH_DOCUMENTS', 'SELECT');

  INSERT INTO cb_permission(perm_id, role_id, target_name, action_name)
  VALUES (456108, 456108, 'CB_V_SEARCH_DOCUMENTS', 'SELECT');

  INSERT INTO cb_permission(perm_id, role_id, target_name, action_name)
  VALUES (456109, 456109, 'CB_V_SEARCH_DOCUMENTS', 'SELECT');

  INSERT INTO cb_permission(perm_id, role_id, target_name, action_name)
  VALUES (456110, 456110, 'BOARD_POSTS', 'SELECT');

  INSERT INTO cb_permission_rule(rule_id, perm_id, rule_column, rule_type, rule_value)
  VALUES (456101, 456101, NULL, 'MY_DEPT', NULL);

  INSERT INTO cb_permission_rule(rule_id, perm_id, rule_column, rule_type, rule_value)
  VALUES (456102, 456102, NULL, 'SELF', NULL);

  INSERT INTO cb_permission_rule(rule_id, perm_id, rule_column, rule_type, rule_value)
  VALUES (456103, 456103, NULL, 'ALL', NULL);

  INSERT INTO cb_permission_rule(rule_id, perm_id, rule_column, rule_type, rule_value)
  VALUES (456104, 456104, 'DOC_ID', '=', '1');

  INSERT INTO cb_permission_rule(rule_id, perm_id, rule_column, rule_type, rule_value)
  VALUES (456105, 456105, 'DEPT_CODE', '!=', 'HR');

  INSERT INTO cb_permission_rule(rule_id, perm_id, rule_column, rule_type, rule_value)
  VALUES (456106, 456106, 'NO_SUCH_COLUMN', '=', 'x');

  INSERT INTO cb_permission_rule(rule_id, perm_id, rule_column, rule_type, rule_value)
  VALUES (456107, 456107, 'TITLE', '=', 'Bob''s plan');

  INSERT INTO cb_permission_rule(rule_id, perm_id, rule_column, rule_type, rule_value)
  VALUES (456108, 456108, 'TITLE', '=', 'HR'' OR ''1''=''1'' --');

  INSERT INTO cb_permission_rule(rule_id, perm_id, rule_column, rule_type, rule_value)
  VALUES (456109, 456109, 'DOC_ID) OR 1=1 --', '=', '1');

  INSERT INTO cb_permission_rule(rule_id, perm_id, rule_column, rule_type, rule_value)
  VALUES (456110, 456110, NULL, 'ALL', NULL);

  COMMIT;
END;
/

PROMPT === Running dynamic VPD filter unit checks ===

DECLARE
  PROCEDURE fail(p_label IN VARCHAR2, p_expected IN VARCHAR2, p_actual IN VARCHAR2) IS
  BEGIN
    RAISE_APPLICATION_ERROR(
      -20456,
      p_label || ' expected [' || p_expected || '] actual [' || p_actual || ']'
    );
  END;

  PROCEDURE assert_equals_for_object(
    p_label    IN VARCHAR2,
    p_user_id  IN NUMBER,
    p_object   IN VARCHAR2,
    p_expected IN VARCHAR2
  ) IS
    v_actual VARCHAR2(32767);
  BEGIN
    IF p_user_id IS NULL THEN
      cb_agent_ctx_pkg.clear_user;
    ELSE
      cb_agent_ctx_pkg.set_user(p_user_id);
    END IF;

    v_actual := cb_agent_doc_vpd_filter('ADMIN', p_object);

    IF v_actual != p_expected THEN
      fail(p_label, p_expected, v_actual);
    END IF;

    DBMS_OUTPUT.PUT_LINE('PASS ' || p_label || ': ' || v_actual);
  END;

  PROCEDURE assert_equals(
    p_label    IN VARCHAR2,
    p_user_id  IN NUMBER,
    p_expected IN VARCHAR2
  ) IS
  BEGIN
    assert_equals_for_object(p_label, p_user_id, 'CB_V_SEARCH_DOCUMENTS', p_expected);
  END;

  PROCEDURE assert_contains_for_object(
    p_label    IN VARCHAR2,
    p_user_id  IN NUMBER,
    p_object   IN VARCHAR2,
    p_expected IN VARCHAR2
  ) IS
    v_actual VARCHAR2(32767);
  BEGIN
    cb_agent_ctx_pkg.set_user(p_user_id);
    v_actual := cb_agent_doc_vpd_filter('ADMIN', p_object);

    IF INSTR(v_actual, p_expected) = 0 THEN
      fail(p_label, p_expected, v_actual);
    END IF;

    DBMS_OUTPUT.PUT_LINE('PASS ' || p_label || ': ' || v_actual);
  END;

  PROCEDURE assert_contains(
    p_label    IN VARCHAR2,
    p_user_id  IN NUMBER,
    p_expected IN VARCHAR2
  ) IS
  BEGIN
    assert_contains_for_object(p_label, p_user_id, 'CB_V_SEARCH_DOCUMENTS', p_expected);
  END;
BEGIN
  assert_equals('NO_CONTEXT_DENIES', NULL, '1 = 0');
  assert_contains('MY_DEPT_DEFAULT_COLUMN', 456101, 'DEPT_CODE = SYS_CONTEXT(''CB_AGENT_CTX'', ''DEPT_CODE'')');
  assert_contains('SELF_DEFAULT_COLUMN', 456102, 'OWNER_EMP_NO = SYS_CONTEXT(''CB_AGENT_CTX'', ''EMP_NO'')');
  assert_equals('ALL_ALLOWS', 456103, '1 = 1');
  assert_contains('EQUALS_RULE', 456104, 'TO_CHAR(DOC_ID) = ''1''');
  assert_contains('NOT_EQUALS_RULE', 456105, 'TO_CHAR(DEPT_CODE) <> ''HR''');
  assert_equals('BAD_COLUMN_DENIES', 456106, '1 = 0');
  assert_contains('QUOTE_VALUE_ESCAPED', 456107, 'TO_CHAR(TITLE) = ''Bob''''s plan''');
  assert_contains('OR_INJECTION_STAYS_LITERAL', 456108, 'TO_CHAR(TITLE) = ''HR'''' OR ''''1''''=''''1'''' --''');
  assert_equals('COLUMN_INJECTION_DENIES', 456109, '1 = 0');
  assert_equals_for_object('TARGET_MISMATCH_DENIES', 456110, 'CB_V_SEARCH_DOCUMENTS', '1 = 0');
  assert_equals_for_object('TARGET_MATCH_ALL_ALLOWS', 456110, 'BOARD_POSTS', '1 = 1');
  cb_agent_ctx_pkg.clear_user;
END;
/

PROMPT === Cleaning dynamic VPD filter unit test data ===

BEGIN
  cb_agent_ctx_pkg.clear_user;

  DELETE FROM cb_permission_rule
  WHERE perm_id BETWEEN 456000 AND 456999;

  DELETE FROM cb_permission
  WHERE perm_id BETWEEN 456000 AND 456999;

  DELETE FROM cb_user_role
  WHERE role_id BETWEEN 456000 AND 456999;

  DELETE FROM cb_app_role
  WHERE role_id BETWEEN 456000 AND 456999;

  DELETE FROM cb_app_user
  WHERE user_id BETWEEN 456000 AND 456999;

  COMMIT;
END;
/

PROMPT === Dynamic VPD filter unit checks passed ===
