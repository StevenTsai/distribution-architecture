-- H2 Schema for distribution-starter tests
-- 兼容 MySQL 模式的 H2 建表语句

CREATE SCHEMA IF NOT EXISTS distribution_starter_test;
SET SCHEMA distribution_starter_test;

-- 共享表
CREATE TABLE IF NOT EXISTS medical_user_info (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  openid VARCHAR(64),
  name VARCHAR(64),
  avatar VARCHAR(512),
  gender VARCHAR(8),
  country VARCHAR(32),
  province VARCHAR(32),
  city VARCHAR(32),
  language VARCHAR(32),
  create_time TIMESTAMP,
  modify_time TIMESTAMP,
  phoneNumber VARCHAR(32),
  role TINYINT
);

CREATE TABLE IF NOT EXISTS admin_user (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  username VARCHAR(50) NOT NULL,
  password VARCHAR(100) NOT NULL,
  user_id BIGINT,
  status TINYINT NOT NULL,
  create_time TIMESTAMP,
  modify_time TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_admin_user_username ON admin_user(username);

CREATE TABLE IF NOT EXISTS user_login_session (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  openid VARCHAR(64) NOT NULL,
  skey VARCHAR(64) NOT NULL,
  biz VARCHAR(64) NOT NULL,
  login_source VARCHAR(32),
  create_time TIMESTAMP,
  expire_time TIMESTAMP NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_user_login_session_skey_biz ON user_login_session(skey, biz);

-- 分销渠道主表
CREATE TABLE IF NOT EXISTS distribution_distributor (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  code VARCHAR(32) NOT NULL,
  name VARCHAR(128) NOT NULL,
  parent_id BIGINT,
  level_code VARCHAR(32) NOT NULL,
  owner_user_id BIGINT,
  contact_name VARCHAR(64),
  contact_phone VARCHAR(32),
  province VARCHAR(32),
  city VARCHAR(32),
  settlement_type VARCHAR(32),
  contract_status VARCHAR(32) NOT NULL DEFAULT 'pending',
  qualification_status VARCHAR(32) NOT NULL DEFAULT 'pending',
  product_lines_json CLOB,
  status VARCHAR(32) NOT NULL DEFAULT 'pending',
  remark VARCHAR(500),
  created_by BIGINT,
  updated_by BIGINT,
  create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  modify_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_distributor_code ON distribution_distributor(code);
CREATE INDEX IF NOT EXISTS idx_distributor_parent_id ON distribution_distributor(parent_id);
CREATE INDEX IF NOT EXISTS idx_distributor_status ON distribution_distributor(status);

-- 分销渠道成员表
CREATE TABLE IF NOT EXISTS distribution_distributor_member (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  distributor_id BIGINT NOT NULL,
  user_id BIGINT,
  name VARCHAR(64) NOT NULL,
  phone VARCHAR(32) NOT NULL,
  role_code VARCHAR(32) NOT NULL,
  data_scope VARCHAR(32) NOT NULL DEFAULT 'SELF',
  manager_member_id BIGINT,
  product_lines_json CLOB,
  training_status VARCHAR(32) NOT NULL DEFAULT 'pending',
  status VARCHAR(32) NOT NULL DEFAULT 'active',
  last_active_at TIMESTAMP,
  remark VARCHAR(500),
  created_by BIGINT,
  updated_by BIGINT,
  create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  modify_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_member_distributor_id ON distribution_distributor_member(distributor_id);
CREATE INDEX IF NOT EXISTS idx_member_user_id ON distribution_distributor_member(user_id);
CREATE INDEX IF NOT EXISTS idx_member_role_code ON distribution_distributor_member(role_code);
CREATE INDEX IF NOT EXISTS idx_member_status ON distribution_distributor_member(status);

-- 分销线索表
CREATE TABLE IF NOT EXISTS distribution_lead (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  lead_no VARCHAR(32) NOT NULL,
  patient_name VARCHAR(64) NOT NULL,
  patient_phone VARCHAR(32) NOT NULL,
  source_distributor_id BIGINT NOT NULL,
  source_member_id BIGINT,
  intent_product_line VARCHAR(32) NOT NULL,
  source_region VARCHAR(64),
  source_channel VARCHAR(64),
  owner_user_id BIGINT,
  stage VARCHAR(32) NOT NULL DEFAULT 'pending_review',
  is_duplicate TINYINT NOT NULL DEFAULT 0,
  duplicate_lead_id BIGINT,
  invalid_reason VARCHAR(255),
  remark VARCHAR(500),
  latest_follow_up_at TIMESTAMP,
  created_by BIGINT,
  updated_by BIGINT,
  create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  modify_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_lead_no ON distribution_lead(lead_no);
CREATE INDEX IF NOT EXISTS idx_lead_patient_phone ON distribution_lead(patient_phone);
CREATE INDEX IF NOT EXISTS idx_lead_source_distributor_id ON distribution_lead(source_distributor_id);
CREATE INDEX IF NOT EXISTS idx_lead_owner_user_id ON distribution_lead(owner_user_id);
CREATE INDEX IF NOT EXISTS idx_lead_stage ON distribution_lead(stage);

-- 分销线索跟进表
CREATE TABLE IF NOT EXISTS distribution_lead_follow_up (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  lead_id BIGINT NOT NULL,
  follow_up_type VARCHAR(32) NOT NULL,
  content CLOB NOT NULL,
  next_action_at TIMESTAMP,
  created_by BIGINT,
  create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_follow_up_lead_id ON distribution_lead_follow_up(lead_id);

-- 分销审计日志表
CREATE TABLE IF NOT EXISTS distribution_audit_log (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  biz_type VARCHAR(32) NOT NULL,
  biz_id BIGINT NOT NULL,
  action VARCHAR(64) NOT NULL,
  before_snapshot CLOB,
  after_snapshot CLOB,
  operator_user_id BIGINT,
  operator_name VARCHAR(64),
  remark VARCHAR(500),
  create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_audit_log_biz ON distribution_audit_log(biz_type, biz_id);
CREATE INDEX IF NOT EXISTS idx_audit_log_action ON distribution_audit_log(action);

-- 合规记录表
CREATE TABLE IF NOT EXISTS distribution_compliance_record (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  biz_type VARCHAR(32) NOT NULL,
  biz_id BIGINT NOT NULL,
  product_line_code VARCHAR(32),
  record_type VARCHAR(32) NOT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'pending',
  content CLOB,
  attachments_json CLOB,
  reviewed_by BIGINT,
  reviewed_at TIMESTAMP,
  remark VARCHAR(500),
  created_by BIGINT,
  updated_by BIGINT,
  create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  modify_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_compliance_record_biz ON distribution_compliance_record(biz_type, biz_id);
CREATE INDEX IF NOT EXISTS idx_compliance_record_status ON distribution_compliance_record(status);
