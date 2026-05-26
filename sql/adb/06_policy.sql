-- ============================================================
-- 05_policy.sql
-- VPD policy function + DBMS_RLS.ADD_POLICY attachments.
--
-- The function returns a row-filter predicate based on the
-- secure context loaded at logon. If no permission is loaded for
-- this object, it returns an impossible predicate so the user
-- sees ZERO rows (fail closed).
-- ============================================================
SET ECHO OFF
SET FEEDBACK ON

PROMPT === Creating policy function vpd_region_filter ===
CREATE OR REPLACE FUNCTION vpd_region_filter(
  p_schema IN VARCHAR2,
  p_object IN VARCHAR2
) RETURN VARCHAR2 AS
  v_regions VARCHAR2(4000);
  v_pred    VARCHAR2(4000);
  v_list    VARCHAR2(4000);
BEGIN
  -- Read the CSV of allowed regions for this object from secure context.
  v_regions := SYS_CONTEXT('VPD_CTX', UPPER(p_object));

  IF v_regions IS NULL THEN
    -- Fail closed: no entry => no rows visible.
    RETURN '1=0';
  END IF;

  IF INSTR(v_regions, '*') > 0 THEN
    -- Wildcard => no row filter (full visibility on this object).
    RETURN NULL;
  END IF;

  -- Convert CSV 'KR,APAC' -> "'KR','APAC'" for an IN-list.
  -- (Region values come only from our own permission table, so quoting
  --  by escaping single quotes is sufficient; no untrusted user input.)
  SELECT LISTAGG('''' || REPLACE(TRIM(column_value),'''','''''') || '''', ',')
         WITHIN GROUP (ORDER BY column_value)
  INTO   v_list
  FROM   TABLE(APEX_STRING.SPLIT(v_regions, ','));

  IF v_list IS NULL THEN
    RETURN '1=0';
  END IF;

  v_pred := 'region IN (' || v_list || ')';
  RETURN v_pred;
END;
/

SHOW ERRORS

PROMPT === Attaching policies to views ===
BEGIN
  DBMS_RLS.ADD_POLICY(
    object_schema   => USER,
    object_name     => 'V_CUSTOMERS_PG',
    policy_name     => 'CUSTOMERS_PG_POLICY',
    function_schema => USER,
    policy_function => 'VPD_REGION_FILTER',
    statement_types => 'SELECT',
    update_check    => FALSE,
    enable          => TRUE
  );
END;
/

BEGIN
  DBMS_RLS.ADD_POLICY(
    object_schema   => USER,
    object_name     => 'V_CUSTOMERS_MY',
    policy_name     => 'CUSTOMERS_MY_POLICY',
    function_schema => USER,
    policy_function => 'VPD_REGION_FILTER',
    statement_types => 'SELECT',
    update_check    => FALSE,
    enable          => TRUE
  );
END;
/

PROMPT === Policies attached ===
EXIT;
