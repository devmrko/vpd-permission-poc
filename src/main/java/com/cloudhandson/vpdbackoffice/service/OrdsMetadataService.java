package com.cloudhandson.vpdbackoffice.service;

import com.cloudhandson.vpdbackoffice.domain.ords.OrdsHandlerView;
import com.cloudhandson.vpdbackoffice.mapper.OrdsMetadataMapper;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class OrdsMetadataService {

  private final OrdsMetadataMapper mapper;

  public OrdsMetadataService(OrdsMetadataMapper mapper) {
    this.mapper = mapper;
  }

  public List<OrdsHandlerView> findHandlers() {
    String packageSource = mapper.findHandlerPackageSource();
    return mapper.findHandlers().stream()
        .map(handler -> new OrdsHandlerView(
            handler.schemaName(),
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
}
