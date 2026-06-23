package com.godzilla.distribution.mapper.distribution;

import com.godzilla.distribution.entity.distribution.DistributionLeadFollowUpEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Mapper
@Repository
public interface DistributionLeadFollowUpMapper {
    List<DistributionLeadFollowUpEntity> selectByLeadId(@Param("leadId") Long leadId);

    int insertSelective(DistributionLeadFollowUpEntity row);
}
