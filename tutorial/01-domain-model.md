# Step 01: 领域模型 — 从实体和关系开始

> 在写任何业务代码之前，先搞清楚"系统里有哪些东西、它们之间是什么关系"。
> 本章定义分销系统的核心实体，建立数据库表结构。

---

## 本章目标

完成本章后，你将拥有：
- 6 张核心数据库表（渠道、成员、线索、归因、业务单、业务单事件）
- 对应的 Java Entity 类
- 理解实体之间的关联关系

## 核心领域概念

分销系统的业务可以用一句话概括：**渠道推荐患者，患者产生业务，业务产生佣金**。

```
┌──────────┐    推荐     ┌──────────┐    产生     ┌──────────┐    触发     ┌──────────┐
│  渠道    │───────────►│  线索    │───────────►│  业务单  │───────────►│  佣金    │
│Distributor│           │  Lead    │           │ BizOrder │           │Commission│
└──────────┘            └──────────┘            └──────────┘            └──────────┘
     │                       │                       │
     │ 包含                  │ 关联                  │ 关联
     ▼                       ▼                       ▼
┌──────────┐            ┌──────────┐            ┌──────────┐
│  成员    │            │  归因    │            │  事件    │
│  Member  │            │Attribution│           │  Event   │
└──────────┘            └──────────┘            └──────────┘
```

## 实体 1：渠道（Distributor）

渠道是分销网络的基本单元。一个渠道可以有上级渠道（形成树形结构），可以有多个成员。

**设计要点**：
- `parent_id` 支持多级渠道树
- `level_code` 区分渠道等级（L1/L2/L3），影响佣金比例
- `product_lines_json` 用 JSON 存储渠道可售的产品线
- `status` 使用字符串枚举而非数字，便于调试

```sql
CREATE TABLE distribution_distributor (
    id              BIGINT NOT NULL AUTO_INCREMENT,
    code            VARCHAR(32) NOT NULL COMMENT '渠道编码，唯一',
    name            VARCHAR(128) NOT NULL COMMENT '渠道名称',
    parent_id       BIGINT DEFAULT NULL COMMENT '上级渠道ID',
    level_code      VARCHAR(32) NOT NULL COMMENT '渠道等级 L1/L2/L3',
    owner_user_id   BIGINT DEFAULT NULL COMMENT '平台负责人',
    contact_name    VARCHAR(64),
    contact_phone   VARCHAR(32),
    province        VARCHAR(32),
    city            VARCHAR(32),
    settlement_type VARCHAR(32) COMMENT '结算方式',
    contract_status VARCHAR(32) NOT NULL DEFAULT 'pending',
    product_lines_json JSON COMMENT '可售产品线 ["gene","proton"]',
    status          VARCHAR(32) NOT NULL DEFAULT 'pending',
    remark          VARCHAR(500),
    created_by      BIGINT,
    updated_by      BIGINT,
    create_time     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    modify_time     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted         TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_code (code),
    KEY idx_parent_id (parent_id),
    KEY idx_status (status)
);
```

**对应的 Java Entity**：

```java
@Data
public class DistributionDistributorEntity {
    private Long id;
    private String code;
    private String name;
    private Long parentId;
    private String levelCode;
    private Long ownerUserId;
    private String contactName;
    private String contactPhone;
    private String province;
    private String city;
    private String settlementType;
    private String contractStatus;
    private String productLinesJson;
    private String status;
    private String remark;
    private Long createdBy;
    private Long updatedBy;
    private Date createTime;
    private Date modifyTime;
    private Integer deleted;
}
```

## 实体 2：成员（Member）

成员属于某个渠道，有角色和数据范围。这是权限系统的基础。

**设计要点**：
- `distributor_id` 外键关联渠道
- `role_code` 定义角色（超级管理员、运营、销售、财务等）
- `data_scope` 定义数据可见范围（ALL/OWN_DISTRIBUTOR/OWN_AND_CHILDREN/SELF）
- 唯一索引 `(distributor_id, phone, deleted)` 保证同一渠道下手机号不重复

```sql
CREATE TABLE distribution_distributor_member (
    id              BIGINT NOT NULL AUTO_INCREMENT,
    distributor_id  BIGINT NOT NULL COMMENT '所属渠道ID',
    user_id         BIGINT DEFAULT NULL COMMENT '系统用户ID',
    name            VARCHAR(64) NOT NULL,
    phone           VARCHAR(32) NOT NULL,
    role_code       VARCHAR(32) NOT NULL COMMENT '分销角色',
    data_scope      VARCHAR(32) NOT NULL DEFAULT 'SELF' COMMENT '数据范围',
    manager_member_id BIGINT DEFAULT NULL COMMENT '直属上级',
    product_lines_json JSON,
    training_status VARCHAR(32) NOT NULL DEFAULT 'pending',
    status          VARCHAR(32) NOT NULL DEFAULT 'active',
    deleted         TINYINT NOT NULL DEFAULT 0,
    -- ...
    PRIMARY KEY (id),
    UNIQUE KEY uk_phone_distributor (distributor_id, phone, deleted),
    KEY idx_user_id (user_id),
    KEY idx_role_code (role_code)
);
```

## 实体 3：线索（Lead）

线索是"潜在患者"，由渠道成员推荐进入系统。

**设计要点**：
- `source_distributor_id` + `source_member_id` 记录线索来源
- `owner_user_id` 记录当前负责人（可以转派）
- `stage` 记录线索阶段（pending_review → contacted → visited → signed → converted → closed）
- `is_duplicate` + `duplicate_lead_id` 处理重复线索

```sql
CREATE TABLE distribution_lead (
    id                  BIGINT NOT NULL AUTO_INCREMENT,
    lead_no             VARCHAR(32) NOT NULL COMMENT '线索编号',
    patient_name        VARCHAR(64) NOT NULL,
    patient_phone       VARCHAR(32) NOT NULL,
    source_distributor_id BIGINT NOT NULL COMMENT '来源渠道',
    source_member_id    BIGINT COMMENT '推荐成员',
    intent_product_line VARCHAR(32) NOT NULL COMMENT '意向产品线',
    owner_user_id       BIGINT COMMENT '当前负责人',
    stage               VARCHAR(32) NOT NULL DEFAULT 'pending_review',
    is_duplicate        TINYINT NOT NULL DEFAULT 0,
    duplicate_lead_id   BIGINT,
    latest_follow_up_at DATETIME,
    deleted             TINYINT NOT NULL DEFAULT 0,
    -- ...
    PRIMARY KEY (id),
    UNIQUE KEY uk_lead_no (lead_no),
    KEY idx_patient_phone (patient_phone),
    KEY idx_source_distributor (source_distributor_id),
    KEY idx_stage (stage)
);
```

## 实体 4：归因（Attribution）

归因记录"这个患者属于谁"。一个患者只能有一个活跃的归因。

**设计要点**：
- `patient_id` 唯一索引（一个患者只能有一条活跃归因）
- `first_*` 记录首次归因（不可变），`current_*` 记录当前归属（可变更）
- `is_locked` 锁定后不允许修改（佣金计算前需要锁定归因）

```sql
CREATE TABLE distribution_patient_attribution (
    id                      BIGINT NOT NULL AUTO_INCREMENT,
    patient_id              BIGINT NOT NULL,
    lead_id                 BIGINT,
    first_distributor_id    BIGINT COMMENT '首归因渠道（不可变）',
    first_member_id         BIGINT COMMENT '首归因成员（不可变）',
    current_distributor_id  BIGINT COMMENT '当前归属渠道',
    current_member_id       BIGINT COMMENT '当前归属成员',
    status                  VARCHAR(32) NOT NULL DEFAULT 'active',
    is_locked               TINYINT NOT NULL DEFAULT 0,
    locked_at               DATETIME,
    locked_by               BIGINT,
    change_reason           VARCHAR(500),
    deleted                 TINYINT NOT NULL DEFAULT 0,
    -- ...
    PRIMARY KEY (id),
    UNIQUE KEY uk_patient_active (patient_id, deleted),
    KEY idx_current_distributor (current_distributor_id)
);
```

## 实体 5：业务单（Business Order）

业务单是"已确认的业务"，由线索转化而来。

**设计要点**：
- 关联线索、患者、归因、产品、渠道、成员——一张单子串联所有实体
- `signed_amount` 签约金额，`received_amount` 累计回款金额
- `status` 状态机驱动（draft → visiting → signed → first_paid → full_paid → service_completed → closed）
- 时间戳字段记录关键节点时间

```sql
CREATE TABLE distribution_business_order (
    id                      BIGINT NOT NULL AUTO_INCREMENT,
    biz_order_no            VARCHAR(32) NOT NULL COMMENT '业务单号',
    lead_id                 BIGINT,
    patient_id              BIGINT NOT NULL,
    attribution_id          BIGINT,
    product_line_code       VARCHAR(32) NOT NULL,
    product_code            VARCHAR(64) NOT NULL,
    product_name            VARCHAR(128) NOT NULL,
    distributor_id          BIGINT,
    member_id               BIGINT,
    current_owner_user_id   BIGINT,
    signed_amount           DECIMAL(18,2),
    received_amount         DECIMAL(18,2) NOT NULL DEFAULT 0.00,
    status                  VARCHAR(32) NOT NULL DEFAULT 'draft',
    signed_at               DATETIME,
    first_payment_at        DATETIME,
    full_payment_at         DATETIME,
    service_completed_at    DATETIME,
    deleted                 TINYINT NOT NULL DEFAULT 0,
    -- ...
    PRIMARY KEY (id),
    UNIQUE KEY uk_biz_order_no (biz_order_no),
    KEY idx_lead_id (lead_id),
    KEY idx_patient_id (patient_id),
    KEY idx_distributor_id (distributor_id),
    KEY idx_status (status)
);
```

## 实体 6：业务单事件（Business Order Event）

业务单的状态变更通过"事件"推进。每次状态变更都创建一条事件记录。

**设计要点**：
- `event_no` 唯一索引，保证事件幂等
- `before_status` + `after_status` 记录状态变更
- `event_amount` 记录事件金额（如回款金额）
- 事件表只追加不修改，天然适合审计

```sql
CREATE TABLE distribution_business_order_event (
    id              BIGINT NOT NULL AUTO_INCREMENT,
    biz_order_id    BIGINT NOT NULL COMMENT '业务单ID',
    event_no        VARCHAR(64) NOT NULL COMMENT '事件号，幂等',
    event_type      VARCHAR(32) NOT NULL COMMENT '事件类型',
    event_amount    DECIMAL(18,2),
    event_time      DATETIME NOT NULL,
    before_status   VARCHAR(32),
    after_status    VARCHAR(32),
    remark          VARCHAR(500),
    created_by      BIGINT,
    create_time     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_event_no (event_no),
    KEY idx_biz_order_id (biz_order_id),
    KEY idx_event_type (event_type)
);
```

## 实体关系全景图

```
medical_user_info (系统用户)
    │
    ├── 1:N ── distribution_distributor_member (分销成员)
    │               │
    │               ├── N:1 ── distribution_distributor (渠道)
    │               │               │
    │               │               └── self-ref ── parent_id (上级渠道)
    │               │
    │               └── 1:N ── distribution_lead (线索)
    │                               │
    │                               ├── 1:1 ── distribution_patient_attribution (归因)
    │                               │               │
    │                               │               └── N:1 ── patient_info (患者)
    │                               │
    │                               └── 1:N ── distribution_business_order (业务单)
    │                                               │
    │                                               └── 1:N ── distribution_business_order_event (事件)
    │
    └── (created_by, updated_by, owner_user_id 在各表中引用)
```

## 设计决策说明

### 为什么用字符串枚举而非数字状态码？

```java
// ✅ 字符串枚举：可读性好，直接看数据库就知道含义
status = 'signed'

// ❌ 数字状态码：需要查文档才知道 3 代表什么
status = 3
```

**权衡**：字符串占用更多存储空间，但对调试和日志排查的便利性远超存储成本。

### 为什么 Entity 用 Lombok @Data？

```java
@Data
public class DistributionDistributorEntity {
    private Long id;
    private String name;
    // ...
}
```

`@Data` 自动生成 getter/setter/toString/equals/hashCode，减少样板代码。Entity 是纯数据容器，不需要额外的业务逻辑。

### 为什么 `deleted` 是 TINYINT 而非 BOOLEAN？

MySQL 的 `BOOLEAN` 实际是 `TINYINT(1)` 的别名。使用 `TINYINT` 更明确，且可以扩展为多种删除状态（如 0=正常, 1=用户删除, 2=管理员删除）。

## 验证

执行建表脚本后，确认表结构：

```bash
mysql -u root -p distribution_server -e "
  SELECT TABLE_NAME, TABLE_COMMENT
  FROM information_schema.TABLES
  WHERE TABLE_SCHEMA = 'distribution_server'
  ORDER BY TABLE_NAME;
"
```

你应该看到 6 张表（加上之前的共享表共 10 张）。

## 下一步

表结构有了，但还没有权限控制。下一步我们加入数据权限层，让不同角色看到不同的数据。

→ [Step 02: 数据权限层](02-data-permission.md)

---

