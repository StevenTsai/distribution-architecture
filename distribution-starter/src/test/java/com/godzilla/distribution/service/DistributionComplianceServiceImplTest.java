package com.godzilla.distribution.service;

import com.godzilla.distribution.common.ResultCode;
import com.godzilla.distribution.dto.distribution.request.ApproveDistributionComplianceRecordRequestDTO;
import com.godzilla.distribution.dto.distribution.request.CreateDistributionComplianceRecordRequestDTO;
import com.godzilla.distribution.dto.distribution.request.RejectDistributionComplianceRecordRequestDTO;
import com.godzilla.distribution.entity.distribution.DistributionComplianceRecordEntity;
import com.godzilla.distribution.entity.distribution.DistributionDistributorMemberEntity;
import com.godzilla.distribution.enums.distribution.DistributionAuditBizType;
import com.godzilla.distribution.enums.distribution.DistributionComplianceRecordType;
import com.godzilla.distribution.enums.distribution.DistributionComplianceStatus;
import com.godzilla.distribution.enums.distribution.DistributionDataScope;
import com.godzilla.distribution.enums.distribution.DistributionRoleCode;
import com.godzilla.distribution.exception.BizException;
import com.godzilla.distribution.mapper.distribution.DistributionComplianceRecordMapper;
import com.godzilla.distribution.mapper.distribution.DistributionDistributorMapper;
import com.godzilla.distribution.mapper.distribution.DistributionDistributorMemberMapper;
import com.godzilla.distribution.mapper.distribution.DistributionLeadMapper;
import com.godzilla.distribution.service.distribution.DistributionAuditLogService;
import com.godzilla.distribution.service.distribution.DistributionOperatorService;
import com.godzilla.distribution.service.distribution.impl.DistributionComplianceServiceImpl;
import com.godzilla.distribution.service.distribution.impl.DistributionDataPermissionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 合规服务测试：验证审核操作的 ALL 范围鉴权、业务类型枚举校验与归属渠道解析。
 */
@ExtendWith(MockitoExtension.class)
class DistributionComplianceServiceImplTest {

    private static final Long OPERATOR_USER_ID = 9L;
    private static final Long DISTRIBUTOR_ID = 1L;

    @Mock
    private DistributionComplianceRecordMapper complianceMapper;
    @Mock
    private DistributionOperatorService operatorService;
    @Mock
    private DistributionAuditLogService auditLogService;
    @Mock
    private DistributionDistributorMemberMapper memberMapper;
    @Mock
    private DistributionLeadMapper leadMapper;
    @Mock
    private DistributionDistributorMapper distributorMapper;

    private DistributionComplianceServiceImpl complianceService;

    @BeforeEach
    void setUp() {
        DistributionDataPermissionService permissionService =
                new DistributionDataPermissionService(operatorService, memberMapper, distributorMapper);
        complianceService = new DistributionComplianceServiceImpl(
                complianceMapper, operatorService, auditLogService, permissionService, memberMapper, leadMapper);
        lenient().when(operatorService.getCurrentOperatorUserId()).thenReturn(OPERATOR_USER_ID);
        // 默认操作者为 ALL 范围，受限用例自行覆盖
        lenient().when(memberMapper.selectByUserId(OPERATOR_USER_ID)).thenReturn(operatorMember(DistributionDataScope.ALL.getCode()));
    }

    // ========== 审核（approve/reject）：仅 ALL 范围 ==========

    @Test
    void rejectRecord_NonAllOperator_IsDenied() {
        when(memberMapper.selectByUserId(OPERATOR_USER_ID))
                .thenReturn(operatorMember(DistributionDataScope.OWN_DISTRIBUTOR.getCode()));
        when(complianceMapper.selectByPrimaryKey(1L)).thenReturn(pendingRecord(1L));

        RejectDistributionComplianceRecordRequestDTO request = new RejectDistributionComplianceRecordRequestDTO();
        request.setRemark("材料不全");

        BizException ex = assertThrows(BizException.class, () -> complianceService.rejectRecord(1L, request));

        assertEquals(ResultCode.DISTRIBUTION_COMPLIANCE_REVIEW_DENIED.getCode(), ex.getCode());
        verify(complianceMapper, never()).updateByPrimaryKeySelective(any());
    }

    @Test
    void approveRecord_NonAllOperator_IsDenied() {
        when(memberMapper.selectByUserId(OPERATOR_USER_ID))
                .thenReturn(operatorMember(DistributionDataScope.OWN_AND_CHILDREN.getCode()));
        when(complianceMapper.selectByPrimaryKey(1L)).thenReturn(pendingRecord(1L));

        BizException ex = assertThrows(BizException.class,
                () -> complianceService.approveRecord(1L, new ApproveDistributionComplianceRecordRequestDTO()));

        assertEquals(ResultCode.DISTRIBUTION_COMPLIANCE_REVIEW_DENIED.getCode(), ex.getCode());
        verify(complianceMapper, never()).updateByPrimaryKeySelective(any());
    }

    @Test
    void rejectRecord_AllOperator_Succeeds() {
        when(complianceMapper.selectByPrimaryKey(1L)).thenReturn(pendingRecord(1L));

        RejectDistributionComplianceRecordRequestDTO request = new RejectDistributionComplianceRecordRequestDTO();
        request.setRemark("材料不全");

        complianceService.rejectRecord(1L, request);

        ArgumentCaptor<DistributionComplianceRecordEntity> captor =
                ArgumentCaptor.forClass(DistributionComplianceRecordEntity.class);
        verify(complianceMapper).updateByPrimaryKeySelective(captor.capture());
        assertEquals(DistributionComplianceStatus.REJECTED.getCode(), captor.getValue().getStatus());
        assertEquals(OPERATOR_USER_ID, captor.getValue().getReviewedBy());
    }

    // ========== 创建：bizType 枚举校验 + 归属渠道解析 ==========

    @Test
    void createRecord_InvalidBizType_IsRejected() {
        CreateDistributionComplianceRecordRequestDTO request = new CreateDistributionComplianceRecordRequestDTO();
        request.setBizType("order"); // 非法枚举值
        request.setBizId(1L);
        request.setRecordType(DistributionComplianceRecordType.QUALIFICATION.getCode());

        BizException ex = assertThrows(BizException.class, () -> complianceService.createRecord(request));

        assertEquals(ResultCode.DISTRIBUTION_COMPLIANCE_BIZ_TYPE_INVALID.getCode(), ex.getCode());
        verify(complianceMapper, never()).insertSelective(any());
    }

    @Test
    void createRecord_NonAllOperatorForeignChannel_IsDenied() {
        when(memberMapper.selectByUserId(OPERATOR_USER_ID))
                .thenReturn(operatorMember(DistributionDataScope.OWN_DISTRIBUTOR.getCode()));

        CreateDistributionComplianceRecordRequestDTO request = new CreateDistributionComplianceRecordRequestDTO();
        request.setBizType(DistributionAuditBizType.DISTRIBUTOR.getCode());
        request.setBizId(99L); // 渠道 99 不在授权范围 [1] 内
        request.setRecordType(DistributionComplianceRecordType.QUALIFICATION.getCode());

        BizException ex = assertThrows(BizException.class, () -> complianceService.createRecord(request));

        assertEquals(ResultCode.DISTRIBUTION_DATA_ACCESS_DENIED.getCode(), ex.getCode());
        verify(complianceMapper, never()).insertSelective(any());
    }

    @Test
    void createRecord_AllOperator_ResolvesDistributorIdAndStores() {
        doAnswer(invocation -> {
            DistributionComplianceRecordEntity entity = invocation.getArgument(0);
            entity.setId(30L);
            return 1;
        }).when(complianceMapper).insertSelective(any(DistributionComplianceRecordEntity.class));

        CreateDistributionComplianceRecordRequestDTO request = new CreateDistributionComplianceRecordRequestDTO();
        request.setBizType(DistributionAuditBizType.DISTRIBUTOR.getCode());
        request.setBizId(DISTRIBUTOR_ID);
        request.setRecordType(DistributionComplianceRecordType.QUALIFICATION.getCode());
        request.setContent("营业执照已更新");

        Long id = complianceService.createRecord(request);

        assertEquals(30L, id);
        ArgumentCaptor<DistributionComplianceRecordEntity> captor =
                ArgumentCaptor.forClass(DistributionComplianceRecordEntity.class);
        verify(complianceMapper).insertSelective(captor.capture());
        // 冗余归属渠道列必须落库，供列表 distributor_id IN 行过滤使用
        assertEquals(DISTRIBUTOR_ID, captor.getValue().getDistributorId());
        assertEquals(DistributionComplianceStatus.PENDING.getCode(), captor.getValue().getStatus());
    }

    // ========== 测试数据构造 ==========

    private DistributionComplianceRecordEntity pendingRecord(Long id) {
        DistributionComplianceRecordEntity entity = new DistributionComplianceRecordEntity();
        entity.setId(id);
        entity.setBizType(DistributionAuditBizType.DISTRIBUTOR.getCode());
        entity.setBizId(DISTRIBUTOR_ID);
        entity.setDistributorId(DISTRIBUTOR_ID);
        entity.setRecordType(DistributionComplianceRecordType.QUALIFICATION.getCode());
        entity.setStatus(DistributionComplianceStatus.PENDING.getCode());
        entity.setDeleted(0);
        return entity;
    }

    private DistributionDistributorMemberEntity operatorMember(String dataScope) {
        DistributionDistributorMemberEntity member = new DistributionDistributorMemberEntity();
        member.setId(20L);
        member.setUserId(OPERATOR_USER_ID);
        member.setDistributorId(DISTRIBUTOR_ID);
        member.setRoleCode(DistributionRoleCode.DIST_SUPER_ADMIN.getCode());
        member.setDataScope(dataScope);
        member.setDeleted(0);
        return member;
    }
}
