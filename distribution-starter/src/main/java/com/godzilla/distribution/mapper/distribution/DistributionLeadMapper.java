package com.godzilla.distribution.mapper.distribution;

import com.godzilla.distribution.entity.distribution.DistributionLeadEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Mapper
@Repository
public interface DistributionLeadMapper {
    long countByCondition(@Param("leadNo") String leadNo,
                          @Param("patientKeyword") String patientKeyword,
                          @Param("sourceDistributorId") Long sourceDistributorId,
                          @Param("intentProductLine") String intentProductLine,
                          @Param("ownerUserId") Long ownerUserId,
                          @Param("stage") String stage);

    List<DistributionLeadEntity> selectByCondition(@Param("leadNo") String leadNo,
                                                   @Param("patientKeyword") String patientKeyword,
                                                   @Param("sourceDistributorId") Long sourceDistributorId,
                                                   @Param("intentProductLine") String intentProductLine,
                                                   @Param("ownerUserId") Long ownerUserId,
                                                   @Param("stage") String stage,
                                                   @Param("offset") int offset,
                                                   @Param("limit") int limit);

    long countByConditionWithScope(@Param("leadNo") String leadNo,
                                   @Param("patientKeyword") String patientKeyword,
                                   @Param("sourceDistributorId") Long sourceDistributorId,
                                   @Param("intentProductLine") String intentProductLine,
                                   @Param("ownerUserId") Long ownerUserId,
                                   @Param("stage") String stage,
                                   @Param("authorizedDistributorIds") List<Long> authorizedDistributorIds,
                                   @Param("authorizedMemberId") Long authorizedMemberId,
                                   @Param("authorizedOwnerUserId") Long authorizedOwnerUserId);

    List<DistributionLeadEntity> selectByConditionWithScope(@Param("leadNo") String leadNo,
                                                            @Param("patientKeyword") String patientKeyword,
                                                            @Param("sourceDistributorId") Long sourceDistributorId,
                                                            @Param("intentProductLine") String intentProductLine,
                                                            @Param("ownerUserId") Long ownerUserId,
                                                            @Param("stage") String stage,
                                                            @Param("authorizedDistributorIds") List<Long> authorizedDistributorIds,
                                                            @Param("authorizedMemberId") Long authorizedMemberId,
                                                            @Param("authorizedOwnerUserId") Long authorizedOwnerUserId,
                                                            @Param("offset") int offset,
                                                            @Param("limit") int limit);

    DistributionLeadEntity selectByPrimaryKey(@Param("id") Long id);

    List<DistributionLeadEntity> selectByIds(@Param("ids") List<Long> ids);

    DistributionLeadEntity selectByPatientPhone(@Param("patientPhone") String patientPhone,
                                                @Param("excludeId") Long excludeId);

    DistributionLeadEntity selectByReferrerAndPatientPhone(@Param("sourceDistributorId") Long sourceDistributorId,
                                                           @Param("sourceMemberId") Long sourceMemberId,
                                                           @Param("patientPhone") String patientPhone,
                                                           @Param("excludeId") Long excludeId);

    DistributionLeadEntity selectDuplicateLead(@Param("patientPhone") String patientPhone,
                                               @Param("intentProductLine") String intentProductLine,
                                               @Param("excludeId") Long excludeId);

    int insertSelective(DistributionLeadEntity row);

    int updateByPrimaryKeySelective(DistributionLeadEntity row);
}
