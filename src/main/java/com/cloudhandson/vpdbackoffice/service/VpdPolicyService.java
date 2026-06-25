package com.cloudhandson.vpdbackoffice.service;

import com.cloudhandson.vpdbackoffice.domain.vpd.VpdPolicyView;
import com.cloudhandson.vpdbackoffice.mapper.VpdPolicyMapper;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class VpdPolicyService {

  private final VpdPolicyMapper mapper;

  public VpdPolicyService(VpdPolicyMapper mapper) {
    this.mapper = mapper;
  }

  public List<VpdPolicyView> findPolicies() {
    return mapper.findPolicies();
  }
}
