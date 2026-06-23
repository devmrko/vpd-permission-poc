package com.cloudhandson.vpdbackoffice.mapper;

import com.cloudhandson.vpdbackoffice.domain.ords.OrdsHandlerView;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface OrdsMetadataMapper {

  List<OrdsHandlerView> findHandlers();

  String findHandlerPackageSource();
}
