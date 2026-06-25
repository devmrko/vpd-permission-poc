package com.cloudhandson.vpdbackoffice.mapper;

import com.cloudhandson.vpdbackoffice.domain.operation.OperationStatusRow;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface OperationStatusMapper {

  List<OperationStatusRow> findRows();
}
