package com.godzilla.distribution.service.distribution.impl;

import com.godzilla.distribution.common.ResultCode;
import com.godzilla.distribution.entity.distribution.DistributionDistributorEntity;
import com.godzilla.distribution.entity.distribution.DistributionDistributorMemberEntity;
import com.godzilla.distribution.enums.distribution.DistributionDataScope;
import com.godzilla.distribution.enums.distribution.DistributionRoleCode;
import com.godzilla.distribution.exception.BizException;
import com.godzilla.distribution.mapper.distribution.DistributionDistributorMapper;
import com.godzilla.distribution.mapper.distribution.DistributionDistributorMemberMapper;
import com.godzilla.distribution.service.distribution.DistributionOperatorService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class DistributionDataPermissionService {

    @Autowired
    private DistributionOperatorService distributionOperatorService;

    @Autowired
    private DistributionDistributorMemberMapper distributionDistributorMemberMapper;

    @Autowired
    private DistributionDistributorMapper distributionDistributorMapper;

    public DistributionDataAccessScope resolveCurrentAccessScope() {
        Long operatorUserId = distributionOperatorService.getCurrentOperatorUserId();
        DistributionDistributorMemberEntity currentMember = distributionDistributorMemberMapper.selectByUserId(operatorUserId);
        if (currentMember == null || (currentMember.getDeleted() != null && currentMember.getDeleted() == 1)) {
            throw new BizException("当前操作人未绑定分销成员", ResultCode.DISTRIBUTOR_MEMBER_NOT_FOUND.getCode());
        }

        String roleCode = trim(currentMember.getRoleCode());
        if (!DistributionRoleCode.isValid(roleCode)) {
            throw new BizException("当前操作人分销角色不合法", ResultCode.DISTRIBUTOR_MEMBER_ROLE_INVALID.getCode());
        }
        String dataScope = trim(currentMember.getDataScope());
        if (!DistributionDataScope.isValid(dataScope)) {
            throw new BizException("当前操作人数据范围不合法", ResultCode.DISTRIBUTOR_MEMBER_DATA_SCOPE_INVALID.getCode());
        }
        if (!DistributionDataScope.ALL.getCode().equals(dataScope) && currentMember.getDistributorId() == null) {
            throw new BizException("当前操作人未绑定所属渠道", ResultCode.DISTRIBUTOR_MEMBER_DISTRIBUTOR_NOT_FOUND.getCode());
        }

        List<Long> authorizedDistributorIds = resolveAuthorizedDistributorIds(currentMember.getDistributorId(), dataScope);
        return new DistributionDataAccessScope(
                operatorUserId,
                currentMember.getId(),
                currentMember.getDistributorId(),
                roleCode,
                dataScope,
                authorizedDistributorIds
        );
    }

    private List<Long> resolveAuthorizedDistributorIds(Long distributorId, String dataScope) {
        if (DistributionDataScope.ALL.getCode().equals(dataScope)) {
            return null;
        }
        if (distributorId == null) {
            return Collections.emptyList();
        }

        LinkedHashSet<Long> authorizedIds = new LinkedHashSet<Long>();
        authorizedIds.add(distributorId);
        if (!DistributionDataScope.OWN_AND_CHILDREN.getCode().equals(dataScope)) {
            return new ArrayList<Long>(authorizedIds);
        }

        List<Long> parentIds = Collections.singletonList(distributorId);
        while (!parentIds.isEmpty()) {
            List<DistributionDistributorEntity> children = distributionDistributorMapper.selectByParentIds(parentIds);
            if (children == null || children.isEmpty()) {
                break;
            }
            List<Long> nextParentIds = new ArrayList<Long>();
            for (DistributionDistributorEntity child : children) {
                if (child == null || child.getId() == null) {
                    continue;
                }
                if (authorizedIds.add(child.getId())) {
                    nextParentIds.add(child.getId());
                }
            }
            parentIds = nextParentIds;
        }
        return new ArrayList<Long>(authorizedIds);
    }

    public void checkAccessPermission(Long distributorId, Long memberId, Long ownerUserId) {
        DistributionDataAccessScope scope = resolveCurrentAccessScope();
        if (scope.isAllScope()) {
            return;
        }
        if (scope.isSelfScope()) {
            boolean matched = false;
            if (memberId != null && scope.getMemberId().equals(memberId)) {
                matched = true;
            }
            if (ownerUserId != null && scope.getOperatorUserId().equals(ownerUserId)) {
                matched = true;
            }
            if (!matched) {
                throw new BizException("无权访问该数据", ResultCode.DISTRIBUTION_DATA_ACCESS_DENIED.getCode());
            }
            return;
        }
        List<Long> authorizedIds = scope.getAuthorizedDistributorIds();
        if (authorizedIds == null || authorizedIds.isEmpty() || !authorizedIds.contains(distributorId)) {
            throw new BizException("无权访问该数据", ResultCode.DISTRIBUTION_DATA_ACCESS_DENIED.getCode());
        }
    }

    private String trim(String value) {
        return StringUtils.trimToNull(value);
    }

    public static final class DistributionDataAccessScope {
        private final Long operatorUserId;
        private final Long memberId;
        private final Long distributorId;
        private final String roleCode;
        private final String dataScope;
        private final List<Long> authorizedDistributorIds;

        private DistributionDataAccessScope(Long operatorUserId,
                                            Long memberId,
                                            Long distributorId,
                                            String roleCode,
                                            String dataScope,
                                            List<Long> authorizedDistributorIds) {
            this.operatorUserId = operatorUserId;
            this.memberId = memberId;
            this.distributorId = distributorId;
            this.roleCode = roleCode;
            this.dataScope = dataScope;
            this.authorizedDistributorIds = authorizedDistributorIds == null
                    ? null
                    : Collections.unmodifiableList(new ArrayList<Long>(authorizedDistributorIds));
        }

        public static DistributionDataAccessScope of(Long operatorUserId,
                                                     Long memberId,
                                                     Long distributorId,
                                                     String roleCode,
                                                     String dataScope,
                                                     List<Long> authorizedDistributorIds) {
            return new DistributionDataAccessScope(
                    operatorUserId,
                    memberId,
                    distributorId,
                    roleCode,
                    dataScope,
                    authorizedDistributorIds
            );
        }

        public Long getOperatorUserId() {
            return operatorUserId;
        }

        public Long getMemberId() {
            return memberId;
        }

        public Long getDistributorId() {
            return distributorId;
        }

        public String getRoleCode() {
            return roleCode;
        }

        public String getDataScope() {
            return dataScope;
        }

        public List<Long> getAuthorizedDistributorIds() {
            return authorizedDistributorIds;
        }

        public boolean isAllScope() {
            return DistributionDataScope.ALL.getCode().equals(dataScope);
        }

        public boolean isSelfScope() {
            return DistributionDataScope.SELF.getCode().equals(dataScope);
        }
    }
}
