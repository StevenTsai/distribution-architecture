package com.godzilla.distribution.mapper.distribution;

import com.godzilla.distribution.entity.distribution.DistributionDistributorEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Mapper
@Repository
public interface DistributionDistributorMapper {
    long countByCondition(@Param("name") String name,
                          @Param("status") String status,
                          @Param("levelCode") String levelCode,
                          @Param("productLine") String productLine);

    List<DistributionDistributorEntity> selectByCondition(@Param("name") String name,
                                                          @Param("status") String status,
                                                          @Param("levelCode") String levelCode,
                                                          @Param("productLine") String productLine,
                                                          @Param("offset") int offset,
                                                          @Param("limit") int limit);

    long countByConditionWithScope(@Param("name") String name,
                                   @Param("status") String status,
                                   @Param("levelCode") String levelCode,
                                   @Param("productLine") String productLine,
                                   @Param("authorizedDistributorIds") List<Long> authorizedDistributorIds);

    List<DistributionDistributorEntity> selectByConditionWithScope(@Param("name") String name,
                                                                   @Param("status") String status,
                                                                   @Param("levelCode") String levelCode,
                                                                   @Param("productLine") String productLine,
                                                                   @Param("authorizedDistributorIds") List<Long> authorizedDistributorIds,
                                                                   @Param("offset") int offset,
                                                                   @Param("limit") int limit);

    DistributionDistributorEntity selectByPrimaryKey(@Param("id") Long id);

    List<DistributionDistributorEntity> selectByIds(@Param("ids") List<Long> ids);

    DistributionDistributorEntity selectByCode(@Param("code") String code);

    List<DistributionDistributorEntity> selectByParentIds(@Param("parentIds") List<Long> parentIds);

    int insertSelective(DistributionDistributorEntity row);

    int updateByPrimaryKeySelective(DistributionDistributorEntity row);
}
