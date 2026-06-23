# Step 07: 合规检查 — 受监管行业的最后一道防线

> 分销系统在医疗、金融等受监管行业运行，需要合规记录来证明业务行为的合法性。本章加入合规检查层。

---

## 本章目标

- 理解审计日志与合规记录的区别
- 创建合规记录表和服务
- 实现合规记录的创建、审核流程
- 理解合规检查如何嵌入业务流程

## 审计日志 vs 合规记录

| 维度 | 审计日志 | 合规记录 |
|------|---------|---------|
| 用途 | 记录所有操作的变更历史 | 记录需要合规审查的业务行为 |
| 写入时机 | 每次写操作自动记录 | 业务触发时创建 |
| 内容 | before/after JSON 快照 | 合规内容 + 附件 + 审查结果 |
| 审查流程 | 无（纯记录） | 有待审 → 通过/驳回 流程 |
| 删除 | 不可删除 | 不可删除 |

**一句话区分**：审计日志是"被动记录"，合规记录是"主动管理"。

## 第一步：建表

```sql
CREATE TABLE distribution_compliance_record (
    id              BIGINT NOT NULL AUTO_INCREMENT,
    biz_type        VARCHAR(32) NOT NULL COMMENT '关联业务类型',
    biz_id          BIGINT NOT NULL COMMENT '关联业务ID',
    product_line_code VARCHAR(32) COMMENT '产品线',
    record_type     VARCHAR(32) NOT NULL COMMENT '记录类型：qualification/training/authorization/violation/approval',
    status          VARCHAR(32) NOT NULL DEFAULT 'pending' COMMENT '状态：pending/approved/rejected/expired',
    content         TEXT COMMENT '合规内容说明',
    attachments_json JSON COMMENT '附件JSON数组',
    reviewed_by     BIGINT COMMENT '审核人',
    reviewed_at     DATETIME COMMENT '审核时间',
    remark          VARCHAR(500),
    created_by      BIGINT,
    updated_by      BIGINT,
    create_time     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    modify_time     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted         TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_biz (biz_type, biz_id),
    KEY idx_record_type (record_type),
    KEY idx_status (status)
);
```

## 第二步：合规记录类型

| record_type | 说明 | 触发场景 |
|------------|------|---------|
| `qualification` | 资质审查 | 渠道入驻时 |
| `training` | 培训记录 | 成员完成培训时 |
| `authorization` | 授权书 | 渠道获得产品授权时 |
| `violation` | 违规记录 | 发现违规行为时 |
| `approval` | 审批记录 | 重要操作的审批 |

## 第三步：合规服务

```java
// (source file)
@Service
public class DistributionComplianceServiceImpl implements DistributionComplianceService {

    @Autowired private DistributionComplianceRecordMapper complianceMapper;
    @Autowired private DistributionOperatorService operatorService;
    @Autowired private DistributionAuditLogService auditLogService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createRecord(CreateComplianceRecordRequestDTO request) {
        validateRecordType(request.getRecordType());

        Long operatorUserId = operatorService.getCurrentOperatorUserId();

        DistributionComplianceRecordEntity entity = new DistributionComplianceRecordEntity();
        entity.setBizType(request.getBizType());
        entity.setBizId(request.getBizId());
        entity.setProductLineCode(request.getProductLineCode());
        entity.setRecordType(request.getRecordType());
        entity.setStatus("pending");
        entity.setContent(request.getContent());
        entity.setAttachmentsJson(writeAttachments(request.getAttachments()));
        entity.setCreatedBy(operatorUserId);
        entity.setUpdatedBy(operatorUserId);
        entity.setDeleted(0);
        complianceMapper.insertSelective(entity);

        auditLogService.record("compliance", entity.getId(), "create", null, entity, "创建合规记录");
        return entity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reviewRecord(Long id, ReviewComplianceRecordRequestDTO request) {
        DistributionComplianceRecordEntity existing = getRecordEntity(id);

        if (!"pending".equals(existing.getStatus())) {
            throw new BizException("只有待审核状态可以审核", ...);
        }

        Long operatorUserId = operatorService.getCurrentOperatorUserId();

        DistributionComplianceRecordEntity update = new DistributionComplianceRecordEntity();
        update.setId(id);
        update.setStatus(request.getStatus());  // "approved" 或 "rejected"
        update.setReviewedBy(operatorUserId);
        update.setReviewedAt(new Date());
        update.setRemark(request.getRemark());
        update.setUpdatedBy(operatorUserId);
        complianceMapper.updateByPrimaryKeySelective(update);

        auditLogService.record("compliance", id, "review_" + request.getStatus(),
            existing, getRecordEntity(id), "审核合规记录");
    }
}
```

## 第四步：合规记录生命周期

```
┌─────────┐
│ PENDING │  ← 创建合规记录
└─────────┘
     │
     ├── approve ──► ┌──────────┐
     │               │ APPROVED │
     │               └──────────┘
     │
     └── reject ───► ┌──────────┐
                     │ REJECTED │
                     └──────────┘

（资质类记录还可以过期）
┌──────────┐
│ APPROVED │ ──► ┌─────────┐
└──────────┘     │ EXPIRED │
                 └─────────┘
```

## 第五步：嵌入业务流程

合规检查不是独立的模块，而是嵌入在业务流程中的。典型场景：

### 场景 1：渠道入驻时的资质审查

```java
// DistributorServiceImpl.createDistributor() 中：
distributorMapper.insertSelective(entity);

// 自动创建合规记录（待审核）
ComplianceRecordRequestDTO compliance = new ComplianceRecordRequestDTO();
compliance.setBizType("distributor");
compliance.setBizId(entity.getId());
compliance.setRecordType("qualification");
compliance.setContent("渠道资质审查");
compliance.setAttachments(request.getQualificationAttachments());
complianceService.createRecord(compliance);
```

### 场景 2：佣金结算前的合规校验

```java
// 结算前检查是否有未通过的合规记录
public void validateComplianceBeforeSettlement(Long distributorId) {
    List<DistributionComplianceRecordEntity> violations =
        complianceMapper.selectByBizAndStatus("distributor", distributorId, "rejected");

    if (!violations.isEmpty()) {
        throw new BizException("渠道存在违规记录，不允许结算", ...);
    }
}
```

### 场景 3：成员培训状态检查

```java
// 创建业务单前检查成员培训状态
private void validateMemberTrainingStatus(Long memberId) {
    DistributionDistributorMemberEntity member = memberMapper.selectByPrimaryKey(memberId);
    if (!"completed".equals(member.getTrainingStatus())) {
        throw new BizException("成员未完成培训，不能创建业务单", ...);
    }
}
```

## 合规记录的查询

```java
// 按业务对象查询合规记录
PageResponseDTO<DistributionComplianceRecordDTO> records =
    complianceService.listRecords("distributor", 1L, "qualification", null, 1, 20);

// 查询待审核的记录
PageResponseDTO<DistributionComplianceRecordDTO> pending =
    complianceService.listRecords(null, null, null, "pending", 1, 20);
```

## 与审计日志的协同

合规记录和审计日志是互补的：

```
业务操作
  │
  ├──► 审计日志：记录"做了什么"（自动，所有操作）
  │
  └──► 合规记录：记录"是否合规"（手动，特定场景）
```

查询时可以交叉验证：

```sql
-- 查看某个渠道的所有操作历史
SELECT * FROM distribution_audit_log
WHERE biz_type = 'distributor' AND biz_id = 1
ORDER BY create_time DESC;

-- 查看该渠道的合规记录
SELECT * FROM distribution_compliance_record
WHERE biz_type = 'distributor' AND biz_id = 1
ORDER BY create_time DESC;
```

## 验证

```bash
# 1. 创建合规记录
curl -X POST \
     -H "Authorization: test-token-123" \
     -H "x-biz: medical-chaperon" \
     -H "Content-Type: application/json" \
     -d '{"bizType":"distributor","bizId":1,"recordType":"qualification","content":"资质审查"}' \
     http://localhost:9030/api/manage/distribution/compliance-records

# 2. 查询待审核记录
curl -H "Authorization: test-token-123" \
     -H "x-biz: medical-chaperon" \
     "http://localhost:9030/api/manage/distribution/compliance-records?status=pending"

# 3. 审核通过
curl -X POST \
     -H "Authorization: test-token-123" \
     -H "x-biz: medical-chaperon" \
     -H "Content-Type: application/json" \
     -d '{"status":"approved","remark":"资质合格"}' \
     http://localhost:9030/api/manage/distribution/compliance-records/1/review
```

## 恭喜！

你已经完成了整个分销管理系统的从零构建。回顾一下我们搭建了什么：

| 步骤 | 内容 | 核心模式 |
|------|------|---------|
| 01 | 领域模型 | 实体设计、关系建模 |
| 02 | 数据权限 | 4 级作用域、SQL 层注入 |
| 03 | CRUD 脚手架 | 三层架构、对偶查询、批量加载 |
| 04 | 审计日志 | JSON 快照、统一入口 |
| 05 | 佣金流转 | 事件驱动状态机、规则快照、冲回机制 |
| 06 | 结算流程 | 归集、审核、打款、冲回联动 |
| 07 | 合规检查 | 审查流程、业务嵌入 |

## 下一步

- 阅读 [架构设计文档](../architecture/) 深入理解每个模式的设计决策
- 阅读 [扩展新模块指南](../guides/extend-new-module.md) 了解如何添加新功能
- 阅读 [ADR 文档](../decisions/) 了解技术选型的 trade-off

---

