-- H2 Seed Data for distribution-starter tests

-- 管理员用户（测试环境专用密码）
INSERT INTO admin_user (id, username, password, status, user_id) VALUES (1, 'admin', 'test_password_only', 1, 1);

-- 业务用户
INSERT INTO medical_user_info (id, openid, name, role) VALUES (1, '1', '系统管理员', 1);
INSERT INTO medical_user_info (id, openid, name, role) VALUES (2, '2', '渠道经理张三', 1);
INSERT INTO medical_user_info (id, openid, name, role) VALUES (3, '3', '销售李四', 1);

-- 登录会话（测试专用 token）
INSERT INTO user_login_session (id, openid, skey, biz, login_source, expire_time) VALUES
(1, '1', 'test-token-admin', 'distribution-starter', 'MANAGE', '2030-12-31 23:59:59');

-- 渠道数据
INSERT INTO distribution_distributor (id, code, name, parent_id, level_code, owner_user_id, contact_name, contact_phone, status) VALUES
(1, 'DIST-001', '华东区域总代理', NULL, 'L1', 2, '张三', '13800000001', 'active'),
(2, 'DIST-002', '上海分公司', 1, 'L2', 2, '王五', '13800000002', 'active'),
(3, 'DIST-003', '杭州分公司', 1, 'L2', 2, '赵六', '13800000003', 'active');

-- 渠道成员
INSERT INTO distribution_distributor_member (id, distributor_id, user_id, name, phone, role_code, data_scope, status) VALUES
(1, 1, 2, '张三', '13800000001', 'DIST_SUPER_ADMIN', 'OWN_AND_CHILDREN', 'active'),
(2, 2, 3, '李四', '13800000004', 'DIST_SALES', 'SELF', 'active'),
(3, 1, 1, '系统管理员', '13800000000', 'DIST_SUPER_ADMIN', 'ALL', 'active');

-- 线索数据
INSERT INTO distribution_lead (id, lead_no, patient_name, patient_phone, source_distributor_id, source_member_id, intent_product_line, stage, owner_user_id) VALUES
(1, 'LEAD-20260101-001', '患者甲', '13900000001', 2, 2, 'gene', 'contacted', 3),
(2, 'LEAD-20260101-002', '患者乙', '13900000002', 2, 2, 'proton', 'interested', 3),
(3, 'LEAD-20260102-001', '患者丙', '13900000003', 3, NULL, 'gene', 'pending_review', NULL);

-- 线索跟进记录
INSERT INTO distribution_lead_follow_up (id, lead_id, follow_up_type, content, created_by) VALUES
(1, 1, 'call', '首次电话联系，了解患者需求', 3),
(2, 1, 'wechat', '微信发送产品资料', 3),
(3, 2, 'visit', '上门拜访，详细介绍产品方案', 3);

-- 审计日志
INSERT INTO distribution_audit_log (id, biz_type, biz_id, action, before_snapshot, after_snapshot, operator_user_id, operator_name) VALUES
(1, 'lead', 1, 'create', NULL, '{"id":1,"leadNo":"LEAD-20260101-001","stage":"pending_review"}', 3, '李四'),
(2, 'lead', 1, 'update_stage', '{"stage":"pending_review"}', '{"stage":"contacted"}', 3, '李四');

-- 合规记录
INSERT INTO distribution_compliance_record (id, biz_type, biz_id, record_type, status, content, created_by) VALUES
(1, 'distributor', 1, 'qualification', 'approved', '资质审核通过', 1),
(2, 'member', 2, 'training', 'pending', '待完成产品培训', 1);
