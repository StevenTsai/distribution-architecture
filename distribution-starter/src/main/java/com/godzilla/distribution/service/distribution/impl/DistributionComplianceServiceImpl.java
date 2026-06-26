package com.godzilla.distribution.service.distribution.impl;

import com.godzilla.distribution.common.ResultCode;
import com.godzilla.distribution.dto.PageResponseDTO;
import com.godzilla.distribution.dto.distribution.request.ApproveDistributionComplianceRecordRequestDTO;
import com.godzilla.distribution.dto.distribution.request.CreateDistributionComplianceRecordRequestDTO;
import com.godzilla.distribution.dto.distribution.request.RejectDistributionComplianceRecordRequestDTO;
import com.godzilla.distribution.dto.distribution.response.DistributionComplianceRecordDTO;
import com.godzilla.distribution.dto.distribution.response.DistributionComplianceRecordDetailDTO;
import com.godzilla.distribution.entity.distribution.DistributionComplianceRecordEntity;
import com.godzilla.distribution.enums.distribution.DistributionAuditBizType;
import com.godzilla.distribution.enums.distribution.DistributionComplianceRecordType;
import com.godzilla.distribution.enums.distribution.DistributionComplianceStatus;
import com.godzilla.distribution.enums.distribution.DistributionDataScope;
import com.godzilla.distribution.exception.BizException;
import com.godzilla.distribution.mapper.distribution.DistributionComplianceRecordMapper;
import com.godzilla.distribution.service.distribution.DistributionAuditLogService;
import com.godzilla.distribution.service.distribution.DistributionComplianceService;
import com.godzilla.distribution.service.distribution.DistributionDataPermissionService;
import com.godzilla.distribution.service.distribution.DistributionOperatorService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

@Service
public class DistributionComplianceServiceImpl implements DistributionComplianceService {

    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_PAGE_SIZE = 10;
    private static final int MAX_PAGE_SIZE = 100;

    @Autowired
    private DistributionComplianceRecordMapper distributionComplianceRecordMapper;
    @Autowired
    private DistributionOperatorService distributionOperatorService;
    @Autowired
    private DistributionAuditLogService distributionAuditLogService;
    @Autowired
    private DistributionDataPermissionService distributionDataPermissionService;

    @Override
    public PageResponseDTO<DistributionComplianceRecordDTO> listRecords(String bizType, Long bizId, String recordType, String status, Integer page, Integer pageSize) {
        int safePage = page == null || page < 1 ? DEFAULT_PAGE : page;
        int safePageSize = pageSize == null || pageSize < 1 ? DEFAULT_PAGE_SIZE : Math.min(pageSize, MAX_PAGE_SIZE);
        long total = distributionComplianceRecordMapper.countByCondition(trim(bizType), bizId, trim(recordType), trim(status));
        List<DistributionComplianceRecordEntity> entities = distributionComplianceRecordMapper.selectByCondition(
                trim(bizType), bizId, trim(recordType), trim(status), (safePage - 1) * safePageSize, safePageSize);
        return new PageResponseDTO<DistributionComplianceRecordDTO>(buildRecordDTOList(entities), total, safePage, safePageSize);
    }

    @Override
    public DistributionComplianceRecordDetailDTO getRecord(Long id) {
        DistributionComplianceRecordEntity entity = getRecordEntity(id);
        validateComplianceAccess(entity);
        DistributionComplianceRecordDetailDTO detailDTO = new DistributionComplianceRecordDetailDTO();
        BeanUtils.copyProperties(entity, detailDTO);
        return detailDTO;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createRecord(CreateDistributionComplianceRecordRequestDTO request) {
        validateRecordType(request.getRecordType());
        validateCreateScope(request.getBizId());
        Long operatorUserId = distributionOperatorService.getCurrentOperatorUserId();
        DistributionComplianceRecordEntity entity = new DistributionComplianceRecordEntity();
        entity.setBizType(trim(request.getBizType()));
        entity.setBizId(request.getBizId());
        entity.setProductLineCode(trim(request.getProductLineCode()));
        entity.setRecordType(trim(request.getRecordType()));
        entity.setStatus(DistributionComplianceStatus.PENDING.getCode());
        entity.setContent(trim(request.getContent()));
        // attachments handled separately
        entity.setRemark(trim(request.getRemark()));
        entity.setCreatedBy(operatorUserId);
        entity.setUpdatedBy(operatorUserId);
        entity.setDeleted(0);
        distributionComplianceRecordMapper.insertSelective(entity);
        distributionAuditLogService.record(DistributionAuditBizType.COMPLIANCE.getCode(), entity.getId(), "create", null, entity, "创建合规记录");
        return entity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void approveRecord(Long id, ApproveDistributionComplianceRecordRequestDTO request) {
        DistributionComplianceRecordEntity existing = getRecordEntity(id);
        validateComplianceAccess(existing);
        validateStatusTransition(existing.getStatus());
        Long operatorUserId = distributionOperatorService.getCurrentOperatorUserId();
        DistributionComplianceRecordEntity update = new DistributionComplianceRecordEntity();
        update.setId(id);
        update.setStatus(DistributionComplianceStatus.APPROVED.getCode());
        update.setReviewedBy(operatorUserId);
        update.setReviewedAt(new Date());
        update.setUpdatedBy(operatorUserId);
        distributionComplianceRecordMapper.updateByPrimaryKeySelective(update);
        distributionAuditLogService.record(DistributionAuditBizType.COMPLIANCE.getCode(), id, "approve", existing, getRecordEntity(id), "审核通过合规记录");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void rejectRecord(Long id, RejectDistributionComplianceRecordRequestDTO request) {
        DistributionComplianceRecordEntity existing = getRecordEntity(id);
        validateComplianceAccess(existing);
        validateStatusTransition(existing.getStatus());
        Long operatorUserId = distributionOperatorService.getCurrentOperatorUserId();
        DistributionComplianceRecordEntity update = new DistributionComplianceRecordEntity();
        update.setId(id);
        update.setStatus(DistributionComplianceStatus.REJECTED.getCode());
        update.setReviewedBy(operatorUserId);
        update.setReviewedAt(new Date());
        update.setRemark(trim(request.getRemark()));
        update.setUpdatedBy(operatorUserId);
        distributionComplianceRecordMapper.updateByPrimaryKeySelective(update);
        distributionAuditLogService.record(DistributionAuditBizType.COMPLIANCE.getCode(), id, "reject", existing, getRecordEntity(id), "审核拒绝合规记录");
    }

    private DistributionComplianceRecordEntity getRecordEntity(Long id) {
        DistributionComplianceRecordEntity entity = distributionComplianceRecordMapper.selectByPrimaryKey(id);
        if (entity == null || (entity.getDeleted() != null && entity.getDeleted() == 1)) {
            throw new BizException("合规记录不存在", ResultCode.DISTRIBUTION_COMPLIANCE_RECORD_NOT_FOUND.getCode());
        }
        return entity;
    }

    private List<DistributionComplianceRecordDTO> buildRecordDTOList(List<DistributionComplianceRecordEntity> entities) {
        if (entities == null || entities.isEmpty()) return Collections.emptyList();
        List<DistributionComplianceRecordDTO> list = new ArrayList<DistributionComplianceRecordDTO>();
        for (DistributionComplianceRecordEntity entity : entities) {
            DistributionComplianceRecordDTO dto = new DistributionComplianceRecordDTO();
            BeanUtils.copyProperties(entity, dto);
            list.add(dto);
        }
        return list;
    }

    private void validateCreateScope(Long bizId) {
        if (bizId == null) return;
        DistributionDataPermissionService.DistributionDataAccessScope accessScope = distributionDataPermissionService.resolveCurrentAccessScope();
        if (accessScope.isAllScope()) return;
        List<Long> authorizedDistributorIds = accessScope.getAuthorizedDistributorIds();
        if (authorizedDistributorIds == null || !authorizedDistributorIds.contains(bizId)) {
            throw new BizException("无权在该渠道下创建数据", ResultCode.DISTRIBUTION_DATA_ACCESS_DENIED.getCode());
        }
    }

    private void validateComplianceAccess(DistributionComplianceRecordEntity entity) {
        if (entity == null) return;
        DistributionDataPermissionService.DistributionDataAccessScope accessScope = distributionDataPermissionService.resolveCurrentAccessScope();
        if (accessScope.isAllScope()) return;
        List<Long> authorizedDistributorIds = accessScope.getAuthorizedDistributorIds();
        // Check ownership: for distributor-type records, bizId is the distributorId;
        // for member/lead records, we require ALL scope to access cross-distributor data
        if (entity.getBizId() != null && authorizedDistributorIds != null
                && authorizedDistributorIds.contains(entity.getBizId())) {
            return; // authorized
        }
        throw new BizException("无权访问该数据", ResultCode.DISTRIBUTION_DATA_ACCESS_DENIED.getCode());
    }

    private void validateRecordType(String recordType) {
        if (!DistributionComplianceRecordType.isValid(trim(recordType))) {
            throw new BizException("合规记录类型不合法", ResultCode.DISTRIBUTION_COMPLIANCE_RECORD_TYPE_INVALID.getCode());
        }
    }

    private void validateStatusTransition(String currentStatus) {
        if (!DistributionComplianceStatus.PENDING.getCode().equals(currentStatus)) {
            throw new BizException("只有待审核状态的记录才能审核", ResultCode.DISTRIBUTION_COMPLIANCE_STATUS_TRANSITION_INVALID.getCode());
        }
    }

    private String trim(String value) {
        return StringUtils.trimToNull(value);
    }
}
