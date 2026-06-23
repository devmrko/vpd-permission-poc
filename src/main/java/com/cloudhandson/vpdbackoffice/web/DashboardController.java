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
      RuntimeErrorMessage error = RuntimeErrorMessages.dataAccess(e);
      model.addAttribute("objects", List.of());
      model.addAttribute("roles", List.of());
      model.addAttribute("tokens", List.of());
      model.addAttribute("runtimeErrorTitle", error.title());
      model.addAttribute("runtimeErrorMessage", error.message());
      model.addAttribute("showSupportCommand", error.showSupportCommand());
    }
    return "dashboard";
  }
}
