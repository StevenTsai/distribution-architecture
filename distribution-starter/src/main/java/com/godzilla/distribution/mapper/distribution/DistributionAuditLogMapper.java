package com.godzilla.distribution.mapper.distribution;

import com.godzilla.distribution.entity.distribution.DistributionAuditLogEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Mapper
@Repository
public interface DistributionAuditLogMapper {
    long countByCondition(@Param("bizType") String bizType,
                          @Param("bizId") Long bizId,
                          @Param("action") String action);

    List<DistributionAuditLogEntity> selectByCondition(@Param("bizType") String bizType,
                                                       @Param("bizId") Long bizId,
                                                       @Param("action") String action,
                                                       @Param("offset") int offset,
                                                       @Param("limit") int limit);

    DistributionAuditLogEntity selectByPrimaryKey(@Param("id") Long id);

    int insertSelective(DistributionAuditLogEntity row);
}
