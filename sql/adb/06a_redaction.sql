-- ============================================================
-- 05a_redaction.sql
-- Column-level masking with Oracle Data Redaction (DBMS_REDACT).
--
-- Policy:
--   PII columns (email, full_name) are MASKED unless the session
--   has full-region access ('*') on the corresponding view.
--
--   - VPDUSER_MY    -> PG view masked, MY view unmasked (allowed '*')
--   - VPDUSER_PG    -> PG view unmasked, MY view masked
--   - VPDUSER_BOTH  -> both views unmasked
--   - VPDUSER_NONE  -> both masked (but rows filtered to 0 anyway)
--
-- Reuses the secure VPD_CTX populated at logon — no new context.
-- Data Redaction and VPD compose: VPD filters rows first, Redaction
-- then transforms columns on the surviving rows.
--
-- NOTE: ADMIN has the EXEMPT REDACTION POLICY system privilege
-- implicitly via DBA, so ADMIN sessions still see real values.
-- End-users do not have it, so they see the masked output.
-- ============================================================
SET ECHO OFF
SET FEEDBACK ON
SET DEFINE OFF

PROMPT === Creating PII redaction policy on v_customers_pg ===
BEGIN
  DBMS_REDACT.ADD_POLICY(
    object_schema       => USER,
    object_name         => 'V_CUSTOMERS_PG',
    column_name         => 'EMAIL',
    policy_name         => 'PII_REDACT_PG',
    function_type       => DBMS_REDACT.REGEXP,
    regexp_pattern      => '^(.)(.*)(@.*)$',
    regexp_replace_string => '\1****\3',
    regexp_position     => 1,
    regexp_occurrence   => 1,
    expression          => 'SYS_CONTEXT(''VPD_CTX'',''V_CUSTOMERS_PG'') IS NULL OR SYS_CONTEXT(''VPD_CTX'',''V_CUSTOMERS_PG'') != ''*'''
  );

  DBMS_REDACT.ALTER_POLICY(
    object_schema       => USER,
    object_name         => 'V_CUSTOMERS_PG',
    policy_name         => 'PII_REDACT_PG',
    action              => DBMS_REDACT.ADD_COLUMN,
    column_name         => 'FULL_NAME',
    function_type       => DBMS_REDACT.REGEXP,
    regexp_pattern      => '^(.)(.*)$',
    regexp_replace_string => '\1****',
    regexp_position     => 1,
    regexp_occurrence   => 1
  );
END;
/

PROMPT === Creating PII redaction policy on v_customers_my ===
BEGIN
  DBMS_REDACT.ADD_POLICY(
    object_schema       => USER,
    object_name         => 'V_CUSTOMERS_MY',
    column_name         => 'EMAIL',
    policy_name         => 'PII_REDACT_MY',
    function_type       => DBMS_REDACT.REGEXP,
    regexp_pattern      => '^(.)(.*)(@.*)$',
    regexp_replace_string => '\1****\3',
    regexp_position     => 1,
    regexp_occurrence   => 1,
    expression          => 'SYS_CONTEXT(''VPD_CTX'',''V_CUSTOMERS_MY'') IS NULL OR SYS_CONTEXT(''VPD_CTX'',''V_CUSTOMERS_MY'') != ''*'''
  );

  DBMS_REDACT.ALTER_POLICY(
    object_schema       => USER,
    object_name         => 'V_CUSTOMERS_MY',
    policy_name         => 'PII_REDACT_MY',
    action              => DBMS_REDACT.ADD_COLUMN,
    column_name         => 'FULL_NAME',
    function_type       => DBMS_REDACT.REGEXP,
    regexp_pattern      => '^(.)(.*)$',
    regexp_replace_string => '\1****',
    regexp_position     => 1,
    regexp_occurrence   => 1
  );
END;
/

PROMPT === Redaction policies attached ===
EXIT;
