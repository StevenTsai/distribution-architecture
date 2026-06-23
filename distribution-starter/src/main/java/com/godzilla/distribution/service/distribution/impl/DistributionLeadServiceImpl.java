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
import com.godzilla.distribution.service.distribution.DistributionOperatorService;
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
import java.util.List;
import java.util.Map;

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
    private DistributionOperatorService distributionOperatorService;
    @Autowired
    private DistributionAuditLogService distributionAuditLogService;
    @Autowired
    private DistributionDataPermissionService distributionDataPermissionService;

    @Override
    public PageResponseDTO<DistributionLeadDTO> listLeads(String leadNo, String patientKeyword, Long sourceDistributorId,
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
        long total = distributionLeadMapper.countByConditionWithScope(trim(leadNo), trim(patientKeyword), sourceDistributorId,
                trim(intentProductLine), ownerUserId, trim(stage), authorizedDistributorIds, authorizedMemberId, authorizedOwnerUserId);
        List<DistributionLeadEntity> entities = distributionLeadMapper.selectByConditionWithScope(trim(leadNo), trim(patientKeyword),
                sourceDistributorId, trim(intentProductLine), ownerUserId, trim(stage), authorizedDistributorIds,
                authorizedMemberId, authorizedOwnerUserId, (safePage - 1) * safePageSize, safePageSize);
        return new PageResponseDTO<DistributionLeadDTO>(buildLeadDTOList(entities), total, safePage, safePageSize);
    }

    @Override
    public DistributionLeadDetailDTO getLead(Long id) {
        DistributionLeadEntity entity = getLeadEntity(id);
        distributionDataPermissionService.checkAccessPermission(entity.getSourceDistributorId(), entity.getSourceMemberId(), entity.getOwnerUserId());
        DistributionLeadDetailDTO detailDTO = new DistributionLeadDetailDTO();
        BeanUtils.copyProperties(toDTO(entity), detailDTO);
        detailDTO.setCreatedBy(entity.getCreatedBy());
        detailDTO.setUpdatedBy(entity.getUpdatedBy());
        return detailDTO;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createLead(CreateDistributionLeadRequestDTO request) {
        validateDistributor(request.getSourceDistributorId());
        validateMember(request.getSourceMemberId());
        validateProductLine(request.getIntentProductLine());
        validateDuplicateLead(request.getPatientPhone(), request.getIntentProductLine(), null);

        Long operatorUserId = distributionOperatorService.getCurrentOperatorUserId();
        DistributionLeadEntity entity = new DistributionLeadEntity();
        entity.setLeadNo(generateLeadNo());
        entity.setPatientName(trim(request.getPatientName()));
        entity.setPatientPhone(trim(request.getPatientPhone()));
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
        distributionLeadMapper.insertSelective(entity);
        distributionAuditLogService.record(DistributionAuditBizType.LEAD.getCode(), entity.getId(), "create", null, entity, "创建线索");
        return entity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateLead(Long id, UpdateDistributionLeadRequestDTO request) {
        DistributionLeadEntity existing = getLeadEntity(id);
        distributionDataPermissionService.checkAccessPermission(existing.getSourceDistributorId(), existing.getSourceMemberId(), existing.getOwnerUserId());
        validateDistributor(request.getSourceDistributorId());
        validateMember(request.getSourceMemberId());
        validateProductLine(request.getIntentProductLine());

        DistributionLeadEntity update = new DistributionLeadEntity();
        update.setId(id);
        update.setPatientName(trim(request.getPatientName()));
        update.setPatientPhone(trim(request.getPatientPhone()));
        update.setSourceDistributorId(request.getSourceDistributorId());
        update.setSourceMemberId(request.getSourceMemberId());
        update.setIntentProductLine(trim(request.getIntentProductLine()));
        update.setSourceRegion(trim(request.getSourceRegion()));
        update.setSourceChannel(trim(request.getSourceChannel()));
        update.setRemark(trim(request.getRemark()));
        update.setUpdatedBy(distributionOperatorService.getCurrentOperatorUserId());
        distributionLeadMapper.updateByPrimaryKeySelective(update);
        distributionAuditLogService.record(DistributionAuditBizType.LEAD.getCode(), id, "update", existing, getLeadEntity(id), "更新线索");
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
        distributionAuditLogService.record(DistributionAuditBizType.LEAD.getCode(), id, "assign", existing, getLeadEntity(id), "分配线索负责人");
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
        distributionAuditLogService.record(DistributionAuditBizType.LEAD.getCode(), id, "update_stage", existing, getLeadEntity(id), "推进线索阶段");
    }

    @Override
    public List<DistributionLeadFollowUpDTO> listFollowUps(Long leadId) {
        getLeadEntity(leadId);
        List<DistributionLeadFollowUpEntity> entities = distributionLeadFollowUpMapper.selectByLeadId(leadId);
        if (entities == null || entities.isEmpty()) return Collections.emptyList();
        List<DistributionLeadFollowUpDTO> list = new ArrayList<DistributionLeadFollowUpDTO>();
        for (DistributionLeadFollowUpEntity entity : entities) {
            DistributionLeadFollowUpDTO dto = new DistributionLeadFollowUpDTO();
            BeanUtils.copyProperties(entity, dto);
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

    private List<DistributionLeadDTO> buildLeadDTOList(List<DistributionLeadEntity> entities) {
        if (entities == null || entities.isEmpty()) return Collections.emptyList();
        Map<Long, String> distributorNameMap = loadDistributorNameMap(entities);
        Map<Long, String> memberNameMap = loadMemberNameMap(entities);
        List<DistributionLeadDTO> list = new ArrayList<DistributionLeadDTO>();
        for (DistributionLeadEntity entity : entities) {
            DistributionLeadDTO dto = toDTO(entity);
            dto.setSourceDistributorName(distributorNameMap.get(entity.getSourceDistributorId()));
            dto.setSourceMemberName(memberNameMap.get(entity.getSourceMemberId()));
            list.add(dto);
        }
        return list;
    }

    private DistributionLeadDTO toDTO(DistributionLeadEntity entity) {
        DistributionLeadDTO dto = new DistributionLeadDTO();
        BeanUtils.copyProperties(entity, dto);
        return dto;
    }

    private Map<Long, String> loadDistributorNameMap(List<DistributionLeadEntity> entities) {
        Map<Long, String> nameMap = new HashMap<Long, String>();
        for (DistributionLeadEntity entity : entities) {
            if (entity.getSourceDistributorId() != null && !nameMap.containsKey(entity.getSourceDistributorId())) {
                DistributionDistributorEntity distributor = distributionDistributorMapper.selectByPrimaryKey(entity.getSourceDistributorId());
                if (distributor != null) nameMap.put(distributor.getId(), distributor.getName());
            }
        }
        return nameMap;
    }

    private Map<Long, String> loadMemberNameMap(List<DistributionLeadEntity> entities) {
        Map<Long, String> nameMap = new HashMap<Long, String>();
        for (DistributionLeadEntity entity : entities) {
            if (entity.getSourceMemberId() != null && !nameMap.containsKey(entity.getSourceMemberId())) {
                DistributionDistributorMemberEntity member = distributionDistributorMemberMapper.selectByPrimaryKey(entity.getSourceMemberId());
                if (member != null) nameMap.put(member.getId(), member.getName());
            }
        }
        return nameMap;
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

    private void validateDuplicateLead(String patientPhone, String intentProductLine, Long excludeId) {
        DistributionLeadEntity duplicate = distributionLeadMapper.selectDuplicateLead(patientPhone, intentProductLine, excludeId);
        if (duplicate != null) {
            throw new BizException("该患者已存在相同产品线的线索", ResultCode.DISTRIBUTION_LEAD_PHONE_EXISTS.getCode());
        }
    }

    private String generateLeadNo() {
        return "LEAD-" + new java.text.SimpleDateFormat("yyyyMMdd").format(new Date()) + "-" + RandomStringUtils.randomNumeric(3);
    }

    private String trim(String value) {
        return value == null ? null : value.trim();
    }
}
