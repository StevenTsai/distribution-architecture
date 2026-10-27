package com.distribution.casestudy.guardrails.state;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Duration;

/**
 * 状态存储 MySQL 实现（默认档）：app_state 单表，主键点查/点写。
 *
 * <p>原子性保持可见——每条语句可被指着问「并发安全写在哪」：
 * <ul>
 *   <li>setIfAbsent = 先清「逻辑过期但未物理删除」的旧行，再 INSERT IGNORE（主键冲突即被占用）；</li>
 *   <li>increment = 单语句 ON DUPLICATE KEY UPDATE 行锁自增，并用
 *       {@code LAST_INSERT_ID(expr)} 把新值登记到<b>会话</b>——随后同连接
 *       {@code SELECT LAST_INSERT_ID()} 读回的必然是本次自增值，
 *       不受并发事务在两次语句之间继续自增的影响（读回值不重不漏）。</li>
 * </ul>
 *
 * <p>时钟归属：now 由应用侧取值并作为参数下推（读过滤与写入同源），统一毫秒语义，
 * 与原生产版「SQL 内 UNIX_TIMESTAMP()」的差异及理由见 docs/from-production-to-generic.md。</p>
 */
public class MysqlStateStore implements StateStore {

    static final String UPSERT = """
            INSERT INTO app_state (state_key, state_value, expire_time, update_time)
            VALUES (?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE state_value = ?, expire_time = ?, update_time = ?""";

    static final String SELECT_LIVE = """
            SELECT state_value FROM app_state WHERE state_key = ? AND expire_time > ?""";

    static final String DELETE_IF_EXPIRED = """
            DELETE FROM app_state WHERE state_key = ? AND expire_time <= ?""";

    static final String INSERT_IF_ABSENT = """
            INSERT IGNORE INTO app_state (state_key, state_value, expire_time, update_time)
            VALUES (?, ?, ?, ?)""";

    static final String DELETE_BY_KEY = """
            DELETE FROM app_state WHERE state_key = ?""";

    static final String EXISTS_LIVE = """
            SELECT 1 FROM app_state WHERE state_key = ? AND expire_time > ? LIMIT 1""";

    static final String INCREMENT_UPSERT = """
            INSERT INTO app_state (state_key, state_value, expire_time, update_time)
            VALUES (?, LAST_INSERT_ID('1'), ?, ?)
            ON DUPLICATE KEY UPDATE
              state_value  = CASE WHEN expire_time <= ? THEN LAST_INSERT_ID('1')
                                  ELSE LAST_INSERT_ID(CAST(state_value AS BIGINT) + 1) END,
              expire_time  = CASE WHEN expire_time <= ? THEN ? ELSE expire_time END,
              update_time  = ?""";

    static final String SELECT_LAST_INSERT_ID = "SELECT LAST_INSERT_ID()";

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public MysqlStateStore(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Override
    public void set(String key, Object value, Duration ttl) {
        long now = nowMillis();
        long expireAt = now + ttl.toMillis();
        String raw = write(value);
        jdbc.update(UPSERT, key, raw, expireAt, now, raw, expireAt, now);
    }

    @Override
    public <T> T get(String key, Class<T> type) {
        return read(jdbc.query(SELECT_LIVE, rs -> rs.next() ? rs.getString(1) : null,
                key, nowMillis()), type, key);
    }

    @Override
    public boolean setIfAbsent(String key, Object value, Duration ttl) {
        long now = nowMillis();
        jdbc.update(DELETE_IF_EXPIRED, key, now);
        int affected = jdbc.update(INSERT_IF_ABSENT, key, write(value), now + ttl.toMillis(), now);
        return affected == 1;
    }

    @Override
    public void delete(String key) {
        jdbc.update(DELETE_BY_KEY, key);
    }

    @Override
    public boolean exists(String key) {
        Integer one = jdbc.query(EXISTS_LIVE, rs -> rs.next() ? rs.getInt(1) : null,
                key, nowMillis());
        return one != null;
    }

    @Override
    public long increment(String key, Duration ttlOnCreate) {
        long now = nowMillis();
        /* 同一连接内完成「校验 + 自增 + 读回」：LAST_INSERT_ID 是会话级状态，
           跨连接不可见；自增语句本身的行锁保证并发互斥，读回值不重不漏 */
        return jdbc.execute((ConnectionCallback<Long>) con -> {
            /* 契约：计数键不得包含非整数——活值先验，非法即拒绝（而非静默归零）。
               校验与自增之间存在极窄的 set() 竞争窗口，落入 CAST 分支时 MySQL 会
               宽松转换——教学版接受该残余，生产落地建议计数键收敛到独立表 */
            try (PreparedStatement live = con.prepareStatement(SELECT_LIVE)) {
                live.setString(1, key);
                live.setLong(2, now);
                try (ResultSet rs = live.executeQuery()) {
                    if (rs.next()) {
                        try {
                            Long.parseLong(rs.getString(1));
                        } catch (NumberFormatException e) {
                            throw new IllegalStateException("拒绝自增：计数键当前值不是整数，key=" + key, e);
                        }
                    }
                }
            }
            try (PreparedStatement upsert = con.prepareStatement(INCREMENT_UPSERT)) {
                long expireAt = now + ttlOnCreate.toMillis();
                upsert.setString(1, key);
                upsert.setLong(2, expireAt);
                upsert.setLong(3, now);
                upsert.setLong(4, now);       /* 过期判定（重置分支） */
                upsert.setLong(5, now);       /* 过期判定（TTL 分支） */
                upsert.setLong(6, expireAt);  /* 重置分支的新 TTL */
                upsert.setLong(7, now);       /* update_time */
                upsert.executeUpdate();
            }
            try (PreparedStatement select = con.prepareStatement(SELECT_LAST_INSERT_ID);
                 ResultSet rs = select.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        });
    }

    private String write(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("状态序列化失败", e);
        }
    }

    private <T> T read(String raw, Class<T> type, String key) {
        if (raw == null) {
            return null;
        }
        try {
            return json.readValue(raw, type);
        } catch (Exception e) {
            /* 宽容读：损坏值视同不存在并清除 */
            jdbc.update(DELETE_BY_KEY, key);
            return null;
        }
    }

    private static long nowMillis() {
        return System.currentTimeMillis();
    }
}
