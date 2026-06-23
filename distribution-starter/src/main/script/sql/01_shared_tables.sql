-- ============================================================
-- 01_shared_tables.sql
-- 共享表：分销模块依赖的公共表结构
-- ============================================================

-- 共享表：用户信息
CREATE TABLE IF NOT EXISTS `medical_user_info` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `openid` varchar(64) DEFAULT NULL,
  `name` varchar(64) DEFAULT NULL,
  `avatar` varchar(256) DEFAULT NULL,
  `gender` char(1) DEFAULT NULL,
  `country` varchar(32) DEFAULT NULL,
  `province` varchar(32) DEFAULT NULL,
  `city` varchar(32) DEFAULT NULL,
  `language` varchar(16) DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `modify_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `phoneNumber` varchar(20) DEFAULT NULL,
  `role` tinyint(4) DEFAULT '0' COMMENT '角色: 0-普通用户, 1-管理员',
  PRIMARY KEY (`id`),
  KEY `idx_openid` (`openid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 共享表：管理员用户
CREATE TABLE IF NOT EXISTS `admin_user` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `username` varchar(64) NOT NULL,
  `password` varchar(128) NOT NULL,
  `status` tinyint(4) DEFAULT '1' COMMENT '状态: 0-禁用, 1-启用',
  `user_id` bigint(20) DEFAULT NULL COMMENT '关联的medical_user_info.id',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `modify_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 共享表：登录会话
CREATE TABLE IF NOT EXISTS `user_login_session` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `openid` varchar(64) DEFAULT NULL,
  `skey` varchar(64) NOT NULL,
  `biz` varchar(32) NOT NULL,
  `login_source` varchar(32) DEFAULT NULL COMMENT '登录来源: MANAGE/PARTNER_PORTAL',
  `expire_time` datetime NOT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_skey_biz` (`skey`, `biz`),
  KEY `idx_openid_biz` (`openid`, `biz`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 初始管理员账号（密码为明文示例，生产环境请务必修改并使用加密存储）
INSERT INTO `admin_user` (`id`, `username`, `password`, `status`, `user_id`) VALUES
(1, 'admin', 'CHANGE_ME_BEFORE_USE', 1, 1);

INSERT INTO `medical_user_info` (`id`, `openid`, `name`, `role`) VALUES
(1, '1', '系统管理员', 1);
