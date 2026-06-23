package com.cloudhandson.vpdbackoffice.web;

import com.cloudhandson.vpdbackoffice.service.BearerTokenService;
import com.cloudhandson.vpdbackoffice.service.PermissionService;
import com.cloudhandson.vpdbackoffice.service.ProtectedObjectService;
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
    model.addAttribute("objects", protectedObjectService.findEnabled());
    model.addAttribute("roles", permissionService.findRoles());
    model.addAttribute("tokens", bearerTokenService.findAll());
    return "dashboard";
  }
}
