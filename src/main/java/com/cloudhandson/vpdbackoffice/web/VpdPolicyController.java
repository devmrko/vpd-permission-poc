package com.cloudhandson.vpdbackoffice.web;

import com.cloudhandson.vpdbackoffice.service.AppException;
import com.cloudhandson.vpdbackoffice.service.VpdPolicyService;
import java.util.List;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

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

  @GetMapping("/vpd-policies/function-source")
  public String functionSource(
      @RequestParam String owner,
      @RequestParam(required = false) String packageName,
      @RequestParam String functionName,
      Model model
  ) {
    try {
      model.addAttribute("source", vpdPolicyService.findFunctionSource(owner, packageName, functionName));
    } catch (AppException exception) {
      model.addAttribute("errorMessage", exception.getMessage());
    } catch (DataAccessException exception) {
      RuntimeErrorMessage message = RuntimeErrorMessages.dataAccess(exception);
      model.addAttribute("errorMessage", message.message());
    }
    return "fragments/vpd-function-source :: source";
  }

  @GetMapping("/vpd-policies/policy-detail")
  public String policyDetail(
      @RequestParam String objectOwner,
      @RequestParam String objectName,
      @RequestParam String policyName,
      Model model
  ) {
    try {
      model.addAttribute("detail", vpdPolicyService.findPolicyDetail(objectOwner, objectName, policyName));
    } catch (AppException exception) {
      model.addAttribute("errorMessage", exception.getMessage());
    } catch (DataAccessException exception) {
      RuntimeErrorMessage message = RuntimeErrorMessages.dataAccess(exception);
      model.addAttribute("errorMessage", message.message());
    }
    return "fragments/vpd-policy-detail :: detail";
  }
}
