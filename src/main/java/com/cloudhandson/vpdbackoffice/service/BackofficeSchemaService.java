package com.cloudhandson.vpdbackoffice.service;

import com.cloudhandson.vpdbackoffice.config.BackofficeProperties;
import com.cloudhandson.vpdbackoffice.domain.schema.SchemaActionResult;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BackofficeSchemaService {

  private final JdbcTemplate jdbcTemplate;
  private final BackofficeProperties properties;

  public BackofficeSchemaService(JdbcTemplate jdbcTemplate, BackofficeProperties properties) {
    this.jdbcTemplate = jdbcTemplate;
    this.properties = properties;
  }

  @Transactional
  public List<SchemaActionResult> initializeSchema() {
    List<SchemaActionResult> results = new ArrayList<>();
    createSequence(results, "cb_agent_bearer_key_seq", 1000);
    createSequence(results, "cb_permission_seq", 1000);
    createSequence(results, "cb_permission_rule_seq", 10000);
    createSequence(results, "cb_ords_probe_audit_seq", 1);

    createTable(results, "cb_app_user", """
        CREATE TABLE cb_app_user (
          user_id            NUMBER PRIMARY KEY,
          user_name          VARCHAR2(100) NOT NULL UNIQUE,
          employee_no        VARCHAR2(50),
          dept_code          VARCHAR2(50),
          can_read_contents  CHAR(1) DEFAULT 'N' CHECK (can_read_contents IN ('Y','N')) NOT NULL,
          active             CHAR(1) DEFAULT 'Y' CHECK (active IN ('Y','N')) NOT NULL
        )
        """);
    createTable(results, "cb_app_role", """
        CREATE TABLE cb_app_role (
          role_id               NUMBER PRIMARY KEY,
          role_name             VARCHAR2(100) NOT NULL UNIQUE,
          max_sensitivity_level VARCHAR2(20) DEFAULT 'PUBLIC' NOT NULL
        )
        """);
    addColumn(results, "cb_app_role", "max_sensitivity_level",
        "ALTER TABLE cb_app_role ADD (max_sensitivity_level VARCHAR2(20) DEFAULT 'PUBLIC' NOT NULL)");
    createTable(results, "cb_app_group", """
        CREATE TABLE cb_app_group (
          group_id     NUMBER PRIMARY KEY,
          group_code   VARCHAR2(100) NOT NULL UNIQUE,
          group_name   VARCHAR2(100) NOT NULL,
          description  VARCHAR2(200),
          active_yn    CHAR(1) DEFAULT 'Y' CHECK (active_yn IN ('Y','N')) NOT NULL
        )
        """);
    createTable(results, "cb_user_role", """
        CREATE TABLE cb_user_role (
          user_id  NUMBER NOT NULL,
          role_id  NUMBER NOT NULL,
          CONSTRAINT cb_user_role_pk PRIMARY KEY (user_id, role_id)
        )
        """);
    createTable(results, "cb_user_group", """
        CREATE TABLE cb_user_group (
          group_id  NUMBER NOT NULL,
          user_id   NUMBER NOT NULL,
          CONSTRAINT cb_user_group_pk PRIMARY KEY (group_id, user_id)
        )
        """);
    createTable(results, "cb_group_role", """
        CREATE TABLE cb_group_role (
          group_id  NUMBER NOT NULL,
          role_id   NUMBER NOT NULL,
          CONSTRAINT cb_group_role_pk PRIMARY KEY (group_id, role_id)
        )
        """);
    createTable(results, "cb_permission", """
        CREATE TABLE cb_permission (
          perm_id      NUMBER PRIMARY KEY,
          role_id      NUMBER NOT NULL,
          target_name  VARCHAR2(128) NOT NULL,
          action_name  VARCHAR2(30) DEFAULT 'SELECT' NOT NULL,
          permission_effect VARCHAR2(10) DEFAULT 'ALLOW' NOT NULL
        )
        """);
    addColumn(results, "cb_permission", "permission_effect",
        "ALTER TABLE cb_permission ADD (permission_effect VARCHAR2(10) DEFAULT 'ALLOW' NOT NULL)");
    createTable(results, "cb_permission_rule", """
        CREATE TABLE cb_permission_rule (
          rule_id      NUMBER PRIMARY KEY,
          perm_id      NUMBER NOT NULL,
          rule_column  VARCHAR2(128),
          rule_type    VARCHAR2(30) NOT NULL,
          rule_value   VARCHAR2(4000)
        )
        """);
    addColumn(results, "cb_permission_rule", "rule_column", "ALTER TABLE cb_permission_rule ADD (rule_column VARCHAR2(128))");
    createTable(results, "cb_permission_column", """
        CREATE TABLE cb_permission_column (
          permission_id  NUMBER NOT NULL,
          column_name    VARCHAR2(128) NOT NULL,
          CONSTRAINT cb_permission_column_pk PRIMARY KEY (permission_id, column_name)
        )
        """);
    createTable(results, "cb_agent_bearer_key", """
        CREATE TABLE cb_agent_bearer_key (
          key_id       NUMBER PRIMARY KEY,
          user_id      NUMBER NOT NULL,
          key_prefix   VARCHAR2(30) NOT NULL,
          key_hash     VARCHAR2(128) NOT NULL UNIQUE,
          expires_at   TIMESTAMP NOT NULL,
          revoked_at   TIMESTAMP,
          description  VARCHAR2(200)
        )
        """);
    addColumn(results, "cb_agent_bearer_key", "description",
        "ALTER TABLE cb_agent_bearer_key ADD (description VARCHAR2(200))");
    createTable(results, "cb_protected_object", """
        CREATE TABLE cb_protected_object (
          object_id    NUMBER PRIMARY KEY,
          owner        VARCHAR2(128) NOT NULL,
          object_name  VARCHAR2(128) NOT NULL UNIQUE,
          ords_path    VARCHAR2(300) NOT NULL,
          enabled_yn   CHAR(1) DEFAULT 'Y' CHECK (enabled_yn IN ('Y','N')) NOT NULL
        )
        """);
    createTable(results, "cb_protected_column", """
        CREATE TABLE cb_protected_column (
          column_id        NUMBER PRIMARY KEY,
          object_id        NUMBER NOT NULL,
          column_name      VARCHAR2(128) NOT NULL,
          sensitive_yn     CHAR(1) DEFAULT 'N' CHECK (sensitive_yn IN ('Y','N')) NOT NULL,
          visible_role_id  NUMBER,
          sensitivity_level VARCHAR2(20) DEFAULT 'PUBLIC' NOT NULL,
          redaction_method  VARCHAR2(20) DEFAULT 'NONE' NOT NULL,
          CONSTRAINT cb_protected_column_uk UNIQUE (object_id, column_name)
        )
        """);
    addColumn(results, "cb_protected_column", "sensitivity_level",
        "ALTER TABLE cb_protected_column ADD (sensitivity_level VARCHAR2(20) DEFAULT 'PUBLIC' NOT NULL)");
    addColumn(results, "cb_protected_column", "redaction_method",
        "ALTER TABLE cb_protected_column ADD (redaction_method VARCHAR2(20) DEFAULT 'NONE' NOT NULL)");
    jdbcTemplate.update("""
        UPDATE cb_protected_column
        SET sensitivity_level = CASE sensitive_yn WHEN 'Y' THEN 'CONFIDENTIAL' ELSE 'PUBLIC' END,
            redaction_method = CASE sensitive_yn WHEN 'Y' THEN 'NULLIFY' ELSE 'NONE' END
        WHERE sensitivity_level = 'PUBLIC'
          AND redaction_method = 'NONE'
          AND sensitive_yn = 'Y'
        """);
    createTable(results, "cb_ords_probe_audit", """
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
        )
        """);
    createTable(results, "cb_backoffice_setting", """
        CREATE TABLE cb_backoffice_setting (
          setting_key    VARCHAR2(100) PRIMARY KEY,
          setting_value  VARCHAR2(1000),
          updated_at     TIMESTAMP DEFAULT SYSTIMESTAMP NOT NULL
        )
        """);
    seedDefaultSettings(results);
    return results;
  }

  private void createSequence(List<SchemaActionResult> results, String name, int startWith) {
    runIgnoringAlreadyExists(results, name, "SEQUENCE",
        "CREATE SEQUENCE " + name + " START WITH " + startWith + " INCREMENT BY 1 NOCACHE");
  }

  private void createTable(List<SchemaActionResult> results, String name, String ddl) {
    runIgnoringAlreadyExists(results, name, "TABLE", ddl);
  }

  private void addColumn(List<SchemaActionResult> results, String table, String column, String ddl) {
    try {
      jdbcTemplate.execute(ddl);
      results.add(new SchemaActionResult(table + "." + column, "COLUMN", "CREATED", "컬럼을 추가했습니다."));
    } catch (RuntimeException exception) {
      if (oracleErrorCode(exception) == 1430) {
        results.add(new SchemaActionResult(table + "." + column, "COLUMN", "EXISTS", "이미 존재합니다."));
        return;
      }
      results.add(new SchemaActionResult(table + "." + column, "COLUMN", "FAILED", safeMessage(exception)));
      throw exception;
    }
  }

  private void runIgnoringAlreadyExists(List<SchemaActionResult> results, String name, String action, String ddl) {
    try {
      jdbcTemplate.execute(ddl);
      results.add(new SchemaActionResult(name, action, "CREATED", "생성했습니다."));
    } catch (RuntimeException exception) {
      if (oracleErrorCode(exception) == 955) {
        results.add(new SchemaActionResult(name, action, "EXISTS", "이미 존재합니다."));
        return;
      }
      results.add(new SchemaActionResult(name, action, "FAILED", safeMessage(exception)));
      throw exception;
    }
  }

  private void seedDefaultSettings(List<SchemaActionResult> results) {
    jdbcTemplate.update("""
        MERGE INTO cb_backoffice_setting dst
        USING (
          SELECT 'ORDS_BASE_URL' setting_key, ? setting_value
          FROM dual
        ) src
        ON (dst.setting_key = src.setting_key)
        WHEN MATCHED THEN UPDATE SET dst.setting_value = src.setting_value, dst.updated_at = SYSTIMESTAMP
          WHERE dst.setting_value IS NULL OR TRIM(dst.setting_value) IS NULL
        WHEN NOT MATCHED THEN INSERT (setting_key, setting_value, updated_at)
          VALUES (src.setting_key, src.setting_value, SYSTIMESTAMP)
        """, properties.ords().baseUrl());
    results.add(new SchemaActionResult("ORDS_BASE_URL", "SETTING", "MERGED", "기본 ORDS URL 설정을 보강했습니다."));
  }

  private int oracleErrorCode(RuntimeException exception) {
    Throwable current = exception;
    while (current != null) {
      if (current instanceof SQLException sqlException) {
        return Math.abs(sqlException.getErrorCode());
      }
      current = current.getCause();
    }
    return 0;
  }

  private String safeMessage(Throwable throwable) {
    String message = throwable.getMessage();
    if (message == null || message.isBlank()) {
      return throwable.getClass().getSimpleName();
    }
    String normalized = message.replaceAll("\\s+", " ").trim();
    return normalized.length() <= 300 ? normalized : normalized.substring(0, 300) + "...";
  }
}
