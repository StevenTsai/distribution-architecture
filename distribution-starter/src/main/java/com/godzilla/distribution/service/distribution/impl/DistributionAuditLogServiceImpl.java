package com.godzilla.distribution.service.distribution.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.godzilla.distribution.common.ResultCode;
import com.godzilla.distribution.dto.PageResponseDTO;
import com.godzilla.distribution.dto.distribution.response.DistributionAuditLogDTO;
import com.godzilla.distribution.dto.distribution.response.DistributionAuditLogDetailDTO;
import com.godzilla.distribution.entity.shared.MedicalUserInfoEntity;
import com.godzilla.distribution.entity.distribution.DistributionAuditLogEntity;
import com.godzilla.distribution.enums.distribution.DistributionAuditBizType;
import com.godzilla.distribution.exception.BizException;
import com.godzilla.distribution.mapper.distribution.DistributionAuditLogMapper;
import com.godzilla.distribution.enums.distribution.DistributionDataScope;
import com.godzilla.distribution.service.distribution.DistributionAuditLogService;
import com.godzilla.distribution.service.distribution.impl.DistributionDataPermissionService;
import com.godzilla.distribution.service.distribution.DistributionOperatorService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
public class DistributionAuditLogServiceImpl implements DistributionAuditLogService {

    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_PAGE_SIZE = 10;
    private static final int MAX_PAGE_SIZE = 100;

    @Autowired
    private DistributionAuditLogMapper distributionAuditLogMapper;

    @Autowired
    private DistributionOperatorService distributionOperatorService;

    @Autowired
    private DistributionDataPermissionService distributionDataPermissionService;

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public PageResponseDTO<DistributionAuditLogDTO> listLogs(String bizType,
                                                             Long bizId,
                                                             String action,
                                                             Integer page,
                                                             Integer pageSize) {
        validateOptionalBizType(bizType);
        int safePage = page == null || page < 1 ? DEFAULT_PAGE : page;
        int safePageSize = pageSize == null || pageSize < 1 ? DEFAULT_PAGE_SIZE : Math.min(pageSize, MAX_PAGE_SIZE);
        long total = distributionAuditLogMapper.countByCondition(trim(bizType), bizId, trim(action));
        List<DistributionAuditLogEntity> entities = distributionAuditLogMapper.selectByCondition(
                trim(bizType), bizId, trim(action), (safePage - 1) * safePageSize, safePageSize);
        return new PageResponseDTO<DistributionAuditLogDTO>(buildAuditLogDTOList(entities), total, safePage, safePageSize);
    }

    @Override
    public DistributionAuditLogDetailDTO getLog(Long id) {
        DistributionAuditLogEntity entity = distributionAuditLogMapper.selectByPrimaryKey(id);
        if (entity == null) {
            throw new BizException("审计日志不存在", ResultCode.DISTRIBUTION_AUDIT_LOG_NOT_FOUND.getCode());
        }
        validateAuditLogAccess(entity);
        DistributionAuditLogDetailDTO detailDTO = new DistributionAuditLogDetailDTO();
        BeanUtils.copyProperties(entity, detailDTO);
        return detailDTO;
    }

    @Override
    public void record(String bizType, Long bizId, String action, Object beforeSnapshot, Object afterSnapshot, String remark) {
        MedicalUserInfoEntity operator = distributionOperatorService.getCurrentOperatorUser();

        DistributionAuditLogEntity entity = new DistributionAuditLogEntity();
        entity.setBizType(bizType);
        entity.setBizId(bizId);
        entity.setAction(action);
        entity.setBeforeSnapshot(writeSnapshot(beforeSnapshot));
        entity.setAfterSnapshot(writeSnapshot(afterSnapshot));
        entity.setOperatorUserId(operator.getId());
        entity.setOperatorName(operator.getName());
        entity.setRemark(remark);
        distributionAuditLogMapper.insertSelective(entity);
    }

    private String writeSnapshot(Object snapshot) {
        if (snapshot == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("审计快照序列化失败", e);
        }
    }

    private void validateAuditLogAccess(DistributionAuditLogEntity entity) {
        if (entity == null) return;
        DistributionDataPermissionService.DistributionDataAccessScope accessScope = distributionDataPermissionService.resolveCurrentAccessScope();
        if (accessScope.isAllScope()) return;
        List<Long> authorizedDistributorIds = accessScope.getAuthorizedDistributorIds();
        if (entity.getBizId() != null && authorizedDistributorIds != null
                && authorizedDistributorIds.contains(entity.getBizId())) {
            return; // authorized
        }
        throw new BizException("无权访问该数据", ResultCode.DISTRIBUTION_DATA_ACCESS_DENIED.getCode());
    }

    private void validateOptionalBizType(String bizType) {
        if (StringUtils.isBlank(trim(bizType))) {
            return;
        }
        if (!DistributionAuditBizType.isValid(trim(bizType))) {
            throw new BizException("审计业务类型不合法", ResultCode.DISTRIBUTION_AUDIT_BIZ_TYPE_INVALID.getCode());
        }
    }

    private List<DistributionAuditLogDTO> buildAuditLogDTOList(List<DistributionAuditLogEntity> entities) {
        if (entities == null || entities.isEmpty()) {
            return Collections.emptyList();
        }
        List<DistributionAuditLogDTO> list = new ArrayList<DistributionAuditLogDTO>();
        for (DistributionAuditLogEntity entity : entities) {
            DistributionAuditLogDTO dto = new DistributionAuditLogDTO();
            BeanUtils.copyProperties(entity, dto);
            list.add(dto);
        }
        return list;
    }

    private String trim(String value) {
        return StringUtils.trimToNull(value);
    }
}
