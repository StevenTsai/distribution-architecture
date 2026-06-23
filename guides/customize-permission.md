# 定制数据权限模型

> 如何根据你的业务需求调整数据权限的范围、角色和过滤逻辑。

---

## 当前模型概览

本项目实现了 4 级数据权限，详见 [数据权限模型](../architecture/data-permission-model.md)：

```
ALL               → 看所有数据
OWN_DISTRIBUTOR   → 看本渠道数据
OWN_AND_CHILDREN  → 看本渠道 + 下级渠道数据
SELF              → 看自己的数据
```

本文档说明如何调整这个模型以适应不同的业务需求。

## 场景 1：添加新的数据范围

### 需求

例如需要添加 `OWN_DEPARTMENT`（看本部门数据），其中"部门"是渠道下的一个子维度。

### 步骤

**1. 扩展枚举**

```java
// enums/distribution/DistributionDataScope.java
public enum DistributionDataScope {
    ALL("ALL"),
    OWN_DISTRIBUTOR("OWN_DISTRIBUTOR"),
    OWN_AND_CHILDREN("OWN_AND_CHILDREN"),
    OWN_DEPARTMENT("OWN_DEPARTMENT"),      // ← 新增
    SELF("SELF");
    // ...
}
```

**2. 修改权限解析逻辑**

```java
// service/distribution/impl/DistributionDataPermissionService.java

private List<Long> resolveAuthorizedDistributorIds(Long distributorId, String dataScope) {
    if (DistributionDataScope.ALL.getCode().equals(dataScope)) {
        return null;
    }
    if (distributorId == null) {
        return Collections.emptyList();
    }

    LinkedHashSet<Long> authorizedIds = new LinkedHashSet<>();
    authorizedIds.add(distributorId);

    // OWN_DEPARTMENT: 只返回本部门关联的渠道
    if (DistributionDataScope.OWN_DEPARTMENT.getCode().equals(dataScope)) {
        // 根据你的业务逻辑查询部门关联的渠道
        List<Long> departmentDistributorIds = queryDistributorsByDepartment(distributorId);
        authorizedIds.addAll(departmentDistributorIds);
        return new ArrayList<>(authorizedIds);
    }

    // OWN_AND_CHILDREN: 递归遍历子渠道
    if (DistributionDataScope.OWN_AND_CHILDREN.getCode().equals(dataScope)) {
        // ... 现有逻辑 ...
    }

    return new ArrayList<>(authorizedIds);
}
```

**3. 修改 Mapper XML 的 ScopeCondition**

如果新范围需要额外的过滤维度（如 `department_id`），需要修改 ScopeCondition：

```xml
<sql id="ScopeCondition">
    <!-- 现有逻辑 -->
    <if test="authorizedDistributorIds != null and authorizedDistributorIds.size() > 0">
        and distributor_id in
        <foreach collection="authorizedDistributorIds" item="id" open="(" separator="," close=")">
            #{id}
        </foreach>
    </if>
    <!-- 新增：部门维度 -->
    <if test="authorizedDepartmentId != null">
        and department_id = #{authorizedDepartmentId}
    </if>
    <!-- ... -->
</sql>
```

**4. 修改 Service 层传参**

```java
// 在 listXxx 方法中
Long authorizedDepartmentId = scope.isDepartmentScope() ? scope.getDepartmentId() : null;

long total = mapper.countByConditionWithScope(
    ..., authorizedDistributorIds, authorizedMemberId, authorizedOwnerUserId, authorizedDepartmentId);
```

## 场景 2：添加新的角色

### 需求

例如需要添加 `DIST_REGIONAL_MANAGER`（区域经理）角色。

### 步骤

**1. 扩展角色枚举**

```java
// enums/distribution/DistributionRoleCode.java
public enum DistributionRoleCode {
    DIST_SUPER_ADMIN("DIST_SUPER_ADMIN"),
    DIST_OPERATOR("DIST_OPERATOR"),
    DIST_SALES("DIST_SALES"),
    DIST_FINANCE("DIST_FINANCE"),
    DIST_COMPLIANCE("DIST_COMPLIANCE"),
    DIST_ANALYST("DIST_ANALYST"),
    DIST_REGIONAL_MANAGER("DIST_REGIONAL_MANAGER");  // ← 新增
    // ...
}
```

**2. 确定新角色的数据范围**

在 `distribution_distributor_member` 表中为新成员设置对应的 `data_scope`：

```sql
-- 区域经理看本渠道及下级渠道数据
UPDATE distribution_distributor_member
SET role_code = 'DIST_REGIONAL_MANAGER', data_scope = 'OWN_AND_CHILDREN'
WHERE id = 123;
```

**3. 如果需要角色特定的业务逻辑**

某些操作可能只允许特定角色执行。在 Service 中添加角色校验：

```java
private void validateRoleForAction(String requiredRole, DistributionDataAccessScope scope) {
    if (!requiredRole.equals(scope.getRoleCode())) {
        throw new BizException("当前角色无权执行此操作", ResultCode.DISTRIBUTION_DATA_ACCESS_DENIED.getCode());
    }
}

// 使用
validateRoleForAction("DIST_FINANCE", scope);
```

## 场景 3：自定义权限校验逻辑

### 需求

例如，只有 `DIST_COMPLIANCE` 角色才能查看合规记录，其他角色即使有数据范围权限也不能看。

### 实现

```java
// 在 DistributionComplianceServiceImpl 中
public PageResponseDTO<DistributionComplianceRecordDTO> listRecords(...) {
    DistributionDataAccessScope scope = dataPermissionService.resolveCurrentAccessScope();

    // 角色级权限校验
    if (!DistributionRoleCode.DIST_COMPLIANCE.getCode().equals(scope.getRoleCode())
        && !DistributionRoleCode.DIST_SUPER_ADMIN.getCode().equals(scope.getRoleCode())) {
        throw new BizException("只有合规角色可以查看合规记录", ResultCode.DISTRIBUTION_DATA_ACCESS_DENIED.getCode());
    }

    // ... 继续数据范围过滤 ...
}
```

## 场景 4：为特定接口禁用权限过滤

### 需求

某些接口（如管理员的全局搜索）不需要数据权限过滤。

### 实现

直接使用不带 `WithScope` 的 Mapper 方法：

```java
// 不带权限过滤的查询
long total = mapper.countByCondition(...);
List<Entity> entities = mapper.selectByCondition(..., offset, limit);
```

这就是对偶设计的好处——你可以**显式选择**是否应用权限过滤。

## 场景 5：缓存权限数据

### 需求

`OWN_AND_CHILDREN` 范围需要递归查询子渠道树，如果渠道层级深、调用频繁，可能成为性能瓶颈。

### 实现

用 Redis 缓存 `authorizedDistributorIds`：

```java
@Service
public class DistributionDataPermissionService {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    private static final String CACHE_PREFIX = "dist:scope:";
    private static final long CACHE_TTL_MINUTES = 5;

    public DistributionDataAccessScope resolveCurrentAccessScope() {
        Long operatorUserId = distributionOperatorService.getCurrentOperatorUserId();
        String cacheKey = CACHE_PREFIX + operatorUserId;

        // 尝试从缓存获取
        DistributionDataAccessScope cached = (DistributionDataAccessScope) redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            return cached;
        }

        // 缓存未命中，解析权限
        DistributionDataAccessScope scope = doResolveAccessScope(operatorUserId);

        // 写入缓存
        redisTemplate.opsForValue().set(cacheKey, scope, CACHE_TTL_MINUTES, TimeUnit.MINUTES);

        return scope;
    }

    // 权限变更时清除缓存
    public void invalidateCache(Long operatorUserId) {
        redisTemplate.delete(CACHE_PREFIX + operatorUserId);
    }
}
```

**注意**：缓存后需要在以下场景清除缓存：
- 成员的 `data_scope` 或 `role_code` 变更
- 成员被删除或禁用
- 渠道的 `parent_id` 变更（影响子渠道树）

## 场景 6：跨系统权限继承

### 需求

分销系统需要从外部系统（如 CRM）获取用户的角色和权限。

### 实现

修改 `DistributionDataPermissionService` 的权限来源，从外部系统查询：

```java
public DistributionDataAccessScope resolveCurrentAccessScope() {
    Long operatorUserId = distributionOperatorService.getCurrentOperatorUserId();

    // 优先从本地 member 表查询
    DistributionDistributorMemberEntity currentMember =
        distributionDistributorMemberMapper.selectByUserId(operatorUserId);

    if (currentMember == null) {
        // 本地无记录，从外部系统查询
        ExternalPermission externalPerm = externalPermissionClient.queryPermission(operatorUserId);
        if (externalPerm == null) {
            throw new BizException("当前操作人未配置权限", ...);
        }
        // 将外部权限映射为本地数据范围
        return mapExternalToLocal(externalPerm);
    }

    // ... 现有逻辑 ...
}
```

## 常见问题

### Q: 修改数据范围后不生效？

检查是否使用了 Redis 缓存。如果是，需要清除对应用户的缓存：

```java
dataPermissionService.invalidateCache(operatorUserId);
```

### Q: ScopeCondition 的 OR 条件导致性能问题？

`SELF` 范围生成的 `AND (member_id = ? OR owner_user_id = ?)` 可能导致索引失效。优化方案：

1. 确保两个字段都有独立索引
2. 考虑用 `UNION` 替代 `OR`：
   ```sql
   (SELECT ... WHERE member_id = ? LIMIT 10)
   UNION
   (SELECT ... WHERE owner_user_id = ? LIMIT 10)
   ```

### Q: 如何实现"只看自己创建的数据"？

`SELF` 范围已经支持这个语义——它通过 `owner_user_id` 过滤。如果数据没有 `owner_user_id` 字段，需要：

1. 给表加 `owner_user_id` 或 `created_by` 字段
2. 在 ScopeCondition 中增加对应的过滤条件

### Q: 如何测试权限是否正确？

建议编写单元测试，覆盖每种数据范围的场景：

```java
@Test
public void testSelfScope_onlySeesOwnData() {
    // 模拟 SELF 范围的用户
    when(scope.isSelfScope()).thenReturn(true);
    when(scope.getMemberId()).thenReturn(1L);

    // 执行查询
    PageResponseDTO<...> result = service.listXxx(...);

    // 验证只返回自己的数据
    for (DTO item : result.getList()) {
        assertEquals(1L, item.getMemberId());
    }
}
```

---

- *权限服务：`service/distribution/impl/DistributionDataPermissionService.java`*
- *数据范围枚举：`enums/distribution/DistributionDataScope.java`*
- *角色枚举：`enums/distribution/DistributionRoleCode.java`*
- *ScopeCondition 示例：各 Mapper XML 文件*
