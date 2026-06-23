package com.cloudhandson.vpdbackoffice.web;

import com.cloudhandson.vpdbackoffice.domain.permission.PermissionSetCommand;
import com.cloudhandson.vpdbackoffice.domain.permission.RuleCommand;
import com.cloudhandson.vpdbackoffice.service.PermissionService;
import com.cloudhandson.vpdbackoffice.service.ProtectedObjectService;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class PermissionController {

  private final PermissionService permissionService;
  private final ProtectedObjectService protectedObjectService;

  public PermissionController(
      PermissionService permissionService,
      ProtectedObjectService protectedObjectService
  ) {
    this.permissionService = permissionService;
    this.protectedObjectService = protectedObjectService;
  }

  @GetMapping("/permissions/object-columns")
  @ResponseBody
  public List<String> objectColumns(@RequestParam String objectRef) {
    if (objectRef == null || objectRef.isBlank()) {
      return List.of();
    }
    if (objectRef.startsWith("protected:")) {
      long objectId;
      try {
        objectId = Long.parseLong(objectRef.substring("protected:".length()));
      } catch (NumberFormatException exception) {
        return List.of();
      }
      return protectedObjectService.findColumns(objectId).stream()
          .map(column -> column.columnName())
          .toList();
    }
    if (objectRef.startsWith("db:")) {
      String value = objectRef.substring("db:".length());
      int dot = value.indexOf('.');
      if (dot < 1 || dot == value.length() - 1) {
        return List.of();
      }
      return protectedObjectService.findDatabaseColumns(value.substring(0, dot), value.substring(dot + 1));
    }
    return List.of();
  }

  @GetMapping("/permissions")
  public String permissions(Model model) {
    var objects = protectedObjectService.findEnabled();
    model.addAttribute("roles", permissionService.findRoles());
    model.addAttribute("objects", objects);
    model.addAttribute("columnsByObject", objects.stream()
        .collect(Collectors.toMap(
            object -> object.objectId(),
            object -> protectedObjectService.findColumns(object.objectId()).stream()
                .map(column -> column.columnName())
                .toList()
        )));
    model.addAttribute("dbObjects", protectedObjectService.findDatabaseObjects());
    model.addAttribute("permissions", permissionService.findPermissionViews());
    return "permissions";
  }

  @PostMapping("/permissions")
  public String save(
      @RequestParam long roleId,
      @RequestParam String objectRef,
      @RequestParam(required = false) List<String> ruleColumn,
      @RequestParam List<String> ruleType,
      @RequestParam(required = false) List<String> ruleValue,
      @RequestParam(required = false) String visibleColumns,
      RedirectAttributes redirectAttributes
  ) {
    long objectId = resolveObjectId(objectRef);
    permissionService.savePermissionSet(new PermissionSetCommand(
        roleId,
        objectId,
        "SELECT",
        buildRules(ruleColumn, ruleType, ruleValue),
        splitColumns(visibleColumns)
    ));
    redirectAttributes.addFlashAttribute("message", "권한을 저장했습니다.");
    return "redirect:/permissions";
  }

  private List<RuleCommand> buildRules(
      List<String> ruleColumns,
      List<String> ruleTypes,
      List<String> ruleValues
  ) {
    if (ruleTypes == null || ruleTypes.isEmpty()) {
      return List.of();
    }
    return java.util.stream.IntStream.range(0, ruleTypes.size())
        .mapToObj(index -> new RuleCommand(
            valueAt(ruleColumns, index),
            valueAt(ruleTypes, index),
            valueAt(ruleValues, index)
        ))
        .toList();
  }

  private String valueAt(List<String> values, int index) {
    return values == null || index >= values.size() ? null : values.get(index);
  }

  private long resolveObjectId(String objectRef) {
    if (objectRef == null || objectRef.isBlank()) {
      throw new IllegalArgumentException("보호 객체를 선택하세요.");
    }
    if (objectRef.startsWith("protected:")) {
      return Long.parseLong(objectRef.substring("protected:".length()));
    }
    if (objectRef.startsWith("db:")) {
      String value = objectRef.substring("db:".length());
      int dot = value.indexOf('.');
      if (dot < 1 || dot == value.length() - 1) {
        throw new IllegalArgumentException("DB 객체 형식이 올바르지 않습니다.");
      }
      return protectedObjectService.ensureProtectedObject(value.substring(0, dot), value.substring(dot + 1)).objectId();
    }
    throw new IllegalArgumentException("보호 객체 형식이 올바르지 않습니다.");
  }

  private List<String> splitColumns(String visibleColumns) {
    if (visibleColumns == null || visibleColumns.isBlank()) {
      return List.of();
    }
    return Arrays.stream(visibleColumns.split(","))
        .map(String::trim)
        .filter(value -> !value.isBlank())
        .toList();
  }

  @PostMapping("/permissions/delete")
  public String delete(@RequestParam long permissionId, RedirectAttributes redirectAttributes) {
    permissionService.deletePermission(permissionId);
    redirectAttributes.addFlashAttribute("message", "권한을 삭제했습니다.");
    return "redirect:/permissions";
  }
}
