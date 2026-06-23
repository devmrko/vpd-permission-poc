package com.cloudhandson.vpdbackoffice.service;

import com.cloudhandson.vpdbackoffice.domain.ords.OrdsHandlerUpdateCommand;
import com.cloudhandson.vpdbackoffice.domain.ords.OrdsHandlerView;
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

  public OrdsMetadataService(OrdsMetadataMapper mapper, JdbcTemplate jdbcTemplate) {
    this.mapper = mapper;
    this.jdbcTemplate = jdbcTemplate;
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
}
