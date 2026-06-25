package com.cloudhandson.vpdbackoffice.mapper;

import com.cloudhandson.vpdbackoffice.domain.vpd.VpdFunctionOption;
import com.cloudhandson.vpdbackoffice.domain.vpd.VpdPolicyTemplateOption;
import com.cloudhandson.vpdbackoffice.domain.vpd.VpdSchemaObjectOption;
import com.cloudhandson.vpdbackoffice.domain.vpd.VpdPolicyView;
import com.cloudhandson.vpdbackoffice.domain.vpd.VpdTargetView;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface VpdPolicyMapper {

  List<VpdPolicyView> findPolicies();

  List<VpdTargetView> findVpdTargets(@Param("owner") String owner);

  List<String> findPolicyNameOptions();

  List<String> findSchemaOwnerOptions();

  List<String> findOwnerOptions();

  List<VpdFunctionOption> findFunctionOptions();

  List<VpdPolicyTemplateOption> findPolicyTemplateOptions();

  List<VpdSchemaObjectOption> findSchemaObjects(
      @Param("owner") String owner,
      @Param("includeTablesYn") String includeTablesYn,
      @Param("includeViewsYn") String includeViewsYn
  );

  VpdPolicyView findPolicy(
      @Param("objectOwner") String objectOwner,
      @Param("objectName") String objectName,
      @Param("policyName") String policyName
  );

  List<VpdPolicyView> findPoliciesForObject(
      @Param("objectOwner") String objectOwner,
      @Param("objectName") String objectName
  );

  VpdPolicyView findAnyPolicy(
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
