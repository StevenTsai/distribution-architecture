# Step 04: Audit Log — Record Every Change

[中文](../../tutorial/04-audit-logging.md) | English

> CRUD works, but if someone changes data, we do not know "who changed it, what was changed, or when it was changed." This chapter adds an audit log.

---

## Chapter Goals

- Create `DistributionAuditLogService`, providing a unified audit recording method
- Create the audit log table and Entity
- Integrate audit logging into all write operations

## Design Principles

1. **Single entry point**: All audit logs are written through the single `record()` method
2. **JSON snapshot**: Record the complete object before and after the change, rather than a field-level diff
3. **Undeletable**: The audit log table has no `deleted` field
4. **Automatic operator retrieval**: Retrieved from ThreadLocal; callers do not need to pass it

## Step 1: Create the Table

```sql
CREATE TABLE distribution_audit_log (
    id              BIGINT NOT NULL AUTO_INCREMENT,
    biz_type        VARCHAR(32) NOT NULL COMMENT 'Business type: lead/distributor/commission/...',
    biz_id          BIGINT NOT NULL COMMENT 'Business object ID',
    action          VARCHAR(64) NOT NULL COMMENT 'Action: create/update/delete/event_xxx',
    before_snapshot LONGTEXT COMMENT 'JSON before the change',
    after_snapshot  LONGTEXT COMMENT 'JSON after the change',
    operator_user_id BIGINT NOT NULL COMMENT 'Operator ID',
    operator_name   VARCHAR(64) COMMENT 'Operator name',
    remark          VARCHAR(500),
    create_time     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_biz (biz_type, biz_id),
    KEY idx_action (action),
    KEY idx_create_time (create_time)
);
```

**Note**: There is no `deleted` field — once an audit log is written, it cannot be deleted.

## Step 2: Create the Audit Service

```java
// (source file)
public interface DistributionAuditLogService {
    void record(String bizType, Long bizId, String action,
                Object beforeSnapshot, Object afterSnapshot, String remark);
    PageResponseDTO<DistributionAuditLogDTO> listLogs(String bizType, Long bizId, String action, Integer page, Integer pageSize);
    DistributionAuditLogDetailDTO getLog(Long id);
}
```

```java
// (source file)
@Service
public class DistributionAuditLogServiceImpl implements DistributionAuditLogService {

    @Autowired private DistributionAuditLogMapper auditLogMapper;
    @Autowired private DistributionOperatorService operatorService;
    @Autowired private ObjectMapper objectMapper;

    @Override
    public void record(String bizType, Long bizId, String action,
                       Object beforeSnapshot, Object afterSnapshot, String remark) {
        // 1. Automatically get the operator
        UserProfileEntity operator = operatorService.getCurrentOperatorUser();

        // 2. Build the log entity
        DistributionAuditLogEntity entity = new DistributionAuditLogEntity();
        entity.setBizType(bizType);
        entity.setBizId(bizId);
        entity.setAction(action);
        entity.setBeforeSnapshot(toJson(beforeSnapshot));
        entity.setAfterSnapshot(toJson(afterSnapshot));
        entity.setOperatorUserId(operator.getId());
        entity.setOperatorName(operator.getName());
        entity.setRemark(remark);

        // 3. Write (audit logs do not support deletion, so use insert rather than insertSelective)
        auditLogMapper.insertSelective(entity);
    }

    private String toJson(Object obj) {
        if (obj == null) return null;
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Audit snapshot serialization failed", e);
        }
    }
}
```

## Step 3: Integrate into the Service

Every write operation follows a fixed pattern: **perform the operation first, then record the log**.

### Create Operation

```java
@Override
@Transactional(rollbackFor = Exception.class)
public Long createLead(CreateDistributionLeadRequestDTO request) {
    // ... validate, build entity, insert ...

    leadMapper.insertSelective(entity);

    // Audit: before=null, after=new object
    auditLogService.record("lead", entity.getId(), "create", null, entity, "Create lead");

    return entity.getId();
}
```

### Update Operation

```java
@Override
@Transactional(rollbackFor = Exception.class)
public void updateLead(Long id, UpdateDistributionLeadRequestDTO request) {
    // 1. First query the object before the change
    DistributionLeadEntity existing = getLeadEntity(id);

    // 2. Perform the update
    DistributionLeadEntity update = new DistributionLeadEntity();
    update.setId(id);
    update.setContactName(request.getContactName());
    // ...
    leadMapper.updateByPrimaryKeySelective(update);

    // 3. Audit: before=old object, after=new object
    auditLogService.record("lead", id, "update", existing, getLeadEntity(id), "Update lead");
}
```

### Delete Operation

```java
@Override
@Transactional(rollbackFor = Exception.class)
public void deleteLead(Long id) {
    DistributionLeadEntity existing = getLeadEntity(id);

    // Logical delete
    DistributionLeadEntity update = new DistributionLeadEntity();
    update.setId(id);
    update.setDeleted(1);
    leadMapper.updateByPrimaryKeySelective(update);

    // Audit: before=old object, after=null
    auditLogService.record("lead", id, "delete", existing, null, "Delete lead");
}
```

### State Change Operation

```java
// action naming convention: event_ + event type
auditLogService.record("business_order", orderId, "event_" + eventType,
    beforeSnapshot, afterSnapshot, "Advance business order event: " + eventType);
```

## Step 4: Query Audit Logs

```java
// Query change history by business object
PageResponseDTO<DistributionAuditLogDTO> logs =
    auditLogService.listLogs("lead", 123L, null, 1, 20);

// View a single log detail (including the full snapshot)
DistributionAuditLogDetailDTO detail = auditLogService.getLog(456L);
// detail.getBeforeSnapshot() → JSON string
// detail.getAfterSnapshot() → JSON string
```

## Action Naming Conventions

| Operation Type | action Value | Example |
|----------------|--------------|---------|
| Create | `create` | `create` |
| Update | `update` | `update` |
| Delete | `delete` | `delete` |
| State change | `event_` + type | `event_signed`, `event_first_payment` |
| Review | `lock` / `approve` / `reject` | `lock` |
| Reversal | `reverse` / `create_reverse` | `reverse` |

## Audit Log Storage Considerations

Each log contains a complete JSON snapshot (1-5KB); accumulated over time, this consumes a large amount of storage.

**Recommendations**:
- Short term: keep it in the primary database for easy querying
- Medium term (6 months+): archive it to a separate audit database
- Long term (1 year+): export it to cold storage (e.g., OSS + Parquet)

## Verification

After creating a lead, query the audit logs:

```bash
curl -H "Authorization: test-token-123" \
     -H "x-biz: distribution-starter" \
     "http://localhost:9030/api/manage/distribution/audit-logs?bizType=lead&bizId=1"
```

You should see a log with `action=create`, and `after_snapshot` contains the complete lead data.

## Next Step

With the audit log in place, the system now has basic traceability. Next we implement the core business logic — commission flow.

→ [Step 05: Commission Flow](05-commission-flow.md)

---
