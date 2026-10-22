package com.godzilla.distribution.mapper.shared;

import com.godzilla.distribution.entity.shared.UserProfileEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

@Mapper
@Repository
public interface UserProfileEntityMapper {
    UserProfileEntity selectByPrimaryKey(@Param("id") Long id);
}
