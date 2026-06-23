package com.godzilla.distribution.mapper.shared;

import com.godzilla.distribution.entity.shared.AdminUserEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

@Mapper
@Repository
public interface AdminUserEntityMapper {
    AdminUserEntity selectByPrimaryKey(@Param("id") Long id);
}
