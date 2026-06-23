# ADR: 为什么选择逻辑删除而非物理删除

中文 | [English](../en/decisions/why-logical-delete.md)

> 状态：已采纳
> 日期：项目初始设计阶段
> 决策者：架构团队

---

## 背景

数据库记录的删除有两种方式：

1. **物理删除**：`DELETE FROM table WHERE id = ?`，记录从数据库中消失
2. **逻辑删除**：`UPDATE table SET deleted = 1 WHERE id = ?`，记录保留但标记为已删除

本项目选择了逻辑删除。

## 决策

所有业务表使用 `deleted` 字段（`TINYINT NOT NULL DEFAULT 0`）实现逻辑删除：
- `deleted = 0`：正常记录
- `deleted = 1`：已删除记录

所有查询自动附加 `WHERE deleted = 0` 条件（通过 MyBatis 的 `ConditionWhere` 片段统一注入）。

### 表结构示例

```sql
CREATE TABLE distribution_distributor (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'pending',
    -- ...
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-否，1-是',
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

### 唯一索引的处理

逻辑删除与唯一索引有冲突：已删除的记录仍然占据唯一约束。解决方案是将 `deleted` 字段加入唯一索引：

```sql
-- 渠道成员表：同一渠道下手机号不能重复（但已删除的可以"释放"）
UNIQUE KEY uk_phone_distributor (distributor_id, phone, deleted)
```

**注意**：这个方案有一个边界问题——如果同一 `(distributor_id, phone)` 先删除再创建再删除，`deleted` 字段都是 0 和 1，不会冲突。但如果需要"删除→创建→删除→创建"多次，MySQL 的唯一索引会报错（因为 `(123, '13800138000, 0)` 会出现两次）。本项目通过业务逻辑避免了这种情况。

## 考虑的替代方案

### 方案 A：物理删除

| 优点 | 缺点 |
|------|------|
| 数据库干净，无垃圾数据 | 删除后无法恢复 |
| 唯一索引无冲突 | 关联数据变成孤儿记录 |
| 查询无需额外条件 | 审计日志无法关联已删除数据 |
| 存储空间不膨胀 | 误操作无法回滚 |

### 方案 B：归档表

将删除的记录移到 `_archive` 后缀的表中。

| 优点 | 缺点 |
|------|------|
| 主表保持干净 | 需要维护两套表结构 |
| 归档数据可独立查询 | 跨表查询复杂 |
| | 表结构变更时需要同步修改两张表 |

### 方案 C：软删除 + 唯一索引用 UUID

用 UUID 替代自增 ID，唯一索引只约束业务字段，不包含 `deleted`。

| 优点 | 缺点 |
|------|------|
| 唯一索引简洁 | UUID 占用 36 字节 vs BIGINT 的 8 字节 |
| | 主键无序，影响聚簇索引性能 |
| | 可读性差 |

## 选择逻辑删除的理由

### 1. 审计合规的刚需

医疗行业的分销系统需要保留完整的数据生命周期。删除渠道、成员、业务单等记录后，审计日志需要能够关联到这些数据。

```sql
-- 审计日志关联已删除的渠道
SELECT a.*, d.name as distributor_name
FROM distribution_audit_log a
LEFT JOIN distribution_distributor d ON a.biz_id = d.id
WHERE a.biz_type = 'distributor' AND a.biz_id = 123;
-- 如果物理删除了渠道，d.name 就是 NULL
```

### 2. 关联数据的完整性

分销系统有复杂的关联关系：

```
渠道 → 成员 → 线索 → 归因 → 业务单 → 佣金流水 → 结算单
```

如果物理删除了中间某个节点（如渠道），所有下游数据都会变成孤儿记录。逻辑删除保证了关联查询的完整性。

### 3. 误操作可恢复

管理后台的操作人员可能误删数据。逻辑删除下，恢复只需要一条 SQL：

```sql
UPDATE distribution_distributor SET deleted = 0 WHERE id = 123;
```

物理删除需要从备份恢复，操作复杂且可能丢失后续数据。

### 4. 实现简单

逻辑删除的实现成本极低：
- 每个表加一个 `deleted` 字段
- 查询条件统一加 `WHERE deleted = 0`（通过 MyBatis 片段自动注入）
- 删除操作改为 `UPDATE SET deleted = 1`

### 5. 与现有系统一致

`medical-chaperon-server` 已经使用逻辑删除，保持一致可以复用已有的查询逻辑和数据。

## Trade-off

### 付出的代价

1. **数据库膨胀**：已删除的记录永久占用存储空间
   - 缓解：业务数据量在可控范围内（百万级而非亿级）
   - 缓解：可以定期归档超过 N 年的已删除记录到冷存储

2. **唯一索引需要包含 `deleted` 字段**：增加了索引的复杂度
   - 缓解：本项目的唯一约束场景不多，影响有限
   - 缓解：通过业务逻辑避免"反复删除创建"的场景

3. **查询需要额外条件**：每个查询都要加 `WHERE deleted = 0`
   - 缓解：通过 MyBatis 的 `ConditionWhere` 片段统一注入，开发者无需手动添加
   - 缓解：`selectByPrimaryKey` 也需要加 `AND deleted = 0`，防止查到已删除记录

4. **级联删除变复杂**：物理删除时 `ON DELETE CASCADE` 可以自动清理关联数据，逻辑删除需要手动处理
   - 缓解：本项目的删除操作不多，且大部分是单表操作

### 收益

1. 数据完整可追溯，满足审计合规要求
2. 误操作可恢复，降低运维风险
3. 关联数据不丢失，查询一致性有保障
4. 实现简单，团队理解成本低

## 实现细节

### 删除操作的标准模式

```java
@Override
@Transactional(rollbackFor = Exception.class)
public void deleteDistributor(Long id) {
    // 1. 查询并校验（getEntity 方法自动过滤 deleted = 0）
    DistributionDistributorEntity existing = getDistributorEntity(id);

    // 2. 权限校验
    validateDistributorAccess(id);

    // 3. 逻辑删除
    DistributionDistributorEntity update = new DistributionDistributorEntity();
    update.setId(id);
    update.setDeleted(1);
    update.setUpdatedBy(distributionOperatorService.getCurrentOperatorUserId());
    distributionDistributorMapper.updateByPrimaryKeySelective(update);

    // 4. 审计日志（记录删除前的完整快照）
    distributionAuditLogService.record(
        DistributionAuditBizType.DISTRIBUTOR.getCode(), id, "delete", existing, null, "删除渠道");
}
```

### 查询自动过滤

```java
// 所有 getEntity 方法都自动过滤已删除记录
private DistributionDistributorEntity getDistributorEntity(Long id) {
    DistributionDistributorEntity entity = distributionDistributorMapper.selectByPrimaryKey(id);
    // selectByPrimaryKey 的 SQL 中已有 AND deleted = 0
    if (entity == null) {
        throw new BizException("渠道不存在", ResultCode.DISTRIBUTOR_NOT_FOUND.getCode());
    }
    return entity;
}
```

### XML 中的统一注入

```xml
<sql id="ConditionWhere">
    where deleted = 0          ← 所有查询自动附加
    <if test="name != null and name != ''">
        and name like concat('%', #{name}, '%')
    </if>
    ...
</sql>
```

## 后续演进

如果未来数据量增长到需要清理已删除记录：

1. **定期归档**：将超过 2 年的 `deleted = 1` 记录迁移到归档表或冷存储
2. **分区表**：按 `deleted` 字段分区，将已删除记录放在独立分区
3. **物理删除窗口**：在低峰期执行物理删除（`DELETE WHERE deleted = 1 AND modify_time < '2024-01-01'`）

---

