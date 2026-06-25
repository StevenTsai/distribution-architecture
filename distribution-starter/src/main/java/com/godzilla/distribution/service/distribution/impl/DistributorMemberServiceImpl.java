package com.godzilla.distribution.service.distribution.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.godzilla.distribution.common.ResultCode;
import com.godzilla.distribution.dto.PageResponseDTO;
import com.godzilla.distribution.dto.distribution.request.CreateDistributorMemberRequestDTO;
import com.godzilla.distribution.dto.distribution.request.UpdateDistributorMemberRequestDTO;
import com.godzilla.distribution.dto.distribution.request.UpdateDistributorMemberStatusRequestDTO;
import com.godzilla.distribution.dto.distribution.response.DistributionDistributorMemberDTO;
import com.godzilla.distribution.dto.distribution.response.DistributionDistributorMemberDetailDTO;
import com.godzilla.distribution.entity.shared.MedicalUserInfoEntity;
import com.godzilla.distribution.entity.distribution.DistributionDistributorEntity;
import com.godzilla.distribution.entity.distribution.DistributionDistributorMemberEntity;
import com.godzilla.distribution.enums.distribution.DistributionAuditBizType;
import com.godzilla.distribution.enums.distribution.DistributionDataScope;
import com.godzilla.distribution.enums.distribution.DistributionMemberStatus;
import com.godzilla.distribution.enums.distribution.DistributionRoleCode;
import com.godzilla.distribution.exception.BizException;
import com.godzilla.distribution.mapper.shared.MedicalUserInfoEntityMapper;
import com.godzilla.distribution.mapper.distribution.DistributionDistributorMapper;
import com.godzilla.distribution.mapper.distribution.DistributionDistributorMemberMapper;
import com.godzilla.distribution.service.distribution.DistributionAuditLogService;
import com.godzilla.distribution.service.distribution.DistributionDistributorMemberService;
import com.godzilla.distribution.service.distribution.DistributionOperatorService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class DistributorMemberServiceImpl implements DistributionDistributorMemberService {

    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_PAGE_SIZE = 10;
    private static final int MAX_PAGE_SIZE = 100;
    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<List<String>>() {};

    @Autowired
    private DistributionDistributorMemberMapper distributionDistributorMemberMapper;
    @Autowired
    private DistributionDistributorMapper distributionDistributorMapper;
    @Autowired
    private MedicalUserInfoEntityMapper medicalUserInfoEntityMapper;
    @Autowired
    private DistributionOperatorService distributionOperatorService;
    @Autowired
    private DistributionAuditLogService distributionAuditLogService;
    @Autowired
    private DistributionDataPermissionService distributionDataPermissionService;
    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public PageResponseDTO<DistributionDistributorMemberDTO> listMembers(Long distributorId, String status, String roleCode, String phone, Integer page, Integer pageSize) {
        int safePage = page == null || page < 1 ? DEFAULT_PAGE : page;
        int safePageSize = pageSize == null || pageSize < 1 ? DEFAULT_PAGE_SIZE : Math.min(pageSize, MAX_PAGE_SIZE);
        DistributionDataPermissionService.DistributionDataAccessScope accessScope = distributionDataPermissionService.resolveCurrentAccessScope();
        List<Long> authorizedDistributorIds = accessScope.isAllScope() ? null : accessScope.getAuthorizedDistributorIds();
        if (!accessScope.isAllScope() && !accessScope.isSelfScope() && (authorizedDistributorIds == null || authorizedDistributorIds.isEmpty())) {
            return new PageResponseDTO<DistributionDistributorMemberDTO>(Collections.<DistributionDistributorMemberDTO>emptyList(), 0, safePage, safePageSize);
        }
        Long authorizedMemberId = accessScope.isSelfScope() ? accessScope.getMemberId() : null;
        long total = distributionDistributorMemberMapper.countByConditionWithScope(distributorId, trim(status), trim(roleCode), trim(phone), authorizedDistributorIds, authorizedMemberId);
        List<DistributionDistributorMemberEntity> entities = distributionDistributorMemberMapper.selectByConditionWithScope(distributorId, trim(status), trim(roleCode), trim(phone), authorizedDistributorIds, authorizedMemberId, (safePage - 1) * safePageSize, safePageSize);
        return new PageResponseDTO<DistributionDistributorMemberDTO>(buildMemberDTOList(entities), total, safePage, safePageSize);
    }

    @Override
    public DistributionDistributorMemberDetailDTO getMember(Long id) {
        DistributionDistributorMemberEntity entity = getMemberEntity(id);
        DistributionDistributorMemberDetailDTO detailDTO = new DistributionDistributorMemberDetailDTO();
        BeanUtils.copyProperties(toDTO(entity, loadDistributorNameMap(Collections.singletonList(entity)), loadManagerNameMap(Collections.singletonList(entity))), detailDTO);
        detailDTO.setCreatedBy(entity.getCreatedBy());
        detailDTO.setUpdatedBy(entity.getUpdatedBy());
        return detailDTO;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createMember(CreateDistributorMemberRequestDTO request) {
        validateDistributor(request.getDistributorId());
        validateRoleCode(request.getRoleCode());
        validateDataScope(request.getDataScope());
        validateMemberPhone(request.getDistributorId(), request.getPhone(), null);
        validateUserBinding(request.getUserId(), null);
        validateManager(request.getDistributorId(), request.getManagerMemberId(), null);
        Long operatorUserId = distributionOperatorService.getCurrentOperatorUserId();
        DistributionDistributorMemberEntity entity = new DistributionDistributorMemberEntity();
        entity.setDistributorId(request.getDistributorId());
        entity.setUserId(request.getUserId());
        entity.setName(trim(request.getName()));
        entity.setPhone(trim(request.getPhone()));
        entity.setRoleCode(trim(request.getRoleCode()));
        entity.setDataScope(trim(request.getDataScope()));
        entity.setManagerMemberId(request.getManagerMemberId());
        entity.setProductLinesJson(writeProductLines(request.getProductLines()));
        entity.setTrainingStatus("pending");
        entity.setStatus(DistributionMemberStatus.ACTIVE.getCode());
        entity.setRemark(trim(request.getRemark()));
        entity.setCreatedBy(operatorUserId);
        entity.setUpdatedBy(operatorUserId);
        entity.setDeleted(0);
        distributionDistributorMemberMapper.insertSelective(entity);
        distributionAuditLogService.record(DistributionAuditBizType.MEMBER.getCode(), entity.getId(), "create", null, entity, "创建渠道成员");
        return entity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateMember(Long id, UpdateDistributorMemberRequestDTO request) {
        DistributionDistributorMemberEntity existing = getMemberEntity(id);
        validateDistributor(request.getDistributorId());
        validateRoleCode(request.getRoleCode());
        validateDataScope(request.getDataScope());
        validateMemberPhone(request.getDistributorId(), request.getPhone(), id);
        validateUserBinding(request.getUserId(), id);
        validateManager(request.getDistributorId(), request.getManagerMemberId(), id);
        DistributionDistributorMemberEntity update = new DistributionDistributorMemberEntity();
        update.setId(id);
        update.setDistributorId(request.getDistributorId());
        update.setUserId(request.getUserId());
        update.setName(trim(request.getName()));
        update.setPhone(trim(request.getPhone()));
        update.setRoleCode(trim(request.getRoleCode()));
        update.setDataScope(trim(request.getDataScope()));
        update.setManagerMemberId(request.getManagerMemberId());
        update.setProductLinesJson(writeProductLines(request.getProductLines()));
        update.setTrainingStatus(trim(request.getTrainingStatus()));
        update.setRemark(trim(request.getRemark()));
        update.setUpdatedBy(distributionOperatorService.getCurrentOperatorUserId());
        distributionDistributorMemberMapper.updateByPrimaryKeySelective(update);
        distributionAuditLogService.record(DistributionAuditBizType.MEMBER.getCode(), id, "update", existing, getMemberEntity(id), "更新渠道成员");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateMemberStatus(Long id, UpdateDistributorMemberStatusRequestDTO request) {
        validateMemberStatus(request.getStatus());
        DistributionDistributorMemberEntity existing = getMemberEntity(id);
        DistributionDistributorMemberEntity update = new DistributionDistributorMemberEntity();
        update.setId(id);
        update.setStatus(trim(request.getStatus()));
        update.setUpdatedBy(distributionOperatorService.getCurrentOperatorUserId());
        distributionDistributorMemberMapper.updateByPrimaryKeySelective(update);
        distributionAuditLogService.record(DistributionAuditBizType.MEMBER.getCode(), id, "update_status", existing, getMemberEntity(id), defaultValue(trim(request.getRemark()), "更新成员状态"));
    }

    private DistributionDistributorMemberEntity getMemberEntity(Long id) {
        DistributionDistributorMemberEntity entity = distributionDistributorMemberMapper.selectByPrimaryKey(id);
        if (entity == null || (entity.getDeleted() != null && entity.getDeleted() == 1)) {
            throw new BizException("渠道成员不存在", ResultCode.DISTRIBUTOR_MEMBER_NOT_FOUND.getCode());
        }
        return entity;
    }

    private List<DistributionDistributorMemberDTO> buildMemberDTOList(List<DistributionDistributorMemberEntity> entities) {
        if (entities == null || entities.isEmpty()) return Collections.emptyList();
        Map<Long, String> distributorNameMap = loadDistributorNameMap(entities);
        Map<Long, String> managerNameMap = loadManagerNameMap(entities);
        List<DistributionDistributorMemberDTO> list = new ArrayList<DistributionDistributorMemberDTO>();
        for (DistributionDistributorMemberEntity entity : entities) {
            list.add(toDTO(entity, distributorNameMap, managerNameMap));
        }
        return list;
    }

    private Map<Long, String> loadDistributorNameMap(List<DistributionDistributorMemberEntity> entities) {
        Map<Long, String> nameMap = new HashMap<Long, String>();
        for (DistributionDistributorMemberEntity entity : entities) {
            DistributionDistributorEntity distributor = distributionDistributorMapper.selectByPrimaryKey(entity.getDistributorId());
            if (distributor != null) nameMap.put(distributor.getId(), distributor.getName());
        }
        return nameMap;
    }

    private Map<Long, String> loadManagerNameMap(List<DistributionDistributorMemberEntity> entities) {
        Set<Long> managerIds = new LinkedHashSet<Long>();
        for (DistributionDistributorMemberEntity entity : entities) {
            if (entity.getManagerMemberId() != null) managerIds.add(entity.getManagerMemberId());
        }
        if (managerIds.isEmpty()) return Collections.emptyMap();
        List<DistributionDistributorMemberEntity> managers = distributionDistributorMemberMapper.selectByIds(new ArrayList<Long>(managerIds));
        Map<Long, String> nameMap = new HashMap<Long, String>();
        for (DistributionDistributorMemberEntity manager : managers) nameMap.put(manager.getId(), manager.getName());
        return nameMap;
    }

    private DistributionDistributorMemberDTO toDTO(DistributionDistributorMemberEntity entity, Map<Long, String> distributorNameMap, Map<Long, String> managerNameMap) {
        DistributionDistributorMemberDTO dto = new DistributionDistributorMemberDTO();
        BeanUtils.copyProperties(entity, dto);
        dto.setDistributorName(distributorNameMap.get(entity.getDistributorId()));
        dto.setManagerMemberName(managerNameMap.get(entity.getManagerMemberId()));
        dto.setProductLines(readProductLines(entity.getProductLinesJson()));
        return dto;
    }

    private void validateDistributor(Long distributorId) {
        DistributionDistributorEntity distributor = distributionDistributorMapper.selectByPrimaryKey(distributorId);
        if (distributor == null || (distributor.getDeleted() != null && distributor.getDeleted() == 1)) {
            throw new BizException("所属渠道不存在", ResultCode.DISTRIBUTOR_MEMBER_DISTRIBUTOR_NOT_FOUND.getCode());
        }
    }

    private void validateRoleCode(String roleCode) {
        if (!DistributionRoleCode.isValid(trim(roleCode))) throw new BizException("成员角色不合法", ResultCode.DISTRIBUTOR_MEMBER_ROLE_INVALID.getCode());
    }

    private void validateDataScope(String dataScope) {
        if (!DistributionDataScope.isValid(trim(dataScope))) throw new BizException("成员数据范围不合法", ResultCode.DISTRIBUTOR_MEMBER_DATA_SCOPE_INVALID.getCode());
    }

    private void validateMemberStatus(String status) {
        if (!DistributionMemberStatus.isValid(trim(status))) throw new BizException("成员状态不合法", ResultCode.DISTRIBUTOR_MEMBER_STATUS_INVALID.getCode());
    }

    private void validateMemberPhone(Long distributorId, String phone, Long currentId) {
        DistributionDistributorMemberEntity existing = distributionDistributorMemberMapper.selectByDistributorAndPhone(distributorId, trim(phone));
        if (existing == null) return;
        if (currentId != null && currentId.equals(existing.getId())) return;
        throw new BizException("同一渠道下成员手机号已存在", ResultCode.DISTRIBUTOR_MEMBER_PHONE_EXISTS.getCode());
    }

    private void validateUserBinding(Long userId, Long currentId) {
        if (userId == null) return;
        MedicalUserInfoEntity user = medicalUserInfoEntityMapper.selectByPrimaryKey(userId);
        if (user == null) throw new BizException("绑定用户不存在", ResultCode.DISTRIBUTOR_MEMBER_USER_NOT_FOUND.getCode());
        DistributionDistributorMemberEntity existing = distributionDistributorMemberMapper.selectByUserId(userId);
        if (existing == null) return;
        if (currentId != null && currentId.equals(existing.getId())) return;
        throw new BizException("该用户已绑定其他分销成员", ResultCode.DISTRIBUTOR_MEMBER_USER_ALREADY_BOUND.getCode());
    }

    private void validateManager(Long distributorId, Long managerMemberId, Long currentId) {
        if (managerMemberId == null) return;
        if (currentId != null && currentId.equals(managerMemberId)) throw new BizException("直属上级不能选择自己", ResultCode.DISTRIBUTOR_MEMBER_MANAGER_INVALID.getCode());
        DistributionDistributorMemberEntity manager = distributionDistributorMemberMapper.selectByPrimaryKey(managerMemberId);
        if (manager == null || (manager.getDeleted() != null && manager.getDeleted() == 1)) throw new BizException("直属上级成员不存在", ResultCode.DISTRIBUTOR_MEMBER_MANAGER_INVALID.getCode());
        if (!distributorId.equals(manager.getDistributorId())) throw new BizException("直属上级成员必须属于同一渠道", ResultCode.DISTRIBUTOR_MEMBER_MANAGER_INVALID.getCode());
    }

    private String writeProductLines(List<String> productLines) {
        try {
            if (productLines == null) return null;
            return objectMapper.writeValueAsString(productLines);
        } catch (JsonProcessingException e) { throw new IllegalStateException("序列化失败", e); }
    }

    private List<String> readProductLines(String productLinesJson) {
        if (StringUtils.isBlank(productLinesJson)) return Collections.emptyList();
        try { return objectMapper.readValue(productLinesJson, STRING_LIST_TYPE); }
        catch (JsonProcessingException e) { throw new IllegalStateException("解析失败", e); }
    }

    private String trim(String value) { return StringUtils.trimToNull(value); }
    private String defaultValue(String value, String defaultValue) { return StringUtils.isBlank(value) ? defaultValue : value; }
}
