# Step 02: 数据权限 — 让不同角色看到不同的数据

> 表结构有了，但如果所有用户都能看到所有数据，系统就不安全。本章加入数据权限层。

---

## 本章目标

- 创建 `DistributionDataPermissionService`，实现 4 级数据范围解析
- 创建 `DistributionDataScope` 和 `DistributionRoleCode` 枚举
- 理解权限如何从成员表 → Service 层 → SQL 层的完整链路

## 问题

张三是 A 渠道的销售，他应该只能看到 A 渠道的线索。李四是超级管理员，他应该看到所有渠道的线索。怎么实现？

## 方案选择

| 方案 | 优点 | 缺点 |
|------|------|------|
| 每个接口手动加 WHERE 条件 | 简单直接 | 容易遗漏，代码重复 |
| Java 层过滤（查出来再筛） | 实现简单 | 性能差，数据量大时不可接受 |
| **SQL 层自动注入** | 安全、高效、统一 | 需要设计好 Mapper 模式 |

我们选方案 3：SQL 层自动注入。

## 第一步：定义权限枚举

```java
// (source file)
public enum DistributionDataScope {
    ALL("ALL"),                          // 超级管理员：看所有数据
    OWN_DISTRIBUTOR("OWN_DISTRIBUTOR"),  // 渠道经理：看本渠道数据
    OWN_AND_CHILDREN("OWN_AND_CHILDREN"), // 区域经理：看本渠道+下级渠道
    SELF("SELF");                         // 销售：只看自己的数据

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
    // ... 同样的 CODE_SET + isValid 模式
}
```

**关键设计**：`data_scope` 存在成员表上，而非用户表上。因为同一个用户在不同渠道可能有不同角色。

## 第二步：创建权限解析服务

```java
// (source file)
@Service
public class DistributionDataPermissionService {

    @Autowired private DistributionOperatorService operatorService;
    @Autowired private DistributionDistributorMemberMapper memberMapper;
    @Autowired private DistributionDistributorMapper distributorMapper;

    /**
     * 解析当前用户的数据访问范围
     * 返回一个不可变的权限对象，后续所有查询都基于这个对象
     */
    public DistributionDataAccessScope resolveCurrentAccessScope() {
        // 1. 获取当前操作人
        Long operatorUserId = operatorService.getCurrentOperatorUserId();
        DistributionDistributorMemberEntity member = memberMapper.selectByUserId(operatorUserId);

        // 2. 校验角色和数据范围
        String roleCode = member.getRoleCode();
        String dataScope = member.getDataScope();

        // 3. 解析可访问的渠道 ID 列表
        List<Long> authorizedDistributorIds =
            resolveAuthorizedDistributorIds(member.getDistributorId(), dataScope);

        // 4. 构建不可变对象
        return DistributionDataAccessScope.of(
            operatorUserId, member.getId(), member.getDistributorId(),
            roleCode, dataScope, authorizedDistributorIds
        );
    }
}
```

## 第三步：渠道树遍历

`OWN_AND_CHILDREN` 范围需要递归查询所有子渠道：

```java
private List<Long> resolveAuthorizedDistributorIds(Long distributorId, String dataScope) {
    // ALL: 返回 null 表示不限制
    if ("ALL".equals(dataScope)) return null;

    LinkedHashSet<Long> ids = new LinkedHashSet<>();
    ids.add(distributorId);

    // OWN_DISTRIBUTOR: 只返回自己
    if (!"OWN_AND_CHILDREN".equals(dataScope)) return new ArrayList<>(ids);

    // OWN_AND_CHILDREN: BFS 遍历子渠道树
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

**BFS 而非 DFS**：避免层级很深时的栈溢出。用 `LinkedHashSet` 自动去重。

## 第四步：权限校验方法

```java
public void checkAccessPermission(Long distributorId, Long memberId, Long ownerUserId) {
    DistributionDataAccessScope scope = resolveCurrentAccessScope();

    if (scope.isAllScope()) return;  // 超级管理员直接通过

    if (scope.isSelfScope()) {
        // 只能访问自己的数据
        if (!scope.getMemberId().equals(memberId)
            && !scope.getOperatorUserId().equals(ownerUserId)) {
            throw new BizException("无权访问该数据", ResultCode.DISTRIBUTION_DATA_ACCESS_DENIED.getCode());
        }
        return;
    }

    // OWN_DISTRIBUTOR / OWN_AND_CHILDREN: 检查渠道是否在授权范围内
    if (!scope.getAuthorizedDistributorIds().contains(distributorId)) {
        throw new BizException("无权访问该数据", ResultCode.DISTRIBUTION_DATA_ACCESS_DENIED.getCode());
    }
}
```

## 第五步：在 Service 中使用

**列表查询**：传入权限参数到 SQL

```java
public PageResponseDTO<DistributionLeadDTO> listLeads(...) {
    DistributionDataAccessScope scope = dataPermissionService.resolveCurrentAccessScope();

    // 根据数据范围决定 SQL 参数
    List<Long> authorizedIds = scope.isAllScope() || scope.isSelfScope()
        ? null : scope.getAuthorizedDistributorIds();
    Long authorizedMemberId = scope.isSelfScope() ? scope.getMemberId() : null;
    Long authorizedOwnerUserId = scope.isSelfScope() ? scope.getOperatorUserId() : null;

    // 带权限的查询
    long total = leadMapper.countByConditionWithScope(
        ..., authorizedIds, authorizedMemberId, authorizedOwnerUserId);
    List<...> entities = leadMapper.selectByConditionWithScope(
        ..., authorizedIds, authorizedMemberId, authorizedOwnerUserId, offset, limit);
}
```

**详情查询**：校验单条记录

```java
public DistributionLeadDetailDTO getLead(Long id) {
    DistributionLeadEntity entity = leadMapper.selectByPrimaryKey(id);
    // 校验当前用户是否有权限访问这条数据
    dataPermissionService.checkAccessPermission(
        entity.getSourceDistributorId(), entity.getSourceMemberId(), entity.getOwnerUserId());
    return toDTO(entity);
}
```

## 权限链路全景

```
请求进入
  │
  ▼
AuthHeaderInterceptor
  │ 解析 Token → userId → ThreadLocal
  ▼
Controller.listLeads()
  │
  ▼
Service.listLeads()
  │
  ├── dataPermissionService.resolveCurrentAccessScope()
  │     │
  │     ├── 查 member 表 → roleCode, dataScope
  │     ├── resolveAuthorizedDistributorIds()
  │     │     └── BFS 遍历渠道树（如果是 OWN_AND_CHILDREN）
  │     └── 返回 DistributionDataAccessScope（不可变）
  │
  ├── mapper.countByConditionWithScope(..., authorizedIds, ...)
  │     └── SQL: WHERE ... AND distributor_id IN (1, 5, 23)
  │
  └── mapper.selectByConditionWithScope(..., authorizedIds, ...)
        └── SQL: WHERE ... AND distributor_id IN (1, 5, 23)
```

## 验证

在数据库中准备测试数据：

```sql
-- 创建两个渠道
INSERT INTO distribution_distributor (id, code, name, level_code, status, deleted)
VALUES (1, 'CH001', '总部渠道', 'L1', 'active', 0),
       (2, 'CH002', '北京分部', 'L2', 'active', 0);

-- 创建两个成员：一个看全部，一个只看自己
INSERT INTO distribution_distributor_member (distributor_id, user_id, name, phone, role_code, data_scope, status, deleted)
VALUES (1, 1, '管理员', '13800000001', 'DIST_SUPER_ADMIN', 'ALL', 'active', 0),
       (2, 2, '张三', '13800000002', 'DIST_SALES', 'SELF', 'active', 0);

-- 创建线索
INSERT INTO distribution_lead (id, lead_no, patient_name, patient_phone, source_distributor_id, source_member_id, intent_product_line, owner_user_id, stage, deleted)
VALUES (1, 'L001', '患者A', '13900000001', 1, NULL, 'gene', 1, 'contacted', 0),
       (2, 'L002', '患者B', '13900000002', 2, 2, 'gene', 2, 'contacted', 0);
```

用管理员 token 查询：应该返回 2 条线索。
用张三 token 查询：应该只返回 1 条线索（自己负责的）。

## 下一步

权限有了，但还没有 API 接口。下一步搭建 CRUD 脚手架，让数据可以通过 HTTP 接口操作。

→ [Step 03: CRUD 脚手架](03-crud-scaffolding.md)

---

