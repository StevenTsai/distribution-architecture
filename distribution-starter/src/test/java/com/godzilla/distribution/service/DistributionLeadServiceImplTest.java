package com.godzilla.distribution.service;

import com.godzilla.distribution.common.ResultCode;
import com.godzilla.distribution.common.pii.PiiCipher;
import com.godzilla.distribution.dto.PageResponseDTO;
import com.godzilla.distribution.dto.distribution.request.CreateDistributionLeadRequestDTO;
import com.godzilla.distribution.dto.distribution.request.UpdateDistributionLeadRequestDTO;
import com.godzilla.distribution.dto.distribution.response.DistributionLeadDTO;
import com.godzilla.distribution.dto.distribution.response.DistributionLeadDetailDTO;
import com.godzilla.distribution.entity.distribution.DistributionDistributorEntity;
import com.godzilla.distribution.entity.distribution.DistributionDistributorMemberEntity;
import com.godzilla.distribution.entity.distribution.DistributionLeadEntity;
import com.godzilla.distribution.enums.distribution.DistributionDataScope;
import com.godzilla.distribution.enums.distribution.DistributionRoleCode;
import com.godzilla.distribution.exception.BizException;
import com.godzilla.distribution.mapper.distribution.DistributionDistributorMapper;
import com.godzilla.distribution.mapper.distribution.DistributionDistributorMemberMapper;
import com.godzilla.distribution.mapper.distribution.DistributionLeadFollowUpMapper;
import com.godzilla.distribution.mapper.distribution.DistributionLeadMapper;
import com.godzilla.distribution.service.distribution.DistributionAuditLogService;
import com.godzilla.distribution.service.distribution.DistributionOperatorService;
import com.godzilla.distribution.service.distribution.impl.DistributionDataPermissionService;
import com.godzilla.distribution.service.distribution.impl.DistributionLeadServiceImpl;
import com.godzilla.distribution.service.distribution.support.DistributionLeadDtoAssembler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.env.MockEnvironment;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 线索服务测试：验证 PII 加密写入、检索键去重、脱敏展示和权限拦截。
 */
@ExtendWith(MockitoExtension.class)
class DistributionLeadServiceImplTest {

    private static final String PHONE = "13800138000";
    private static final Long OPERATOR_USER_ID = 9L;
    private static final Long DISTRIBUTOR_ID = 1L;

    @Mock
    private DistributionLeadMapper leadMapper;
    @Mock
    private DistributionLeadFollowUpMapper followUpMapper;
    @Mock
    private DistributionDistributorMapper distributorMapper;
    @Mock
    private DistributionDistributorMemberMapper memberMapper;
    @Mock
    private DistributionOperatorService operatorService;
    @Mock
    private DistributionAuditLogService auditLogService;
    private PiiCipher piiCipher;
    private DistributionDataPermissionService dataPermissionService;
    private DistributionLeadServiceImpl leadService;

    @BeforeEach
    void setUp() {
        piiCipher = new PiiCipher("unit-test-key", new MockEnvironment());
        // 权限服务使用真实实现，通过 mock 它的依赖来控制数据范围
        dataPermissionService = new DistributionDataPermissionService(operatorService, memberMapper, distributorMapper);
        leadService = new DistributionLeadServiceImpl(
                leadMapper, followUpMapper, distributorMapper, memberMapper,
                operatorService, auditLogService, dataPermissionService, piiCipher,
                new DistributionLeadDtoAssembler(distributorMapper, memberMapper, piiCipher));
        lenient().when(operatorService.getCurrentOperatorUserId()).thenReturn(OPERATOR_USER_ID);
        lenient().when(memberMapper.selectByUserId(OPERATOR_USER_ID)).thenReturn(member(1L, DISTRIBUTOR_ID,
                DistributionRoleCode.DIST_SUPER_ADMIN.getCode(), DistributionDataScope.ALL.getCode(), null));
    }

    @Test
    void createLead_StoresEncryptedPhoneAndLookupKey() {
        mockDistributorExists();
        when(leadMapper.selectDuplicateLead(anyString(), anyString(), isNull())).thenReturn(null);
        doAnswer(invocation -> {
            DistributionLeadEntity entity = invocation.getArgument(0);
            entity.setId(100L);
            return 1;
        }).when(leadMapper).insertSelective(any(DistributionLeadEntity.class));

        Long id = leadService.createLead(createRequest());

        assertEquals(100L, id);
        ArgumentCaptor<DistributionLeadEntity> captor = ArgumentCaptor.forClass(DistributionLeadEntity.class);
        verify(leadMapper).insertSelective(captor.capture());
        DistributionLeadEntity saved = captor.getValue();
        assertNotEquals(PHONE, saved.getContactPhone());
        assertTrue(saved.getContactPhone().startsWith("enc:v1:"));
        assertEquals(piiCipher.lookupKey(PHONE), saved.getContactPhoneLookup());
        assertEquals(PHONE, piiCipher.decrypt(saved.getContactPhone()));
    }

    @Test
    void createLead_DuplicatePhone_Throws() {
        mockDistributorExists();
        when(leadMapper.selectDuplicateLead(anyString(), anyString(), isNull()))
                .thenReturn(new DistributionLeadEntity());

        assertThrows(BizException.class, () -> leadService.createLead(createRequest()));
        verify(leadMapper, never()).insertSelective(any());
    }

    @Test
    void createLead_DuplicateCheckUsesLookupKeyNotPlainText() {
        mockDistributorExists();
        when(leadMapper.selectDuplicateLead(anyString(), anyString(), isNull())).thenReturn(null);
        when(operatorService.getCurrentOperatorUserId()).thenReturn(OPERATOR_USER_ID);

        leadService.createLead(createRequest());

        verify(leadMapper).selectDuplicateLead(eq(piiCipher.lookupKey(PHONE)), eq("gene"), isNull());
        verify(leadMapper, never()).selectDuplicateLead(eq(PHONE), anyString(), any());
    }

    @Test
    void createLead_OutsideAuthorizedScope_Throws() {
        mockDistributorExists();
        when(memberMapper.selectByUserId(OPERATOR_USER_ID)).thenReturn(member(2L, 99L,
                DistributionRoleCode.DIST_SALES.getCode(), DistributionDataScope.OWN_DISTRIBUTOR.getCode(), null));

        assertThrows(BizException.class, () -> leadService.createLead(createRequest()));
        verify(leadMapper, never()).insertSelective(any());
    }

    @Test
    void listLeads_ReturnsMaskedPhone() {
        when(leadMapper.countByConditionWithScope(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(1L);
        when(leadMapper.selectByConditionWithScope(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(Collections.singletonList(encryptedLead()));
        when(distributorMapper.selectByPrimaryKey(DISTRIBUTOR_ID)).thenReturn(null);

        PageResponseDTO<DistributionLeadDTO> page = leadService.listLeads(null, null, null, null, null, null, 1, 10);

        assertEquals(1, page.getList().size());
        assertEquals("138****8000", page.getList().get(0).getContactPhone());
    }

    @Test
    void listLeads_PhoneKeywordIsConvertedToLookupKey() {
        when(leadMapper.countByConditionWithScope(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(0L);
        when(leadMapper.selectByConditionWithScope(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(Collections.emptyList());

        leadService.listLeads(null, PHONE, null, null, null, null, 1, 10);

        verify(leadMapper).countByConditionWithScope(isNull(), eq(PHONE), eq(piiCipher.lookupKey(PHONE)),
                isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), isNull());
    }

    @Test
    void listLeads_NameKeywordIsNotConvertedToLookupKey() {
        when(leadMapper.countByConditionWithScope(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(0L);
        when(leadMapper.selectByConditionWithScope(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(Collections.emptyList());

        leadService.listLeads(null, "张三", null, null, null, null, 1, 10);

        verify(leadMapper).countByConditionWithScope(isNull(), eq("张三"), isNull(),
                isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), isNull());
    }

    @Test
    void getLead_ReturnsDecryptedPhone() {
        DistributionLeadEntity entity = encryptedLead();
        when(leadMapper.selectByPrimaryKey(1L)).thenReturn(entity);

        DistributionLeadDetailDTO detail = leadService.getLead(1L);

        assertEquals(PHONE, detail.getContactPhone());
    }

    @Test
    void getLead_NotFound_Throws() {
        when(leadMapper.selectByPrimaryKey(404L)).thenReturn(null);

        assertThrows(BizException.class, () -> leadService.getLead(404L));
    }

    @Test
    void updateLead_ReEncryptsPhoneWhenChanged() {
        DistributionLeadEntity existing = encryptedLead();
        when(leadMapper.selectByPrimaryKey(1L)).thenReturn(existing);
        when(distributorMapper.selectByPrimaryKey(DISTRIBUTOR_ID)).thenReturn(distributor());
        when(leadMapper.selectDuplicateLead(anyString(), anyString(), eq(1L))).thenReturn(null);

        UpdateDistributionLeadRequestDTO request = new UpdateDistributionLeadRequestDTO();
        request.setContactName("张三");
        request.setContactPhone("13900139000");
        request.setSourceDistributorId(DISTRIBUTOR_ID);
        request.setIntentProductLine("gene");

        leadService.updateLead(1L, request);

        ArgumentCaptor<DistributionLeadEntity> captor = ArgumentCaptor.forClass(DistributionLeadEntity.class);
        verify(leadMapper).updateByPrimaryKeySelective(captor.capture());
        DistributionLeadEntity update = captor.getValue();
        assertTrue(update.getContactPhone().startsWith("enc:v1:"));
        assertEquals(piiCipher.lookupKey("13900139000"), update.getContactPhoneLookup());
        assertEquals("13900139000", piiCipher.decrypt(update.getContactPhone()));
    }

    @Test
    void updateLead_SourceDistributorChangeOutsideScope_Throws() {
        when(memberMapper.selectByUserId(OPERATOR_USER_ID)).thenReturn(member(2L, DISTRIBUTOR_ID,
                DistributionRoleCode.DIST_SALES.getCode(), DistributionDataScope.OWN_DISTRIBUTOR.getCode(), null));
        when(leadMapper.selectByPrimaryKey(1L)).thenReturn(encryptedLead());
        when(distributorMapper.selectByPrimaryKey(99L)).thenReturn(distributorWithId(99L));

        // 全量 PUT：变更后的来源渠道 99 不在授权范围 [1] 内，必须拒绝
        UpdateDistributionLeadRequestDTO request = new UpdateDistributionLeadRequestDTO();
        request.setContactName("张三");
        request.setContactPhone(PHONE);
        request.setSourceDistributorId(99L);
        request.setIntentProductLine("gene");

        BizException ex = assertThrows(BizException.class, () -> leadService.updateLead(1L, request));

        assertEquals(ResultCode.DISTRIBUTION_DATA_ACCESS_DENIED.getCode(), ex.getCode());
        verify(leadMapper, never()).updateByPrimaryKeySelective(any());
    }

    @Test
    void listLeads_LikeWildcardsInKeywordAreEscaped() {
        when(leadMapper.countByConditionWithScope(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(0L);
        when(leadMapper.selectByConditionWithScope(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(Collections.emptyList());

        leadService.listLeads(null, "张%", null, null, null, null, 1, 10);

        // % 必须以字面量匹配，不能变成通配符
        verify(leadMapper).countByConditionWithScope(isNull(), eq("张\\%"), isNull(),
                isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), isNull());
    }

    private void mockDistributorExists() {
        when(distributorMapper.selectByPrimaryKey(DISTRIBUTOR_ID)).thenReturn(distributor());
    }

    private DistributionLeadEntity encryptedLead() {
        DistributionLeadEntity entity = new DistributionLeadEntity();
        entity.setId(1L);
        entity.setLeadNo("LEAD-20260101-001");
        entity.setContactName("张三");
        entity.setContactPhone(piiCipher.encrypt(PHONE));
        entity.setContactPhoneLookup(piiCipher.lookupKey(PHONE));
        entity.setSourceDistributorId(DISTRIBUTOR_ID);
        entity.setIntentProductLine("gene");
        entity.setDeleted(0);
        return entity;
    }

    private DistributionDistributorEntity distributor() {
        return distributorWithId(DISTRIBUTOR_ID);
    }

    private DistributionDistributorEntity distributorWithId(Long id) {
        DistributionDistributorEntity distributor = new DistributionDistributorEntity();
        distributor.setId(id);
        distributor.setName("华东总代理");
        distributor.setDeleted(0);
        return distributor;
    }

    private CreateDistributionLeadRequestDTO createRequest() {
        CreateDistributionLeadRequestDTO request = new CreateDistributionLeadRequestDTO();
        request.setContactName("张三");
        request.setContactPhone(PHONE);
        request.setSourceDistributorId(DISTRIBUTOR_ID);
        request.setIntentProductLine("gene");
        return request;
    }

    private DistributionDistributorMemberEntity member(Long memberId, Long distributorId, String roleCode,
                                                       String dataScope, Integer deleted) {
        DistributionDistributorMemberEntity member = new DistributionDistributorMemberEntity();
        member.setId(memberId);
        member.setUserId(OPERATOR_USER_ID);
        member.setDistributorId(distributorId);
        member.setRoleCode(roleCode);
        member.setDataScope(dataScope);
        member.setDeleted(deleted == null ? 0 : deleted);
        return member;
    }
}
