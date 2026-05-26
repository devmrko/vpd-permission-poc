-- ============================================================
-- 09_tests_admin_audit.sql
-- Run as ADMIN to audit / verify policy attachment.
-- ============================================================
SET FEEDBACK ON
SET LINESIZE 220
SET PAGESIZE 100
COL object_name FORMAT a20
COL policy      FORMAT a25
COL pf_owner    FORMAT a15
COL package     FORMAT a15
COL function    FORMAT a25
COL sel         FORMAT a3
COL enable      FORMAT a6

PROMPT
PROMPT === Attached VPD policies ===
SELECT object_name,
       policy_name AS policy,
       pf_owner,
       package,
       function,
       sel,
       enable
FROM   dba_policies
WHERE  object_owner = USER
ORDER BY object_name, policy_name;

PROMPT
PROMPT === Attached Data Redaction policies ===
COL object_name   FORMAT a20
COL policy_name   FORMAT a20
COL expression    FORMAT a80 WORD_WRAPPED
SELECT object_name,
       policy_name,
       expression
FROM   redaction_policies
WHERE  object_owner = USER
ORDER  BY object_name, policy_name;

PROMPT
PROMPT === Redacted columns (which columns get masked, and how) ===
COL object_name   FORMAT a20
COL column_name   FORMAT a15
COL function_type FORMAT a15
COL regexp_pattern FORMAT a25
COL regexp_replace_string FORMAT a15
SELECT object_name,
       column_name,
       function_type,
       regexp_pattern,
       regexp_replace_string
FROM   redaction_columns
WHERE  object_owner = USER
ORDER  BY object_name, column_name;

PROMPT
PROMPT === Permission summary (who can see what) ===
SELECT u.db_username,
       g.group_name,
       s.source_name,
       p.object_name,
       p.allowed_regions
FROM   app_user u
JOIN   user_group ug ON ug.user_id  = u.user_id
JOIN   app_group g  ON g.group_id   = ug.group_id
JOIN   permission p ON p.group_id   = g.group_id
JOIN   db_source s  ON s.source_id  = p.source_id
ORDER BY u.db_username, p.object_name;

EXIT;
