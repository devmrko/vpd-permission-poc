-- ============================================================
-- 26_agent_ords_security_dynamic_vpd_filter.sql
-- Replaces the demo VPD filter with a whitelist predicate builder.
--
-- The function does not hardcode object names or rule values. It reads:
--   CB_AGENT_CTX.USER_ID -> CB_USER_ROLE -> CB_PERMISSION -> CB_PERMISSION_RULE
-- and builds a row predicate for the object passed by DBMS_RLS.
-- ============================================================
WHENEVER SQLERROR EXIT SQL.SQLCODE
SET ECHO OFF
SET FEEDBACK ON
SET DEFINE OFF

PROMPT === Creating dynamic whitelist VPD filter ===
CREATE OR REPLACE FUNCTION cb_agent_doc_vpd_filter(
  p_schema IN VARCHAR2,
  p_object IN VARCHAR2
) RETURN VARCHAR2
AUTHID DEFINER
AS
  -- This function is intentionally whitelist-first:
  -- no matched stored row rule means no rows are visible.
  v_user_id    NUMBER;
  v_target     VARCHAR2(128);
  v_predicate  VARCHAR2(32767);

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

  PROCEDURE append_or(p_clause IN VARCHAR2) IS
  BEGIN
    IF p_clause IS NULL THEN
      RETURN;
    END IF;

    IF v_predicate IS NULL THEN
      v_predicate := '(' || p_clause || ')';
    ELSE
      v_predicate := v_predicate || ' OR (' || p_clause || ')';
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
        RETURN '1 = 1';
      ELSIF r.rule_type = 'MY_DEPT' THEN
        -- NULL rule_column keeps backward compatibility with the demo seed.
        v_column := safe_column(p_schema, p_object, NVL(r.rule_column, 'DEPT_CODE'));
        IF v_column IS NOT NULL THEN
          append_or(v_column || ' = SYS_CONTEXT(''CB_AGENT_CTX'', ''DEPT_CODE'')');
        END IF;
      ELSIF r.rule_type = 'SELF' THEN
        -- NULL rule_column keeps backward compatibility with the demo seed.
        v_column := safe_column(p_schema, p_object, NVL(r.rule_column, 'OWNER_EMP_NO'));
        IF v_column IS NOT NULL THEN
          append_or(v_column || ' = SYS_CONTEXT(''CB_AGENT_CTX'', ''EMP_NO'')');
        END IF;
      ELSIF r.rule_type = 'DEPT' THEN
        v_column := safe_column(p_schema, p_object, NVL(r.rule_column, 'DEPT_CODE'));
        IF v_column IS NOT NULL THEN
          append_or(v_column || ' = ' || quote_literal(r.rule_value));
        END IF;
      ELSIF r.rule_type = 'EMP_NO' THEN
        v_column := safe_column(p_schema, p_object, NVL(r.rule_column, 'OWNER_EMP_NO'));
        IF v_column IS NOT NULL THEN
          append_or(v_column || ' = ' || quote_literal(r.rule_value));
        END IF;
      ELSIF r.rule_type = '=' THEN
        v_column := safe_column(p_schema, p_object, r.rule_column);
        IF v_column IS NOT NULL THEN
          append_or('TO_CHAR(' || v_column || ') = ' || quote_literal(r.rule_value));
        END IF;
      ELSIF r.rule_type IN ('!=', '<>') THEN
        v_column := safe_column(p_schema, p_object, r.rule_column);
        IF v_column IS NOT NULL THEN
          append_or('TO_CHAR(' || v_column || ') <> ' || quote_literal(r.rule_value));
        END IF;
      END IF;
    END;
  END LOOP;

  IF v_predicate IS NULL THEN
    RETURN '1 = 0';
  END IF;

  RETURN '(' || v_predicate || ')';
END;
/

SHOW ERRORS FUNCTION cb_agent_doc_vpd_filter

PROMPT === Dynamic whitelist VPD filter ready ===
