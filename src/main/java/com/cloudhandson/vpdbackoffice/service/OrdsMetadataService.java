package com.cloudhandson.vpdbackoffice.service;

import com.cloudhandson.vpdbackoffice.domain.ords.OrdsHandlerUpdateCommand;
import com.cloudhandson.vpdbackoffice.domain.ords.OrdsHandlerView;
import com.cloudhandson.vpdbackoffice.domain.ords.OrdsObjectHandlerResult;
import com.cloudhandson.vpdbackoffice.domain.protectedobject.ProtectedColumn;
import com.cloudhandson.vpdbackoffice.domain.protectedobject.ProtectedObject;
import com.cloudhandson.vpdbackoffice.mapper.OrdsMetadataMapper;
import java.util.List;
import java.util.Locale;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrdsMetadataService {

  private final OrdsMetadataMapper mapper;
  private final JdbcTemplate jdbcTemplate;
  private final ProtectedObjectService protectedObjectService;

  public OrdsMetadataService(
      OrdsMetadataMapper mapper,
      JdbcTemplate jdbcTemplate,
      ProtectedObjectService protectedObjectService
  ) {
    this.mapper = mapper;
    this.jdbcTemplate = jdbcTemplate;
    this.protectedObjectService = protectedObjectService;
  }

  public List<OrdsHandlerView> findHandlers() {
    String packageSource = mapper.findHandlerPackageSource();
    return mapper.findHandlers().stream()
        .map(handler -> new OrdsHandlerView(
            handler.handlerId(),
            handler.schemaName(),
            handler.parsingSchema(),
            handler.moduleName(),
            handler.basePath(),
            handler.template(),
            handler.method(),
            handler.sourceType(),
            handler.source(),
            handler.parameters(),
            packageSource
        ))
        .toList();
  }

  @Transactional
  public void updateHandlerSource(OrdsHandlerUpdateCommand command) {
    if (command.source() == null || command.source().isBlank()) {
      throw new AppException("ORDS Handler Source는 필수입니다.");
    }
    OrdsHandlerView handler = mapper.findHandler(command.handlerId());
    if (handler == null) {
      throw new AppException("수정할 ORDS Handler를 찾을 수 없습니다.");
    }
    String sourceType = handler.sourceType() == null ? "" : handler.sourceType().toLowerCase(Locale.ROOT);
    if (!sourceType.contains("plsql")) {
      throw new AppException("현재는 PL/SQL ORDS Handler만 수정할 수 있습니다: " + handler.sourceType());
    }
    String currentUser = jdbcTemplate.queryForObject("SELECT USER FROM dual", String.class);
    if (currentUser == null || !currentUser.equalsIgnoreCase(handler.parsingSchema())) {
      throw new AppException("ORDS Handler 수정은 parsing schema로 DB에 연결했을 때만 가능합니다. 현재 연결 사용자: "
          + currentUser + ", Handler parsing schema: " + handler.parsingSchema());
    }
    jdbcTemplate.update("""
        BEGIN
          ORDS.DEFINE_HANDLER(
            p_module_name    => ?,
            p_pattern        => ?,
            p_method         => ?,
            p_source_type    => ORDS.source_type_plsql,
            p_source         => ?,
            p_items_per_page => 0
          );
          COMMIT;
        END;
        """, handler.moduleName(), handler.template(), handler.method(), command.source());
  }

  @Transactional
  public OrdsObjectHandlerResult createObjectQueryHandler(long objectId) {
    ProtectedObject object = protectedObjectService.assertEnabled(objectId);
    List<String> columns = protectedObjectService.findColumns(objectId).stream()
        .map(ProtectedColumn::columnName)
        .toList();
    if (columns.isEmpty()) {
      throw new AppException("보호 객체 컬럼이 없어 ORDS 조회 Handler를 만들 수 없습니다.");
    }
    requireIdentifier(object.owner(), "owner");
    requireIdentifier(object.objectName(), "objectName");
    columns.forEach(column -> requireIdentifier(column, "column"));

    String currentUser = jdbcTemplate.queryForObject("SELECT USER FROM dual", String.class);
    if (currentUser == null || !currentUser.equalsIgnoreCase("CB_ORDS")) {
      throw new AppException("ORDS 조회 Handler 생성은 ORDS parsing schema(CB_ORDS)로 DB에 연결했을 때만 가능합니다. 현재 연결 사용자: "
          + currentUser);
    }

    String moduleName = "cb.object.query";
    String basePath = "cb-object-query/";
    String template = object.owner().toLowerCase(Locale.ROOT) + "/" + object.objectName().toLowerCase(Locale.ROOT);
    String ordsPath = "cb-ords/" + basePath + template;
    String source = objectQuerySource(object, columns);

    jdbcTemplate.update("""
        BEGIN
          ORDS.DEFINE_MODULE(
            p_module_name    => ?,
            p_base_path      => ?,
            p_items_per_page => 25,
            p_status         => 'PUBLISHED'
          );
          ORDS.DEFINE_TEMPLATE(
            p_module_name => ?,
            p_pattern     => ?
          );
          ORDS.DEFINE_HANDLER(
            p_module_name    => ?,
            p_pattern        => ?,
            p_method         => 'POST',
            p_source_type    => ORDS.source_type_plsql,
            p_source         => ?,
            p_items_per_page => 0
          );
          ORDS.DEFINE_PARAMETER(
            p_module_name        => ?,
            p_pattern            => ?,
            p_method             => 'POST',
            p_name               => 'Authorization',
            p_bind_variable_name => 'auth_header',
            p_source_type        => 'HEADER',
            p_param_type         => 'STRING',
            p_access_method      => 'IN'
          );
          ORDS.DEFINE_PARAMETER(
            p_module_name        => ?,
            p_pattern            => ?,
            p_method             => 'POST',
            p_name               => 'limit',
            p_bind_variable_name => 'row_limit',
            p_source_type        => 'QUERY',
            p_param_type         => 'INT',
            p_access_method      => 'IN'
          );
          COMMIT;
        END;
        """,
        moduleName, basePath,
        moduleName, template,
        moduleName, template, source,
        moduleName, template,
        moduleName, template);
    protectedObjectService.updateOrdsPath(object.objectId(), ordsPath);
    return new OrdsObjectHandlerResult(object.objectId(), ordsPath, moduleName, template);
  }

  private String objectQuerySource(ProtectedObject object, List<String> columns) {
    String jsonEntries = columns.stream()
        .map(column -> "'" + column.toLowerCase(Locale.ROOT) + "' VALUE " + column)
        .reduce((left, right) -> left + ",\n                 " + right)
        .orElseThrow();
    String selectColumns = String.join(", ", columns);
    return """
        DECLARE
          v_auth VARCHAR2(4000);
          v_bearer_key VARCHAR2(4000);
          v_limit NUMBER := LEAST(GREATEST(NVL(:row_limit, 50), 1), 500);
          v_rows_json CLOB;
        BEGIN
          v_auth := TRIM(:auth_header);
          IF v_auth IS NULL OR LOWER(SUBSTR(v_auth, 1, 7)) != 'bearer ' THEN
            RAISE_APPLICATION_ERROR(-20101, 'Authorization header must be Bearer <key>');
          END IF;
          v_bearer_key := TRIM(SUBSTR(v_auth, 8));

          admin.cb_agent_ctx_pkg.clear_user;
          admin.cb_agent_ctx_pkg.set_user_by_bearer(v_bearer_key);

          SELECT JSON_ARRAYAGG(
                   JSON_OBJECT(
                     %s
                     RETURNING CLOB
                   )
                   RETURNING CLOB
                 )
          INTO   v_rows_json
          FROM (
            SELECT %s
            FROM   %s.%s
            WHERE  ROWNUM <= v_limit
          );

          IF v_rows_json IS NULL THEN
            v_rows_json := '[]';
          END IF;

          :status_code := 200;
          OWA_UTIL.MIME_HEADER('application/json', TRUE);
          HTP.P(JSON_OBJECT('rows' VALUE v_rows_json FORMAT JSON RETURNING CLOB));
          admin.cb_agent_ctx_pkg.clear_user;
        EXCEPTION
          WHEN OTHERS THEN
            admin.cb_agent_ctx_pkg.clear_user;
            :status_code := 403;
            OWA_UTIL.MIME_HEADER('application/json', TRUE);
            HTP.P(JSON_OBJECT('error' VALUE SQLERRM RETURNING CLOB));
        END;
        """.formatted(jsonEntries, selectColumns, object.owner(), object.objectName());
  }

  private void requireIdentifier(String value, String label) {
    if (value == null || !value.matches("[A-Z][A-Z0-9_$#]*")) {
      throw new AppException("ORDS Handler 생성에 사용할 수 없는 " + label + " 식별자입니다: " + value);
    }
  }
}
