# Step 02: Data Permissions — Let Different Roles See Different Data

[中文](../../tutorial/02-data-permission.md) | English

> The table structures are in place, but if all users can see all data, the system is not secure. This chapter adds a data permission layer.

---

## Chapter Goals

- Create `DistributionDataPermissionService` to implement 4-level data scope resolution
- Create the `DistributionDataScope` and `DistributionRoleCode` enums
- Understand the complete path of permissions from the member table → Service layer → SQL layer

## The Problem

Zhang San is a salesperson for distributor A and should only see leads for distributor A. Li Si is a super administrator and should see leads for all distributors. How do we implement this?

## Solution Options

| Solution | Advantages | Disadvantages |
|----------|------------|---------------|
| Manually add a WHERE condition to every endpoint | Simple and direct | Easy to miss, duplicated code |
| Filter at the Java layer (query first, then filter) | Simple to implement | Poor performance; unacceptable with large data volumes |
| **Automatic injection at the SQL layer** | Secure, efficient, consistent | Requires a well-designed Mapper pattern |

We choose option 3: automatic injection at the SQL layer.

## Step 1: Define the Permission Enums

```java
// (source file)
public enum DistributionDataScope {
    ALL("ALL"),                          // Super administrator: see all data
    OWN_DISTRIBUTOR("OWN_DISTRIBUTOR"),  // Distributor manager: see own distributor's data
    OWN_AND_CHILDREN("OWN_AND_CHILDREN"), // Regional manager: see own distributor + child distributors
    SELF("SELF");                         // Sales: see only own data

    private static final Set<String> CODE_SET = new HashSet<>();
    static {
        for (DistributionDataScope scope : values()) {
            CODE_SET.add(scope.code);
        }
    }

    private final String code;
    DistributionDataScope(String code) { this.code = code; }
    public String getCode() { return code; }
    public static boolean isValid(String code) { return CODE_SET.contains(code); }
}
```

```java
// (source file)
public enum DistributionRoleCode {
    DIST_SUPER_ADMIN("DIST_SUPER_ADMIN"),
    DIST_OPERATOR("DIST_OPERATOR"),
    DIST_SALES("DIST_SALES"),
    DIST_FINANCE("DIST_FINANCE"),
    DIST_COMPLIANCE("DIST_COMPLIANCE"),
    DIST_ANALYST("DIST_ANALYST");
    // ... same CODE_SET + isValid pattern
}
```

**Key design**: `data_scope` is stored on the member table, not the user table, because the same user may have different roles in different distributors.

## Step 2: Create the Permission Resolution Service

```java
// (source file)
@Service
public class DistributionDataPermissionService {

    @Autowired private DistributionOperatorService operatorService;
    @Autowired private DistributionDistributorMemberMapper memberMapper;
    @Autowired private DistributionDistributorMapper distributorMapper;

    /**
     * Resolve the current user's data access scope
     * Return an immutable permission object on which all subsequent queries are based
     */
    public DistributionDataAccessScope resolveCurrentAccessScope() {
        // 1. Get the current operator
        Long operatorUserId = operatorService.getCurrentOperatorUserId();
        DistributionDistributorMemberEntity member = memberMapper.selectByUserId(operatorUserId);

        // 2. Validate the role and data scope
        String roleCode = member.getRoleCode();
        String dataScope = member.getDataScope();

        // 3. Resolve the list of accessible distributor IDs
        List<Long> authorizedDistributorIds =
            resolveAuthorizedDistributorIds(member.getDistributorId(), dataScope);

        // 4. Build the immutable object
        return DistributionDataAccessScope.of(
            operatorUserId, member.getId(), member.getDistributorId(),
            roleCode, dataScope, authorizedDistributorIds
        );
    }
}
```

## Step 3: Traverse the Distributor Tree

The `OWN_AND_CHILDREN` scope requires recursively querying all child distributors:

```java
private List<Long> resolveAuthorizedDistributorIds(Long distributorId, String dataScope) {
    // ALL: return null to indicate no restriction
    if ("ALL".equals(dataScope)) return null;

    LinkedHashSet<Long> ids = new LinkedHashSet<>();
    ids.add(distributorId);

    // OWN_DISTRIBUTOR: return only the distributor itself
    if (!"OWN_AND_CHILDREN".equals(dataScope)) return new ArrayList<>(ids);

    // OWN_AND_CHILDREN: traverse the child distributor tree with BFS
    List<Long> parentIds = Collections.singletonList(distributorId);
    while (!parentIds.isEmpty()) {
        List<DistributionDistributorEntity> children =
            distributorMapper.selectByParentIds(parentIds);
        if (children == null || children.isEmpty()) break;

        List<Long> nextParentIds = new ArrayList<>();
        for (DistributionDistributorEntity child : children) {
            if (ids.add(child.getId())) {
                nextParentIds.add(child.getId());
            }
        }
        parentIds = nextParentIds;
    }
    return new ArrayList<>(ids);
}
```

**BFS rather than DFS**: avoids stack overflow when the hierarchy is very deep. Use `LinkedHashSet` to deduplicate automatically.

## Step 4: Permission Validation Method

```java
public void checkAccessPermission(Long distributorId, Long memberId, Long ownerUserId) {
    DistributionDataAccessScope scope = resolveCurrentAccessScope();

    if (scope.isAllScope()) return;  // Super administrator passes directly

    if (scope.isSelfScope()) {
        // Can only access own data
        if (!scope.getMemberId().equals(memberId)
            && !scope.getOperatorUserId().equals(ownerUserId)) {
            throw new BizException("You do not have permission to access this data", ResultCode.DISTRIBUTION_DATA_ACCESS_DENIED.getCode());
        }
        return;
    }

    // OWN_DISTRIBUTOR / OWN_AND_CHILDREN: check whether the distributor is within the authorized scope
    if (!scope.getAuthorizedDistributorIds().contains(distributorId)) {
        throw new BizException("You do not have permission to access this data", ResultCode.DISTRIBUTION_DATA_ACCESS_DENIED.getCode());
    }
}
```

## Step 5: Use It in the Service

**List query**: pass the permission parameters into SQL

```java
public PageResponseDTO<DistributionLeadDTO> listLeads(...) {
    DistributionDataAccessScope scope = dataPermissionService.resolveCurrentAccessScope();

    // Determine SQL parameters based on the data scope
    List<Long> authorizedIds = scope.isAllScope() || scope.isSelfScope()
        ? null : scope.getAuthorizedDistributorIds();
    Long authorizedMemberId = scope.isSelfScope() ? scope.getMemberId() : null;
    Long authorizedOwnerUserId = scope.isSelfScope() ? scope.getOperatorUserId() : null;

    // Query with permissions
    long total = leadMapper.countByConditionWithScope(
        ..., authorizedIds, authorizedMemberId, authorizedOwnerUserId);
    List<...> entities = leadMapper.selectByConditionWithScope(
        ..., authorizedIds, authorizedMemberId, authorizedOwnerUserId, offset, limit);
}
```

**Detail query**: validate a single record

```java
public DistributionLeadDetailDTO getLead(Long id) {
    DistributionLeadEntity entity = leadMapper.selectByPrimaryKey(id);
    // Check whether the current user has permission to access this record
    dataPermissionService.checkAccessPermission(
        entity.getSourceDistributorId(), entity.getSourceMemberId(), entity.getOwnerUserId());
    return toDTO(entity);
}
```

## Permission Path Overview

```
Request enters
  │
  ▼
AuthHeaderInterceptor
  │ Parse Token → userId → ThreadLocal
  ▼
Controller.listLeads()
  │
  ▼
Service.listLeads()
  │
  ├── dataPermissionService.resolveCurrentAccessScope()
  │     │
  │     ├── query member table → roleCode, dataScope
  │     ├── resolveAuthorizedDistributorIds()
  │     │     └── BFS traverse distributor tree (if OWN_AND_CHILDREN)
  │     └── return DistributionDataAccessScope (immutable)
  │
  ├── mapper.countByConditionWithScope(..., authorizedIds, ...)
  │     └── SQL: WHERE ... AND distributor_id IN (1, 5, 23)
  │
  └── mapper.selectByConditionWithScope(..., authorizedIds, ...)
        └── SQL: WHERE ... AND distributor_id IN (1, 5, 23)
```

## Verification

Prepare test data in the database:

```sql
-- Create two distributors
INSERT INTO distribution_distributor (id, code, name, level_code, status, deleted)
VALUES (1, 'CH001', 'Headquarters Distributor', 'L1', 'active', 0),
       (2, 'CH002', 'Beijing Branch', 'L2', 'active', 0);

-- Create two members: one sees everything, one sees only their own data
INSERT INTO distribution_distributor_member (distributor_id, user_id, name, phone, role_code, data_scope, status, deleted)
VALUES (1, 1, 'Administrator', '13800000001', 'DIST_SUPER_ADMIN', 'ALL', 'active', 0),
       (2, 2, 'Zhang San', '13800000002', 'DIST_SALES', 'SELF', 'active', 0);

-- Create leads
INSERT INTO distribution_lead (id, lead_no, contact_name, contact_phone, source_distributor_id, source_member_id, intent_product_line, owner_user_id, stage, deleted)
VALUES (1, 'L001', 'Contact A', '13900000001', 1, NULL, 'gene', 1, 'contacted', 0),
       (2, 'L002', 'Contact B', '13900000002', 2, 2, 'gene', 2, 'contacted', 0);
```

Query with the administrator token: 2 leads should be returned.
Query with Zhang San's token: only 1 lead should be returned (the one assigned to him).

## Next Step

Permissions are in place, but there are no API endpoints yet. Next we build the CRUD scaffolding so data can be operated on through HTTP endpoints.

→ [Step 03: CRUD Scaffolding](03-crud-scaffolding.md)

---
