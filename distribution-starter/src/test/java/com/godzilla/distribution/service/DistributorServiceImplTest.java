package com.godzilla.distribution.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.godzilla.distribution.common.ResultCode;
import com.godzilla.distribution.common.pii.PiiCipher;
import com.godzilla.distribution.dto.PageResponseDTO;
import com.godzilla.distribution.dto.distribution.request.CreateDistributorRequestDTO;
import com.godzilla.distribution.dto.distribution.request.UpdateDistributorRequestDTO;
import com.godzilla.distribution.dto.distribution.response.DistributionDistributorDTO;
import com.godzilla.distribution.dto.distribution.response.DistributionDistributorDetailDTO;
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
import com.godzilla.distribution.service.distribution.impl.DistributorServiceImpl;
import com.godzilla.distribution.service.distribution.support.ProductLineJsonCodec;
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
 * 渠道服务测试：验证 PII 加密写入、编码唯一性、脱敏列表和明文详情。
 */
@ExtendWith(MockitoExtension.class)
class DistributorServiceImplTest {

    private static final String PHONE = "13800138000";
    private static final Long OPERATOR_USER_ID = 9L;

    @Mock
    private DistributionDistributorMapper distributorMapper;
    @Mock
    private UserProfileEntityMapper userProfileMapper;
    @Mock
    private DistributionDistributorMemberMapper memberMapper;
    @Mock
    private DistributionOperatorService operatorService;
    @Mock
    private DistributionAuditLogService auditLogService;

    private PiiCipher piiCipher;
    private DistributorServiceImpl distributorService;

    @BeforeEach
    void setUp() {
        piiCipher = new PiiCipher("unit-test-key", new MockEnvironment());
        DistributionDataPermissionService permissionService =
                new DistributionDataPermissionService(operatorService, memberMapper, distributorMapper);
        ObjectMapper objectMapper = new ObjectMapper();
        distributorService = new DistributorServiceImpl(
                distributorMapper, userProfileMapper, operatorService, auditLogService,
                permissionService, piiCipher, new ProductLineJsonCodec(objectMapper));
        lenient().when(operatorService.getCurrentOperatorUserId()).thenReturn(OPERATOR_USER_ID);
        lenient().when(memberMapper.selectByUserId(OPERATOR_USER_ID)).thenReturn(member());
    }

    @Test
    void createDistributor_StoresEncryptedPhone() {
        when(distributorMapper.selectByCode("DIST-001")).thenReturn(null);
        doAnswer(invocation -> {
            DistributionDistributorEntity entity = invocation.getArgument(0);
            entity.setId(1L);
            return 1;
        }).when(distributorMapper).insertSelective(any(DistributionDistributorEntity.class));

        Long id = distributorService.createDistributor(createRequest());

        assertEquals(1L, id);
        ArgumentCaptor<DistributionDistributorEntity> captor = ArgumentCaptor.forClass(DistributionDistributorEntity.class);
        verify(distributorMapper).insertSelective(captor.capture());
        DistributionDistributorEntity saved = captor.getValue();
        assertTrue(saved.getContactPhone().startsWith("enc:v1:"));
        assertEquals(PHONE, piiCipher.decrypt(saved.getContactPhone()));
    }

    @Test
    void createDistributor_DuplicateCode_Throws() {
        when(distributorMapper.selectByCode("DIST-001")).thenReturn(distributor());

        assertThrows(BizException.class, () -> distributorService.createDistributor(createRequest()));
        verify(distributorMapper, never()).insertSelective(any());
    }

    @Test
    void createDistributor_OwnScopeCannotCreateUnderForeignParent() {
        when(distributorMapper.selectByCode("DIST-001")).thenReturn(null);
        when(distributorMapper.selectByPrimaryKey(2L)).thenReturn(distributorWithId(2L));
        when(memberMapper.selectByUserId(OPERATOR_USER_ID)).thenReturn(memberWith(99L, DistributionDataScope.OWN_DISTRIBUTOR.getCode()));

        CreateDistributorRequestDTO request = createRequest();
        request.setParentId(2L);

        assertThrows(BizException.class, () -> distributorService.createDistributor(request));
    }

    @Test
    void listDistributors_ReturnsMaskedPhone() {
        when(distributorMapper.countByConditionWithScope(any(), any(), any(), any(), isNull())).thenReturn(1L);
        when(distributorMapper.selectByConditionWithScope(any(), any(), any(), any(), isNull(), anyInt(), anyInt()))
                .thenReturn(Collections.singletonList(encryptedDistributor()));

        PageResponseDTO<DistributionDistributorDTO> page =
                distributorService.listDistributors(null, null, null, null, 1, 10);

        assertEquals("138****8000", page.getList().get(0).getContactPhone());
    }

    @Test
    void getDistributor_ReturnsDecryptedPhone() {
        when(distributorMapper.selectByPrimaryKey(1L)).thenReturn(encryptedDistributor());

        DistributionDistributorDetailDTO detail = distributorService.getDistributor(1L);

        assertEquals(PHONE, detail.getContactPhone());
        assertEquals(Collections.singletonList("gene"), detail.getProductLines());
    }

    @Test
    void updateDistributor_ReEncryptsPhone() {
        when(distributorMapper.selectByPrimaryKey(1L)).thenReturn(encryptedDistributor());

        UpdateDistributorRequestDTO request = new UpdateDistributorRequestDTO();
        request.setName("华东总代理");
        request.setLevelCode("L1");
        request.setContactPhone("13900139000");

        distributorService.updateDistributor(1L, request);

        ArgumentCaptor<DistributionDistributorEntity> captor = ArgumentCaptor.forClass(DistributionDistributorEntity.class);
        verify(distributorMapper).updateByPrimaryKeySelective(captor.capture());
        assertTrue(captor.getValue().getContactPhone().startsWith("enc:v1:"));
        assertEquals("13900139000", piiCipher.decrypt(captor.getValue().getContactPhone()));
    }

    @Test
    void deleteDistributor_MarksDeleted() {
        when(distributorMapper.selectByPrimaryKey(1L)).thenReturn(encryptedDistributor());

        distributorService.deleteDistributor(1L);

        ArgumentCaptor<DistributionDistributorEntity> captor = ArgumentCaptor.forClass(DistributionDistributorEntity.class);
        verify(distributorMapper).updateByPrimaryKeySelective(captor.capture());
        assertEquals(Integer.valueOf(1), captor.getValue().getDeleted());
    }

    @Test
    void deleteDistributor_NonAllOperator_IsDenied() {
        when(distributorMapper.selectByPrimaryKey(1L)).thenReturn(encryptedDistributor());
        when(memberMapper.selectByUserId(OPERATOR_USER_ID))
                .thenReturn(memberWith(1L, DistributionDataScope.OWN_DISTRIBUTOR.getCode()));

        BizException ex = assertThrows(BizException.class, () -> distributorService.deleteDistributor(1L));

        assertEquals(ResultCode.DISTRIBUTOR_DELETE_DENIED.getCode(), ex.getCode());
        verify(distributorMapper, never()).updateByPrimaryKeySelective(any());
    }

    @Test
    void createDistributor_NonAllOperatorWithoutParent_IsDenied() {
        when(distributorMapper.selectByCode("DIST-001")).thenReturn(null);
        when(memberMapper.selectByUserId(OPERATOR_USER_ID))
                .thenReturn(memberWith(1L, DistributionDataScope.OWN_DISTRIBUTOR.getCode()));

        // parentId 为空（创建根渠道）对非全局操作者一律拒绝，保证 fail-closed
        BizException ex = assertThrows(BizException.class, () -> distributorService.createDistributor(createRequest()));

        assertEquals(ResultCode.DISTRIBUTION_DATA_ACCESS_DENIED.getCode(), ex.getCode());
        verify(distributorMapper, never()).insertSelective(any());
    }

    @Test
    void updateDistributor_ParentChangeOutsideScope_IsDenied() {
        when(distributorMapper.selectByPrimaryKey(1L)).thenReturn(encryptedDistributor());
        when(distributorMapper.selectByPrimaryKey(2L)).thenReturn(distributorWithId(2L));
        when(memberMapper.selectByUserId(OPERATOR_USER_ID))
                .thenReturn(memberWith(1L, DistributionDataScope.OWN_DISTRIBUTOR.getCode()));

        UpdateDistributorRequestDTO request = new UpdateDistributorRequestDTO();
        request.setName("华东总代理");
        request.setLevelCode("L1");
        request.setParentId(2L);

        BizException ex = assertThrows(BizException.class, () -> distributorService.updateDistributor(1L, request));

        assertEquals(ResultCode.DISTRIBUTION_DATA_ACCESS_DENIED.getCode(), ex.getCode());
        verify(distributorMapper, never()).updateByPrimaryKeySelective(any());
    }

    @Test
    void listDistributors_LikeWildcardsInNameAreEscaped() {
        when(distributorMapper.countByConditionWithScope(any(), any(), any(), any(), isNull())).thenReturn(0L);
        when(distributorMapper.selectByConditionWithScope(any(), any(), any(), any(), isNull(), anyInt(), anyInt()))
                .thenReturn(Collections.emptyList());

        distributorService.listDistributors("张%", null, null, null, 1, 10);

        // % 必须以字面量匹配，不能变成通配符
        verify(distributorMapper).countByConditionWithScope(eq("张\\%"), isNull(), isNull(), isNull(), isNull());
    }

    private DistributionDistributorEntity encryptedDistributor() {
        DistributionDistributorEntity entity = distributor();
        entity.setContactPhone(piiCipher.encrypt(PHONE));
        entity.setProductLinesJson("[\"gene\"]");
        return entity;
    }

    private DistributionDistributorEntity distributor() {
        return distributorWithId(1L);
    }

    private DistributionDistributorEntity distributorWithId(Long id) {
        DistributionDistributorEntity entity = new DistributionDistributorEntity();
        entity.setId(id);
        entity.setCode("DIST-001");
        entity.setName("华东总代理");
        entity.setLevelCode("L1");
        entity.setStatus("active");
        entity.setContactName("张三");
        entity.setDeleted(0);
        return entity;
    }

    private CreateDistributorRequestDTO createRequest() {
        CreateDistributorRequestDTO request = new CreateDistributorRequestDTO();
        request.setCode("DIST-001");
        request.setName("华东总代理");
        request.setLevelCode("L1");
        request.setContactName("张三");
        request.setContactPhone(PHONE);
        return request;
    }

    private DistributionDistributorMemberEntity member() {
        return memberWith(1L, DistributionDataScope.ALL.getCode());
    }

    private DistributionDistributorMemberEntity memberWith(Long distributorId, String dataScope) {
        DistributionDistributorMemberEntity member = new DistributionDistributorMemberEntity();
        member.setId(1L);
        member.setUserId(OPERATOR_USER_ID);
        member.setDistributorId(distributorId);
        member.setRoleCode(DistributionRoleCode.DIST_SUPER_ADMIN.getCode());
        member.setDataScope(dataScope);
        member.setDeleted(0);
        return member;
    }
}
