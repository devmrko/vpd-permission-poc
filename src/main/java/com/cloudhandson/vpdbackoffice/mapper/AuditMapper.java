package com.cloudhandson.vpdbackoffice.mapper;

import com.cloudhandson.vpdbackoffice.domain.audit.AuditEvent;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AuditMapper {

  void insert(AuditEvent event);
}
