package com.godzilla.distribution.service.distribution.impl;

import com.godzilla.distribution.common.ResultCode;
import com.godzilla.distribution.entity.shared.MedicalUserInfoEntity;
import com.godzilla.distribution.controller.interceptors.AuthHeaderInterceptor;
import com.godzilla.distribution.exception.BizException;
import com.godzilla.distribution.mapper.shared.MedicalUserInfoEntityMapper;
import com.godzilla.distribution.service.distribution.DistributionOperatorService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class DistributionOperatorServiceImpl implements DistributionOperatorService {

    @Autowired
    private MedicalUserInfoEntityMapper medicalUserInfoEntityMapper;

    private Long getCurrentOperatorUserIdFromContext() {
        String userIdValue = AuthHeaderInterceptor.getCurrentUserId();
        if (StringUtils.isBlank(userIdValue)) {
            throw new BizException("未登录或登录已过期", ResultCode.DISTRIBUTION_OPERATOR_NOT_LOGIN.getCode());
        }
        try {
            return Long.parseLong(userIdValue.trim());
        } catch (NumberFormatException e) {
            throw new BizException("未登录或登录已过期", ResultCode.DISTRIBUTION_OPERATOR_NOT_LOGIN.getCode());
        }
    }

    @Override
    public Long getCurrentOperatorUserId() {
        return getCurrentOperatorUserIdFromContext();
    }

    @Override
    public MedicalUserInfoEntity getCurrentOperatorUser() {
        Long userId = getCurrentOperatorUserIdFromContext();
        MedicalUserInfoEntity user = medicalUserInfoEntityMapper.selectByPrimaryKey(userId);
        if (user == null) {
            throw new BizException("当前后台账号绑定的业务用户不存在", ResultCode.DISTRIBUTION_OPERATOR_NOT_BOUND.getCode());
        }
        return user;
    }
}
