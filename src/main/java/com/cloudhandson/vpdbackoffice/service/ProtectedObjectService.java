package com.cloudhandson.vpdbackoffice.service;

import com.cloudhandson.vpdbackoffice.domain.protectedobject.ProtectedColumn;
import com.cloudhandson.vpdbackoffice.domain.protectedobject.ProtectedObject;
import com.cloudhandson.vpdbackoffice.mapper.ProtectedObjectMapper;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ProtectedObjectService {

  private final ProtectedObjectMapper mapper;

  public ProtectedObjectService(ProtectedObjectMapper mapper) {
    this.mapper = mapper;
  }

  public List<ProtectedObject> findEnabled() {
    return mapper.findEnabled();
  }

  public ProtectedObject assertEnabled(long objectId) {
    ProtectedObject object = mapper.findById(objectId);
    if (object == null || !object.enabled()) {
      throw new AppException("활성 보호 객체를 찾을 수 없습니다.");
    }
    return object;
  }

  public List<ProtectedColumn> findColumns(long objectId) {
    return mapper.findColumns(objectId);
  }
}
