package com.cloudhandson.vpdbackoffice.web;

import com.cloudhandson.vpdbackoffice.domain.ords.OrdsHandlerUpdateCommand;
import com.cloudhandson.vpdbackoffice.service.AppException;
import com.cloudhandson.vpdbackoffice.service.OrdsMetadataService;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class OrdsHandlerController {

  private final OrdsMetadataService ordsMetadataService;

  public OrdsHandlerController(OrdsMetadataService ordsMetadataService) {
    this.ordsMetadataService = ordsMetadataService;
  }

  @GetMapping("/ords-handlers")
  public String handlers(Model model) {
    model.addAttribute("handlers", ordsMetadataService.findHandlers());
    return "ords-handlers";
  }

  @PostMapping("/ords-handlers/update")
  public String update(
      @RequestParam long handlerId,
      @RequestParam String source,
      RedirectAttributes redirectAttributes
  ) {
    try {
      ordsMetadataService.updateHandlerSource(new OrdsHandlerUpdateCommand(handlerId, source));
      redirectAttributes.addFlashAttribute("message", "ORDS Handler Source를 저장했습니다.");
    } catch (AppException exception) {
      redirectAttributes.addFlashAttribute("errorMessage", exception.getMessage());
    } catch (DataAccessException exception) {
      RuntimeErrorMessage error = RuntimeErrorMessages.dataAccess(exception);
      redirectAttributes.addFlashAttribute("errorMessage", error.message());
    }
    return "redirect:/ords-handlers";
  }
}
