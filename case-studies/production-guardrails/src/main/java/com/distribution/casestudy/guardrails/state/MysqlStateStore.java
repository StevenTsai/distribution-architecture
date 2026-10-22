package com.distribution.casestudy.guardrails.state;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;

/**
 * 状态存储 MySQL 实现（默认档）：app_state 单表，主键点查/点写。
 *
 * <p>原子性保持可见——每条语句可被指着问「并发安全写在哪」：
 * <ul>
 *   <li>setIfAbsent = 先清「逻辑过期但未物理删除」的旧行，再 INSERT IGNORE（主键冲突即被占用）；</li>
 *   <li>increment = 单语句 ON DUPLICATE KEY UPDATE 行锁自增，语义对齐 Redis INCR。</li>
 * </ul>
 *
 * <p>时钟归属：now 由应用侧取值并作为参数下推（读过滤与写入同源），
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
            VALUES (?, '1', ?, ?)
            ON DUPLICATE KEY UPDATE
              state_value  = CASE WHEN expire_time <= ? THEN '1' ELSE CAST(state_value AS BIGINT) + 1 END,
              expire_time  = CASE WHEN expire_time <= ? THEN ? ELSE expire_time END,
              update_time  = ?""";

    static final String SELECT_RAW = """
            SELECT state_value FROM app_state WHERE state_key = ?""";

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public MysqlStateStore(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Override
    public void set(String key, Object value, Duration ttl) {
        long now = nowSeconds();
        long expireAt = now + ttl.getSeconds();
        String raw = write(value);
        jdbc.update(UPSERT, key, raw, expireAt, now, raw, expireAt, now);
    }

    @Override
    public <T> T get(String key, Class<T> type) {
        return read(jdbc.query(SELECT_LIVE, rs -> rs.next() ? rs.getString(1) : null,
                key, nowSeconds()), type, key);
    }

    @Override
    public boolean setIfAbsent(String key, Object value, Duration ttl) {
        long now = nowSeconds();
        jdbc.update(DELETE_IF_EXPIRED, key, now);
        int affected = jdbc.update(INSERT_IF_ABSENT, key, write(value), now + ttl.getSeconds(), now);
        return affected == 1;
    }

    @Override
    public void delete(String key) {
        jdbc.update(DELETE_BY_KEY, key);
    }

    @Override
    public boolean exists(String key) {
        Integer one = jdbc.query(EXISTS_LIVE, rs -> rs.next() ? rs.getInt(1) : null,
                key, nowSeconds());
        return one != null;
    }

    @Override
    public long increment(String key, Duration ttlOnCreate) {
        long now = nowSeconds();
        jdbc.update(INCREMENT_UPSERT, key, now + ttlOnCreate.getSeconds(), now,
                now, now, now + ttlOnCreate.getSeconds(), now);
        String raw = jdbc.query(SELECT_RAW, rs -> rs.next() ? rs.getString(1) : null, key);
        if (raw == null) {
            /* 极端窗口（刚写入即被并发清理）：按首次创建语义返回 1 */
            return 1L;
        }
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException e) {
            return 1L;
        }
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

    private static long nowSeconds() {
        return System.currentTimeMillis() / 1000;
    }
}
