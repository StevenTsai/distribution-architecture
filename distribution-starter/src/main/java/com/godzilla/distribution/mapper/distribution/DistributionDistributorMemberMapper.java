package com.godzilla.distribution.mapper.distribution;

import com.godzilla.distribution.entity.distribution.DistributionDistributorMemberEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Mapper
@Repository
public interface DistributionDistributorMemberMapper {
    long countByCondition(@Param("distributorId") Long distributorId,
                          @Param("status") String status,
                          @Param("roleCode") String roleCode,
                          @Param("phone") String phone);

    List<DistributionDistributorMemberEntity> selectByCondition(@Param("distributorId") Long distributorId,
                                                                @Param("status") String status,
                                                                @Param("roleCode") String roleCode,
                                                                @Param("phone") String phone,
                                                                @Param("offset") int offset,
                                                                @Param("limit") int limit);

    long countByConditionWithScope(@Param("distributorId") Long distributorId,
                                   @Param("status") String status,
                                   @Param("roleCode") String roleCode,
                                   @Param("phone") String phone,
                                   @Param("authorizedDistributorIds") List<Long> authorizedDistributorIds,
                                   @Param("authorizedMemberId") Long authorizedMemberId);

    List<DistributionDistributorMemberEntity> selectByConditionWithScope(@Param("distributorId") Long distributorId,
                                                                         @Param("status") String status,
                                                                         @Param("roleCode") String roleCode,
                                                                         @Param("phone") String phone,
                                                                         @Param("authorizedDistributorIds") List<Long> authorizedDistributorIds,
                                                                         @Param("authorizedMemberId") Long authorizedMemberId,
                                                                         @Param("offset") int offset,
                                                                         @Param("limit") int limit);

    DistributionDistributorMemberEntity selectByPrimaryKey(@Param("id") Long id);

    DistributionDistributorMemberEntity selectByUserId(@Param("userId") Long userId);

    DistributionDistributorMemberEntity selectByDistributorAndPhone(@Param("distributorId") Long distributorId,
                                                                    @Param("phone") String phone);

    DistributionDistributorMemberEntity selectByDistributorAndName(@Param("distributorId") Long distributorId,
                                                                   @Param("name") String name);

    List<DistributionDistributorMemberEntity> selectByIds(@Param("ids") List<Long> ids);

    int insertSelective(DistributionDistributorMemberEntity row);

    int updateByPrimaryKeySelective(DistributionDistributorMemberEntity row);
}
