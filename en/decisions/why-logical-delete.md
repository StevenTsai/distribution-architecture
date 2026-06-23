# ADR: Why Logical Delete over Physical Delete

[中文](../../decisions/why-logical-delete.md) | English

> Status: Accepted
> Date: Initial design phase
> Decision Maker: Architecture Team

---

## Background

There are two approaches to deleting database records:

1. **Physical Delete**: `DELETE FROM table WHERE id = ?` — the record disappears from the database
2. **Logical Delete**: `UPDATE table SET deleted = 1 WHERE id = ?` — the record is preserved but marked as deleted

This project chose logical delete.

## Decision

All business tables use a `deleted` field (`TINYINT NOT NULL DEFAULT 0`) for logical deletion:
- `deleted = 0`: Normal record
- `deleted = 1`: Deleted record

All queries automatically include `WHERE deleted = 0` conditions (uniformly injected via MyBatis `ConditionWhere` fragments).

### Table Structure Example

```sql
CREATE TABLE distribution_distributor (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'pending',
    -- ...
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT 'Logical delete: 0-No, 1-Yes',
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

### Unique Index Handling

Logical delete conflicts with unique indexes: deleted records still occupy the unique constraint. The solution is to include the `deleted` field in the unique index:

```sql
-- Distributor member table: phone cannot be duplicated within the same distributor (but deleted records can "release")
UNIQUE KEY uk_phone_distributor (distributor_id, phone, deleted)
```

**Note**: This solution has an edge case — if the same `(distributor_id, phone)` is deleted, created, then deleted again, the `deleted` field alternates between 0 and 1, so no conflict occurs. But if you need "delete → create → delete → create" multiple times, MySQL's unique index will error (because `(123, '13800138000', 0)` appears twice). This project avoids this scenario through business logic.

## Alternatives Considered

### Option A: Physical Delete

| Pros | Cons |
|------|------|
| Clean database, no junk data | Cannot recover after deletion |
| No unique index conflicts | Related data becomes orphan records |
| No extra query conditions needed | Audit logs cannot reference deleted data |
| No storage bloat | Accidental operations cannot be rolled back |

### Option B: Archive Tables

Move deleted records to tables with `_archive` suffix.

| Pros | Cons |
|------|------|
| Main table stays clean | Need to maintain two sets of table structures |
| Archived data independently queryable | Cross-table queries are complex |
| | Table structure changes require syncing both tables |

### Option C: Soft Delete + UUID for Unique Index

Use UUID instead of auto-increment ID; unique index only constrains business fields, not including `deleted`.

| Pros | Cons |
|------|------|
| Clean unique index | UUID uses 36 bytes vs BIGINT's 8 bytes |
| | Primary key is unordered, affecting clustered index performance |
| | Poor readability |

## Reasons for Choosing Logical Delete

### 1. Audit Compliance Requirement

Distribution systems in regulated industries need to preserve complete data lifecycles. After deleting distributors, members, business orders, etc., audit logs need to reference this data.

```sql
-- Audit log referencing a deleted distributor
SELECT a.*, d.name as distributor_name
FROM distribution_audit_log a
LEFT JOIN distribution_distributor d ON a.biz_id = d.id
WHERE a.biz_type = 'distributor' AND a.biz_id = 123;
-- If the distributor was physically deleted, d.name would be NULL
```

### 2. Related Data Integrity

Distribution systems have complex relationships:

```
Distributor → Member → Lead → Attribution → Business Order → Commission Ledger → Settlement
```

If an intermediate node (e.g., distributor) is physically deleted, all downstream data becomes orphan records. Logical deletion ensures referential query integrity.

### 3. Accidental Operations Are Recoverable

Admin panel operators may accidentally delete data. With logical deletion, recovery requires just one SQL:

```sql
UPDATE distribution_distributor SET deleted = 0 WHERE id = 123;
```

Physical deletion requires backup restoration, which is complex and may lose subsequent data.

### 4. Simple Implementation

Logical deletion has minimal implementation cost:
- Add a `deleted` field to each table
- Uniformly add `WHERE deleted = 0` to queries (auto-injected via MyBatis fragments)
- Change delete operations to `UPDATE SET deleted = 1`

### 5. Consistency with Existing System

The existing system already uses logical deletion. Maintaining consistency allows reuse of existing query logic and data.

## Trade-offs

### Costs Paid

1. **Database Bloat**: Deleted records permanently occupy storage space
   - Mitigation: Business data volume is manageable (millions, not billions)
   - Mitigation: Can periodically archive records older than N years to cold storage

2. **Unique Index Must Include `deleted` Field**: Increases index complexity
   - Mitigation: This project has few unique constraint scenarios, limited impact
   - Mitigation: Business logic avoids "repeated delete-create" scenarios

3. **Queries Need Extra Conditions**: Every query needs `WHERE deleted = 0`
   - Mitigation: Uniformly injected via MyBatis `ConditionWhere` fragments, developers don't need to add manually
   - Mitigation: `selectByPrimaryKey` also needs `AND deleted = 0` to prevent finding deleted records

4. **Cascade Delete Becomes Complex**: Physical delete with `ON DELETE CASCADE` auto-cleans related data; logical delete needs manual handling
   - Mitigation: This project has few delete operations, and most are single-table operations

### Benefits Gained

1. Complete, traceable data meeting audit compliance requirements
2. Accidental operations are recoverable, reducing operational risk
3. Related data is preserved, query consistency guaranteed
4. Simple implementation, low team learning curve

## Implementation Details

### Standard Delete Pattern

```java
@Override
@Transactional(rollbackFor = Exception.class)
public void deleteDistributor(Long id) {
    // 1. Query and validate (getEntity method auto-filters deleted = 0)
    DistributionDistributorEntity existing = getDistributorEntity(id);

    // 2. Permission check
    validateDistributorAccess(id);

    // 3. Logical delete
    DistributionDistributorEntity update = new DistributionDistributorEntity();
    update.setId(id);
    update.setDeleted(1);
    update.setUpdatedBy(distributionOperatorService.getCurrentOperatorUserId());
    distributionDistributorMapper.updateByPrimaryKeySelective(update);

    // 4. Audit log (record full snapshot before deletion)
    distributionAuditLogService.record(
        DistributionAuditBizType.DISTRIBUTOR.getCode(), id, "delete", existing, null, "Delete distributor");
}
```

### Auto-Filter in Queries

```java
// All getEntity methods automatically filter deleted records
private DistributionDistributorEntity getDistributorEntity(Long id) {
    DistributionDistributorEntity entity = distributionDistributorMapper.selectByPrimaryKey(id);
    // selectByPrimaryKey SQL already has AND deleted = 0
    if (entity == null) {
        throw new BizException("Distributor not found", ResultCode.DISTRIBUTOR_NOT_FOUND.getCode());
    }
    return entity;
}
```

### Uniform Injection in XML

```xml
<sql id="ConditionWhere">
    where deleted = 0          ← All queries auto-include
    <if test="name != null and name != ''">
        and name like concat('%', #{name}, '%')
    </if>
    ...
</sql>
```

## Future Evolution

If data volume grows to require cleaning deleted records:

1. **Periodic Archive**: Migrate `deleted = 1` records older than 2 years to archive tables or cold storage
2. **Partitioned Tables**: Partition by `deleted` field, placing deleted records in separate partitions
3. **Physical Delete Window**: Execute physical deletes during off-peak hours (`DELETE WHERE deleted = 1 AND modify_time < '2024-01-01'`)

---
