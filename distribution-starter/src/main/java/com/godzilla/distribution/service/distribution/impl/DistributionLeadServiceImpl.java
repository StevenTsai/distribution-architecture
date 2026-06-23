package com.godzilla.distribution.service.distribution.impl;

import com.godzilla.distribution.common.ResultCode;
import com.godzilla.distribution.dto.PageResponseDTO;
import com.godzilla.distribution.dto.distribution.request.AssignDistributionLeadRequestDTO;
import com.godzilla.distribution.dto.distribution.request.CreateDistributionLeadFollowUpRequestDTO;
import com.godzilla.distribution.dto.distribution.request.CreateDistributionLeadRequestDTO;
import com.godzilla.distribution.dto.distribution.request.UpdateDistributionLeadRequestDTO;
import com.godzilla.distribution.dto.distribution.request.UpdateDistributionLeadStageRequestDTO;
import com.godzilla.distribution.dto.distribution.response.DistributionLeadDTO;
import com.godzilla.distribution.dto.distribution.response.DistributionLeadDetailDTO;
import com.godzilla.distribution.dto.distribution.response.DistributionLeadFollowUpDTO;
import com.godzilla.distribution.dto.distribution.response.DistributionProductLineDTO;
import com.godzilla.distribution.entity.shared.MedicalUserInfoEntity;
import com.godzilla.distribution.entity.shared.PatientInfoEntity;
import com.godzilla.distribution.entity.shared.PatientInfoEntityExample;
import com.godzilla.distribution.entity.shared.PatientInfoEntityWithBLOBs;
import com.godzilla.distribution.entity.distribution.DistributionDistributorEntity;
import com.godzilla.distribution.entity.distribution.DistributionDistributorMemberEntity;
import com.godzilla.distribution.entity.distribution.DistributionBusinessOrderEntity;
import com.godzilla.distribution.entity.distribution.DistributionLeadEntity;
import com.godzilla.distribution.entity.distribution.DistributionLeadFollowUpEntity;
import com.godzilla.distribution.entity.distribution.DistributionPatientAttributionEntity;
import com.godzilla.distribution.entity.distribution.DistributionProductEntity;
import com.godzilla.distribution.enums.distribution.DistributionAttributionStatus;
import com.godzilla.distribution.enums.distribution.DistributionAuditBizType;
import com.godzilla.distribution.enums.distribution.DistributionBusinessOrderStatus;
import com.godzilla.distribution.enums.distribution.DistributionFollowUpType;
import com.godzilla.distribution.enums.distribution.DistributionLeadStage;
import com.godzilla.distribution.enums.distribution.DistributionMemberStatus;
import com.godzilla.distribution.exception.BizException;
import com.godzilla.distribution.mapper.shared.MedicalUserInfoEntityMapper;
import com.godzilla.distribution.mapper.shared.PatientInfoEntityMapper;
import com.godzilla.distribution.mapper.distribution.DistributionDistributorMapper;
import com.godzilla.distribution.mapper.distribution.DistributionDistributorMemberMapper;
import com.godzilla.distribution.mapper.distribution.DistributionBusinessOrderMapper;
import com.godzilla.distribution.mapper.distribution.DistributionLeadFollowUpMapper;
import com.godzilla.distribution.mapper.distribution.DistributionLeadMapper;
import com.godzilla.distribution.mapper.distribution.DistributionPatientAttributionMapper;
import com.godzilla.distribution.mapper.distribution.DistributionProductMapper;
import com.godzilla.distribution.service.distribution.DistributionAuditLogService;
import com.godzilla.distribution.service.distribution.DistributionLeadService;
import com.godzilla.distribution.service.distribution.DistributionOperatorService;
import com.godzilla.distribution.service.distribution.DistributionProductLineService;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class DistributionLeadServiceImpl implements DistributionLeadService {

    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_PAGE_SIZE = 10;
    private static final int MAX_PAGE_SIZE = 100;
    @Autowired
    private DistributionLeadMapper distributionLeadMapper;

    @Autowired
    private DistributionLeadFollowUpMapper distributionLeadFollowUpMapper;

    @Autowired
    private DistributionDistributorMapper distributionDistributorMapper;

    @Autowired
    private DistributionDistributorMemberMapper distributionDistributorMemberMapper;

    @Autowired
    private MedicalUserInfoEntityMapper medicalUserInfoEntityMapper;

    @Autowired
    private PatientInfoEntityMapper patientInfoEntityMapper;

    @Autowired
    private DistributionPatientAttributionMapper distributionPatientAttributionMapper;

    @Autowired
    private DistributionBusinessOrderMapper distributionBusinessOrderMapper;

    @Autowired
    private DistributionProductMapper distributionProductMapper;

    @Autowired
    private DistributionOperatorService distributionOperatorService;

    @Autowired
    private DistributionAuditLogService distributionAuditLogService;

    @Autowired
    private DistributionDataPermissionService distributionDataPermissionService;

    @Autowired
    private DistributionProductLineService distributionProductLineService;

    @Override
    public PageResponseDTO<DistributionLeadDTO> listLeads(String leadNo,
                                                          String patientKeyword,
                                                          Long sourceDistributorId,
                                                          String intentProductLine,
                                                          Long ownerUserId,
                                                          String stage,
                                                          Integer page,
                                                          Integer pageSize) {
        int safePage = page == null || page < 1 ? DEFAULT_PAGE : page;
        int safePageSize = pageSize == null || pageSize < 1 ? DEFAULT_PAGE_SIZE : Math.min(pageSize, MAX_PAGE_SIZE);
        DistributionDataPermissionService.DistributionDataAccessScope accessScope = distributionDataPermissionService.resolveCurrentAccessScope();
        List<Long> authorizedDistributorIds = accessScope.isAllScope() || accessScope.isSelfScope()
                ? null
                : accessScope.getAuthorizedDistributorIds();
        if (!accessScope.isAllScope() && !accessScope.isSelfScope()
                && (authorizedDistributorIds == null || authorizedDistributorIds.isEmpty())) {
            return emptyPage(safePage, safePageSize);
        }
        Long authorizedMemberId = accessScope.isSelfScope() ? accessScope.getMemberId() : null;
        Long authorizedOwnerUserId = accessScope.isSelfScope() ? accessScope.getOperatorUserId() : null;
        long total = distributionLeadMapper.countByConditionWithScope(
                trim(leadNo),
                trim(patientKeyword),
                sourceDistributorId,
                trim(intentProductLine),
                ownerUserId,
                trim(stage),
                authorizedDistributorIds,
                authorizedMemberId,
                authorizedOwnerUserId
        );
        List<DistributionLeadEntity> entities = distributionLeadMapper.selectByConditionWithScope(
                trim(leadNo),
                trim(patientKeyword),
                sourceDistributorId,
                trim(intentProductLine),
                ownerUserId,
                trim(stage),
                authorizedDistributorIds,
                authorizedMemberId,
                authorizedOwnerUserId,
                (safePage - 1) * safePageSize,
                safePageSize
        );
        return new PageResponseDTO<DistributionLeadDTO>(buildLeadDTOList(entities), total, safePage, safePageSize);
    }

    @Override
    public DistributionLeadDetailDTO getLead(Long id) {
        DistributionLeadEntity entity = getAuthorizedLeadEntity(id);
        DistributionLeadDetailDTO detailDTO = new DistributionLeadDetailDTO();
        BeanUtils.copyProperties(
                toLeadDTO(
                        entity,
                        loadDistributorNameMap(Collections.singletonList(entity)),
                        loadMemberNameMap(Collections.singletonList(entity)),
                        loadUserNameMap(Collections.singletonList(entity)),
                        loadProductLineMap(Collections.singletonList(entity))
                ),
                detailDTO
        );
        detailDTO.setCreatedBy(entity.getCreatedBy());
        detailDTO.setUpdatedBy(entity.getUpdatedBy());
        return detailDTO;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createLead(CreateDistributionLeadRequestDTO request) {
        String patientPhone = trim(request.getPatientPhone());
        String intentProductLine = trim(request.getIntentProductLine());
        validateLeadRequest(request.getSourceDistributorId(), request.getSourceMemberId(), intentProductLine, request.getOwnerUserId());
        distributionDataPermissionService.checkAccessPermission(
                request.getSourceDistributorId(),
                request.getSourceMemberId(),
                request.getOwnerUserId()
        );
        ensureNoDuplicateLeadForSameReferrer(request.getSourceDistributorId(), request.getSourceMemberId(), patientPhone, null);
        Long operatorUserId = distributionOperatorService.getCurrentOperatorUserId();
        DistributionLeadEntity duplicate = distributionLeadMapper.selectDuplicateLead(patientPhone, intentProductLine, null);

        DistributionLeadEntity entity = new DistributionLeadEntity();
        entity.setLeadNo(generateLeadNo());
        entity.setPatientName(trim(request.getPatientName()));
        entity.setPatientPhone(patientPhone);
        entity.setSourceDistributorId(request.getSourceDistributorId());
        entity.setSourceMemberId(request.getSourceMemberId());
        entity.setIntentProductLine(intentProductLine);
        entity.setSourceRegion(trim(request.getSourceRegion()));
        entity.setSourceChannel(trim(request.getSourceChannel()));
        entity.setOwnerUserId(request.getOwnerUserId());
        entity.setStage(DistributionLeadStage.PENDING_REVIEW.getCode());
        entity.setIsDuplicate(duplicate == null ? 0 : 1);
        entity.setDuplicateLeadId(duplicate == null ? null : duplicate.getId());
        entity.setRemark(trim(request.getRemark()));
        entity.setCreatedBy(operatorUserId);
        entity.setUpdatedBy(operatorUserId);
        entity.setDeleted(0);
        distributionLeadMapper.insertSelective(entity);
        distributionAuditLogService.record(
                DistributionAuditBizType.LEAD.getCode(),
                entity.getId(),
                "create",
                null,
                getLead(entity.getId()),
                "创建线索"
        );
        return entity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateLead(Long id, UpdateDistributionLeadRequestDTO request) {
        DistributionLeadEntity existing = getAuthorizedLeadEntity(id);
        ensureLeadEditable(existing);
        String patientPhone = trim(request.getPatientPhone());
        String intentProductLine = trim(request.getIntentProductLine());
        validateLeadRequest(request.getSourceDistributorId(), request.getSourceMemberId(), intentProductLine, request.getOwnerUserId());
        distributionDataPermissionService.checkAccessPermission(
                request.getSourceDistributorId(),
                request.getSourceMemberId(),
                request.getOwnerUserId()
        );
        ensureNoDuplicateLeadForSameReferrer(request.getSourceDistributorId(), request.getSourceMemberId(), patientPhone, id);
        DistributionLeadEntity duplicate = distributionLeadMapper.selectDuplicateLead(patientPhone, intentProductLine, id);

        DistributionLeadEntity update = new DistributionLeadEntity();
        update.setId(id);
        update.setPatientName(trim(request.getPatientName()));
        update.setPatientPhone(patientPhone);
        update.setSourceDistributorId(request.getSourceDistributorId());
        update.setSourceMemberId(request.getSourceMemberId());
        update.setIntentProductLine(intentProductLine);
        update.setSourceRegion(trim(request.getSourceRegion()));
        update.setSourceChannel(trim(request.getSourceChannel()));
        update.setOwnerUserId(request.getOwnerUserId());
        update.setInvalidReason(trim(request.getInvalidReason()));
        update.setRemark(trim(request.getRemark()));
        update.setIsDuplicate(duplicate == null ? 0 : 1);
        update.setDuplicateLeadId(duplicate == null ? null : duplicate.getId());
        update.setUpdatedBy(distributionOperatorService.getCurrentOperatorUserId());
        distributionLeadMapper.updateByPrimaryKeySelective(update);
        distributionAuditLogService.record(
                DistributionAuditBizType.LEAD.getCode(),
                id,
                "update",
                existing,
                getLead(id),
                "更新线索"
        );
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assignLead(Long id, AssignDistributionLeadRequestDTO request) {
        validateOwnerUser(request.getOwnerUserId());
        DistributionLeadEntity existing = getAuthorizedLeadEntity(id);
        ensureLeadEditable(existing);

        DistributionLeadEntity update = new DistributionLeadEntity();
        update.setId(id);
        update.setOwnerUserId(request.getOwnerUserId());
        update.setUpdatedBy(distributionOperatorService.getCurrentOperatorUserId());
        distributionLeadMapper.updateByPrimaryKeySelective(update);
        distributionAuditLogService.record(
                DistributionAuditBizType.LEAD.getCode(),
                id,
                "assign",
                existing,
                getLead(id),
                "分配线索负责人"
        );
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateLeadStage(Long id, UpdateDistributionLeadStageRequestDTO request) {
        String targetStage = trim(request.getStage());
        validateLeadStage(targetStage);
        if (DistributionLeadStage.SCHEDULED.getCode().equals(targetStage) && request.getProductId() == null) {
            throw new BizException("推进到已预约状态时产品ID不能为空", ResultCode.DISTRIBUTION_LEAD_PRODUCT_REQUIRED.getCode());
        }
        DistributionLeadEntity existing = getAuthorizedLeadEntity(id);
        ensureLeadEditable(existing);
        String previousStage = trim(existing.getStage());
        Long operatorUserId = distributionOperatorService.getCurrentOperatorUserId();

        DistributionLeadEntity update = new DistributionLeadEntity();
        update.setId(id);
        update.setStage(targetStage);
        update.setInvalidReason(trim(request.getInvalidReason()));
        update.setUpdatedBy(operatorUserId);
        distributionLeadMapper.updateByPrimaryKeySelective(update);
        ensureAttributionCreatedWhenScheduled(existing, previousStage, targetStage, operatorUserId);
        ensureBusinessOrderCreatedWhenScheduled(existing, previousStage, targetStage, request.getProductId(), operatorUserId);
        ensureAttributionDeletedWhenLeadInvalid(existing, targetStage, operatorUserId);
        distributionAuditLogService.record(
                DistributionAuditBizType.LEAD.getCode(),
                id,
                "update_stage",
                existing,
                getLead(id),
                defaultValue(trim(request.getRemark()), "推进线索阶段")
        );
    }

    @Override
    public List<DistributionLeadFollowUpDTO> listFollowUps(Long leadId) {
        getAuthorizedLeadEntity(leadId);
        List<DistributionLeadFollowUpEntity> entities = distributionLeadFollowUpMapper.selectByLeadId(leadId);
        return buildFollowUpDTOList(entities);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createFollowUp(Long leadId, CreateDistributionLeadFollowUpRequestDTO request) {
        validateFollowUpType(trim(request.getFollowUpType()));
        DistributionLeadEntity existing = getAuthorizedLeadEntity(leadId);
        ensureLeadEditable(existing);
        Long operatorUserId = distributionOperatorService.getCurrentOperatorUserId();

        DistributionLeadFollowUpEntity entity = new DistributionLeadFollowUpEntity();
        entity.setLeadId(leadId);
        entity.setFollowUpType(trim(request.getFollowUpType()));
        entity.setContent(trim(request.getContent()));
        entity.setNextActionAt(request.getNextActionAt());
        entity.setCreatedBy(operatorUserId);
        distributionLeadFollowUpMapper.insertSelective(entity);

        DistributionLeadEntity leadUpdate = new DistributionLeadEntity();
        leadUpdate.setId(leadId);
        leadUpdate.setLatestFollowUpAt(new Date());
        leadUpdate.setUpdatedBy(operatorUserId);
        distributionLeadMapper.updateByPrimaryKeySelective(leadUpdate);

        distributionAuditLogService.record(
                DistributionAuditBizType.LEAD.getCode(),
                leadId,
                "follow_up",
                null,
                entity,
                "新增线索跟进记录"
        );
        return entity.getId();
    }

    private DistributionLeadEntity getLeadEntity(Long id) {
        DistributionLeadEntity entity = distributionLeadMapper.selectByPrimaryKey(id);
        if (entity == null || (entity.getDeleted() != null && entity.getDeleted() == 1)) {
            throw new BizException("线索不存在", ResultCode.DISTRIBUTION_LEAD_NOT_FOUND.getCode());
        }
        return entity;
    }

    private DistributionLeadEntity getAuthorizedLeadEntity(Long id) {
        DistributionLeadEntity entity = getLeadEntity(id);
        distributionDataPermissionService.checkAccessPermission(
                entity.getSourceDistributorId(),
                entity.getSourceMemberId(),
                entity.getOwnerUserId()
        );
        return entity;
    }

    private PageResponseDTO<DistributionLeadDTO> emptyPage(int page, int pageSize) {
        return new PageResponseDTO<DistributionLeadDTO>(Collections.<DistributionLeadDTO>emptyList(), 0, page, pageSize);
    }

    private void validateLeadRequest(Long sourceDistributorId, Long sourceMemberId, String intentProductLine, Long ownerUserId) {
        validateDistributor(sourceDistributorId);
        validateSourceMember(sourceDistributorId, sourceMemberId);
        validateProductLine(intentProductLine);
        validateOwnerUser(ownerUserId);
    }

    private void ensureNoDuplicateLeadForSameReferrer(Long sourceDistributorId,
                                                      Long sourceMemberId,
                                                      String patientPhone,
                                                      Long excludeId) {
        DistributionLeadEntity existingLead = distributionLeadMapper.selectByReferrerAndPatientPhone(
                sourceDistributorId,
                sourceMemberId,
                patientPhone,
                excludeId
        );
        if (existingLead != null) {
            throw new BizException("同一个推荐人对同一个患者不能重复创建线索", ResultCode.DISTRIBUTION_LEAD_PHONE_EXISTS.getCode());
        }
    }

    private void validateDistributor(Long distributorId) {
        DistributionDistributorEntity distributor = distributionDistributorMapper.selectByPrimaryKey(distributorId);
        if (distributor == null || (distributor.getDeleted() != null && distributor.getDeleted() == 1)) {
            throw new BizException("来源渠道不存在", ResultCode.DISTRIBUTION_LEAD_DISTRIBUTOR_NOT_FOUND.getCode());
        }
    }

    private void validateSourceMember(Long distributorId, Long sourceMemberId) {
        if (sourceMemberId == null) {
            return;
        }
        DistributionDistributorMemberEntity member = distributionDistributorMemberMapper.selectByPrimaryKey(sourceMemberId);
        if (member == null || (member.getDeleted() != null && member.getDeleted() == 1)) {
            throw new BizException("来源成员不存在", ResultCode.DISTRIBUTION_LEAD_MEMBER_NOT_FOUND.getCode());
        }
        if (!distributorId.equals(member.getDistributorId())) {
            throw new BizException("来源成员与来源渠道不匹配", ResultCode.DISTRIBUTION_LEAD_MEMBER_NOT_FOUND.getCode());
        }
        if (!DistributionMemberStatus.ACTIVE.getCode().equals(member.getStatus())) {
            throw new BizException("来源成员未启用，不能录入线索", ResultCode.DISTRIBUTION_LEAD_SOURCE_MEMBER_DISABLED.getCode());
        }
    }

    private void validateProductLine(String productLine) {
        if (!distributionProductLineService.existsActiveProductLine(trim(productLine))) {
            throw new BizException("意向产品线不合法", ResultCode.DISTRIBUTION_LEAD_PRODUCT_LINE_INVALID.getCode());
        }
    }

    private void validateOwnerUser(Long ownerUserId) {
        if (ownerUserId == null) {
            return;
        }
        MedicalUserInfoEntity user = medicalUserInfoEntityMapper.selectByPrimaryKey(ownerUserId);
        if (user == null) {
            throw new BizException("线索负责人不存在", ResultCode.DISTRIBUTION_LEAD_OWNER_NOT_FOUND.getCode());
        }
    }

    private void validateLeadStage(String stage) {
        if (!DistributionLeadStage.isValid(stage)) {
            throw new BizException("线索阶段不合法", ResultCode.DISTRIBUTION_LEAD_STAGE_INVALID.getCode());
        }
    }

    private void ensureLeadEditable(DistributionLeadEntity lead) {
        if (lead == null) {
            return;
        }
        if (DistributionLeadStage.INVALID.getCode().equals(trim(lead.getStage()))) {
            throw new BizException("无效线索不能再编辑", ResultCode.DISTRIBUTION_LEAD_STAGE_INVALID.getCode());
        }
    }

    private void ensurePatientCreatedWhenInterested(DistributionLeadEntity lead, String targetStage, Long operatorUserId) {
        if (!DistributionLeadStage.INTERESTED.getCode().equals(targetStage) || lead == null) {
            return;
        }
        if (findPatientByPhone(trim(lead.getPatientPhone())) != null) {
            return;
        }
        createPatientFromLead(lead, operatorUserId);
    }

    private void ensureAttributionCreatedWhenScheduled(DistributionLeadEntity lead,
                                                       String previousStage,
                                                       String targetStage,
                                                       Long operatorUserId) {
        if (lead == null
                || !DistributionLeadStage.INTERESTED.getCode().equals(previousStage)
                || !DistributionLeadStage.SCHEDULED.getCode().equals(targetStage)) {
            return;
        }

        PatientInfoEntity patient = findPatientByPhone(trim(lead.getPatientPhone()));
        if (patient == null) {
            patient = createPatientFromLead(lead, operatorUserId);
        }
        if (patient == null || patient.getId() == null) {
            return;
        }

        DistributionPatientAttributionEntity existingAttribution = distributionPatientAttributionMapper.selectByPatientId(patient.getId());
        if (existingAttribution != null && (existingAttribution.getDeleted() == null || existingAttribution.getDeleted() == 0)) {
            return;
        }

        DistributionPatientAttributionEntity attribution = new DistributionPatientAttributionEntity();
        attribution.setPatientId(patient.getId());
        attribution.setLeadId(lead.getId());
        attribution.setFirstDistributorId(lead.getSourceDistributorId());
        attribution.setFirstMemberId(lead.getSourceMemberId());
        attribution.setCurrentDistributorId(lead.getSourceDistributorId());
        attribution.setCurrentMemberId(lead.getSourceMemberId());
        attribution.setStatus(DistributionAttributionStatus.ACTIVE.getCode());
        attribution.setIsLocked(0);
        attribution.setChangeReason("线索推进至已预约自动创建归因");
        attribution.setCreatedBy(operatorUserId);
        attribution.setUpdatedBy(operatorUserId);
        attribution.setDeleted(0);
        distributionPatientAttributionMapper.insertSelective(attribution);
        distributionAuditLogService.record(
                DistributionAuditBizType.ATTRIBUTION.getCode(),
                attribution.getId(),
                "create",
                null,
                attribution,
                "线索推进至已预约自动创建归因"
        );
    }

    private void ensureBusinessOrderCreatedWhenScheduled(DistributionLeadEntity lead,
                                                         String previousStage,
                                                         String targetStage,
                                                         Long productId,
                                                         Long operatorUserId) {
        if (lead == null
                || DistributionLeadStage.SCHEDULED.getCode().equals(previousStage)
                || !DistributionLeadStage.SCHEDULED.getCode().equals(targetStage)) {
            return;
        }
        if (productId == null) {
            throw new BizException("创建业务单时产品ID不能为空", ResultCode.DISTRIBUTION_BIZ_ORDER_PRODUCT_INVALID.getCode());
        }
        if (lead.getId() != null && distributionBusinessOrderMapper.selectByLeadId(lead.getId()) != null) {
            return;
        }

        PatientInfoEntity patient = findPatientByPhone(trim(lead.getPatientPhone()));
        if (patient == null) {
            patient = createPatientFromLead(lead, operatorUserId);
        }
        if (patient == null || patient.getId() == null) {
            return;
        }

        DistributionPatientAttributionEntity attribution = distributionPatientAttributionMapper.selectByPatientId(patient.getId());
        if (attribution == null || attribution.getId() == null
                || (attribution.getDeleted() != null && attribution.getDeleted() == 1)) {
            return;
        }

        DistributionProductEntity product = distributionProductMapper.selectByPrimaryKey(productId);
        if (product == null || (product.getDeleted() != null && product.getDeleted() == 1)) {
            throw new BizException("产品不存在", ResultCode.DISTRIBUTION_BIZ_ORDER_PRODUCT_INVALID.getCode());
        }

        DistributionBusinessOrderEntity entity = new DistributionBusinessOrderEntity();
        entity.setBizOrderNo(generateBusinessOrderNo());
        entity.setLeadId(lead.getId());
        entity.setPatientId(patient.getId());
        entity.setAttributionId(attribution.getId());
        entity.setProductLineCode(trim(lead.getIntentProductLine()));
        entity.setProductId(productId);
        entity.setProductCode(trim(product.getProductCode()));
        entity.setProductName(product.getName());
        entity.setDistributorId(attribution.getCurrentDistributorId() == null ? lead.getSourceDistributorId() : attribution.getCurrentDistributorId());
        entity.setMemberId(attribution.getCurrentMemberId() == null ? lead.getSourceMemberId() : attribution.getCurrentMemberId());
        entity.setCurrentOwnerUserId(lead.getOwnerUserId());
        entity.setReceivedAmount(java.math.BigDecimal.ZERO);
        entity.setStatus(DistributionBusinessOrderStatus.DRAFT.getCode());
        entity.setRemark("线索推进至已预约自动创建业务单");
        entity.setCreatedBy(operatorUserId);
        entity.setUpdatedBy(operatorUserId);
        entity.setDeleted(0);
        distributionBusinessOrderMapper.insertSelective(entity);
        distributionAuditLogService.record(
                DistributionAuditBizType.BUSINESS_ORDER.getCode(),
                entity.getId(),
                "create",
                null,
                entity,
                "线索推进至已预约自动创建业务单"
        );
    }

    private void ensureAttributionDeletedWhenLeadInvalid(DistributionLeadEntity lead,
                                                         String targetStage,
                                                         Long operatorUserId) {
        if (lead == null || !DistributionLeadStage.INVALID.getCode().equals(targetStage)) {
            return;
        }
        PatientInfoEntity patient = findPatientByPhone(trim(lead.getPatientPhone()));
        if (patient == null || patient.getId() == null) {
            return;
        }
        DistributionPatientAttributionEntity attribution = distributionPatientAttributionMapper.selectByPatientId(patient.getId());
        if (attribution == null || attribution.getId() == null) {
            return;
        }
        if (lead.getId() != null && attribution.getLeadId() != null && !lead.getId().equals(attribution.getLeadId())) {
            return;
        }
        deleteAttribution(attribution, operatorUserId, "线索变更为无效，自动删除归因");
    }

    private void deleteAttribution(DistributionPatientAttributionEntity attribution,
                                   Long operatorUserId,
                                   String remark) {
        if (attribution == null || attribution.getId() == null) {
            return;
        }
        DistributionPatientAttributionEntity update = new DistributionPatientAttributionEntity();
        update.setId(attribution.getId());
        update.setDeleted(1);
        update.setUpdatedBy(operatorUserId);
        distributionPatientAttributionMapper.updateByPrimaryKeySelective(update);
        distributionAuditLogService.record(
                DistributionAuditBizType.ATTRIBUTION.getCode(),
                attribution.getId(),
                "delete",
                attribution,
                null,
                remark
        );
    }

    private PatientInfoEntity findPatientByPhone(String phone) {
        if (StringUtils.isBlank(phone)) {
            return null;
        }
        PatientInfoEntityExample example = new PatientInfoEntityExample();
        example.createCriteria().andPhoneEqualTo(phone);
        List<PatientInfoEntity> existingPatients = patientInfoEntityMapper.selectByExample(example);
        if (existingPatients == null || existingPatients.isEmpty()) {
            return null;
        }
        return existingPatients.get(0);
    }

    private PatientInfoEntityWithBLOBs createPatientFromLead(DistributionLeadEntity lead, Long operatorUserId) {
        if (lead == null || StringUtils.isBlank(trim(lead.getPatientPhone()))) {
            return null;
        }
        Date now = new Date();
        PatientInfoEntityWithBLOBs patient = new PatientInfoEntityWithBLOBs();
        patient.setName(trim(lead.getPatientName()));
        patient.setPhone(trim(lead.getPatientPhone()));
        patient.setCreateTime(now);
        patient.setModifyTime(now);
        patientInfoEntityMapper.insertSelective(patient);
        return patient;
    }

    private String generateBusinessOrderNo() {
        return "DBO" + new java.text.SimpleDateFormat("yyyyMMddHHmmss").format(new Date()) + RandomStringUtils.randomNumeric(4);
    }

    private void validateFollowUpType(String followUpType) {
        if (!DistributionFollowUpType.isValid(followUpType)) {
            throw new BizException("跟进类型不合法", ResultCode.DISTRIBUTION_LEAD_FOLLOW_UP_TYPE_INVALID.getCode());
        }
    }

    private String generateLeadNo() {
        return "DL" + new java.text.SimpleDateFormat("yyyyMMddHHmmss").format(new Date()) + RandomStringUtils.randomNumeric(4);
    }

    private List<DistributionLeadDTO> buildLeadDTOList(List<DistributionLeadEntity> entities) {
        if (entities == null || entities.isEmpty()) {
            return Collections.emptyList();
        }
        Map<Long, String> distributorNameMap = loadDistributorNameMap(entities);
        Map<Long, String> memberNameMap = loadMemberNameMap(entities);
        Map<Long, String> userNameMap = loadUserNameMap(entities);
        Map<String, DistributionProductLineDTO> productLineMap = loadProductLineMap(entities);
        List<DistributionLeadDTO> list = new ArrayList<DistributionLeadDTO>();
        for (DistributionLeadEntity entity : entities) {
            list.add(toLeadDTO(entity, distributorNameMap, memberNameMap, userNameMap, productLineMap));
        }
        return list;
    }

    private DistributionLeadDTO toLeadDTO(DistributionLeadEntity entity,
                                          Map<Long, String> distributorNameMap,
                                          Map<Long, String> memberNameMap,
                                          Map<Long, String> userNameMap,
                                          Map<String, DistributionProductLineDTO> productLineMap) {
        DistributionLeadDTO dto = new DistributionLeadDTO();
        BeanUtils.copyProperties(entity, dto);
        dto.setSourceDistributorName(distributorNameMap.get(entity.getSourceDistributorId()));
        dto.setSourceMemberName(memberNameMap.get(entity.getSourceMemberId()));
        dto.setOwnerUserName(userNameMap.get(entity.getOwnerUserId()));
        dto.setProductLineMeta(resolveProductLineMeta(productLineMap, entity.getIntentProductLine()));
        return dto;
    }

    private Map<String, DistributionProductLineDTO> loadProductLineMap(List<DistributionLeadEntity> entities) {
        if (entities == null || entities.isEmpty()) {
            return Collections.emptyMap();
        }
        List<String> productLineCodes = new ArrayList<String>();
        for (DistributionLeadEntity entity : entities) {
            productLineCodes.add(entity.getIntentProductLine());
        }
        return distributionProductLineService.getActiveProductLineMap(productLineCodes);
    }

    private DistributionProductLineDTO resolveProductLineMeta(Map<String, DistributionProductLineDTO> productLineMap,
                                                              String productLineCode) {
        return productLineMap == null ? null : productLineMap.get(trim(productLineCode));
    }

    private List<DistributionLeadFollowUpDTO> buildFollowUpDTOList(List<DistributionLeadFollowUpEntity> entities) {
        if (entities == null || entities.isEmpty()) {
            return Collections.emptyList();
        }
        Map<Long, String> userNameMap = loadUserNameMapFromFollowUps(entities);
        List<DistributionLeadFollowUpDTO> list = new ArrayList<DistributionLeadFollowUpDTO>();
        for (DistributionLeadFollowUpEntity entity : entities) {
            DistributionLeadFollowUpDTO dto = new DistributionLeadFollowUpDTO();
            BeanUtils.copyProperties(entity, dto);
            dto.setCreatedByName(userNameMap.get(entity.getCreatedBy()));
            list.add(dto);
        }
        return list;
    }

    private Map<Long, String> loadDistributorNameMap(List<DistributionLeadEntity> entities) {
        Map<Long, String> nameMap = new HashMap<Long, String>();
        for (DistributionLeadEntity entity : entities) {
            DistributionDistributorEntity distributor = distributionDistributorMapper.selectByPrimaryKey(entity.getSourceDistributorId());
            if (distributor != null) {
                nameMap.put(distributor.getId(), distributor.getName());
            }
        }
        return nameMap;
    }

    private Map<Long, String> loadMemberNameMap(List<DistributionLeadEntity> entities) {
        Set<Long> memberIds = new LinkedHashSet<Long>();
        for (DistributionLeadEntity entity : entities) {
            if (entity.getSourceMemberId() != null) {
                memberIds.add(entity.getSourceMemberId());
            }
        }
        if (memberIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<DistributionDistributorMemberEntity> members = distributionDistributorMemberMapper.selectByIds(new ArrayList<Long>(memberIds));
        Map<Long, String> nameMap = new HashMap<Long, String>();
        for (DistributionDistributorMemberEntity member : members) {
            nameMap.put(member.getId(), member.getName());
        }
        return nameMap;
    }

    private Map<Long, String> loadUserNameMap(List<DistributionLeadEntity> entities) {
        Set<Long> userIds = new LinkedHashSet<Long>();
        for (DistributionLeadEntity entity : entities) {
            if (entity.getOwnerUserId() != null) {
                userIds.add(entity.getOwnerUserId());
            }
        }
        return loadUserNames(new ArrayList<Long>(userIds));
    }

    private Map<Long, String> loadUserNameMapFromFollowUps(List<DistributionLeadFollowUpEntity> entities) {
        Set<Long> userIds = new LinkedHashSet<Long>();
        for (DistributionLeadFollowUpEntity entity : entities) {
            if (entity.getCreatedBy() != null) {
                userIds.add(entity.getCreatedBy());
            }
        }
        return loadUserNames(new ArrayList<Long>(userIds));
    }

    private Map<Long, String> loadUserNames(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, String> nameMap = new HashMap<Long, String>();
        for (Long userId : userIds) {
            MedicalUserInfoEntity user = medicalUserInfoEntityMapper.selectByPrimaryKey(userId);
            if (user != null) {
                nameMap.put(user.getId(), user.getName());
            }
        }
        return nameMap;
    }

    private String trim(String value) {
        return value == null ? null : value.trim();
    }

    private String defaultValue(String value, String defaultValue) {
        return StringUtils.isBlank(value) ? defaultValue : value;
    }
}
