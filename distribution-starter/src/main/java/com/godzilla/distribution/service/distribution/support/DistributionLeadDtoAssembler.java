package com.godzilla.distribution.service.distribution.support;

import com.godzilla.distribution.common.pii.PiiCipher;
import com.godzilla.distribution.dto.distribution.response.DistributionLeadDTO;
import com.godzilla.distribution.dto.distribution.response.DistributionLeadDetailDTO;
import com.godzilla.distribution.entity.distribution.DistributionDistributorEntity;
import com.godzilla.distribution.entity.distribution.DistributionDistributorMemberEntity;
import com.godzilla.distribution.entity.distribution.DistributionLeadEntity;
import com.godzilla.distribution.mapper.distribution.DistributionDistributorMapper;
import com.godzilla.distribution.mapper.distribution.DistributionDistributorMemberMapper;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 线索实体到 DTO 的装配：负责 PII 解密/脱敏和渠道、成员名称补全。
 *
 * <p>列表脱敏、详情返回明文，两条路径共用同一份字段映射，避免新增字段时只改一处。</p>
 */
@Component
public class DistributionLeadDtoAssembler {

    private final DistributionDistributorMapper distributorMapper;
    private final DistributionDistributorMemberMapper memberMapper;
    private final PiiCipher piiCipher;

    public DistributionLeadDtoAssembler(DistributionDistributorMapper distributorMapper,
                                        DistributionDistributorMemberMapper memberMapper,
                                        PiiCipher piiCipher) {
        this.distributorMapper = distributorMapper;
        this.memberMapper = memberMapper;
        this.piiCipher = piiCipher;
    }

    /** 列表：手机号脱敏，并补全渠道/成员名称。 */
    public List<DistributionLeadDTO> toList(List<DistributionLeadEntity> entities) {
        if (entities == null || entities.isEmpty()) {
            return Collections.emptyList();
        }
        Map<Long, String> distributorNameMap = loadDistributorNameMap(entities);
        Map<Long, String> memberNameMap = loadMemberNameMap(entities);
        List<DistributionLeadDTO> list = new ArrayList<DistributionLeadDTO>();
        for (DistributionLeadEntity entity : entities) {
            DistributionLeadDTO dto = new DistributionLeadDTO();
            fillBaseDTO(entity, dto, true);
            dto.setSourceDistributorName(distributorNameMap.get(entity.getSourceDistributorId()));
            dto.setSourceMemberName(memberNameMap.get(entity.getSourceMemberId()));
            list.add(dto);
        }
        return list;
    }

    /** 详情：返回解密后的完整手机号（调用方已完成权限校验）。 */
    public DistributionLeadDetailDTO toDetail(DistributionLeadEntity entity) {
        DistributionLeadDetailDTO dto = new DistributionLeadDetailDTO();
        fillBaseDTO(entity, dto, false);
        dto.setCreatedBy(entity.getCreatedBy());
        dto.setUpdatedBy(entity.getUpdatedBy());
        return dto;
    }

    private void fillBaseDTO(DistributionLeadEntity entity, DistributionLeadDTO dto, boolean maskPhone) {
        dto.setId(entity.getId());
        dto.setLeadNo(entity.getLeadNo());
        dto.setContactName(entity.getContactName());
        String plainPhone = piiCipher.decrypt(entity.getContactPhone());
        dto.setContactPhone(maskPhone ? piiCipher.mask(plainPhone) : plainPhone);
        dto.setSourceDistributorId(entity.getSourceDistributorId());
        dto.setSourceMemberId(entity.getSourceMemberId());
        dto.setIntentProductLine(entity.getIntentProductLine());
        dto.setSourceRegion(entity.getSourceRegion());
        dto.setSourceChannel(entity.getSourceChannel());
        dto.setOwnerUserId(entity.getOwnerUserId());
        dto.setStage(entity.getStage());
        dto.setIsDuplicate(entity.getIsDuplicate());
        dto.setDuplicateLeadId(entity.getDuplicateLeadId());
        dto.setInvalidReason(entity.getInvalidReason());
        dto.setRemark(entity.getRemark());
        dto.setLatestFollowUpAt(entity.getLatestFollowUpAt());
        dto.setCreateTime(entity.getCreateTime());
        dto.setModifyTime(entity.getModifyTime());
    }

    private Map<Long, String> loadDistributorNameMap(List<DistributionLeadEntity> entities) {
        Map<Long, String> nameMap = new HashMap<Long, String>();
        for (DistributionLeadEntity entity : entities) {
            if (entity.getSourceDistributorId() != null && !nameMap.containsKey(entity.getSourceDistributorId())) {
                DistributionDistributorEntity distributor = distributorMapper.selectByPrimaryKey(entity.getSourceDistributorId());
                if (distributor != null) {
                    nameMap.put(distributor.getId(), distributor.getName());
                }
            }
        }
        return nameMap;
    }

    private Map<Long, String> loadMemberNameMap(List<DistributionLeadEntity> entities) {
        Map<Long, String> nameMap = new HashMap<Long, String>();
        for (DistributionLeadEntity entity : entities) {
            if (entity.getSourceMemberId() != null && !nameMap.containsKey(entity.getSourceMemberId())) {
                DistributionDistributorMemberEntity member = memberMapper.selectByPrimaryKey(entity.getSourceMemberId());
                if (member != null) {
                    nameMap.put(member.getId(), member.getName());
                }
            }
        }
        return nameMap;
    }
}
