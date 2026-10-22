-- ============================================================================
-- app_state：短生命周期状态表（验证码 / 令牌吊销黑名单 / 握手材料）
--
-- state_value 用 VARCHAR(2048) 而非 TEXT：increment 的 CAST(state_value AS BIGINT)
-- 需要字符串类型承载；契约测试在 H2(MODE=MySQL) 与 MySQL 双方言下同一 DDL 可跑。
-- 生产中若有更大 JSON 值，可换 TEXT 并把计数键收敛到独立表。
-- 过期语义：读路径按「应用时钟参数」过滤（单一时钟域），
--          物理清理交给后台任务（本案例未包含，见 README「Not Included」）。
-- ============================================================================
CREATE TABLE IF NOT EXISTS app_state (
  state_key   VARCHAR(128)  NOT NULL,
  state_value VARCHAR(2048) NOT NULL,
  expire_time BIGINT        NOT NULL,
  update_time BIGINT        NOT NULL DEFAULT 0,
  PRIMARY KEY (state_key)
);

CREATE INDEX IF NOT EXISTS idx_app_state_expire_time ON app_state (expire_time);
