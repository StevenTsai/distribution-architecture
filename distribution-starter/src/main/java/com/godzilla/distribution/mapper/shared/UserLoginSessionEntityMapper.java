package com.godzilla.distribution.mapper.shared;

import com.godzilla.distribution.entity.shared.UserLoginSessionEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

import java.util.Date;
import java.util.List;

@Mapper
@Repository
public interface UserLoginSessionEntityMapper {
    List<UserLoginSessionEntity> selectBySkeyAndBiz(@Param("skey") String skey,
                                                     @Param("biz") String biz,
                                                     @Param("now") Date now);
}
