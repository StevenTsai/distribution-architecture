package com.godzilla.distribution.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.godzilla.distribution.common.ResultCode;
import com.godzilla.distribution.common.pii.PiiCipher;
import com.godzilla.distribution.dto.distribution.request.CreateDistributorMemberRequestDTO;
import com.godzilla.distribution.dto.distribution.request.UpdateDistributorMemberRequestDTO;
import com.godzilla.distribution.entity.distribution.DistributionDistributorEntity;
import com.godzilla.distribution.entity.distribution.DistributionDistributorMemberEntity;
import com.godzilla.distribution.enums.distribution.DistributionDataScope;
import com.godzilla.distribution.enums.distribution.DistributionRoleCode;
import com.godzilla.distribution.exception.BizException;
import com.godzilla.distribution.mapper.distribution.DistributionDistributorMapper;
import com.godzilla.distribution.mapper.distribution.DistributionDistributorMemberMapper;
import com.godzilla.distribution.mapper.shared.UserProfileEntityMapper;
import com.godzilla.distribution.service.distribution.DistributionAuditLogService;
import com.godzilla.distribution.service.distribution.DistributionOperatorService;
import com.godzilla.distribution.service.distribution.impl.DistributionDataPermissionService;
import com.godzilla.distribution.service.distribution.impl.DistributorMemberServiceImpl;
import com.godzilla.distribution.service.distribution.support.ProductLineJsonCodec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 渠道成员服务测试：验证数据范围偏序授权（防自助提权）与全量 PUT 语义下的变更校验。
 */
@ExtendWith(MockitoExtension.class)
class DistributorMemberServiceImplTest {

    private static final String PHONE = "13800138000";
    private static final Long OPERATOR_USER_ID = 9L;
    private static final Long DISTRIBUTOR_ID = 1L;

    @Mock
    private DistributionDistributorMemberMapper memberMapper;
    @Mock
    private DistributionDistributorMapper distributorMapper;
    @Mock
    private UserProfileEntityMapper userProfileMapper;
    @Mock
    private DistributionOperatorService operatorService;
    @Mock
    private DistributionAuditLogService auditLogService;

    private PiiCipher piiCipher;
    private DistributorMemberServiceImpl memberService;

    @BeforeEach
    void setUp() {
        piiCipher = new PiiCipher("unit-test-key", new MockEnvironment());
        DistributionDataPermissionService permissionService =
                new DistributionDataPermissionService(operatorService, memberMapper, distributorMapper);
        memberService = new DistributorMemberServiceImpl(
                memberMapper, distributorMapper, userProfileMapper, operatorService,
                auditLogService, permissionService, piiCipher,
                new ProductLineJsonCodec(new ObjectMapper()));
        lenient().when(operatorService.getCurrentOperatorUserId()).thenReturn(OPERATOR_USER_ID);
        // 默认操作者为 ALL 范围，单个用例可覆盖为受限范围
        lenient().when(memberMapper.selectByUserId(OPERATOR_USER_ID))
                .thenReturn(operatorMember(3L, DISTRIBUTOR_ID, DistributionDataScope.ALL.getCode()));
    }

    // ========== createMember：数据范围授予偏序 ==========

    @Test
    void createMember_SelfScopeOperator_IsDenied() {
        when(memberMapper.selectByUserId(OPERATOR_USER_ID))
                .thenReturn(operatorMember(3L, DISTRIBUTOR_ID, DistributionDataScope.SELF.getCode()));
        when(distributorMapper.selectByPrimaryKey(DISTRIBUTOR_ID)).thenReturn(distributor());

        BizException ex = assertThrows(BizException.class,
                () -> memberService.createMember(createRequest(DistributionDataScope.ALL.getCode())));

        assertEquals(ResultCode.DISTRIBUTION_DATA_ACCESS_DENIED.getCode(), ex.getCode());
        verify(memberMapper, never()).insertSelective(any());
    }

    @Test
    void createMember_OwnScopeOperatorCannotGrantAll_IsDenied() {
        when(memberMapper.selectByUserId(OPERATOR_USER_ID))
                .thenReturn(operatorMember(4L, DISTRIBUTOR_ID, DistributionDataScope.OWN_DISTRIBUTOR.getCode()));
        when(distributorMapper.selectByPrimaryKey(DISTRIBUTOR_ID)).thenReturn(distributor());

        BizException ex = assertThrows(BizException.class,
                () -> memberService.createMember(createRequest(DistributionDataScope.ALL.getCode())));

        assertEquals(ResultCode.DISTRIBUTION_DATA_ACCESS_DENIED.getCode(), ex.getCode());
        verify(memberMapper, never()).insertSelective(any());
    }

    @Test
    void createMember_AllScopeOperatorCanGrantAll_Passes() {
        when(distributorMapper.selectByPrimaryKey(DISTRIBUTOR_ID)).thenReturn(distributor());
        when(memberMapper.selectByDistributorAndPhone(eq(DISTRIBUTOR_ID), anyString())).thenReturn(null);
        doAnswer(invocation -> {
            DistributionDistributorMemberEntity entity = invocation.getArgument(0);
            entity.setId(7L);
            return 1;
        }).when(memberMapper).insertSelective(any(DistributionDistributorMemberEntity.class));

        Long id = memberService.createMember(createRequest(DistributionDataScope.ALL.getCode()));

        assertEquals(7L, id);
        ArgumentCaptor<DistributionDistributorMemberEntity> captor =
                ArgumentCaptor.forClass(DistributionDistributorMemberEntity.class);
        verify(memberMapper).insertSelective(captor.capture());
        assertEquals(DistributionDataScope.ALL.getCode(), captor.getValue().getDataScope());
        assertEquals(DISTRIBUTOR_ID, captor.getValue().getDistributorId());
    }

    // ========== updateMember：全量 PUT 语义下的提权拦截 ==========

    @Test
    void updateMember_SelfScopeEscalationToAll_IsDenied() {
        when(memberMapper.selectByUserId(OPERATOR_USER_ID))
                .thenReturn(operatorMember(3L, DISTRIBUTOR_ID, DistributionDataScope.SELF.getCode()));
        DistributionDistributorMemberEntity existing = operatorMember(3L, DISTRIBUTOR_ID, DistributionDataScope.SELF.getCode());
        existing.setRoleCode(DistributionRoleCode.DIST_SALES.getCode());
        when(memberMapper.selectByPrimaryKey(3L)).thenReturn(existing);

        UpdateDistributorMemberRequestDTO request = updateRequest(
                DistributionRoleCode.DIST_SALES.getCode(), DistributionDataScope.ALL.getCode(), "新名字");

        BizException ex = assertThrows(BizException.class, () -> memberService.updateMember(3L, request));

        assertEquals(ResultCode.DISTRIBUTION_DATA_ACCESS_DENIED.getCode(), ex.getCode());
        verify(memberMapper, never()).updateByPrimaryKeySelective(any());
    }

    @Test
    void updateMember_SelfScopeRenameSamePrivileges_Passes() {
        when(memberMapper.selectByUserId(OPERATOR_USER_ID))
                .thenReturn(operatorMember(3L, DISTRIBUTOR_ID, DistributionDataScope.SELF.getCode()));
        DistributionDistributorMemberEntity existing = operatorMember(3L, DISTRIBUTOR_ID, DistributionDataScope.SELF.getCode());
        existing.setRoleCode(DistributionRoleCode.DIST_SALES.getCode());
        existing.setName("旧名字");
        existing.setPhone(piiCipher.encrypt(PHONE));
        when(memberMapper.selectByPrimaryKey(3L)).thenReturn(existing);
        when(distributorMapper.selectByPrimaryKey(DISTRIBUTOR_ID)).thenReturn(distributor());
        when(memberMapper.selectByDistributorAndPhone(eq(DISTRIBUTOR_ID), eq(piiCipher.lookupKey(PHONE)))).thenReturn(null);

        // 仅改名：角色/数据范围/归属渠道全部保持原值，应放行
        UpdateDistributorMemberRequestDTO request = updateRequest(
                DistributionRoleCode.DIST_SALES.getCode(), DistributionDataScope.SELF.getCode(), "新名字");

        memberService.updateMember(3L, request);

        ArgumentCaptor<DistributionDistributorMemberEntity> captor =
                ArgumentCaptor.forClass(DistributionDistributorMemberEntity.class);
        verify(memberMapper).updateByPrimaryKeySelective(captor.capture());
        assertEquals("新名字", captor.getValue().getName());
        assertEquals(DistributionDataScope.SELF.getCode(), captor.getValue().getDataScope());
        assertEquals(DISTRIBUTOR_ID, captor.getValue().getDistributorId());
    }

    @Test
    void updateMember_OwnScopeRoleChange_IsDenied() {
        when(memberMapper.selectByUserId(OPERATOR_USER_ID))
                .thenReturn(operatorMember(4L, DISTRIBUTOR_ID, DistributionDataScope.OWN_DISTRIBUTOR.getCode()));
        DistributionDistributorMemberEntity existing = memberEntity(5L, DISTRIBUTOR_ID,
                DistributionRoleCode.DIST_SALES.getCode(), DistributionDataScope.OWN_DISTRIBUTOR.getCode());
        when(memberMapper.selectByPrimaryKey(5L)).thenReturn(existing);

        UpdateDistributorMemberRequestDTO request = updateRequest(
                DistributionRoleCode.DIST_SUPER_ADMIN.getCode(), DistributionDataScope.OWN_DISTRIBUTOR.getCode(), "李四");

        BizException ex = assertThrows(BizException.class, () -> memberService.updateMember(5L, request));

        assertEquals(ResultCode.DISTRIBUTION_DATA_ACCESS_DENIED.getCode(), ex.getCode());
        verify(memberMapper, never()).updateByPrimaryKeySelective(any());
    }

    // ========== 测试数据构造 ==========

    private CreateDistributorMemberRequestDTO createRequest(String dataScope) {
        CreateDistributorMemberRequestDTO request = new CreateDistributorMemberRequestDTO();
        request.setDistributorId(DISTRIBUTOR_ID);
        request.setName("李四");
        request.setPhone("13900139000");
        request.setRoleCode(DistributionRoleCode.DIST_SALES.getCode());
        request.setDataScope(dataScope);
        return request;
    }

    private UpdateDistributorMemberRequestDTO updateRequest(String roleCode, String dataScope, String name) {
        UpdateDistributorMemberRequestDTO request = new UpdateDistributorMemberRequestDTO();
        request.setDistributorId(DISTRIBUTOR_ID);
        request.setName(name);
        request.setPhone(PHONE);
        request.setRoleCode(roleCode);
        request.setDataScope(dataScope);
        request.setTrainingStatus("pending");
        return request;
    }

    private DistributionDistributorMemberEntity operatorMember(Long memberId, Long distributorId, String dataScope) {
        return memberEntity(memberId, distributorId, DistributionRoleCode.DIST_SUPER_ADMIN.getCode(), dataScope);
    }

    private DistributionDistributorMemberEntity memberEntity(Long memberId, Long distributorId, String roleCode, String dataScope) {
        DistributionDistributorMemberEntity member = new DistributionDistributorMemberEntity();
        member.setId(memberId);
        member.setUserId(OPERATOR_USER_ID);
        member.setDistributorId(distributorId);
        member.setRoleCode(roleCode);
        member.setDataScope(dataScope);
        member.setDeleted(0);
        return member;
    }

    private DistributionDistributorEntity distributor() {
        DistributionDistributorEntity distributor = new DistributionDistributorEntity();
        distributor.setId(DISTRIBUTOR_ID);
        distributor.setName("华东总代理");
        distributor.setDeleted(0);
        return distributor;
    }
}
