package com.cloudhandson.vpdbackoffice.service;

import com.cloudhandson.vpdbackoffice.domain.ords.OrdsHandlerUpdateCommand;
import com.cloudhandson.vpdbackoffice.domain.ords.OrdsHandlerView;
import com.cloudhandson.vpdbackoffice.domain.ords.OrdsObjectHandlerResult;
import com.cloudhandson.vpdbackoffice.domain.protectedobject.ProtectedColumn;
import com.cloudhandson.vpdbackoffice.domain.protectedobject.ProtectedObject;
import com.cloudhandson.vpdbackoffice.mapper.OrdsMetadataMapper;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.annotation.PreDestroy;
import java.util.List;
import java.util.Locale;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrdsMetadataService {

  private final OrdsMetadataMapper mapper;
  private final JdbcTemplate jdbcTemplate;
  private final JdbcTemplate ordsMetadataJdbcTemplate;
  private final HikariDataSource ordsMetadataDataSource;
  private final ProtectedObjectService protectedObjectService;

  public OrdsMetadataService(
      OrdsMetadataMapper mapper,
      JdbcTemplate jdbcTemplate,
      ProtectedObjectService protectedObjectService,
      DataSource dataSource,
      @Value("${backoffice.ords.metadata-db.url:}") String ordsMetadataUrl,
      @Value("${backoffice.ords.metadata-db.username:}") String ordsMetadataUsername,
      @Value("${backoffice.ords.metadata-db.password:}") String ordsMetadataPassword
  ) {
    this.mapper = mapper;
    this.jdbcTemplate = jdbcTemplate;
    this.protectedObjectService = protectedObjectService;
    if (hasText(ordsMetadataUrl) && hasText(ordsMetadataUsername)) {
      HikariConfig config = new HikariConfig();
      config.setPoolName("vpd-ords-metadata-pool");
      config.setJdbcUrl(ordsMetadataUrl.trim());
      config.setUsername(ordsMetadataUsername.trim());
      config.setPassword(ordsMetadataPassword == null ? "" : ordsMetadataPassword);
      config.setDriverClassName("oracle.jdbc.OracleDriver");
      config.setMaximumPoolSize(2);
      config.setMinimumIdle(0);
      config.setConnectionTimeout(10000);
      this.ordsMetadataDataSource = new HikariDataSource(config);
      this.ordsMetadataJdbcTemplate = new JdbcTemplate(this.ordsMetadataDataSource);
    } else {
      this.ordsMetadataDataSource = null;
      this.ordsMetadataJdbcTemplate = new JdbcTemplate(dataSource);
    }
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

  public String objectQueryHandlerSource(long objectId) {
    ProtectedObject object = protectedObjectService.assertEnabled(objectId);
    List<String> columns = protectedObjectService.findColumns(objectId).stream()
        .map(ProtectedColumn::columnName)
        .toList();
    if (columns.isEmpty()) {
      throw new AppException("보호 객체 컬럼이 없어 ORDS 조회 Handler Source를 만들 수 없습니다.");
    }
    requireIdentifier(object.owner(), "owner");
    requireIdentifier(object.objectName(), "objectName");
    columns.forEach(column -> requireIdentifier(column, "column"));
    return objectQuerySource(object, columns);
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
    if (!sourceType.contains("plsql") && !sourceType.contains("query")) {
      throw new AppException("현재는 PL/SQL/SQL Query ORDS Handler만 수정할 수 있습니다: " + handler.sourceType());
    }
    String currentUser = jdbcTemplate.queryForObject("SELECT USER FROM dual", String.class);
    if (currentUser == null || !currentUser.equalsIgnoreCase(handler.parsingSchema())) {
      throw new AppException("ORDS Handler 수정은 parsing schema로 DB에 연결했을 때만 가능합니다. 현재 연결 사용자: "
          + currentUser + ", Handler parsing schema: " + handler.parsingSchema());
    }
    jdbcTemplate.update("""
        DECLARE
          v_source_type VARCHAR2(30);
        BEGIN
          IF ? = 'QUERY' THEN
            v_source_type := ORDS.source_type_query;
          ELSE
            v_source_type := ORDS.source_type_plsql;
          END IF;

          ORDS.DEFINE_HANDLER(
            p_module_name    => ?,
            p_pattern        => ?,
            p_method         => ?,
            p_source_type    => v_source_type,
            p_source         => ?,
            p_items_per_page => 0
          );
          COMMIT;
        END;
        """,
        handlerSourceTypeCode(handler.sourceType()),
        handler.moduleName(),
        handler.template(),
        handler.method(),
        command.source());
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

    String currentUser = ordsMetadataJdbcTemplate.queryForObject("SELECT USER FROM dual", String.class);
    if (currentUser == null || !currentUser.equalsIgnoreCase("CB_ORDS")) {
      throw new AppException("ORDS 조회 Handler 생성은 ORDS parsing schema(CB_ORDS)로 DB에 연결했을 때만 가능합니다. 현재 연결 사용자: "
          + currentUser + ". 현재 연결에서는 소스 보기를 눌러 기본 PL/SQL을 확인한 뒤 ORDS Handler에 적용하세요.");
    }

    String moduleName = "cb.object.query";
    String basePath = "cb-object-query/";
    String template = object.owner().toLowerCase(Locale.ROOT) + "/" + object.objectName().toLowerCase(Locale.ROOT);
    String ordsPath = "cb-ords/" + basePath + template;
    String source = objectQueryHandlerSource(objectId);

    ordsMetadataJdbcTemplate.update("""
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
            p_source_type    => ORDS.source_type_query,
            p_source         => ?,
            p_items_per_page => 25
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
            p_source_type        => 'URI',
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
    String selectColumns = columns.stream()
        .map(column -> "o." + column)
        .reduce((left, right) -> left + ",\n       " + right)
        .orElseThrow();
    return """
        WITH vpd_ctx AS (
          SELECT cb_ords_handler_pkg.set_vpd_context_sql(:auth_header) AS applied
          FROM   dual
        )
        SELECT %s
        FROM   %s.%s o
               CROSS JOIN vpd_ctx
        WHERE  ROWNUM <= LEAST(GREATEST(NVL(:row_limit, 50), 1), 500)
        """.formatted(selectColumns, object.owner(), object.objectName());
  }

  private String handlerSourceTypeCode(String sourceType) {
    String normalized = sourceType == null ? "" : sourceType.toLowerCase(Locale.ROOT);
    if (normalized.contains("query")) {
      return "QUERY";
    }
    return "PLSQL";
  }

  private void requireIdentifier(String value, String label) {
    if (value == null || !value.matches("[A-Z][A-Z0-9_$#]*")) {
      throw new AppException("ORDS Handler 생성에 사용할 수 없는 " + label + " 식별자입니다: " + value);
    }
  }

  private boolean hasText(String value) {
    return value != null && !value.isBlank();
  }

  @PreDestroy
  public void closeOrdsMetadataDataSource() {
    if (ordsMetadataDataSource != null) {
      ordsMetadataDataSource.close();
    }
  }
}
