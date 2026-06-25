package com.cloudhandson.vpdbackoffice.service;

import com.cloudhandson.vpdbackoffice.domain.operation.OperationStatusRow;
import com.cloudhandson.vpdbackoffice.mapper.OperationStatusMapper;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class OperationStatusService {

  private final OperationStatusMapper mapper;

  public OperationStatusService(OperationStatusMapper mapper) {
    this.mapper = mapper;
  }

  public List<OperationStatusRow> findRows() {
    return mapper.findRows();
  }
}
