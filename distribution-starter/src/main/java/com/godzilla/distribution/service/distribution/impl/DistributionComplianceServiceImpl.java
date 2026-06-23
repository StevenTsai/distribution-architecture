package com.godzilla.distribution.service.distribution.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.godzilla.distribution.common.ResultCode;
import com.godzilla.distribution.dto.PageResponseDTO;
import com.godzilla.distribution.dto.distribution.request.ApproveDistributionComplianceRecordRequestDTO;
import com.godzilla.distribution.dto.distribution.request.CreateDistributionComplianceRecordRequestDTO;
import com.godzilla.distribution.dto.distribution.request.RejectDistributionComplianceRecordRequestDTO;
import com.godzilla.distribution.dto.distribution.response.DistributionComplianceRecordDTO;
import com.godzilla.distribution.dto.distribution.response.DistributionComplianceRecordDetailDTO;
import com.godzilla.distribution.entity.distribution.DistributionComplianceRecordEntity;
import com.godzilla.distribution.entity.distribution.DistributionBusinessOrderEntity;
import com.godzilla.distribution.entity.distribution.DistributionCommissionLedgerEntity;
import com.godzilla.distribution.entity.distribution.DistributionDistributorEntity;
import com.godzilla.distribution.entity.distribution.DistributionDistributorMemberEntity;
import com.godzilla.distribution.entity.distribution.DistributionLeadEntity;
import com.godzilla.distribution.entity.distribution.DistributionPatientAttributionEntity;
import com.godzilla.distribution.entity.distribution.DistributionProductPolicyEntity;
import com.godzilla.distribution.entity.distribution.DistributionSettlementEntity;
import com.godzilla.distribution.enums.distribution.DistributionAuditBizType;
import com.godzilla.distribution.enums.distribution.DistributionComplianceRecordType;
import com.godzilla.distribution.enums.distribution.DistributionComplianceStatus;
import com.godzilla.distribution.exception.BizException;
import com.godzilla.distribution.mapper.distribution.DistributionComplianceRecordMapper;
import com.godzilla.distribution.mapper.distribution.DistributionBusinessOrderMapper;
import com.godzilla.distribution.mapper.distribution.DistributionCommissionLedgerMapper;
import com.godzilla.distribution.mapper.distribution.DistributionDistributorMapper;
import com.godzilla.distribution.mapper.distribution.DistributionDistributorMemberMapper;
import com.godzilla.distribution.mapper.distribution.DistributionLeadMapper;
import com.godzilla.distribution.mapper.distribution.DistributionPatientAttributionMapper;
import com.godzilla.distribution.mapper.distribution.DistributionProductPolicyMapper;
import com.godzilla.distribution.mapper.distribution.DistributionSettlementMapper;
import com.godzilla.distribution.service.distribution.DistributionAuditLogService;
import com.godzilla.distribution.service.distribution.DistributionComplianceService;
import com.godzilla.distribution.service.distribution.DistributionOperatorService;
import com.godzilla.distribution.service.distribution.DistributionProductLineService;
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
    private static final int BATCH_SIZE = 200;
    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<List<String>>() {};

    @Autowired
    private DistributionComplianceRecordMapper distributionComplianceRecordMapper;

    @Autowired
    private DistributionLeadMapper distributionLeadMapper;

    @Autowired
    private DistributionPatientAttributionMapper distributionPatientAttributionMapper;

    @Autowired
    private DistributionBusinessOrderMapper distributionBusinessOrderMapper;

    @Autowired
    private DistributionCommissionLedgerMapper distributionCommissionLedgerMapper;

    @Autowired
    private DistributionSettlementMapper distributionSettlementMapper;

    @Autowired
    private DistributionDistributorMapper distributionDistributorMapper;

    @Autowired
    private DistributionDistributorMemberMapper distributionDistributorMemberMapper;

    @Autowired
    private DistributionProductPolicyMapper distributionProductPolicyMapper;

    @Autowired
    private DistributionOperatorService distributionOperatorService;

    @Autowired
    private DistributionAuditLogService distributionAuditLogService;

    @Autowired
    private DistributionProductLineService distributionProductLineService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DistributionDataPermissionService distributionDataPermissionService;

    @Override
    public PageResponseDTO<DistributionComplianceRecordDTO> listRecords(String bizType,
                                                                        Long bizId,
                                                                        String recordType,
                                                                        String status,
                                                                        Integer page,
                                                                        Integer pageSize) {
        validateOptionalBizType(bizType);
        validateOptionalRecordType(recordType);
        validateOptionalStatus(status);
        int safePage = page == null || page < 1 ? DEFAULT_PAGE : page;
        int safePageSize = pageSize == null || pageSize < 1 ? DEFAULT_PAGE_SIZE : Math.min(pageSize, MAX_PAGE_SIZE);
        String safeBizType = trim(bizType);
        String safeRecordType = trim(recordType);
        String safeStatus = trim(status);
        DistributionDataPermissionService.DistributionDataAccessScope accessScope = distributionDataPermissionService.resolveCurrentAccessScope();
        if (accessScope.isAllScope()) {
            long total = distributionComplianceRecordMapper.countByCondition(safeBizType, bizId, safeRecordType, safeStatus);
            List<DistributionComplianceRecordEntity> entities = distributionComplianceRecordMapper.selectByCondition(
                    safeBizType, bizId, safeRecordType, safeStatus, (safePage - 1) * safePageSize, safePageSize);
            return new PageResponseDTO<DistributionComplianceRecordDTO>(buildComplianceDTOList(entities), total, safePage, safePageSize);
        }
        List<DistributionComplianceRecordEntity> accessibleRecords = listAccessibleRecords(accessScope, safeBizType, bizId, safeRecordType, safeStatus);
        int fromIndex = Math.min((safePage - 1) * safePageSize, accessibleRecords.size());
        int toIndex = Math.min(fromIndex + safePageSize, accessibleRecords.size());
        return new PageResponseDTO<DistributionComplianceRecordDTO>(
                buildComplianceDTOList(accessibleRecords.subList(fromIndex, toIndex)),
                accessibleRecords.size(),
                safePage,
                safePageSize
        );
    }

    @Override
    public DistributionComplianceRecordDetailDTO getRecord(Long id) {
        DistributionComplianceRecordEntity entity = getRecordEntity(id);
        ensureRecordAccess(entity);
        DistributionComplianceRecordDetailDTO detailDTO = new DistributionComplianceRecordDetailDTO();
        BeanUtils.copyProperties(entity, detailDTO);
        detailDTO.setAttachments(readAttachments(entity.getAttachmentsJson()));
        return detailDTO;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createRecord(CreateDistributionComplianceRecordRequestDTO request) {
        validateCreateRequest(request);
        ensureBizAccess(trim(request.getBizType()), request.getBizId());
        Long operatorUserId = distributionOperatorService.getCurrentOperatorUserId();

        DistributionComplianceRecordEntity entity = new DistributionComplianceRecordEntity();
        entity.setBizType(trim(request.getBizType()));
        entity.setBizId(request.getBizId());
        entity.setProductLineCode(trim(request.getProductLineCode()));
        entity.setRecordType(trim(request.getRecordType()));
        entity.setStatus(DistributionComplianceStatus.PENDING.getCode());
        entity.setContent(trim(request.getContent()));
        entity.setAttachmentsJson(writeAttachments(request.getAttachments()));
        entity.setRemark(trim(request.getRemark()));
        entity.setCreatedBy(operatorUserId);
        entity.setUpdatedBy(operatorUserId);
        entity.setDeleted(0);
        distributionComplianceRecordMapper.insertSelective(entity);

        distributionAuditLogService.record(
                DistributionAuditBizType.COMPLIANCE.getCode(),
                entity.getId(),
                "create",
                null,
                getRecord(entity.getId()),
                defaultValue(trim(request.getRemark()), "创建分销合规记录")
        );
        return entity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void approveRecord(Long id, ApproveDistributionComplianceRecordRequestDTO request) {
        DistributionComplianceRecordEntity existing = getRecordEntity(id);
        ensureRecordAccess(existing);
        ensureStatusTransition(existing, DistributionComplianceStatus.PENDING.getCode(), DistributionComplianceStatus.APPROVED.getCode());

        DistributionComplianceRecordEntity update = new DistributionComplianceRecordEntity();
        update.setId(id);
        update.setStatus(DistributionComplianceStatus.APPROVED.getCode());
        update.setReviewedBy(distributionOperatorService.getCurrentOperatorUserId());
        update.setReviewedAt(new Date());
        update.setRemark(defaultValue(request == null ? null : trim(request.getRemark()), "合规审核通过"));
        update.setUpdatedBy(distributionOperatorService.getCurrentOperatorUserId());
        distributionComplianceRecordMapper.updateByPrimaryKeySelective(update);

        distributionAuditLogService.record(
                DistributionAuditBizType.COMPLIANCE.getCode(),
                id,
                "approve",
                getRecordSnapshot(existing),
                getRecord(id),
                defaultValue(request == null ? null : trim(request.getRemark()), "合规审核通过")
        );
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void rejectRecord(Long id, RejectDistributionComplianceRecordRequestDTO request) {
        DistributionComplianceRecordEntity existing = getRecordEntity(id);
        ensureRecordAccess(existing);
        ensureStatusTransition(existing, DistributionComplianceStatus.PENDING.getCode(), DistributionComplianceStatus.REJECTED.getCode());

        DistributionComplianceRecordEntity update = new DistributionComplianceRecordEntity();
        update.setId(id);
        update.setStatus(DistributionComplianceStatus.REJECTED.getCode());
        update.setReviewedBy(distributionOperatorService.getCurrentOperatorUserId());
        update.setReviewedAt(new Date());
        update.setRemark(defaultValue(request == null ? null : trim(request.getRemark()), "合规审核驳回"));
        update.setUpdatedBy(distributionOperatorService.getCurrentOperatorUserId());
        distributionComplianceRecordMapper.updateByPrimaryKeySelective(update);

        distributionAuditLogService.record(
                DistributionAuditBizType.COMPLIANCE.getCode(),
                id,
                "reject",
                getRecordSnapshot(existing),
                getRecord(id),
                defaultValue(request == null ? null : trim(request.getRemark()), "合规审核驳回")
        );
    }

    private void validateCreateRequest(CreateDistributionComplianceRecordRequestDTO request) {
        if (request == null) {
            throw new BizException("合规记录不能为空", ResultCode.FAIL.getCode());
        }
        validateRequiredBizType(request.getBizType());
        if (request.getBizId() == null) {
            throw new BizException("业务ID不能为空", ResultCode.FAIL.getCode());
        }
        validateRequiredRecordType(request.getRecordType());
        validateOptionalProductLine(request.getProductLineCode());
    }

    private DistributionComplianceRecordEntity getRecordEntity(Long id) {
        DistributionComplianceRecordEntity entity = distributionComplianceRecordMapper.selectByPrimaryKey(id);
        if (entity == null || (entity.getDeleted() != null && entity.getDeleted() == 1)) {
            throw new BizException("合规记录不存在", ResultCode.DISTRIBUTION_COMPLIANCE_RECORD_NOT_FOUND.getCode());
        }
        return entity;
    }

    private List<DistributionComplianceRecordEntity> listAccessibleRecords(DistributionDataPermissionService.DistributionDataAccessScope accessScope,
                                                                           String bizType,
                                                                           Long bizId,
                                                                           String recordType,
                                                                           String status) {
        long total = distributionComplianceRecordMapper.countByCondition(bizType, bizId, recordType, status);
        if (total <= 0) {
            return Collections.emptyList();
        }
        List<DistributionComplianceRecordEntity> records = new ArrayList<DistributionComplianceRecordEntity>();
        for (int offset = 0; offset < total; offset += BATCH_SIZE) {
            List<DistributionComplianceRecordEntity> batch = distributionComplianceRecordMapper.selectByCondition(
                    bizType,
                    bizId,
                    recordType,
                    status,
                    offset,
                    BATCH_SIZE
            );
            if (batch == null || batch.isEmpty()) {
                break;
            }
            for (DistributionComplianceRecordEntity entity : batch) {
                if (hasBizAccess(accessScope, entity.getBizType(), entity.getBizId())) {
                    records.add(entity);
                }
            }
        }
        return records;
    }

    private void ensureRecordAccess(DistributionComplianceRecordEntity entity) {
        ensureBizAccess(entity == null ? null : entity.getBizType(), entity == null ? null : entity.getBizId());
    }

    private void ensureBizAccess(String bizType, Long bizId) {
        DistributionDataPermissionService.DistributionDataAccessScope accessScope = distributionDataPermissionService.resolveCurrentAccessScope();
        if (accessScope.isAllScope()) {
            return;
        }
        if (!hasBizAccess(accessScope, bizType, bizId)) {
            throw new BizException("无权访问该数据", ResultCode.DISTRIBUTION_DATA_ACCESS_DENIED.getCode());
        }
    }

    private boolean hasBizAccess(DistributionDataPermissionService.DistributionDataAccessScope accessScope,
                                 String bizType,
                                 Long bizId) {
        String safeBizType = trim(bizType);
        if (safeBizType == null || bizId == null) {
            return false;
        }
        if (DistributionAuditBizType.POLICY.getCode().equals(safeBizType)) {
            DistributionProductPolicyEntity policy = distributionProductPolicyMapper.selectByPrimaryKey(bizId);
            return policy != null && !accessScope.isSelfScope();
        }
        if (DistributionAuditBizType.DISTRIBUTOR.getCode().equals(safeBizType)) {
            DistributionDistributorEntity distributor = distributionDistributorMapper.selectByPrimaryKey(bizId);
            return distributor != null
                    && (distributor.getDeleted() == null || distributor.getDeleted() == 0)
                    && hasDistributorAccess(accessScope, distributor.getId());
        }
        if (DistributionAuditBizType.MEMBER.getCode().equals(safeBizType)) {
            DistributionDistributorMemberEntity member = distributionDistributorMemberMapper.selectByPrimaryKey(bizId);
            return member != null
                    && (member.getDeleted() == null || member.getDeleted() == 0)
                    && hasScopedAccess(accessScope, member.getDistributorId(), member.getId(), member.getUserId());
        }
        if (DistributionAuditBizType.LEAD.getCode().equals(safeBizType)) {
            DistributionLeadEntity lead = distributionLeadMapper.selectByPrimaryKey(bizId);
            return lead != null
                    && (lead.getDeleted() == null || lead.getDeleted() == 0)
                    && hasScopedAccess(accessScope, lead.getSourceDistributorId(), lead.getSourceMemberId(), lead.getOwnerUserId());
        }
        if (DistributionAuditBizType.ATTRIBUTION.getCode().equals(safeBizType)) {
            DistributionPatientAttributionEntity attribution = distributionPatientAttributionMapper.selectByPrimaryKey(bizId);
            return attribution != null
                    && (attribution.getDeleted() == null || attribution.getDeleted() == 0)
                    && hasAttributionAccess(accessScope, attribution);
        }
        if (DistributionAuditBizType.BUSINESS_ORDER.getCode().equals(safeBizType)) {
            DistributionBusinessOrderEntity businessOrder = distributionBusinessOrderMapper.selectByPrimaryKey(bizId);
            return businessOrder != null
                    && (businessOrder.getDeleted() == null || businessOrder.getDeleted() == 0)
                    && hasScopedAccess(accessScope, businessOrder.getDistributorId(), businessOrder.getMemberId(), businessOrder.getCurrentOwnerUserId());
        }
        if (DistributionAuditBizType.COMMISSION.getCode().equals(safeBizType)) {
            DistributionCommissionLedgerEntity ledger = distributionCommissionLedgerMapper.selectByPrimaryKey(bizId);
            return ledger != null
                    && (ledger.getDeleted() == null || ledger.getDeleted() == 0)
                    && hasScopedAccess(accessScope, ledger.getDistributorId(), ledger.getMemberId(), null);
        }
        if (DistributionAuditBizType.SETTLEMENT.getCode().equals(safeBizType)) {
            DistributionSettlementEntity settlement = distributionSettlementMapper.selectByPrimaryKey(bizId);
            return settlement != null
                    && (settlement.getDeleted() == null || settlement.getDeleted() == 0)
                    && hasSettlementAccess(accessScope, settlement);
        }
        return false;
    }

    private boolean hasScopedAccess(DistributionDataPermissionService.DistributionDataAccessScope accessScope,
                                    Long distributorId,
                                    Long memberId,
                                    Long ownerUserId) {
        if (accessScope.isAllScope()) {
            return true;
        }
        if (accessScope.isSelfScope()) {
            boolean matched = false;
            if (memberId != null && accessScope.getMemberId().equals(memberId)) {
                matched = true;
            }
            if (ownerUserId != null && accessScope.getOperatorUserId().equals(ownerUserId)) {
                matched = true;
            }
            return matched;
        }
        List<Long> authorizedDistributorIds = accessScope.getAuthorizedDistributorIds();
        return authorizedDistributorIds != null
                && distributorId != null
                && authorizedDistributorIds.contains(distributorId);
    }

    private boolean hasAttributionAccess(DistributionDataPermissionService.DistributionDataAccessScope accessScope,
                                         DistributionPatientAttributionEntity attribution) {
        if (accessScope.isSelfScope()) {
            return matchesMember(accessScope.getMemberId(), attribution.getCurrentMemberId())
                    || matchesMember(accessScope.getMemberId(), attribution.getFirstMemberId());
        }
        return hasScopedAccess(accessScope, attribution.getCurrentDistributorId(), null, null);
    }

    private boolean hasDistributorAccess(DistributionDataPermissionService.DistributionDataAccessScope accessScope,
                                         Long distributorId) {
        if (accessScope.isSelfScope()) {
            return distributorId != null && distributorId.equals(accessScope.getDistributorId());
        }
        return hasScopedAccess(accessScope, distributorId, null, null);
    }

    private boolean hasSettlementAccess(DistributionDataPermissionService.DistributionDataAccessScope accessScope,
                                        DistributionSettlementEntity settlement) {
        if (accessScope.isSelfScope()) {
            return false;
        }
        return hasScopedAccess(accessScope, settlement.getDistributorId(), null, null);
    }

    private boolean matchesMember(Long scopedMemberId, Long targetMemberId) {
        return scopedMemberId != null && scopedMemberId.equals(targetMemberId);
    }

    private void ensureStatusTransition(DistributionComplianceRecordEntity entity, String expectedStatus, String targetStatus) {
        if (entity == null || !expectedStatus.equals(entity.getStatus())) {
            throw new BizException(
                    "合规记录当前状态不允许流转到" + targetStatus,
                    ResultCode.DISTRIBUTION_COMPLIANCE_STATUS_TRANSITION_INVALID.getCode()
            );
        }
    }

    private void validateOptionalBizType(String bizType) {
        if (StringUtils.isBlank(trim(bizType))) {
            return;
        }
        validateRequiredBizType(bizType);
    }

    private void validateRequiredBizType(String bizType) {
        if (!DistributionAuditBizType.isValid(trim(bizType)) || DistributionAuditBizType.COMPLIANCE.getCode().equals(trim(bizType))) {
            throw new BizException("合规业务类型不合法", ResultCode.DISTRIBUTION_COMPLIANCE_BIZ_TYPE_INVALID.getCode());
        }
    }

    private void validateOptionalRecordType(String recordType) {
        if (StringUtils.isBlank(trim(recordType))) {
            return;
        }
        validateRequiredRecordType(recordType);
    }

    private void validateRequiredRecordType(String recordType) {
        if (!DistributionComplianceRecordType.isValid(trim(recordType))) {
            throw new BizException("合规记录类型不合法", ResultCode.DISTRIBUTION_COMPLIANCE_RECORD_TYPE_INVALID.getCode());
        }
    }

    private void validateOptionalStatus(String status) {
        if (StringUtils.isBlank(trim(status))) {
            return;
        }
        if (!DistributionComplianceStatus.isValid(trim(status))) {
            throw new BizException("合规状态不合法", ResultCode.DISTRIBUTION_COMPLIANCE_STATUS_INVALID.getCode());
        }
    }

    private void validateOptionalProductLine(String productLineCode) {
        if (StringUtils.isBlank(trim(productLineCode))) {
            return;
        }
        if (!distributionProductLineService.existsActiveProductLine(trim(productLineCode))) {
            throw new BizException("产品线不合法", ResultCode.DISTRIBUTION_LEAD_PRODUCT_LINE_INVALID.getCode());
        }
    }

    private DistributionComplianceRecordDetailDTO getRecordSnapshot(DistributionComplianceRecordEntity entity) {
        DistributionComplianceRecordDetailDTO detailDTO = new DistributionComplianceRecordDetailDTO();
        BeanUtils.copyProperties(entity, detailDTO);
        detailDTO.setAttachments(readAttachments(entity.getAttachmentsJson()));
        return detailDTO;
    }

    private List<DistributionComplianceRecordDTO> buildComplianceDTOList(List<DistributionComplianceRecordEntity> entities) {
        if (entities == null || entities.isEmpty()) {
            return Collections.emptyList();
        }
        List<DistributionComplianceRecordDTO> list = new ArrayList<DistributionComplianceRecordDTO>();
        for (DistributionComplianceRecordEntity entity : entities) {
            DistributionComplianceRecordDTO dto = new DistributionComplianceRecordDTO();
            BeanUtils.copyProperties(entity, dto);
            dto.setAttachments(readAttachments(entity.getAttachmentsJson()));
            list.add(dto);
        }
        return list;
    }

    private String writeAttachments(List<String> attachments) {
        if (attachments == null || attachments.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(attachments);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("合规附件序列化失败", e);
        }
    }

    private List<String> readAttachments(String attachmentsJson) {
        if (StringUtils.isBlank(attachmentsJson)) {
            return Collections.emptyList();
        }
        try {
            List<String> attachments = objectMapper.readValue(attachmentsJson, STRING_LIST_TYPE);
            return attachments == null ? Collections.<String>emptyList() : attachments;
        } catch (Exception e) {
            throw new IllegalStateException("合规附件反序列化失败", e);
        }
    }

    private String trim(String value) {
        return StringUtils.trimToNull(value);
    }

    private String defaultValue(String value, String defaultValue) {
        return value == null ? defaultValue : value;
    }
}
