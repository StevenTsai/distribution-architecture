# Customizing the Data Permission Model

[中文](../../guides/customize-permission.md) | English

> How to adjust the scope, roles, and filtering logic of data permissions to fit your business requirements.

---

## Current Model Overview

This project implements 4 levels of data permissions, detailed in [Data Permission Model](../architecture/data-permission-model.md):

```
ALL               → see all data
OWN_DISTRIBUTOR   → see own distributor's data
OWN_AND_CHILDREN  → see own distributor and child distributor data
SELF              → see own data
```

This document explains how to adjust this model for different business requirements.

## Scenario 1: Add a New Data Scope

### Requirement

For example, you need to add `OWN_DEPARTMENT` (see own department's data), where "department" is a sub-dimension under a distributor.

### Steps

**1. Extend the enum**

```java
// enums/distribution/DistributionDataScope.java
public enum DistributionDataScope {
    ALL("ALL"),
    OWN_DISTRIBUTOR("OWN_DISTRIBUTOR"),
    OWN_AND_CHILDREN("OWN_AND_CHILDREN"),
    OWN_DEPARTMENT("OWN_DEPARTMENT"),      // ← new
    SELF("SELF");
    // ...
}
```

**2. Modify the permission resolution logic**

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

    // OWN_DEPARTMENT: return only distributors associated with this department
    if (DistributionDataScope.OWN_DEPARTMENT.getCode().equals(dataScope)) {
        // Query the distributors associated with the department according to your business logic
        List<Long> departmentDistributorIds = queryDistributorsByDepartment(distributorId);
        authorizedIds.addAll(departmentDistributorIds);
        return new ArrayList<>(authorizedIds);
    }

    // OWN_AND_CHILDREN: recursively traverse child distributors
    if (DistributionDataScope.OWN_AND_CHILDREN.getCode().equals(dataScope)) {
        // ... existing logic ...
    }

    return new ArrayList<>(authorizedIds);
}
```

**3. Modify the Mapper XML's ScopeCondition**

If the new scope needs an additional filtering dimension (such as `department_id`), modify ScopeCondition:

```xml
<sql id="ScopeCondition">
    <!-- Existing logic -->
    <if test="authorizedDistributorIds != null and authorizedDistributorIds.size() > 0">
        and distributor_id in
        <foreach collection="authorizedDistributorIds" item="id" open="(" separator="," close=")">
            #{id}
        </foreach>
    </if>
    <!-- New: department dimension -->
    <if test="authorizedDepartmentId != null">
        and department_id = #{authorizedDepartmentId}
    </if>
    <!-- ... -->
</sql>
```

**4. Modify Service layer parameter passing**

```java
// In the listXxx method
Long authorizedDepartmentId = scope.isDepartmentScope() ? scope.getDepartmentId() : null;

long total = mapper.countByConditionWithScope(
    ..., authorizedDistributorIds, authorizedMemberId, authorizedOwnerUserId, authorizedDepartmentId);
```

## Scenario 2: Add a New Role

### Requirement

For example, you need to add the `DIST_REGIONAL_MANAGER` (regional manager) role.

### Steps

**1. Extend the role enum**

```java
// enums/distribution/DistributionRoleCode.java
public enum DistributionRoleCode {
    DIST_SUPER_ADMIN("DIST_SUPER_ADMIN"),
    DIST_OPERATOR("DIST_OPERATOR"),
    DIST_SALES("DIST_SALES"),
    DIST_FINANCE("DIST_FINANCE"),
    DIST_COMPLIANCE("DIST_COMPLIANCE"),
    DIST_ANALYST("DIST_ANALYST"),
    DIST_REGIONAL_MANAGER("DIST_REGIONAL_MANAGER");  // ← new
    // ...
}
```

**2. Determine the new role's data scope**

Set the corresponding `data_scope` for the new member in the `distribution_distributor_member` table:

```sql
-- Regional managers see their own distributor and child distributor data
UPDATE distribution_distributor_member
SET role_code = 'DIST_REGIONAL_MANAGER', data_scope = 'OWN_AND_CHILDREN'
WHERE id = 123;
```

**3. Add role-specific business logic if needed**

Some operations may only be allowed for specific roles. Add a role check in the Service:

```java
private void validateRoleForAction(String requiredRole, DistributionDataAccessScope scope) {
    if (!requiredRole.equals(scope.getRoleCode())) {
        throw new BizException("The current role is not authorized to perform this operation", ResultCode.DISTRIBUTION_DATA_ACCESS_DENIED.getCode());
    }
}

// Usage
validateRoleForAction("DIST_FINANCE", scope);
```

## Scenario 3: Custom Permission Check Logic

### Requirement

For example, only the `DIST_COMPLIANCE` role can view compliance records; other roles cannot see them even if they have the data scope permission.

### Implementation

```java
// In DistributionComplianceServiceImpl
public PageResponseDTO<DistributionComplianceRecordDTO> listRecords(...) {
    DistributionDataAccessScope scope = dataPermissionService.resolveCurrentAccessScope();

    // Role-level permission check
    if (!DistributionRoleCode.DIST_COMPLIANCE.getCode().equals(scope.getRoleCode())
        && !DistributionRoleCode.DIST_SUPER_ADMIN.getCode().equals(scope.getRoleCode())) {
        throw new BizException("Only the compliance role can view compliance records", ResultCode.DISTRIBUTION_DATA_ACCESS_DENIED.getCode());
    }

    // ... continue with data scope filtering ...
}
```

## Scenario 4: Disable Permission Filtering for Specific Endpoints

### Requirement

Some endpoints (such as an admin's global search) do not need data permission filtering.

### Implementation

Use the Mapper methods without the `WithScope` suffix directly:

```java
// Query without permission filtering
long total = mapper.countByCondition(...);
List<Entity> entities = mapper.selectByCondition(..., offset, limit);
```

This is the benefit of the dual design—you can **explicitly choose** whether to apply permission filtering.

## Scenario 5: Cache Permission Data

### Requirement

The `OWN_AND_CHILDREN` scope needs to recursively query the child distributor tree. If the distributor hierarchy is deep and called frequently, this can become a performance bottleneck.

### Implementation

Cache `authorizedDistributorIds` in Redis:

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

        // Try to get from cache
        DistributionDataAccessScope cached = (DistributionDataAccessScope) redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            return cached;
        }

        // Cache miss, resolve permissions
        DistributionDataAccessScope scope = doResolveAccessScope(operatorUserId);

        // Write to cache
        redisTemplate.opsForValue().set(cacheKey, scope, CACHE_TTL_MINUTES, TimeUnit.MINUTES);

        return scope;
    }

    // Clear the cache when permissions change
    public void invalidateCache(Long operatorUserId) {
        redisTemplate.delete(CACHE_PREFIX + operatorUserId);
    }
}
```

**Note**: after caching, clear the cache in the following scenarios:

- A member's `data_scope` or `role_code` changes
- A member is deleted or disabled
- A distributor's `parent_id` changes (affects the child distributor tree)

## Scenario 6: Cross-System Permission Inheritance

### Requirement

The distribution system needs to obtain user roles and permissions from an external system (such as a CRM).

### Implementation

Modify the permission source of `DistributionDataPermissionService` to query the external system:

```java
public DistributionDataAccessScope resolveCurrentAccessScope() {
    Long operatorUserId = distributionOperatorService.getCurrentOperatorUserId();

    // Query the local member table first
    DistributionDistributorMemberEntity currentMember =
        distributionDistributorMemberMapper.selectByUserId(operatorUserId);

    if (currentMember == null) {
        // No local record, query the external system
        ExternalPermission externalPerm = externalPermissionClient.queryPermission(operatorUserId);
        if (externalPerm == null) {
            throw new BizException("No permissions configured for the current operator", ...);
        }
        // Map external permissions to the local data scope
        return mapExternalToLocal(externalPerm);
    }

    // ... existing logic ...
}
```

## FAQ

### Q: Changes to the data scope don't take effect?

Check whether the Redis cache is in use. If so, clear the cache for the corresponding user:

```java
dataPermissionService.invalidateCache(operatorUserId);
```

### Q: The OR condition in ScopeCondition causes performance problems?

The `AND (member_id = ? OR owner_user_id = ?)` generated by the `SELF` scope can cause indexes to be skipped. Optimization options:

1. Ensure both fields have independent indexes
2. Consider using `UNION` instead of `OR`:
   ```sql
   (SELECT ... WHERE member_id = ? LIMIT 10)
   UNION
   (SELECT ... WHERE owner_user_id = ? LIMIT 10)
   ```

### Q: How do I implement "see only data I created"?

The `SELF` scope already supports this semantics—it filters by `owner_user_id`. If the data has no `owner_user_id` field, you need to:

1. Add an `owner_user_id` or `created_by` field to the table
2. Add the corresponding filtering condition in ScopeCondition

### Q: How do I test whether permissions are correct?

It is recommended to write unit tests covering each data scope scenario:

```java
@Test
public void testSelfScope_onlySeesOwnData() {
    // Simulate a user in the SELF scope
    when(scope.isSelfScope()).thenReturn(true);
    when(scope.getMemberId()).thenReturn(1L);

    // Execute the query
    PageResponseDTO<...> result = service.listXxx(...);

    // Verify that only own data is returned
    for (DTO item : result.getList()) {
        assertEquals(1L, item.getMemberId());
    }
}
```

---

- *Permission service: `service/distribution/impl/DistributionDataPermissionService.java`*
- *Data scope enum: `enums/distribution/DistributionDataScope.java`*
- *Role enum: `enums/distribution/DistributionRoleCode.java`*
- *ScopeCondition examples: each Mapper XML file*
