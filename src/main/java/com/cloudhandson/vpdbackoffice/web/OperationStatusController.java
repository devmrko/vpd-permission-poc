package com.cloudhandson.vpdbackoffice.web;

import com.cloudhandson.vpdbackoffice.service.OperationStatusService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class OperationStatusController {

  private final OperationStatusService service;

  public OperationStatusController(OperationStatusService service) {
    this.service = service;
  }

  @GetMapping("/operation-status")
  public String status(Model model) {
    model.addAttribute("rows", service.findRows());
    return "operation-status";
  }
}
