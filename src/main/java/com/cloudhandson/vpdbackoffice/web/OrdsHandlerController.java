package com.cloudhandson.vpdbackoffice.web;

import com.cloudhandson.vpdbackoffice.service.OrdsMetadataService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

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
}
