package com.cloudhandson.vpdbackoffice.mapper;

import com.cloudhandson.vpdbackoffice.domain.vpd.VpdPolicyView;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface VpdPolicyMapper {

  List<VpdPolicyView> findPolicies();

  VpdPolicyView findPolicy(
      @Param("objectOwner") String objectOwner,
      @Param("objectName") String objectName,
      @Param("policyName") String policyName
  );

  String findFunctionSource(
      @Param("owner") String owner,
      @Param("objectName") String objectName,
      @Param("objectType") String objectType
  );
}
