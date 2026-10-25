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
import com.godzilla.distribution.entity.distribution.DistributionDistributorEntity;
import com.godzilla.distribution.entity.distribution.DistributionDistributorMemberEntity;
import com.godzilla.distribution.entity.distribution.DistributionLeadEntity;
import com.godzilla.distribution.entity.distribution.DistributionLeadFollowUpEntity;
import com.godzilla.distribution.enums.distribution.DistributionAuditBizType;
import com.godzilla.distribution.enums.distribution.DistributionFollowUpType;
import com.godzilla.distribution.enums.distribution.DistributionLeadStage;
import com.godzilla.distribution.enums.distribution.DistributionMemberStatus;
import com.godzilla.distribution.exception.BizException;
import com.godzilla.distribution.mapper.distribution.DistributionDistributorMapper;
import com.godzilla.distribution.mapper.distribution.DistributionDistributorMemberMapper;
import com.godzilla.distribution.mapper.distribution.DistributionLeadFollowUpMapper;
import com.godzilla.distribution.mapper.distribution.DistributionLeadMapper;
import com.godzilla.distribution.service.distribution.DistributionAuditLogService;
import com.godzilla.distribution.service.distribution.DistributionLeadService;
import com.godzilla.distribution.common.pii.PiiCipher;
import com.godzilla.distribution.service.distribution.DistributionOperatorService;
import com.godzilla.distribution.service.distribution.support.DistributionLeadDtoAssembler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class DistributionLeadServiceImpl implements DistributionLeadService {

    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_PAGE_SIZE = 10;
    private static final int MAX_PAGE_SIZE = 100;

    private final DistributionLeadMapper distributionLeadMapper;
    private final DistributionLeadFollowUpMapper distributionLeadFollowUpMapper;
    private final DistributionDistributorMapper distributionDistributorMapper;
    private final DistributionDistributorMemberMapper distributionDistributorMemberMapper;
    private final DistributionOperatorService distributionOperatorService;
    private final DistributionAuditLogService distributionAuditLogService;
    private final DistributionDataPermissionService distributionDataPermissionService;
    private final PiiCipher piiCipher;
    private final DistributionLeadDtoAssembler leadDtoAssembler;

    @Override
    public PageResponseDTO<DistributionLeadDTO> listLeads(String leadNo, String contactKeyword, Long sourceDistributorId,
                                                           String intentProductLine, Long ownerUserId, String stage,
                                                           Integer page, Integer pageSize) {
        int safePage = page == null || page < 1 ? DEFAULT_PAGE : page;
        int safePageSize = pageSize == null || pageSize < 1 ? DEFAULT_PAGE_SIZE : Math.min(pageSize, MAX_PAGE_SIZE);
        DistributionDataPermissionService.DistributionDataAccessScope accessScope = distributionDataPermissionService.resolveCurrentAccessScope();
        List<Long> authorizedDistributorIds = accessScope.isAllScope() ? null : accessScope.getAuthorizedDistributorIds();
        if (!accessScope.isAllScope() && !accessScope.isSelfScope() && (authorizedDistributorIds == null || authorizedDistributorIds.isEmpty())) {
            return new PageResponseDTO<DistributionLeadDTO>(Collections.<DistributionLeadDTO>emptyList(), 0, safePage, safePageSize);
        }
        Long authorizedMemberId = accessScope.isSelfScope() ? accessScope.getMemberId() : null;
        Long authorizedOwnerUserId = accessScope.isSelfScope() ? accessScope.getOperatorUserId() : null;
        String keyword = trim(contactKeyword);
        // 手机号加密落库后不能 LIKE 查询，只有完整手机号才转成检索键做精确匹配
        String contactPhoneLookup = isPhoneNumber(keyword) ? piiCipher.lookupKey(keyword) : null;
        String escapedKeyword = escapeLike(keyword);
        long total = distributionLeadMapper.countByConditionWithScope(trim(leadNo), escapedKeyword, contactPhoneLookup, sourceDistributorId,
                trim(intentProductLine), ownerUserId, trim(stage), authorizedDistributorIds, authorizedMemberId, authorizedOwnerUserId);
        List<DistributionLeadEntity> entities = distributionLeadMapper.selectByConditionWithScope(trim(leadNo), escapedKeyword, contactPhoneLookup,
                sourceDistributorId, trim(intentProductLine), ownerUserId, trim(stage), authorizedDistributorIds,
                authorizedMemberId, authorizedOwnerUserId, (safePage - 1) * safePageSize, safePageSize);
        return new PageResponseDTO<DistributionLeadDTO>(leadDtoAssembler.toList(entities), total, safePage, safePageSize);
    }

    @Override
    public DistributionLeadDetailDTO getLead(Long id) {
        DistributionLeadEntity entity = getLeadEntity(id);
        distributionDataPermissionService.checkAccessPermission(entity.getSourceDistributorId(), entity.getSourceMemberId(), entity.getOwnerUserId());
        return leadDtoAssembler.toDetail(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createLead(CreateDistributionLeadRequestDTO request) {
        validateDistributor(request.getSourceDistributorId());
        validateDistributorScope(request.getSourceDistributorId());
        validateMember(request.getSourceMemberId());
        validateProductLine(request.getIntentProductLine());
        validateDuplicateLead(request.getContactPhone(), request.getIntentProductLine(), null);

        Long operatorUserId = distributionOperatorService.getCurrentOperatorUserId();
        DistributionLeadEntity entity = new DistributionLeadEntity();
        entity.setLeadNo(generateLeadNo());
        entity.setContactName(trim(request.getContactName()));
        setEncryptedContactPhone(entity, trim(request.getContactPhone()));
        entity.setSourceDistributorId(request.getSourceDistributorId());
        entity.setSourceMemberId(request.getSourceMemberId());
        entity.setIntentProductLine(trim(request.getIntentProductLine()));
        entity.setSourceRegion(trim(request.getSourceRegion()));
        entity.setSourceChannel(trim(request.getSourceChannel()));
        entity.setStage(DistributionLeadStage.PENDING_REVIEW.getCode());
        entity.setIsDuplicate(0);
        entity.setCreatedBy(operatorUserId);
        entity.setUpdatedBy(operatorUserId);
        entity.setDeleted(0);
        insertLeadWithRetry(entity);
        distributionAuditLogService.record(DistributionAuditBizType.LEAD.getCode(), entity.getId(), entity.getSourceDistributorId(), "create", null, entity, "创建线索");
        return entity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateLead(Long id, UpdateDistributionLeadRequestDTO request) {
        DistributionLeadEntity existing = getLeadEntity(id);
        distributionDataPermissionService.checkAccessPermission(existing.getSourceDistributorId(), existing.getSourceMemberId(), existing.getOwnerUserId());
        validateDistributor(request.getSourceDistributorId());
        // update 也是全量 PUT：变更后的来源渠道同样要落在操作者授权范围内
        validateDistributorScope(request.getSourceDistributorId());
        validateMember(request.getSourceMemberId());
        validateProductLine(request.getIntentProductLine());
        validateDuplicateLead(trim(request.getContactPhone()), trim(request.getIntentProductLine()), id);

        DistributionLeadEntity update = new DistributionLeadEntity();
        update.setId(id);
        update.setContactName(trim(request.getContactName()));
        setEncryptedContactPhone(update, trim(request.getContactPhone()));
        update.setSourceDistributorId(request.getSourceDistributorId());
        update.setSourceMemberId(request.getSourceMemberId());
        update.setIntentProductLine(trim(request.getIntentProductLine()));
        update.setSourceRegion(trim(request.getSourceRegion()));
        update.setSourceChannel(trim(request.getSourceChannel()));
        update.setRemark(trim(request.getRemark()));
        update.setUpdatedBy(distributionOperatorService.getCurrentOperatorUserId());
        distributionLeadMapper.updateByPrimaryKeySelective(update);
        distributionAuditLogService.record(DistributionAuditBizType.LEAD.getCode(), id, existing.getSourceDistributorId(), "update", existing, getLeadEntity(id), "更新线索");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void assignLead(Long id, AssignDistributionLeadRequestDTO request) {
        DistributionLeadEntity existing = getLeadEntity(id);
        distributionDataPermissionService.checkAccessPermission(existing.getSourceDistributorId(), existing.getSourceMemberId(), existing.getOwnerUserId());
        DistributionLeadEntity update = new DistributionLeadEntity();
        update.setId(id);
        update.setOwnerUserId(request.getOwnerUserId());
        update.setUpdatedBy(distributionOperatorService.getCurrentOperatorUserId());
        distributionLeadMapper.updateByPrimaryKeySelective(update);
        distributionAuditLogService.record(DistributionAuditBizType.LEAD.getCode(), id, existing.getSourceDistributorId(), "assign", existing, getLeadEntity(id), "分配线索负责人");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateLeadStage(Long id, UpdateDistributionLeadStageRequestDTO request) {
        DistributionLeadEntity existing = getLeadEntity(id);
        distributionDataPermissionService.checkAccessPermission(existing.getSourceDistributorId(), existing.getSourceMemberId(), existing.getOwnerUserId());
        DistributionLeadEntity update = new DistributionLeadEntity();
        update.setId(id);
        update.setStage(trim(request.getStage()));
        update.setUpdatedBy(distributionOperatorService.getCurrentOperatorUserId());
        distributionLeadMapper.updateByPrimaryKeySelective(update);
        distributionAuditLogService.record(DistributionAuditBizType.LEAD.getCode(), id, existing.getSourceDistributorId(), "update_stage", existing, getLeadEntity(id), "推进线索阶段");
    }

    @Override
    public List<DistributionLeadFollowUpDTO> listFollowUps(Long leadId) {
        DistributionLeadEntity lead = getLeadEntity(leadId);
        distributionDataPermissionService.checkAccessPermission(lead.getSourceDistributorId(), lead.getSourceMemberId(), lead.getOwnerUserId());
        List<DistributionLeadFollowUpEntity> entities = distributionLeadFollowUpMapper.selectByLeadId(leadId);
        if (entities == null || entities.isEmpty()) return Collections.emptyList();
        List<DistributionLeadFollowUpDTO> list = new ArrayList<DistributionLeadFollowUpDTO>();
        for (DistributionLeadFollowUpEntity entity : entities) {
            DistributionLeadFollowUpDTO dto = new DistributionLeadFollowUpDTO();
            dto.setId(entity.getId());
            dto.setLeadId(entity.getLeadId());
            dto.setFollowUpType(entity.getFollowUpType());
            dto.setContent(entity.getContent());
            dto.setNextActionAt(entity.getNextActionAt());
            dto.setCreatedBy(entity.getCreatedBy());
            dto.setCreateTime(entity.getCreateTime());
            list.add(dto);
        }
        return list;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createFollowUp(Long leadId, CreateDistributionLeadFollowUpRequestDTO request) {
        DistributionLeadEntity lead = getLeadEntity(leadId);
        distributionDataPermissionService.checkAccessPermission(lead.getSourceDistributorId(), lead.getSourceMemberId(), lead.getOwnerUserId());
        validateFollowUpType(request.getFollowUpType());
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
        return entity.getId();
    }

    private DistributionLeadEntity getLeadEntity(Long id) {
        DistributionLeadEntity entity = distributionLeadMapper.selectByPrimaryKey(id);
        if (entity == null || (entity.getDeleted() != null && entity.getDeleted() == 1)) {
            throw new BizException("线索不存在", ResultCode.DISTRIBUTION_LEAD_NOT_FOUND.getCode());
        }
        return entity;
    }

    private void validateDistributorScope(Long distributorId) {
        DistributionDataPermissionService.DistributionDataAccessScope accessScope = distributionDataPermissionService.resolveCurrentAccessScope();
        if (accessScope.isAllScope()) return;
        List<Long> authorizedDistributorIds = accessScope.getAuthorizedDistributorIds();
        if (authorizedDistributorIds == null || !authorizedDistributorIds.contains(distributorId)) {
            throw new BizException("无权操作该渠道下的数据", ResultCode.DISTRIBUTION_DATA_ACCESS_DENIED.getCode());
        }
    }

    private void validateDistributor(Long distributorId) {
        DistributionDistributorEntity distributor = distributionDistributorMapper.selectByPrimaryKey(distributorId);
        if (distributor == null || (distributor.getDeleted() != null && distributor.getDeleted() == 1)) {
            throw new BizException("来源渠道不存在", ResultCode.DISTRIBUTION_LEAD_DISTRIBUTOR_NOT_FOUND.getCode());
        }
    }

    private void validateMember(Long memberId) {
        if (memberId == null) return;
        DistributionDistributorMemberEntity member = distributionDistributorMemberMapper.selectByPrimaryKey(memberId);
        if (member == null || (member.getDeleted() != null && member.getDeleted() == 1)) {
            throw new BizException("推荐成员不存在", ResultCode.DISTRIBUTION_LEAD_MEMBER_NOT_FOUND.getCode());
        }
        if (!DistributionMemberStatus.ACTIVE.getCode().equals(member.getStatus())) {
            throw new BizException("推荐成员已禁用", ResultCode.DISTRIBUTION_LEAD_SOURCE_MEMBER_DISABLED.getCode());
        }
    }

    private void validateProductLine(String productLine) {
        if (StringUtils.isBlank(productLine)) {
            throw new BizException("意向产品线不能为空", ResultCode.DISTRIBUTION_LEAD_PRODUCT_LINE_INVALID.getCode());
        }
    }

    private void validateFollowUpType(String followUpType) {
        if (!DistributionFollowUpType.isValid(trim(followUpType))) {
            throw new BizException("跟进类型不合法", ResultCode.DISTRIBUTION_LEAD_FOLLOW_UP_TYPE_INVALID.getCode());
        }
    }

    private void validateDuplicateLead(String contactPhone, String intentProductLine, Long excludeId) {
        if (StringUtils.isBlank(contactPhone)) {
            return;
        }
        DistributionLeadEntity duplicate = distributionLeadMapper.selectDuplicateLead(
                piiCipher.lookupKey(contactPhone), intentProductLine, excludeId);
        if (duplicate != null) {
            throw new BizException("该联系人已存在相同产品线的线索", ResultCode.DISTRIBUTION_LEAD_PHONE_EXISTS.getCode());
        }
    }

    /** 写入前加密手机号并同步写入 HMAC 检索键。 */
    private void setEncryptedContactPhone(DistributionLeadEntity entity, String contactPhone) {
        if (StringUtils.isBlank(contactPhone)) {
            return;
        }
        entity.setContactPhone(piiCipher.encrypt(contactPhone));
        entity.setContactPhoneLookup(piiCipher.lookupKey(contactPhone));
    }

    private boolean isPhoneNumber(String value) {
        return value != null && value.matches("^1\\d{10}$");
    }

    private static final int LEAD_NO_MAX_RETRIES = 3;

    private void insertLeadWithRetry(DistributionLeadEntity entity) {
        for (int i = 0; i < LEAD_NO_MAX_RETRIES; i++) {
            entity.setLeadNo(generateLeadNo());
            try {
                distributionLeadMapper.insertSelective(entity);
                return;
            } catch (org.springframework.dao.DuplicateKeyException e) {
                if (i == LEAD_NO_MAX_RETRIES - 1) {
                    throw new BizException("线索编号生成失败，请重试", ResultCode.FAIL.getCode());
                }
                log.warn("lead_no collision, retrying ({}/{}): {}", i + 1, LEAD_NO_MAX_RETRIES, entity.getLeadNo());
            }
        }
    }

    private String generateLeadNo() {
        // 6-digit random suffix: 1M combinations per day
        return "LEAD-" + java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE)
                + "-" + RandomStringUtils.randomNumeric(6);
    }

    private String trim(String value) {
        return StringUtils.trimToNull(value);
    }

    /** 转义 LIKE 通配符（%、_、\），防止关键字注入改变匹配语义。 */
    private String escapeLike(String keyword) {
        if (keyword == null) {
            return null;
        }
        return keyword.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
