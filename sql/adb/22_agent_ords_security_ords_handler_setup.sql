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
-- ============================================================
WHENEVER SQLERROR EXIT SQL.SQLCODE
SET ECHO OFF
SET FEEDBACK ON

PROMPT === Creating CB_ORDS handler package ===
CREATE OR REPLACE PACKAGE cb_ords_handler_pkg AUTHID DEFINER AS
  PROCEDURE set_vpd_context(p_authorization IN VARCHAR2);
  PROCEDURE clear_vpd_context;
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
    p_source         => q'!
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
    HTP.P(JSON_OBJECT('error' VALUE SQLERRM RETURNING CLOB));
END;
!',
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

  COMMIT;
END;
/

PROMPT === ORDS handler setup complete ===
EXIT;
