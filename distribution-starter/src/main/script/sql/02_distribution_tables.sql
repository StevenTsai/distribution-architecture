-- ============================================================
-- 02_distribution_tables.sql
-- 分销模块核心表结构（distribution-starter 精简版）
-- 包含：渠道管理、线索管理、审计日志、合规记录
-- ============================================================


-- ----------------------------
-- 1. 渠道主表
-- ----------------------------
CREATE TABLE IF NOT EXISTS `distribution_distributor` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `code` VARCHAR(32) NOT NULL COMMENT '渠道编码，唯一',
  `name` VARCHAR(128) NOT NULL COMMENT '渠道名称',
  `parent_id` BIGINT DEFAULT NULL COMMENT '上级渠道ID',
  `level_code` VARCHAR(32) NOT NULL COMMENT '渠道等级，如L1/L2/L3',
  `owner_user_id` BIGINT DEFAULT NULL COMMENT '平台负责人用户ID，关联medical_user_info.id',
  `contact_name` VARCHAR(64) DEFAULT NULL COMMENT '联系人',
  `contact_phone` VARCHAR(32) DEFAULT NULL COMMENT '联系电话',
  `province` VARCHAR(32) DEFAULT NULL COMMENT '省',
  `city` VARCHAR(32) DEFAULT NULL COMMENT '市',
  `settlement_type` VARCHAR(32) DEFAULT NULL COMMENT '结算方式，bank_transfer/monthly等',
  `contract_status` VARCHAR(32) NOT NULL DEFAULT 'pending' COMMENT '合同状态：pending/active/expired/terminated',
  `qualification_status` VARCHAR(32) NOT NULL DEFAULT 'pending' COMMENT '资质状态：pending/approved/rejected/expired',
  `product_lines_json` JSON DEFAULT NULL COMMENT '可售产品线JSON数组，如["gene","proton"]',
  `status` VARCHAR(32) NOT NULL DEFAULT 'pending' COMMENT '状态：pending/reviewing/active/frozen/disabled/rejected',
  `remark` VARCHAR(500) DEFAULT NULL COMMENT '备注',
  `created_by` BIGINT DEFAULT NULL COMMENT '创建人user_id',
  `updated_by` BIGINT DEFAULT NULL COMMENT '更新人user_id',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `modify_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-否，1-是',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_distribution_distributor_code` (`code`),
  KEY `idx_distribution_distributor_parent_id` (`parent_id`),
  KEY `idx_distribution_distributor_owner_user_id` (`owner_user_id`),
  KEY `idx_distribution_distributor_level_code` (`level_code`),
  KEY `idx_distribution_distributor_status` (`status`),
  KEY `idx_distribution_distributor_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='分销渠道主表';


-- ----------------------------
-- 2. 渠道成员表
-- ----------------------------
CREATE TABLE IF NOT EXISTS `distribution_distributor_member` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `distributor_id` BIGINT NOT NULL COMMENT '所属渠道ID',
  `user_id` BIGINT DEFAULT NULL COMMENT '系统用户ID，关联medical_user_info.id',
  `name` VARCHAR(64) NOT NULL COMMENT '成员姓名',
  `phone` VARCHAR(32) NOT NULL COMMENT '成员手机号',
  `role_code` VARCHAR(32) NOT NULL COMMENT '分销角色：DIST_SUPER_ADMIN/DIST_OPERATOR/DIST_SALES/DIST_FINANCE/DIST_COMPLIANCE/DIST_ANALYST',
  `data_scope` VARCHAR(32) NOT NULL DEFAULT 'SELF' COMMENT '数据范围：ALL/OWN_DISTRIBUTOR/OWN_AND_CHILDREN/SELF',
  `manager_member_id` BIGINT DEFAULT NULL COMMENT '直属上级成员ID',
  `product_lines_json` JSON DEFAULT NULL COMMENT '可售产品线JSON数组',
  `training_status` VARCHAR(32) NOT NULL DEFAULT 'pending' COMMENT '培训状态：pending/completed/expired',
  `status` VARCHAR(32) NOT NULL DEFAULT 'active' COMMENT '状态：active/disabled/frozen',
  `last_active_at` DATETIME DEFAULT NULL COMMENT '最近活跃时间',
  `remark` VARCHAR(500) DEFAULT NULL COMMENT '备注',
  `created_by` BIGINT DEFAULT NULL COMMENT '创建人user_id',
  `updated_by` BIGINT DEFAULT NULL COMMENT '更新人user_id',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `modify_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-否，1-是',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_distribution_distributor_member_phone_distributor` (`distributor_id`, `phone`, `deleted`),
  KEY `idx_distribution_member_distributor_id` (`distributor_id`),
  KEY `idx_distribution_member_user_id` (`user_id`),
  KEY `idx_distribution_member_manager_member_id` (`manager_member_id`),
  KEY `idx_distribution_member_role_code` (`role_code`),
  KEY `idx_distribution_member_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='分销渠道成员表';


-- ----------------------------
-- 3. 分销线索表
-- ----------------------------
CREATE TABLE IF NOT EXISTS `distribution_lead` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `lead_no` VARCHAR(32) NOT NULL COMMENT '线索编号，唯一',
  `patient_name` VARCHAR(64) NOT NULL COMMENT '患者姓名',
  `patient_phone` VARCHAR(32) NOT NULL COMMENT '患者手机号',
  `source_distributor_id` BIGINT NOT NULL COMMENT '来源渠道ID',
  `source_member_id` BIGINT DEFAULT NULL COMMENT '推荐成员ID',
  `intent_product_line` VARCHAR(32) NOT NULL COMMENT '意向产品线：gene/proton/organoid',
  `source_region` VARCHAR(64) DEFAULT NULL COMMENT '来源地区',
  `source_channel` VARCHAR(64) DEFAULT NULL COMMENT '来源子渠道',
  `owner_user_id` BIGINT DEFAULT NULL COMMENT '当前负责人user_id',
  `stage` VARCHAR(32) NOT NULL DEFAULT 'pending_review' COMMENT '线索阶段：pending_review/invalid/contacted/interested/scheduled/visited/signed/converted/closed',
  `is_duplicate` TINYINT NOT NULL DEFAULT 0 COMMENT '是否疑似重复：0-否，1-是',
  `duplicate_lead_id` BIGINT DEFAULT NULL COMMENT '重复线索ID',
  `invalid_reason` VARCHAR(255) DEFAULT NULL COMMENT '无效原因',
  `remark` VARCHAR(500) DEFAULT NULL COMMENT '备注',
  `latest_follow_up_at` DATETIME DEFAULT NULL COMMENT '最近跟进时间',
  `created_by` BIGINT DEFAULT NULL COMMENT '创建人user_id',
  `updated_by` BIGINT DEFAULT NULL COMMENT '更新人user_id',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `modify_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-否，1-是',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_distribution_lead_no` (`lead_no`),
  KEY `idx_distribution_lead_patient_phone` (`patient_phone`),
  KEY `idx_distribution_lead_source_distributor_id` (`source_distributor_id`),
  KEY `idx_distribution_lead_source_member_id` (`source_member_id`),
  KEY `idx_distribution_lead_owner_user_id` (`owner_user_id`),
  KEY `idx_distribution_lead_stage` (`stage`),
  KEY `idx_distribution_lead_intent_product_line` (`intent_product_line`),
  KEY `idx_distribution_lead_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='分销线索表';


-- ----------------------------
-- 4. 分销线索跟进表
-- ----------------------------
CREATE TABLE IF NOT EXISTS `distribution_lead_follow_up` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `lead_id` BIGINT NOT NULL COMMENT '线索ID',
  `follow_up_type` VARCHAR(32) NOT NULL COMMENT '跟进类型：call/wechat/visit/remark/system',
  `content` TEXT NOT NULL COMMENT '跟进内容',
  `next_action_at` DATETIME DEFAULT NULL COMMENT '下次跟进时间',
  `created_by` BIGINT DEFAULT NULL COMMENT '创建人user_id',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_distribution_lead_follow_up_lead_id` (`lead_id`),
  KEY `idx_distribution_lead_follow_up_created_by` (`created_by`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='分销线索跟进表';


-- ----------------------------
-- 5. 分销审计日志表
-- ----------------------------
CREATE TABLE IF NOT EXISTS `distribution_audit_log` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `biz_type` VARCHAR(32) NOT NULL COMMENT '业务类型：distributor/member/policy/lead/attribution/business_order/commission/settlement/compliance',
  `biz_id` BIGINT NOT NULL COMMENT '业务ID',
  `action` VARCHAR(64) NOT NULL COMMENT '动作：create/update/assign/freeze/lock/approve/reject/reverse/pay等',
  `before_snapshot` JSON DEFAULT NULL COMMENT '变更前快照',
  `after_snapshot` JSON DEFAULT NULL COMMENT '变更后快照',
  `operator_user_id` BIGINT DEFAULT NULL COMMENT '操作人user_id，来自手机号映射',
  `operator_name` VARCHAR(64) DEFAULT NULL COMMENT '操作人名称',
  `remark` VARCHAR(500) DEFAULT NULL COMMENT '备注',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_distribution_audit_log_biz` (`biz_type`, `biz_id`),
  KEY `idx_distribution_audit_log_action` (`action`),
  KEY `idx_distribution_audit_log_operator_user_id` (`operator_user_id`),
  KEY `idx_distribution_audit_log_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='分销审计日志表';


-- ----------------------------
-- 6. 合规记录表
-- ----------------------------
CREATE TABLE IF NOT EXISTS `distribution_compliance_record` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `biz_type` VARCHAR(32) NOT NULL COMMENT '业务类型：distributor/member/policy/lead',
  `biz_id` BIGINT NOT NULL COMMENT '业务ID',
  `product_line_code` VARCHAR(32) DEFAULT NULL COMMENT '产品线',
  `record_type` VARCHAR(32) NOT NULL COMMENT '记录类型：qualification/training/authorization/violation/approval',
  `status` VARCHAR(32) NOT NULL DEFAULT 'pending' COMMENT '状态：pending/approved/rejected/expired',
  `content` TEXT DEFAULT NULL COMMENT '内容说明',
  `attachments_json` JSON DEFAULT NULL COMMENT '附件JSON数组',
  `reviewed_by` BIGINT DEFAULT NULL COMMENT '审核人user_id',
  `reviewed_at` DATETIME DEFAULT NULL COMMENT '审核时间',
  `remark` VARCHAR(500) DEFAULT NULL COMMENT '备注',
  `created_by` BIGINT DEFAULT NULL COMMENT '创建人user_id',
  `updated_by` BIGINT DEFAULT NULL COMMENT '更新人user_id',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `modify_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-否，1-是',
  PRIMARY KEY (`id`),
  KEY `idx_distribution_compliance_record_biz` (`biz_type`, `biz_id`),
  KEY `idx_distribution_compliance_record_record_type` (`record_type`),
  KEY `idx_distribution_compliance_record_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='分销合规记录表';
