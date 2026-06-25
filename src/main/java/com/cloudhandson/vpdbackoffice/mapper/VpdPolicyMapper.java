package com.cloudhandson.vpdbackoffice.mapper;

import com.cloudhandson.vpdbackoffice.domain.vpd.VpdPolicyView;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface VpdPolicyMapper {

  List<VpdPolicyView> findPolicies();
}
