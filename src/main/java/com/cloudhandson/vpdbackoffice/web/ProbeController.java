package com.cloudhandson.vpdbackoffice.web;

import com.cloudhandson.vpdbackoffice.domain.probe.ProbeCommand;
import com.cloudhandson.vpdbackoffice.service.OrdsProbeService;
import com.cloudhandson.vpdbackoffice.service.ProtectedObjectService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class ProbeController {

  private final OrdsProbeService probeService;
  private final ProtectedObjectService protectedObjectService;

  public ProbeController(
      OrdsProbeService probeService,
      ProtectedObjectService protectedObjectService
  ) {
    this.probeService = probeService;
    this.protectedObjectService = protectedObjectService;
  }

  @GetMapping("/probe")
  public String probe(Model model) {
    model.addAttribute("objects", protectedObjectService.findEnabled());
    return "probe";
  }

  @PostMapping("/probe")
  public String run(
      @RequestParam long objectId,
      @RequestParam String bearerToken,
      @RequestParam(defaultValue = "50") int limit,
      Model model
  ) {
    model.addAttribute("result", probeService.runProbe(new ProbeCommand(objectId, bearerToken, limit)));
    return "fragments/probe-result :: result";
  }
}
