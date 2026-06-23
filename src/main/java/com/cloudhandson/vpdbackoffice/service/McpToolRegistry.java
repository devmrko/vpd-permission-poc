package com.cloudhandson.vpdbackoffice.service;

import com.cloudhandson.vpdbackoffice.domain.mcp.McpToolView;
import com.cloudhandson.vpdbackoffice.domain.protectedobject.ProtectedObject;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;

@Service
public class McpToolRegistry {

  private final ProtectedObjectService protectedObjectService;

  public McpToolRegistry(ProtectedObjectService protectedObjectService) {
    this.protectedObjectService = protectedObjectService;
  }

  public List<McpToolView> listTools() {
    return protectedObjectService.findEnabled().stream()
        .map(this::toTool)
        .toList();
  }

  public McpToolView toolFor(ProtectedObject object) {
    return toTool(object);
  }

  private McpToolView toTool(ProtectedObject object) {
    String name = "ords.query." + safeName(object.owner()) + "." + safeName(object.objectName());
    return new McpToolView(
        name,
        object.displayName() + " 보호 객체를 Bearer Token으로 ORDS 호출해 VPD/Redaction 적용 결과를 조회합니다.",
        object.objectId(),
        object.displayName(),
        object.ordsPath()
    );
  }

  private String safeName(String value) {
    return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]+", "_");
  }
}
