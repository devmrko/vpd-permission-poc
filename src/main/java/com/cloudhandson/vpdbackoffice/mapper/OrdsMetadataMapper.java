package com.cloudhandson.vpdbackoffice.mapper;

import com.cloudhandson.vpdbackoffice.domain.ords.OrdsHandlerView;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface OrdsMetadataMapper {

  List<OrdsHandlerView> findHandlers();

  OrdsHandlerView findHandler(@Param("handlerId") long handlerId);

  String findHandlerPackageSource();
}
