# Step 04: 审计日志 — 记录每一次变更

> CRUD 能跑了，但如果有人改了数据，我们不知道"谁改的、改了什么、什么时候改的"。本章加入审计日志。

---

## 本章目标

- 创建 `DistributionAuditLogService`，提供统一的审计记录方法
- 创建审计日志表和 Entity
- 在所有写操作中集成审计日志

## 设计原则

1. **统一入口**：所有审计日志通过 `record()` 一个方法写入
2. **JSON 快照**：记录变更前后的完整对象，而非字段级 diff
3. **不可删除**：审计日志表没有 `deleted` 字段
4. **自动获取操作人**：从 ThreadLocal 获取，调用方不需要传

## 第一步：建表

```sql
CREATE TABLE distribution_audit_log (
    id              BIGINT NOT NULL AUTO_INCREMENT,
    biz_type        VARCHAR(32) NOT NULL COMMENT '业务类型：lead/distributor/commission/...',
    biz_id          BIGINT NOT NULL COMMENT '业务对象ID',
    action          VARCHAR(64) NOT NULL COMMENT '操作：create/update/delete/event_xxx',
    before_snapshot LONGTEXT COMMENT '变更前 JSON',
    after_snapshot  LONGTEXT COMMENT '变更后 JSON',
    operator_user_id BIGINT NOT NULL COMMENT '操作人ID',
    operator_name   VARCHAR(64) COMMENT '操作人姓名',
    remark          VARCHAR(500),
    create_time     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_biz (biz_type, biz_id),
    KEY idx_action (action),
    KEY idx_create_time (create_time)
);
```

**注意**：没有 `deleted` 字段——审计日志一旦写入，不可删除。

## 第二步：创建审计服务

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
        // 1. 自动获取操作人
        MedicalUserInfoEntity operator = operatorService.getCurrentOperatorUser();

        // 2. 构建日志实体
        DistributionAuditLogEntity entity = new DistributionAuditLogEntity();
        entity.setBizType(bizType);
        entity.setBizId(bizId);
        entity.setAction(action);
        entity.setBeforeSnapshot(toJson(beforeSnapshot));
        entity.setAfterSnapshot(toJson(afterSnapshot));
        entity.setOperatorUserId(operator.getId());
        entity.setOperatorName(operator.getName());
        entity.setRemark(remark);

        // 3. 写入（审计日志不支持删除，所以用 insert 而非 insertSelective）
        auditLogMapper.insertSelective(entity);
    }

    private String toJson(Object obj) {
        if (obj == null) return null;
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("审计快照序列化失败", e);
        }
    }
}
```

## 第三步：在 Service 中集成

每个写操作都遵循固定模式：**先操作，后记日志**。

### 创建操作

```java
@Override
@Transactional(rollbackFor = Exception.class)
public Long createLead(CreateDistributionLeadRequestDTO request) {
    // ... 校验、构建实体、插入 ...

    leadMapper.insertSelective(entity);

    // 审计：before=null, after=新对象
    auditLogService.record("lead", entity.getId(), "create", null, entity, "创建线索");

    return entity.getId();
}
```

### 更新操作

```java
@Override
@Transactional(rollbackFor = Exception.class)
public void updateLead(Long id, UpdateDistributionLeadRequestDTO request) {
    // 1. 先查出变更前的对象
    DistributionLeadEntity existing = getLeadEntity(id);

    // 2. 执行更新
    DistributionLeadEntity update = new DistributionLeadEntity();
    update.setId(id);
    update.setPatientName(request.getPatientName());
    // ...
    leadMapper.updateByPrimaryKeySelective(update);

    // 3. 审计：before=旧对象, after=新对象
    auditLogService.record("lead", id, "update", existing, getLeadEntity(id), "更新线索");
}
```

### 删除操作

```java
@Override
@Transactional(rollbackFor = Exception.class)
public void deleteLead(Long id) {
    DistributionLeadEntity existing = getLeadEntity(id);

    // 逻辑删除
    DistributionLeadEntity update = new DistributionLeadEntity();
    update.setId(id);
    update.setDeleted(1);
    leadMapper.updateByPrimaryKeySelective(update);

    // 审计：before=旧对象, after=null
    auditLogService.record("lead", id, "delete", existing, null, "删除线索");
}
```

### 状态变更操作

```java
// action 命名规范：event_ + 事件类型
auditLogService.record("business_order", orderId, "event_" + eventType,
    beforeSnapshot, afterSnapshot, "推进业务单事件：" + eventType);
```

## 第四步：查询审计日志

```java
// 按业务对象查询变更历史
PageResponseDTO<DistributionAuditLogDTO> logs =
    auditLogService.listLogs("lead", 123L, null, 1, 20);

// 查看单条日志详情（含完整快照）
DistributionAuditLogDetailDTO detail = auditLogService.getLog(456L);
// detail.getBeforeSnapshot() → JSON 字符串
// detail.getAfterSnapshot() → JSON 字符串
```

## Action 命名规范

| 操作类型 | action 值 | 示例 |
|---------|----------|------|
| 创建 | `create` | `create` |
| 更新 | `update` | `update` |
| 删除 | `delete` | `delete` |
| 状态变更 | `event_` + 类型 | `event_signed`, `event_first_payment` |
| 审核 | `lock` / `approve` / `reject` | `lock` |
| 冲回 | `reverse` / `create_reverse` | `reverse` |

## 审计日志的存储考虑

每条日志包含完整的 JSON 快照（1-5KB），长期积累后会占用大量存储。

**建议**：
- 短期：保持在主库，方便查询
- 中期（6 个月+）：归档到独立的审计库
- 长期（1 年+）：导出到冷存储（如 OSS + Parquet）

## 验证

创建一条线索后，查询审计日志：

```bash
curl -H "Authorization: test-token-123" \
     -H "x-biz: medical-chaperon" \
     "http://localhost:9030/api/manage/distribution/audit-logs?bizType=lead&bizId=1"
```

应该看到一条 `action=create` 的日志，`after_snapshot` 包含完整的线索数据。

## 下一步

审计日志有了，系统已经具备基本的可追溯性。下一步实现核心业务逻辑——佣金流转。

→ [Step 05: 佣金流转](05-commission-flow.md)

---

