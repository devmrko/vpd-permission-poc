package com.cloudhandson.vpdbackoffice.web;

import com.cloudhandson.vpdbackoffice.domain.vpd.VpdPolicyCreateCommand;
import com.cloudhandson.vpdbackoffice.service.AppException;
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

  public VpdPolicyController(VpdPolicyService vpdPolicyService) {
    this.vpdPolicyService = vpdPolicyService;
  }

  @GetMapping("/vpd-policies")
  public String policies(
      @RequestParam(required = false) String schemaOwner,
      Model model
  ) {
    populatePolicyModel(schemaOwner, model);
    return "vpd-policies";
  }

  @GetMapping("/vpd-filter-policies")
  public String filterPolicies(
      @RequestParam(required = false) String schemaOwner,
      Model model
  ) {
    populatePolicyModel(schemaOwner, model);
    return "vpd-filter-policies";
  }

  private void populatePolicyModel(String schemaOwner, Model model) {
    try {
      String selectedSchemaOwner = schemaOwner == null ? "" : schemaOwner.trim().toUpperCase();
      model.addAttribute("policies", vpdPolicyService.findPolicies());
      model.addAttribute("vpdTargets", vpdPolicyService.findVpdTargets(selectedSchemaOwner));
      model.addAttribute("selectedSchemaOwner", selectedSchemaOwner);
      model.addAttribute("formOptions", vpdPolicyService.formOptions());
    } catch (DataAccessException exception) {
      RuntimeErrorMessage message = RuntimeErrorMessages.dataAccess(exception);
      model.addAttribute("runtimeError", message);
      model.addAttribute("policies", List.of());
      model.addAttribute("vpdTargets", List.of());
      model.addAttribute("selectedSchemaOwner", "");
      model.addAttribute("formOptions", vpdPolicyService.emptyFormOptions());
    } catch (AppException exception) {
      model.addAttribute("errorMessage", exception.getMessage());
      model.addAttribute("policies", List.of());
      model.addAttribute("vpdTargets", List.of());
      model.addAttribute("selectedSchemaOwner", "");
      model.addAttribute("formOptions", vpdPolicyService.emptyFormOptions());
    }
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
    createPolicyInternal(objectKey, policyName, functionKey, functionOwner, functionName, statementTypes, enabled,
        updateCheck, filterPredicate, redirectAttributes);
    return "redirect:/vpd-policies";
  }

  @PostMapping("/vpd-filter-policies")
  public String createFilterPolicy(
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
    createPolicyInternal(objectKey, policyName, functionKey, functionOwner, functionName, statementTypes, enabled,
        updateCheck, filterPredicate, redirectAttributes);
    return "redirect:/vpd-filter-policies";
  }

  @PostMapping("/vpd-filter-policies/filters")
  public String saveFilter(
      @RequestParam(required = false) String functionOwner,
      @RequestParam String functionName,
      @RequestParam String filterPredicate,
      RedirectAttributes redirectAttributes
  ) {
    try {
      vpdPolicyService.saveFilterFunction(functionOwner, functionName, filterPredicate);
      redirectAttributes.addFlashAttribute("successMessage", "Filter function을 저장했습니다: " + functionName);
    } catch (AppException exception) {
      redirectAttributes.addFlashAttribute("errorMessage", exception.getMessage());
    } catch (DataAccessException exception) {
      RuntimeErrorMessage message = RuntimeErrorMessages.dataAccess(exception);
      redirectAttributes.addFlashAttribute("errorMessage", message.message());
    }
    return "redirect:/vpd-filter-policies";
  }

  @PostMapping("/vpd-filter-policies/replace")
  public String replaceFilterPolicy(
      @RequestParam String oldObjectKey,
      @RequestParam String oldPolicyName,
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
      vpdPolicyService.replacePolicy(oldObjectKey, oldPolicyName, new VpdPolicyCreateCommand(
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
      redirectAttributes.addFlashAttribute("successMessage", "Policy를 수정했습니다: " + policyName);
    } catch (AppException exception) {
      redirectAttributes.addFlashAttribute("errorMessage", exception.getMessage());
    } catch (DataAccessException exception) {
      RuntimeErrorMessage message = RuntimeErrorMessages.dataAccess(exception);
      redirectAttributes.addFlashAttribute("errorMessage", message.message());
    }
    return "redirect:/vpd-filter-policies";
  }

  private void createPolicyInternal(
      String objectKey,
      String policyName,
      String functionKey,
      String functionOwner,
      String functionName,
      List<String> statementTypes,
      boolean enabled,
      boolean updateCheck,
      String filterPredicate,
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
    bulkApplyPolicyInternal(schemaOwner, includeTables, includeViews, functionKey, functionOwner, functionName,
        statementTypes, enabled, updateCheck, filterPredicate, redirectAttributes);
    return "redirect:/vpd-policies";
  }

  @PostMapping("/vpd-filter-policies/bulk")
  public String bulkApplyFilterPolicy(
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
    bulkApplyPolicyInternal(schemaOwner, includeTables, includeViews, functionKey, functionOwner, functionName,
        statementTypes, enabled, updateCheck, filterPredicate, redirectAttributes);
    return "redirect:/vpd-filter-policies";
  }

  private void bulkApplyPolicyInternal(
      String schemaOwner,
      boolean includeTables,
      boolean includeViews,
      String functionKey,
      String functionOwner,
      String functionName,
      List<String> statementTypes,
      boolean enabled,
      boolean updateCheck,
      String filterPredicate,
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

  @GetMapping("/vpd-policies/object-filter-detail")
  public String objectFilterDetail(
      @RequestParam String objectOwner,
      @RequestParam String objectName,
      Model model
  ) {
    try {
      model.addAttribute("detail", vpdPolicyService.findObjectFilterDetail(objectOwner, objectName));
    } catch (AppException exception) {
      model.addAttribute("errorMessage", exception.getMessage());
    } catch (DataAccessException exception) {
      RuntimeErrorMessage message = RuntimeErrorMessages.dataAccess(exception);
      model.addAttribute("errorMessage", message.message());
    }
    return "fragments/vpd-object-filter-detail :: detail";
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
