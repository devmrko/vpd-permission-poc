package com.cloudhandson.vpdbackoffice.web;

import com.cloudhandson.vpdbackoffice.service.BearerTokenService;
import com.cloudhandson.vpdbackoffice.service.PermissionService;
import com.cloudhandson.vpdbackoffice.service.ProtectedObjectService;
import java.util.List;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class DashboardController {

  private final ProtectedObjectService protectedObjectService;
  private final PermissionService permissionService;
  private final BearerTokenService bearerTokenService;

  public DashboardController(
      ProtectedObjectService protectedObjectService,
      PermissionService permissionService,
      BearerTokenService bearerTokenService
  ) {
    this.protectedObjectService = protectedObjectService;
    this.permissionService = permissionService;
    this.bearerTokenService = bearerTokenService;
  }

  @GetMapping("/")
  public String dashboard(Model model) {
    try {
      model.addAttribute("objects", protectedObjectService.findEnabled());
      model.addAttribute("roles", permissionService.findRoles());
      model.addAttribute("tokens", bearerTokenService.findAll());
    } catch (DataAccessException e) {
      model.addAttribute("objects", List.of());
      model.addAttribute("roles", List.of());
      model.addAttribute("tokens", List.of());
      model.addAttribute("setupError", dbSetupMessage(e));
    }
    return "dashboard";
  }

  private String dbSetupMessage(DataAccessException e) {
    String detail = e.getMostSpecificCause() == null ? e.getMessage() : e.getMostSpecificCause().getMessage();
    return "ADB 연결을 확인할 수 없습니다. BACKOFFICE_DB_URL, BACKOFFICE_DB_USERNAME, "
        + "BACKOFFICE_DB_PASSWORD를 설정하고 ./run.sh backoffice-support를 먼저 실행하세요. 상세: "
        + detail;
  }
}
