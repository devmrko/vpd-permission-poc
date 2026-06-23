package com.cloudhandson.vpdbackoffice.mapper;

import com.cloudhandson.vpdbackoffice.domain.token.BearerTokenRecord;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface BearerTokenMapper {

  List<BearerTokenRecord> findAll();

  BearerTokenRecord findById(@Param("keyId") long keyId);

  long nextKeyId();

  void insertToken(BearerTokenRecord token);

  int revokeToken(
      @Param("keyId") long keyId,
      @Param("revokedAt") LocalDateTime revokedAt,
      @Param("reason") String reason
  );
}
