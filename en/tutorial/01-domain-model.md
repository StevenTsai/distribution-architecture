# Step 01: Domain Model — Start with Entities and Relationships

[中文](../../tutorial/01-domain-model.md) | English

> Before writing any business code, first figure out "what things exist in the system and how they relate to each other."
> This chapter defines the core entities of the distribution system and establishes the database table structures.

---

## Chapter Goals

After completing this chapter, you will have:
- 6 core database tables (distributor, member, lead, attribution, business order, business order event)
- The corresponding Java Entity classes
- An understanding of the relationships between entities

## Core Domain Concepts

The distribution business can be summed up in one sentence: **A distributor recommends a contact, the contact generates business, and the business generates commission**.

```
┌──────────┐   recommend   ┌──────────┐   generate   ┌──────────┐    trigger    ┌──────────┐
│Distributor│──────────────►│   Lead   │─────────────►│ BizOrder │──────────────►│Commission│
└──────────┘               └──────────┘              └──────────┘               └──────────┘
     │                          │                         │
     │ contains                 │ associated              │ associated
     ▼                          ▼                         ▼
┌──────────┐               ┌──────────┐              ┌──────────┐
│  Member  │               │Attribution│             │  Event   │
└──────────┘               └──────────┘              └──────────┘
```

## Entity 1: Distributor

A distributor is the basic unit of the distribution network. A distributor can have a parent distributor (forming a tree structure) and can have multiple members.

**Design points**:
- `parent_id` supports a multi-level distributor tree
- `level_code` distinguishes the distributor tier (L1/L2/L3), which affects the commission rate
- `product_lines_json` stores the product lines the distributor can sell as JSON
- `status` uses a string enum rather than a number, making debugging easier

```sql
CREATE TABLE distribution_distributor (
    id              BIGINT NOT NULL AUTO_INCREMENT,
    code            VARCHAR(32) NOT NULL COMMENT 'Distributor code, unique',
    name            VARCHAR(128) NOT NULL COMMENT 'Distributor name',
    parent_id       BIGINT DEFAULT NULL COMMENT 'Parent distributor ID',
    level_code      VARCHAR(32) NOT NULL COMMENT 'Distributor tier L1/L2/L3',
    owner_user_id   BIGINT DEFAULT NULL COMMENT 'Platform owner',
    contact_name    VARCHAR(64),
    contact_phone   VARCHAR(32),
    province        VARCHAR(32),
    city            VARCHAR(32),
    settlement_type VARCHAR(32) COMMENT 'Settlement method',
    contract_status VARCHAR(32) NOT NULL DEFAULT 'pending',
    product_lines_json JSON COMMENT 'Sellable product lines ["gene","proton"]',
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

**Corresponding Java Entity**:

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

## Entity 2: Member

A member belongs to a distributor and has a role and a data scope. This is the foundation of the permission system.

**Design points**:
- `distributor_id` is a foreign key associating the member with a distributor
- `role_code` defines the role (super administrator, operations, sales, finance, etc.)
- `data_scope` defines the visible data range (ALL/OWN_DISTRIBUTOR/OWN_AND_CHILDREN/SELF)
- The unique index `(distributor_id, phone, deleted)` ensures phone numbers are unique within a distributor

```sql
CREATE TABLE distribution_distributor_member (
    id              BIGINT NOT NULL AUTO_INCREMENT,
    distributor_id  BIGINT NOT NULL COMMENT 'Owning distributor ID',
    user_id         BIGINT DEFAULT NULL COMMENT 'System user ID',
    name            VARCHAR(64) NOT NULL,
    phone           VARCHAR(32) NOT NULL,
    role_code       VARCHAR(32) NOT NULL COMMENT 'Distribution role',
    data_scope      VARCHAR(32) NOT NULL DEFAULT 'SELF' COMMENT 'Data scope',
    manager_member_id BIGINT DEFAULT NULL COMMENT 'Direct manager',
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

## Entity 3: Lead

A lead is a "potential contact" recommended into the system by a distributor member.

**Design points**:
- `source_distributor_id` + `source_member_id` record the lead source
- `owner_user_id` records the current owner (the lead can be reassigned)
- `stage` records the lead stage (pending_review → contacted → visited → signed → converted → closed)
- `is_duplicate` + `duplicate_lead_id` handle duplicate leads

```sql
CREATE TABLE distribution_lead (
    id                  BIGINT NOT NULL AUTO_INCREMENT,
    lead_no             VARCHAR(32) NOT NULL COMMENT 'Lead number',
    contact_name        VARCHAR(64) NOT NULL,
    contact_phone       VARCHAR(32) NOT NULL,
    source_distributor_id BIGINT NOT NULL COMMENT 'Source distributor',
    source_member_id    BIGINT COMMENT 'Referring member',
    intent_product_line VARCHAR(32) NOT NULL COMMENT 'Intended product line',
    owner_user_id       BIGINT COMMENT 'Current owner',
    stage               VARCHAR(32) NOT NULL DEFAULT 'pending_review',
    is_duplicate        TINYINT NOT NULL DEFAULT 0,
    duplicate_lead_id   BIGINT,
    latest_follow_up_at DATETIME,
    deleted             TINYINT NOT NULL DEFAULT 0,
    -- ...
    PRIMARY KEY (id),
    UNIQUE KEY uk_lead_no (lead_no),
    KEY idx_contact_phone (contact_phone),
    KEY idx_source_distributor (source_distributor_id),
    KEY idx_stage (stage)
);
```

## Entity 4: Attribution

Attribution records "who this contact belongs to". A contact can have only one active attribution.

**Design points**:
- `contact_id` unique index (a contact can have only one active attribution)
- `first_*` records the first attribution (immutable), while `current_*` records the current ownership (mutable)
- Once `is_locked` is set, modification is not allowed (the attribution must be locked before commission calculation)

```sql
CREATE TABLE distribution_contact_attribution (
    id                      BIGINT NOT NULL AUTO_INCREMENT,
    contact_id              BIGINT NOT NULL,
    lead_id                 BIGINT,
    first_distributor_id    BIGINT COMMENT 'First-attribution distributor (immutable)',
    first_member_id         BIGINT COMMENT 'First-attribution member (immutable)',
    current_distributor_id  BIGINT COMMENT 'Current owning distributor',
    current_member_id       BIGINT COMMENT 'Current owning member',
    status                  VARCHAR(32) NOT NULL DEFAULT 'active',
    is_locked               TINYINT NOT NULL DEFAULT 0,
    locked_at               DATETIME,
    locked_by               BIGINT,
    change_reason           VARCHAR(500),
    deleted                 TINYINT NOT NULL DEFAULT 0,
    -- ...
    PRIMARY KEY (id),
    UNIQUE KEY uk_contact_active (contact_id, deleted),
    KEY idx_current_distributor (current_distributor_id)
);
```

## Entity 5: Business Order

A business order is "confirmed business", converted from a lead.

**Design points**:
- Links the lead, contact, attribution, product, distributor, and member — one order connects all entities
- `signed_amount` is the signed amount, and `received_amount` is the cumulative received amount
- `status` is driven by a state machine (draft → visiting → signed → first_paid → full_paid → service_completed → closed)
- Timestamp fields record the time of key milestones

```sql
CREATE TABLE distribution_business_order (
    id                      BIGINT NOT NULL AUTO_INCREMENT,
    biz_order_no            VARCHAR(32) NOT NULL COMMENT 'Business order number',
    lead_id                 BIGINT,
    contact_id              BIGINT NOT NULL,
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
    KEY idx_contact_id (contact_id),
    KEY idx_distributor_id (distributor_id),
    KEY idx_status (status)
);
```

## Entity 6: Business Order Event

Business order state changes are advanced through "events". Each state change creates an event record.

**Design points**:
- `event_no` unique index guarantees event idempotency
- `before_status` + `after_status` record the state change
- `event_amount` records the event amount (such as a received payment)
- The event table is append-only, making it naturally suited to auditing

```sql
CREATE TABLE distribution_business_order_event (
    id              BIGINT NOT NULL AUTO_INCREMENT,
    biz_order_id    BIGINT NOT NULL COMMENT 'Business order ID',
    event_no        VARCHAR(64) NOT NULL COMMENT 'Event number, idempotent',
    event_type      VARCHAR(32) NOT NULL COMMENT 'Event type',
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

## Entity Relationship Overview

```
user_profile (system user)
    │
    ├── 1:N ── distribution_distributor_member (distributor member)
    │               │
    │               ├── N:1 ── distribution_distributor (distributor)
    │               │               │
    │               │               └── self-ref ── parent_id (parent distributor)
    │               │
    │               └── 1:N ── distribution_lead (lead)
    │                               │
    │                               ├── 1:1 ── distribution_contact_attribution (attribution)
    │                               │               │
    │                               │               └── N:1 ── contact_info (contact)
    │                               │
    │                               └── 1:N ── distribution_business_order (business order)
    │                                               │
    │                                               └── 1:N ── distribution_business_order_event (event)
    │
    └── (created_by, updated_by, owner_user_id referenced in each table)
```

## Design Decision Notes

### Why use string enums instead of numeric status codes?

```java
// ✅ String enum: highly readable; you can tell the meaning directly from the database
status = 'signed'

// ❌ Numeric status code: you have to consult documentation to know what 3 means
status = 3
```

**Trade-off**: Strings take up more storage space, but the convenience for debugging and log investigation far outweighs the storage cost.

### Why use Lombok @Data for Entities?

```java
@Data
public class DistributionDistributorEntity {
    private Long id;
    private String name;
    // ...
}
```

`@Data` automatically generates getters/setters/toString/equals/hashCode, reducing boilerplate code. An Entity is a pure data container and does not need additional business logic.

### Why is `deleted` TINYINT rather than BOOLEAN?

MySQL's `BOOLEAN` is actually an alias for `TINYINT(1)`. Using `TINYINT` is more explicit and can be extended to multiple deletion states (e.g., 0=normal, 1=deleted by user, 2=deleted by administrator).

## Verification

After running the table creation script, confirm the table structure:

```bash
mysql -u root -p distribution_server -e "
  SELECT TABLE_NAME, TABLE_COMMENT
  FROM information_schema.TABLES
  WHERE TABLE_SCHEMA = 'distribution_server'
  ORDER BY TABLE_NAME;
"
```

You should see 6 tables (10 in total including the shared tables from earlier).

## Next Step

The table structures are in place, but there is no permission control yet. Next we add a data permission layer so different roles see different data.

→ [Step 02: Data Permission Layer](02-data-permission.md)

---
