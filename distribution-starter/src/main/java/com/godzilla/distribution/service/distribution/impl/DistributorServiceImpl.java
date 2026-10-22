package com.godzilla.distribution.service.distribution.impl;

import com.godzilla.distribution.common.ResultCode;
import com.godzilla.distribution.common.pii.PiiCipher;
import com.godzilla.distribution.dto.PageResponseDTO;
import com.godzilla.distribution.dto.distribution.request.CreateDistributorRequestDTO;
import com.godzilla.distribution.dto.distribution.request.UpdateDistributorRequestDTO;
import com.godzilla.distribution.dto.distribution.request.UpdateDistributorStatusRequestDTO;
import com.godzilla.distribution.dto.distribution.response.DistributionDistributorDTO;
import com.godzilla.distribution.dto.distribution.response.DistributionDistributorDetailDTO;
import com.godzilla.distribution.dto.distribution.response.DistributionProductLineDTO;
import com.godzilla.distribution.entity.shared.UserProfileEntity;
import com.godzilla.distribution.entity.distribution.DistributionDistributorEntity;
import com.godzilla.distribution.enums.distribution.DistributionAuditBizType;
import com.godzilla.distribution.enums.distribution.DistributionDataScope;
import com.godzilla.distribution.enums.distribution.DistributionDistributorStatus;
import com.godzilla.distribution.exception.BizException;
import com.godzilla.distribution.mapper.shared.UserProfileEntityMapper;
import com.godzilla.distribution.mapper.distribution.DistributionDistributorMapper;
import com.godzilla.distribution.service.distribution.DistributionAuditLogService;
import com.godzilla.distribution.service.distribution.DistributionOperatorService;
import com.godzilla.distribution.service.distribution.DistributionDistributorService;
import com.godzilla.distribution.service.distribution.support.ProductLineJsonCodec;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DistributorServiceImpl implements DistributionDistributorService {

    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_PAGE_SIZE = 10;
    private static final int MAX_PAGE_SIZE = 100;

    private final DistributionDistributorMapper distributionDistributorMapper;
    private final UserProfileEntityMapper userProfileMapper;
    private final DistributionOperatorService distributionOperatorService;
    private final DistributionAuditLogService distributionAuditLogService;
    private final DistributionDataPermissionService distributionDataPermissionService;
    private final PiiCipher piiCipher;
    private final ProductLineJsonCodec productLineJsonCodec;

    @Override
    public PageResponseDTO<DistributionDistributorDTO> listDistributors(String name, String status, String levelCode, String productLine, Integer page, Integer pageSize) {
        int safePage = page == null || page < 1 ? DEFAULT_PAGE : page;
        int safePageSize = pageSize == null || pageSize < 1 ? DEFAULT_PAGE_SIZE : Math.min(pageSize, MAX_PAGE_SIZE);
        DistributionDataPermissionService.DistributionDataAccessScope accessScope = distributionDataPermissionService.resolveCurrentAccessScope();
        List<Long> authorizedDistributorIds = accessScope.isAllScope()
                ? null
                : accessScope.getAuthorizedDistributorIds();
        if (!accessScope.isAllScope() && (authorizedDistributorIds == null || authorizedDistributorIds.isEmpty())) {
            return new PageResponseDTO<DistributionDistributorDTO>(Collections.<DistributionDistributorDTO>emptyList(), 0, safePage, safePageSize);
        }
        if (DistributionDataScope.SELF.getCode().equals(accessScope.getDataScope())) {
            authorizedDistributorIds = Collections.singletonList(accessScope.getDistributorId());
        }
        long total = distributionDistributorMapper.countByConditionWithScope(
                trim(name),
                trim(status),
                trim(levelCode),
                trim(productLine),
                authorizedDistributorIds
        );
        List<DistributionDistributorEntity> entities = distributionDistributorMapper.selectByConditionWithScope(
                trim(name),
                trim(status),
                trim(levelCode),
                trim(productLine),
                authorizedDistributorIds,
                (safePage - 1) * safePageSize,
                safePageSize
        );
        List<DistributionDistributorDTO> list = new ArrayList<DistributionDistributorDTO>();
        for (DistributionDistributorEntity entity : entities) {
            list.add(toDTO(entity, Collections.<String, DistributionProductLineDTO>emptyMap(), true));
        }
        return new PageResponseDTO<DistributionDistributorDTO>(list, total, safePage, safePageSize);
    }

    @Override
    public DistributionDistributorDetailDTO getDistributor(Long id) {
        DistributionDistributorEntity entity = getDistributorEntity(id);
        validateDistributorAccess(id);
        DistributionDistributorDetailDTO detailDTO = new DistributionDistributorDetailDTO();
        fillBaseDTO(entity, detailDTO, Collections.<String, DistributionProductLineDTO>emptyMap(), false);
        detailDTO.setCreatedBy(entity.getCreatedBy());
        detailDTO.setUpdatedBy(entity.getUpdatedBy());
        return detailDTO;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createDistributor(CreateDistributorRequestDTO request) {
        validateStatus(DistributionDistributorStatus.PENDING.getCode());
        ensureDistributorCodeNotExists(request.getCode(), null);
        validateParent(request.getParentId(), null);
        validateOwnerUser(request.getOwnerUserId());
        validateCreateScope(request.getParentId());

        Long operatorUserId = distributionOperatorService.getCurrentOperatorUserId();
        DistributionDistributorEntity entity = new DistributionDistributorEntity();
        entity.setCode(trim(request.getCode()));
        entity.setName(trim(request.getName()));
        entity.setParentId(request.getParentId());
        entity.setLevelCode(trim(request.getLevelCode()));
        entity.setOwnerUserId(request.getOwnerUserId());
        entity.setContactName(trim(request.getContactName()));
        entity.setContactPhone(encryptPhone(trim(request.getContactPhone())));
        entity.setProvince(trim(request.getProvince()));
        entity.setCity(trim(request.getCity()));
        entity.setSettlementType(trim(request.getSettlementType()));
        entity.setContractStatus(defaultValue(trim(request.getContractStatus()), "pending"));
        entity.setQualificationStatus(defaultValue(trim(request.getQualificationStatus()), "pending"));
        entity.setProductLinesJson(productLineJsonCodec.write(request.getProductLines()));
        entity.setStatus(DistributionDistributorStatus.PENDING.getCode());
        entity.setRemark(trim(request.getRemark()));
        entity.setCreatedBy(operatorUserId);
        entity.setUpdatedBy(operatorUserId);
        entity.setDeleted(0);
        distributionDistributorMapper.insertSelective(entity);
        distributionAuditLogService.record(
                DistributionAuditBizType.DISTRIBUTOR.getCode(),
                entity.getId(),
                "create",
                null,
                entity,
                "创建渠道"
        );
        return entity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateDistributor(Long id, UpdateDistributorRequestDTO request) {
        DistributionDistributorEntity existing = getDistributorEntity(id);
        validateDistributorAccess(id);
        validateParent(request.getParentId(), id);
        validateOwnerUser(request.getOwnerUserId());

        DistributionDistributorEntity update = new DistributionDistributorEntity();
        update.setId(id);
        update.setName(trim(request.getName()));
        update.setParentId(request.getParentId());
        update.setLevelCode(trim(request.getLevelCode()));
        update.setOwnerUserId(request.getOwnerUserId());
        update.setContactName(trim(request.getContactName()));
        update.setContactPhone(encryptPhone(trim(request.getContactPhone())));
        update.setProvince(trim(request.getProvince()));
        update.setCity(trim(request.getCity()));
        update.setSettlementType(trim(request.getSettlementType()));
        update.setContractStatus(defaultValue(trim(request.getContractStatus()), existing.getContractStatus()));
        update.setQualificationStatus(defaultValue(trim(request.getQualificationStatus()), existing.getQualificationStatus()));
        update.setProductLinesJson(productLineJsonCodec.write(request.getProductLines()));
        update.setRemark(trim(request.getRemark()));
        update.setUpdatedBy(distributionOperatorService.getCurrentOperatorUserId());
        distributionDistributorMapper.updateByPrimaryKeySelective(update);
        distributionAuditLogService.record(
                DistributionAuditBizType.DISTRIBUTOR.getCode(),
                id,
                "update",
                existing,
                getDistributorEntity(id),
                "更新渠道"
        );
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateDistributorStatus(Long id, UpdateDistributorStatusRequestDTO request) {
        validateStatus(request.getStatus());
        DistributionDistributorEntity existing = getDistributorEntity(id);
        validateDistributorAccess(id);

        DistributionDistributorEntity update = new DistributionDistributorEntity();
        update.setId(id);
        update.setStatus(trim(request.getStatus()));
        update.setUpdatedBy(distributionOperatorService.getCurrentOperatorUserId());
        distributionDistributorMapper.updateByPrimaryKeySelective(update);
        distributionAuditLogService.record(
                DistributionAuditBizType.DISTRIBUTOR.getCode(),
                id,
                "update_status",
                existing,
                getDistributorEntity(id),
                defaultValue(trim(request.getRemark()), "更新渠道状态")
        );
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteDistributor(Long id) {
        DistributionDistributorEntity existing = getDistributorEntity(id);
        validateDistributorAccess(id);

        DistributionDistributorEntity update = new DistributionDistributorEntity();
        update.setId(id);
        update.setDeleted(1);
        update.setUpdatedBy(distributionOperatorService.getCurrentOperatorUserId());
        distributionDistributorMapper.updateByPrimaryKeySelective(update);
        distributionAuditLogService.record(
                DistributionAuditBizType.DISTRIBUTOR.getCode(),
                id,
                "delete",
                existing,
                null,
                "删除渠道"
        );
    }

    private DistributionDistributorEntity getDistributorEntity(Long id) {
        DistributionDistributorEntity entity = distributionDistributorMapper.selectByPrimaryKey(id);
        if (entity == null || (entity.getDeleted() != null && entity.getDeleted() == 1)) {
            throw new BizException("渠道不存在", ResultCode.DISTRIBUTOR_NOT_FOUND.getCode());
        }
        return entity;
    }

    private void ensureDistributorCodeNotExists(String code, Long currentId) {
        DistributionDistributorEntity existing = distributionDistributorMapper.selectByCode(trim(code));
        if (existing == null) {
            return;
        }
        if (currentId != null && currentId.equals(existing.getId())) {
            return;
        }
        throw new BizException("渠道编码已存在", ResultCode.DISTRIBUTOR_CODE_EXISTS.getCode());
    }

    private void validateParent(Long parentId, Long currentId) {
        if (parentId == null) {
            return;
        }
        if (currentId != null && currentId.equals(parentId)) {
            throw new BizException("上级渠道不能选择自己", ResultCode.DISTRIBUTOR_PARENT_INVALID.getCode());
        }
        DistributionDistributorEntity parent = distributionDistributorMapper.selectByPrimaryKey(parentId);
        if (parent == null || (parent.getDeleted() != null && parent.getDeleted() == 1)) {
            throw new BizException("上级渠道不存在", ResultCode.DISTRIBUTOR_PARENT_INVALID.getCode());
        }
    }

    private void validateOwnerUser(Long ownerUserId) {
        if (ownerUserId == null) {
            return;
        }
        UserProfileEntity user = userProfileMapper.selectByPrimaryKey(ownerUserId);
        if (user == null) {
            throw new BizException("负责人用户不存在", ResultCode.DISTRIBUTOR_OWNER_NOT_FOUND.getCode());
        }
    }

    private void validateStatus(String status) {
        if (!DistributionDistributorStatus.isValid(trim(status))) {
            throw new BizException("渠道状态不合法", ResultCode.DISTRIBUTOR_STATUS_INVALID.getCode());
        }
    }

    private void validateCreateScope(Long parentId) {
        DistributionDataPermissionService.DistributionDataAccessScope accessScope = distributionDataPermissionService.resolveCurrentAccessScope();
        if (accessScope.isAllScope()) {
            return;
        }
        if (parentId != null) {
            List<Long> authorizedDistributorIds = accessScope.getAuthorizedDistributorIds();
            if (authorizedDistributorIds == null || !authorizedDistributorIds.contains(parentId)) {
                throw new BizException("无权在该渠道下创建数据", ResultCode.DISTRIBUTION_DATA_ACCESS_DENIED.getCode());
            }
        }
    }

    private void validateDistributorAccess(Long distributorId) {
        DistributionDataPermissionService.DistributionDataAccessScope accessScope = distributionDataPermissionService.resolveCurrentAccessScope();
        if (accessScope.isAllScope()) {
            return;
        }
        List<Long> authorizedDistributorIds = accessScope.getAuthorizedDistributorIds();
        if (authorizedDistributorIds == null || !authorizedDistributorIds.contains(distributorId)) {
            throw new BizException("无权访问该数据", ResultCode.DISTRIBUTION_DATA_ACCESS_DENIED.getCode());
        }
    }

    private DistributionDistributorDTO toDTO(DistributionDistributorEntity entity,
                                             Map<String, DistributionProductLineDTO> productLineMap,
                                             boolean maskPhone) {
        DistributionDistributorDTO dto = new DistributionDistributorDTO();
        fillBaseDTO(entity, dto, productLineMap, maskPhone);
        return dto;
    }

    private void fillBaseDTO(DistributionDistributorEntity entity, DistributionDistributorDTO dto,
                             Map<String, DistributionProductLineDTO> productLineMap, boolean maskPhone) {
        dto.setId(entity.getId());
        dto.setCode(entity.getCode());
        dto.setName(entity.getName());
        dto.setParentId(entity.getParentId());
        dto.setLevelCode(entity.getLevelCode());
        dto.setOwnerUserId(entity.getOwnerUserId());
        dto.setContactName(entity.getContactName());
        String plainPhone = piiCipher.decrypt(entity.getContactPhone());
        dto.setContactPhone(maskPhone ? piiCipher.mask(plainPhone) : plainPhone);
        dto.setProvince(entity.getProvince());
        dto.setCity(entity.getCity());
        dto.setSettlementType(entity.getSettlementType());
        dto.setContractStatus(entity.getContractStatus());
        dto.setQualificationStatus(entity.getQualificationStatus());
        List<String> productLines = productLineJsonCodec.read(entity.getProductLinesJson());
        dto.setProductLines(productLines);
        dto.setProductLineMetas(resolveProductLineMetas(productLineMap, productLines));
        dto.setStatus(entity.getStatus());
        dto.setRemark(entity.getRemark());
        dto.setCreateTime(entity.getCreateTime());
        dto.setModifyTime(entity.getModifyTime());
    }

    private String encryptPhone(String contactPhone) {
        return StringUtils.isBlank(contactPhone) ? null : piiCipher.encrypt(contactPhone);
    }

    private List<DistributionProductLineDTO> resolveProductLineMetas(Map<String, DistributionProductLineDTO> productLineMap,
                                                                     List<String> productLines) {
        if (productLines == null || productLines.isEmpty()) {
            return Collections.emptyList();
        }
        List<DistributionProductLineDTO> result = new ArrayList<DistributionProductLineDTO>();
        for (String productLine : productLines) {
            DistributionProductLineDTO dto = productLineMap == null ? null : productLineMap.get(trim(productLine));
            if (dto != null) {
                result.add(dto);
            }
        }
        return result;
    }

    private String trim(String value) {
        return StringUtils.trimToNull(value);
    }

    private String defaultValue(String value, String defaultValue) {
        return StringUtils.isBlank(value) ? defaultValue : value;
    }
}
