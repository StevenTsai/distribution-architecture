package com.godzilla.distribution.mapper.shared;

import com.godzilla.distribution.entity.shared.MedicalUserInfoEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

@Mapper
@Repository
public interface MedicalUserInfoEntityMapper {
    MedicalUserInfoEntity selectByPrimaryKey(@Param("id") Long id);
}
