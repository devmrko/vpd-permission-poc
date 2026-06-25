package com.cloudhandson.vpdbackoffice.web;

import com.cloudhandson.vpdbackoffice.service.VpdPolicyService;
import java.util.List;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class VpdPolicyController {

  private final VpdPolicyService vpdPolicyService;

  public VpdPolicyController(VpdPolicyService vpdPolicyService) {
    this.vpdPolicyService = vpdPolicyService;
  }

  @GetMapping("/vpd-policies")
  public String policies(Model model) {
    try {
      model.addAttribute("policies", vpdPolicyService.findPolicies());
    } catch (DataAccessException exception) {
      RuntimeErrorMessage message = RuntimeErrorMessages.dataAccess(exception);
      model.addAttribute("runtimeError", message);
      model.addAttribute("policies", List.of());
    }
    return "vpd-policies";
  }
}
