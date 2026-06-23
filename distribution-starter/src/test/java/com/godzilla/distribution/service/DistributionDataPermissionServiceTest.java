package com.godzilla.distribution.service;

import com.godzilla.distribution.entity.distribution.DistributionDistributorEntity;
import com.godzilla.distribution.entity.distribution.DistributionDistributorMemberEntity;
import com.godzilla.distribution.enums.distribution.DistributionDataScope;
import com.godzilla.distribution.enums.distribution.DistributionRoleCode;
import com.godzilla.distribution.exception.BizException;
import com.godzilla.distribution.mapper.distribution.DistributionDistributorMapper;
import com.godzilla.distribution.mapper.distribution.DistributionDistributorMemberMapper;
import com.godzilla.distribution.service.distribution.DistributionOperatorService;
import com.godzilla.distribution.service.distribution.impl.DistributionDataPermissionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 数据权限服务测试
 * 验证 4 级数据权限模型的核心逻辑
 */
@ExtendWith(MockitoExtension.class)
public class DistributionDataPermissionServiceTest {

    @InjectMocks
    private DistributionDataPermissionService dataPermissionService;

    @Mock
    private DistributionOperatorService distributionOperatorService;

    @Mock
    private DistributionDistributorMemberMapper distributionDistributorMemberMapper;

    @Mock
    private DistributionDistributorMapper distributionDistributorMapper;

    private DistributionDistributorMemberEntity memberEntity;
    private DistributionDistributorEntity distributorEntity;

    @BeforeEach
    void setUp() {
        memberEntity = new DistributionDistributorMemberEntity();
        memberEntity.setId(1L);
        memberEntity.setDistributorId(1L);
        memberEntity.setUserId(2L);
        memberEntity.setRoleCode(DistributionRoleCode.DIST_SALES.getCode());
        memberEntity.setDataScope(DistributionDataScope.SELF.getCode());
        memberEntity.setDeleted(0);

        distributorEntity = new DistributionDistributorEntity();
        distributorEntity.setId(1L);
        distributorEntity.setCode("DIST-001");
        distributorEntity.setName("华东区域总代理");
        distributorEntity.setParentId(null);
    }

    /**
     * 测试 ALL 权限：可以看到所有数据
     */
    @Test
    void testAllScope_AuthorizedDistributorIdsIsNull() {
        memberEntity.setDataScope(DistributionDataScope.ALL.getCode());
        when(distributionOperatorService.getCurrentOperatorUserId()).thenReturn(2L);
        when(distributionDistributorMemberMapper.selectByUserId(2L)).thenReturn(memberEntity);

        DistributionDataPermissionService.DistributionDataAccessScope scope =
                dataPermissionService.resolveCurrentAccessScope();

        assertTrue(scope.isAllScope());
        assertNull(scope.getAuthorizedDistributorIds());
    }

    /**
     * 测试 OWN_DISTRIBUTOR 权限：只能看到本渠道数据
     */
    @Test
    void testOwnDistributorScope_ReturnsOwnDistributorId() {
        memberEntity.setDataScope(DistributionDataScope.OWN_DISTRIBUTOR.getCode());
        when(distributionOperatorService.getCurrentOperatorUserId()).thenReturn(2L);
        when(distributionDistributorMemberMapper.selectByUserId(2L)).thenReturn(memberEntity);

        DistributionDataPermissionService.DistributionDataAccessScope scope =
                dataPermissionService.resolveCurrentAccessScope();

        assertFalse(scope.isAllScope());
        assertFalse(scope.isSelfScope());
        assertEquals(Collections.singletonList(1L), scope.getAuthorizedDistributorIds());
    }

    /**
     * 测试 SELF 权限：只能看到自己的数据
     */
    @Test
    void testSelfScope_ReturnsOwnDistributorId() {
        memberEntity.setDataScope(DistributionDataScope.SELF.getCode());
        when(distributionOperatorService.getCurrentOperatorUserId()).thenReturn(2L);
        when(distributionDistributorMemberMapper.selectByUserId(2L)).thenReturn(memberEntity);

        DistributionDataPermissionService.DistributionDataAccessScope scope =
                dataPermissionService.resolveCurrentAccessScope();

        assertTrue(scope.isSelfScope());
        assertEquals(Collections.singletonList(1L), scope.getAuthorizedDistributorIds());
    }

    /**
     * 测试 OWN_AND_CHILDREN 权限：可以看到本渠道及下级渠道数据
     */
    @Test
    void testOwnAndChildrenScope_ReturnsTreeIds() {
        memberEntity.setDataScope(DistributionDataScope.OWN_AND_CHILDREN.getCode());
        when(distributionOperatorService.getCurrentOperatorUserId()).thenReturn(2L);
        when(distributionDistributorMemberMapper.selectByUserId(2L)).thenReturn(memberEntity);

        DistributionDistributorEntity child1 = new DistributionDistributorEntity();
        child1.setId(2L);
        child1.setParentId(1L);
        DistributionDistributorEntity child2 = new DistributionDistributorEntity();
        child2.setId(3L);
        child2.setParentId(1L);

        when(distributionDistributorMapper.selectByParentIds(Arrays.asList(1L)))
                .thenReturn(Arrays.asList(child1, child2));
        when(distributionDistributorMapper.selectByParentIds(Arrays.asList(2L, 3L)))
                .thenReturn(Collections.emptyList());

        DistributionDataPermissionService.DistributionDataAccessScope scope =
                dataPermissionService.resolveCurrentAccessScope();

        assertFalse(scope.isAllScope());
        List<Long> authorizedIds = scope.getAuthorizedDistributorIds();
        assertEquals(3, authorizedIds.size());
        assertTrue(authorizedIds.contains(1L));
        assertTrue(authorizedIds.contains(2L));
        assertTrue(authorizedIds.contains(3L));
    }

    /**
     * 测试单条数据权限校验：ALL 权限始终通过
     */
    @Test
    void testCheckAccessPermission_AllScope_AlwaysPasses() {
        memberEntity.setDataScope(DistributionDataScope.ALL.getCode());
        when(distributionOperatorService.getCurrentOperatorUserId()).thenReturn(2L);
        when(distributionDistributorMemberMapper.selectByUserId(2L)).thenReturn(memberEntity);

        // 不抛异常即为通过
        dataPermissionService.checkAccessPermission(999L, 999L, 999L);
    }

    /**
     * 测试单条数据权限校验：SELF 权限只允许自己的数据
     */
    @Test
    void testCheckAccessPermission_SelfScope_OnlyOwnData() {
        memberEntity.setDataScope(DistributionDataScope.SELF.getCode());
        when(distributionOperatorService.getCurrentOperatorUserId()).thenReturn(2L);
        when(distributionDistributorMemberMapper.selectByUserId(2L)).thenReturn(memberEntity);

        // 自己的数据通过
        dataPermissionService.checkAccessPermission(1L, 1L, 2L);
    }

    /**
     * 测试单条数据权限校验：SELF 权限拒绝他人数据
     */
    @Test
    void testCheckAccessPermission_SelfScope_RejectsOtherData() {
        memberEntity.setDataScope(DistributionDataScope.SELF.getCode());
        when(distributionOperatorService.getCurrentOperatorUserId()).thenReturn(2L);
        when(distributionDistributorMemberMapper.selectByUserId(2L)).thenReturn(memberEntity);

        // 他人的数据被拒绝
        assertThrows(BizException.class, () ->
                dataPermissionService.checkAccessPermission(999L, 999L, 999L));
    }

    /**
     * 测试未绑定成员时抛异常
     */
    @Test
    void testResolveAccessScope_NoMember_ThrowsException() {
        when(distributionOperatorService.getCurrentOperatorUserId()).thenReturn(999L);
        when(distributionDistributorMemberMapper.selectByUserId(999L)).thenReturn(null);

        assertThrows(BizException.class, () ->
                dataPermissionService.resolveCurrentAccessScope());
    }
}
