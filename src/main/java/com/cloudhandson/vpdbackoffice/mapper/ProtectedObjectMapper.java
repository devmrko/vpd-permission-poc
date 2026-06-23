package com.cloudhandson.vpdbackoffice.mapper;

import com.cloudhandson.vpdbackoffice.domain.protectedobject.ProtectedColumn;
import com.cloudhandson.vpdbackoffice.domain.protectedobject.ProtectedObjectCreateCommand;
import com.cloudhandson.vpdbackoffice.domain.protectedobject.ProtectedObject;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ProtectedObjectMapper {

  List<ProtectedObject> findEnabled();

  ProtectedObject findById(@Param("objectId") long objectId);

  List<ProtectedColumn> findColumns(@Param("objectId") long objectId);

  long nextObjectId();

  long nextColumnId();

  void insertObject(@Param("objectId") long objectId, @Param("command") ProtectedObjectCreateCommand command);

  void insertColumn(@Param("columnId") long columnId,
                    @Param("objectId") long objectId,
                    @Param("columnName") String columnName,
                    @Param("sensitiveYn") String sensitiveYn);

  int disableObject(@Param("objectId") long objectId);
}
