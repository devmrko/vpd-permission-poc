-- ============================================================
-- 22_agent_ords_security_ords_handler_setup.sql
-- ORDS module/handler package for Agent ORDS security.
--
-- Run as CB_ORDS after:
--   * ADMIN VPD setup
--   * ADMIN DDS setup
--   * ADMIN ORDS schema enablement
--
-- Purpose:
--   * Common package: Authorization header -> Bearer key -> SYS_CONTEXT.
--   * Object query handlers: call the common package, then query their
--     own protected table/view.
--   * VPD demo path: a fixed demo handler for ADMIN.CB_V_SEARCH_DOCUMENTS.
--   * DDS probe: Authorization header -> local mapping only. This does not
--     attach a DDS EndUserSecurityContext, so DDS cannot treat the request
--     as the mapped END USER. It is not a protected object query handler.
-- ============================================================
WHENEVER SQLERROR EXIT SQL.SQLCODE
SET ECHO OFF
SET FEEDBACK ON

PROMPT === Creating CB_ORDS handler package ===
CREATE OR REPLACE PACKAGE cb_ords_handler_pkg AUTHID DEFINER AS
  PROCEDURE set_vpd_context(p_authorization IN VARCHAR2);
  PROCEDURE clear_vpd_context;
  FUNCTION vpd_search_json(p_authorization IN VARCHAR2) RETURN CLOB;
  FUNCTION dds_bearer_probe_json(p_authorization IN VARCHAR2) RETURN CLOB;
END;
/

CREATE OR REPLACE PACKAGE BODY cb_ords_handler_pkg AS
  FUNCTION extract_bearer_key(p_authorization IN VARCHAR2) RETURN VARCHAR2 AS
    v_auth VARCHAR2(4000);
  BEGIN
    v_auth := TRIM(p_authorization);

    IF v_auth IS NULL OR LOWER(SUBSTR(v_auth, 1, 7)) != 'bearer ' THEN
      RAISE_APPLICATION_ERROR(-20101, 'Authorization header must be Bearer <key>');
    END IF;

    RETURN TRIM(SUBSTR(v_auth, 8));
  END;

  FUNCTION mapped_dds_end_user(p_bearer_key IN VARCHAR2) RETURN VARCHAR2 AS
  BEGIN
    CASE p_bearer_key
      WHEN 'cb_hr_key'  THEN RETURN 'cb_dds_hr';
      WHEN 'cb_fin_key' THEN RETURN 'cb_dds_fin';
      WHEN 'cb_all_key' THEN RETURN 'cb_dds_all';
      ELSE RETURN NULL;
    END CASE;
  END;

  PROCEDURE set_vpd_context(p_authorization IN VARCHAR2) AS
    v_bearer_key VARCHAR2(4000);
  BEGIN
    admin.cb_agent_ctx_pkg.clear_user;
    v_bearer_key := extract_bearer_key(p_authorization);
    admin.cb_agent_ctx_pkg.set_user_by_bearer(v_bearer_key);
  END;

  PROCEDURE clear_vpd_context AS
  BEGIN
    admin.cb_agent_ctx_pkg.clear_user;
  END;

  FUNCTION vpd_search_json(p_authorization IN VARCHAR2) RETURN CLOB AS
    v_rows_json  CLOB;
    v_response   CLOB;
    v_context_read_contents VARCHAR2(1);
  BEGIN
    set_vpd_context(p_authorization);

    SELECT JSON_ARRAYAGG(
             JSON_OBJECT(
               'doc_id'       VALUE doc_id,
               'title'        VALUE title,
               'owner_emp_no' VALUE owner_emp_no,
               'dept_code'    VALUE dept_code,
               'contents'     VALUE contents
               RETURNING CLOB
             )
             ORDER BY doc_id
             RETURNING CLOB
           )
    INTO   v_rows_json
    FROM   admin.cb_v_search_documents;

    IF v_rows_json IS NULL THEN
      v_rows_json := '[]';
    END IF;

    v_context_read_contents := admin.cb_agent_can_read_column('CB_V_SEARCH_DOCUMENTS', 'CONTENTS');

    SELECT JSON_OBJECT(
             'scenario'              VALUE 'VPD_BEARER_HEADER',
             'db_user'               VALUE SYS_CONTEXT('USERENV', 'SESSION_USER'),
             'context_user_id'       VALUE SYS_CONTEXT('CB_AGENT_CTX', 'USER_ID'),
             'context_emp_no'        VALUE SYS_CONTEXT('CB_AGENT_CTX', 'EMP_NO'),
             'context_dept_code'     VALUE SYS_CONTEXT('CB_AGENT_CTX', 'DEPT_CODE'),
             'context_read_contents' VALUE v_context_read_contents,
             'rows'                  VALUE v_rows_json FORMAT JSON
             RETURNING CLOB
           )
    INTO   v_response
    FROM   dual;

    clear_vpd_context;
    RETURN v_response;
  EXCEPTION
    WHEN OTHERS THEN
      clear_vpd_context;
      RAISE;
  END;

  FUNCTION dds_bearer_probe_json(p_authorization IN VARCHAR2) RETURN CLOB AS
    v_bearer_key           VARCHAR2(4000);
    v_mapped_end_user      VARCHAR2(128);
    v_dds_context_username VARCHAR2(128);
    v_dds_context_error    VARCHAR2(4000);
    v_rows_visible         NUMBER;
    v_query_error          VARCHAR2(4000);
    v_result               VARCHAR2(40);
    v_response             CLOB;
  BEGIN
    v_bearer_key := extract_bearer_key(p_authorization);
    v_mapped_end_user := mapped_dds_end_user(v_bearer_key);

    BEGIN
      SELECT ORA_END_USER_CONTEXT.username
      INTO   v_dds_context_username
      FROM   dual;
    EXCEPTION
      WHEN OTHERS THEN
        v_dds_context_error := SQLERRM;
    END;

    BEGIN
      EXECUTE IMMEDIATE
        'SELECT COUNT(*) FROM admin.cb_dds_v_search_documents'
      INTO v_rows_visible;

      v_result := 'UNEXPECTED_ACCESS';
    EXCEPTION
      WHEN OTHERS THEN
        v_query_error := SQLERRM;
        v_result := 'EXPECTED_BLOCKED';
    END;

    SELECT JSON_OBJECT(
             'scenario'              VALUE 'DDS_BEARER_HANDLER_PROBE',
             'db_user'               VALUE SYS_CONTEXT('USERENV', 'SESSION_USER'),
             'bearer_mapped_end_user' VALUE v_mapped_end_user,
             'dds_context_username'  VALUE v_dds_context_username,
             'dds_context_error'     VALUE v_dds_context_error,
             'rows_visible'          VALUE v_rows_visible,
             'query_error'           VALUE v_query_error,
             'result'                VALUE v_result
             RETURNING CLOB
           )
    INTO   v_response
    FROM   dual;

    RETURN v_response;
  END;
END;
/

SHOW ERRORS

PROMPT === Defining ORDS module and handlers ===
BEGIN
  ORDS.DELETE_MODULE(p_module_name => 'cb.agent.security');
EXCEPTION
  WHEN OTHERS THEN NULL;
END;
/

BEGIN
  ORDS.DEFINE_MODULE(
    p_module_name    => 'cb.agent.security',
    p_base_path      => 'cb-agent-security/',
    p_items_per_page => 25,
    p_status         => 'PUBLISHED'
  );

  ORDS.DEFINE_TEMPLATE(
    p_module_name => 'cb.agent.security',
    p_pattern     => 'vpd/documents'
  );

  ORDS.DEFINE_HANDLER(
    p_module_name    => 'cb.agent.security',
    p_pattern        => 'vpd/documents',
    p_method         => 'POST',
    p_source_type    => ORDS.source_type_plsql,
    p_source         => q'[
DECLARE
  v_rows_json CLOB;
BEGIN
  cb_ords_handler_pkg.set_vpd_context(:auth_header);

  SELECT JSON_ARRAYAGG(
           JSON_OBJECT(
             'doc_id'       VALUE doc_id,
             'title'        VALUE title,
             'owner_emp_no' VALUE owner_emp_no,
             'dept_code'    VALUE dept_code,
             'contents'     VALUE contents
             RETURNING CLOB
           )
           ORDER BY doc_id
           RETURNING CLOB
         )
  INTO   v_rows_json
  FROM   admin.cb_v_search_documents;

  IF v_rows_json IS NULL THEN
    v_rows_json := '[]';
  END IF;

  :status_code := 200;
  OWA_UTIL.MIME_HEADER('application/json', TRUE);
  HTP.P(JSON_OBJECT('rows' VALUE v_rows_json FORMAT JSON RETURNING CLOB));
  cb_ords_handler_pkg.clear_vpd_context;
EXCEPTION
  WHEN OTHERS THEN
    cb_ords_handler_pkg.clear_vpd_context;
    :status_code := 403;
    OWA_UTIL.MIME_HEADER('application/json', TRUE);
    HTP.P('{"error":"' || REPLACE(SQLERRM, '"', '\"') || '"}');
END;
]',
    p_items_per_page => 0
  );

  ORDS.DEFINE_PARAMETER(
    p_module_name        => 'cb.agent.security',
    p_pattern            => 'vpd/documents',
    p_method             => 'POST',
    p_name               => 'Authorization',
    p_bind_variable_name => 'auth_header',
    p_source_type        => 'HEADER',
    p_param_type         => 'STRING',
    p_access_method      => 'IN'
  );

  ORDS.DEFINE_TEMPLATE(
    p_module_name => 'cb.agent.security',
    p_pattern     => 'dds/bearer-probe'
  );

  ORDS.DEFINE_HANDLER(
    p_module_name    => 'cb.agent.security',
    p_pattern        => 'dds/bearer-probe',
    p_method         => 'POST',
    p_source_type    => ORDS.source_type_plsql,
    p_source         => q'[
DECLARE
  v_response CLOB;
BEGIN
  v_response := cb_ords_handler_pkg.dds_bearer_probe_json(:auth_header);
  :status_code := 200;
  OWA_UTIL.MIME_HEADER('application/json', TRUE);
  HTP.P(v_response);
EXCEPTION
  WHEN OTHERS THEN
    :status_code := 403;
    OWA_UTIL.MIME_HEADER('application/json', TRUE);
    HTP.P('{"error":"' || REPLACE(SQLERRM, '"', '\"') || '"}');
END;
]',
    p_items_per_page => 0
  );

  ORDS.DEFINE_PARAMETER(
    p_module_name        => 'cb.agent.security',
    p_pattern            => 'dds/bearer-probe',
    p_method             => 'POST',
    p_name               => 'Authorization',
    p_bind_variable_name => 'auth_header',
    p_source_type        => 'HEADER',
    p_param_type         => 'STRING',
    p_access_method      => 'IN'
  );

  COMMIT;
END;
/

PROMPT === ORDS handler setup complete ===
EXIT;
