package com.godzilla.distribution.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.godzilla.distribution.common.ResultCode;
import com.godzilla.distribution.dto.PageResponseDTO;
import com.godzilla.distribution.dto.distribution.response.DistributionAuditLogDTO;
import com.godzilla.distribution.dto.distribution.response.DistributionAuditLogDetailDTO;
import com.godzilla.distribution.entity.distribution.DistributionAuditLogEntity;
import com.godzilla.distribution.entity.distribution.DistributionDistributorMemberEntity;
import com.godzilla.distribution.enums.distribution.DistributionAuditBizType;
import com.godzilla.distribution.enums.distribution.DistributionDataScope;
import com.godzilla.distribution.enums.distribution.DistributionRoleCode;
import com.godzilla.distribution.exception.BizException;
import com.godzilla.distribution.mapper.distribution.DistributionAuditLogMapper;
import com.godzilla.distribution.mapper.distribution.DistributionDistributorMapper;
import com.godzilla.distribution.mapper.distribution.DistributionDistributorMemberMapper;
import com.godzilla.distribution.mapper.distribution.DistributionLeadMapper;
import com.godzilla.distribution.service.distribution.DistributionOperatorService;
import com.godzilla.distribution.service.distribution.impl.DistributionAuditLogServiceImpl;
import com.godzilla.distribution.service.distribution.impl.DistributionDataPermissionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 审计日志服务测试：验证租户行过滤基于冗余 distributor_id 列，
 * 而非会与其他表渠道 ID 撞号的 biz_id（跨租户读回归）。
 */
@ExtendWith(MockitoExtension.class)
class DistributionAuditLogServiceImplTest {

    private static final Long OPERATOR_USER_ID = 9L;
    /** 操作者所属渠道；授权范围 = [OWN_DISTRIBUTOR_ID] */
    private static final Long OWN_DISTRIBUTOR_ID = 2L;

    @Mock
    private DistributionAuditLogMapper auditLogMapper;
    @Mock
    private DistributionOperatorService operatorService;
    @Mock
    private DistributionDistributorMemberMapper memberMapper;
    @Mock
    private DistributionLeadMapper leadMapper;
    @Mock
    private DistributionDistributorMapper distributorMapper;

    private DistributionAuditLogServiceImpl auditLogService;

    @BeforeEach
    void setUp() {
        DistributionDataPermissionService permissionService =
                new DistributionDataPermissionService(operatorService, memberMapper, distributorMapper);
        auditLogService = new DistributionAuditLogServiceImpl(
                auditLogMapper, operatorService, permissionService, memberMapper, leadMapper, new ObjectMapper());
        lenient().when(operatorService.getCurrentOperatorUserId()).thenReturn(OPERATOR_USER_ID);
        lenient().when(memberMapper.selectByUserId(OPERATOR_USER_ID)).thenReturn(operatorMember());
    }

    /**
     * 回归：审计行 biz_id=2 恰好撞上操作者授权渠道 ID（2），但其真实归属渠道
     * distributor_id=3 属于其他租户。旧实现按 biz_id IN (授权渠道) 过滤会泄露该行；
     * 新实现按 distributor_id 过滤，详情查询必须拒绝。
     */
    @Test
    void getLog_BizIdCollidesWithAuthorizedDistributorId_CrossTenantRowDenied() {
        DistributionAuditLogEntity entity = auditLog(10L, DistributionAuditBizType.MEMBER.getCode(), 2L, 3L);
        when(auditLogMapper.selectByPrimaryKey(10L)).thenReturn(entity);

        BizException ex = assertThrows(BizException.class, () -> auditLogService.getLog(10L));

        assertEquals(ResultCode.DISTRIBUTION_DATA_ACCESS_DENIED.getCode(), ex.getCode());
    }

    /** 列表查询的租户过滤参数必须是授权渠道 ID 集合（SQL 层映射到 distributor_id IN ...）。 */
    @Test
    void listLogs_ScopeFilterUsesAuthorizedDistributorIds() {
        when(auditLogMapper.countByConditionWithScope(isNull(), isNull(), isNull(), anyList())).thenReturn(0L);
        when(auditLogMapper.selectByConditionWithScope(isNull(), isNull(), isNull(), anyList(), anyInt(), anyInt()))
                .thenReturn(Collections.emptyList());

        PageResponseDTO<DistributionAuditLogDTO> page = auditLogService.listLogs(null, null, null, 1, 10);

        List<Long> expectedScope = Collections.singletonList(OWN_DISTRIBUTOR_ID);
        verify(auditLogMapper).countByConditionWithScope(isNull(), isNull(), isNull(), eq(expectedScope));
        verify(auditLogMapper).selectByConditionWithScope(isNull(), isNull(), isNull(), eq(expectedScope), eq(0), eq(10));
        assertEquals(0, page.getList().size());
    }

    /** 存量行 distributor_id 为空时回退到业务表解析归属渠道，授权行仍可访问。 */
    @Test
    void getLog_LegacyNullDistributorId_FallsBackToBizTableLookup() {
        DistributionAuditLogEntity entity = auditLog(11L, DistributionAuditBizType.MEMBER.getCode(), 5L, null);
        when(auditLogMapper.selectByPrimaryKey(11L)).thenReturn(entity);
        DistributionDistributorMemberEntity member = new DistributionDistributorMemberEntity();
        member.setId(5L);
        member.setDistributorId(OWN_DISTRIBUTOR_ID);
        when(memberMapper.selectByPrimaryKey(5L)).thenReturn(member);

        DistributionAuditLogDetailDTO detail = auditLogService.getLog(11L);

        assertEquals(11L, detail.getId());
    }

    private DistributionAuditLogEntity auditLog(Long id, String bizType, Long bizId, Long distributorId) {
        DistributionAuditLogEntity entity = new DistributionAuditLogEntity();
        entity.setId(id);
        entity.setBizType(bizType);
        entity.setBizId(bizId);
        entity.setDistributorId(distributorId);
        entity.setAction("update");
        entity.setOperatorUserId(OPERATOR_USER_ID);
        entity.setOperatorName("测试操作者");
        return entity;
    }

    private DistributionDistributorMemberEntity operatorMember() {
        DistributionDistributorMemberEntity member = new DistributionDistributorMemberEntity();
        member.setId(20L);
        member.setUserId(OPERATOR_USER_ID);
        member.setDistributorId(OWN_DISTRIBUTOR_ID);
        member.setRoleCode(DistributionRoleCode.DIST_SUPER_ADMIN.getCode());
        member.setDataScope(DistributionDataScope.OWN_DISTRIBUTOR.getCode());
        member.setDeleted(0);
        return member;
    }
}
