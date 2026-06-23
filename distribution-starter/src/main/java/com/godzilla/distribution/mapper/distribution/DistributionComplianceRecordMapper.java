package com.godzilla.distribution.mapper.distribution;

import com.godzilla.distribution.entity.distribution.DistributionComplianceRecordEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Mapper
@Repository
public interface DistributionComplianceRecordMapper {
    long countByCondition(@Param("bizType") String bizType,
                          @Param("bizId") Long bizId,
                          @Param("recordType") String recordType,
                          @Param("status") String status);

    List<DistributionComplianceRecordEntity> selectByCondition(@Param("bizType") String bizType,
                                                               @Param("bizId") Long bizId,
                                                               @Param("recordType") String recordType,
                                                               @Param("status") String status,
                                                               @Param("offset") int offset,
                                                               @Param("limit") int limit);

    DistributionComplianceRecordEntity selectByPrimaryKey(@Param("id") Long id);

    int insertSelective(DistributionComplianceRecordEntity row);

    int updateByPrimaryKeySelective(DistributionComplianceRecordEntity row);
}
