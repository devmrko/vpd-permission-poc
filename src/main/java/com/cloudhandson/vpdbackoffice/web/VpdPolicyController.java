package com.cloudhandson.vpdbackoffice.web;

import com.cloudhandson.vpdbackoffice.domain.vpd.VpdPolicyCreateCommand;
import com.cloudhandson.vpdbackoffice.service.AppException;
import com.cloudhandson.vpdbackoffice.service.ProtectedObjectService;
import com.cloudhandson.vpdbackoffice.service.VpdPolicyService;
import java.util.List;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class VpdPolicyController {

  private final VpdPolicyService vpdPolicyService;
  private final ProtectedObjectService protectedObjectService;

  public VpdPolicyController(VpdPolicyService vpdPolicyService, ProtectedObjectService protectedObjectService) {
    this.vpdPolicyService = vpdPolicyService;
    this.protectedObjectService = protectedObjectService;
  }

  @GetMapping("/vpd-policies")
  public String policies(Model model) {
    try {
      model.addAttribute("policies", vpdPolicyService.findPolicies());
      model.addAttribute("objects", protectedObjectService.findEnabled());
      model.addAttribute("formOptions", vpdPolicyService.formOptions());
    } catch (DataAccessException exception) {
      RuntimeErrorMessage message = RuntimeErrorMessages.dataAccess(exception);
      model.addAttribute("runtimeError", message);
      model.addAttribute("policies", List.of());
      model.addAttribute("objects", List.of());
      model.addAttribute("formOptions", vpdPolicyService.emptyFormOptions());
    }
    return "vpd-policies";
  }

  @PostMapping("/vpd-policies")
  public String createPolicy(
      @RequestParam String objectKey,
      @RequestParam String policyName,
      @RequestParam(required = false) String functionKey,
      @RequestParam(required = false) String functionOwner,
      @RequestParam(required = false) String functionName,
      @RequestParam(defaultValue = "SELECT") List<String> statementTypes,
      @RequestParam(defaultValue = "false") boolean enabled,
      @RequestParam(defaultValue = "false") boolean updateCheck,
      @RequestParam(required = false) String filterPredicate,
      RedirectAttributes redirectAttributes
  ) {
    try {
      String[] objectParts = objectKey.split("\\.", 2);
      if (objectParts.length != 2) {
        throw new AppException("조회 대상 형식이 올바르지 않습니다: " + objectKey);
      }
      vpdPolicyService.createPolicy(new VpdPolicyCreateCommand(
          objectParts[0],
          objectParts[1],
          policyName,
          functionKey,
          functionOwner,
          functionName,
          String.join(",", statementTypes),
          enabled,
          updateCheck,
          filterPredicate
      ));
      redirectAttributes.addFlashAttribute("successMessage", "VPD policy를 등록했습니다: " + policyName);
    } catch (AppException exception) {
      redirectAttributes.addFlashAttribute("errorMessage", exception.getMessage());
    } catch (DataAccessException exception) {
      RuntimeErrorMessage message = RuntimeErrorMessages.dataAccess(exception);
      redirectAttributes.addFlashAttribute("errorMessage", message.message());
    }
    return "redirect:/vpd-policies";
  }

  @PostMapping("/vpd-policies/bulk")
  public String bulkApplyPolicy(
      @RequestParam String schemaOwner,
      @RequestParam(defaultValue = "false") boolean includeTables,
      @RequestParam(defaultValue = "false") boolean includeViews,
      @RequestParam(required = false) String functionKey,
      @RequestParam(required = false) String functionOwner,
      @RequestParam(required = false) String functionName,
      @RequestParam(defaultValue = "SELECT") List<String> statementTypes,
      @RequestParam(defaultValue = "false") boolean enabled,
      @RequestParam(defaultValue = "false") boolean updateCheck,
      @RequestParam(required = false) String filterPredicate,
      RedirectAttributes redirectAttributes
  ) {
    try {
      var result = vpdPolicyService.bulkApplySchema(
          schemaOwner,
          includeTables,
          includeViews,
          functionKey,
          functionOwner,
          functionName,
          String.join(",", statementTypes),
          enabled,
          updateCheck,
          filterPredicate
      );
      redirectAttributes.addFlashAttribute("successMessage", result.summary());
    } catch (AppException exception) {
      redirectAttributes.addFlashAttribute("errorMessage", exception.getMessage());
    } catch (DataAccessException exception) {
      RuntimeErrorMessage message = RuntimeErrorMessages.dataAccess(exception);
      redirectAttributes.addFlashAttribute("errorMessage", message.message());
    }
    return "redirect:/vpd-policies";
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

  @GetMapping("/vpd-policies/policy-explanation")
  public String policyExplanation(
      @RequestParam String objectOwner,
      @RequestParam String objectName,
      @RequestParam String policyName,
      Model model
  ) {
    try {
      model.addAttribute("explanation", vpdPolicyService.explainPolicy(objectOwner, objectName, policyName));
    } catch (AppException exception) {
      model.addAttribute("errorMessage", exception.getMessage());
    } catch (DataAccessException exception) {
      RuntimeErrorMessage message = RuntimeErrorMessages.dataAccess(exception);
      model.addAttribute("errorMessage", message.message());
    }
    return "fragments/vpd-policy-explanation :: explanation";
  }
}
