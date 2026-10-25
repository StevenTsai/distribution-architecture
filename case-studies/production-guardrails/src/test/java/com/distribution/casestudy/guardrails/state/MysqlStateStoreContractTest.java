package com.distribution.casestudy.guardrails.state;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.charset.StandardCharsets;

/**
 * MySQL 实现的契约执行——H2 以 {@code MODE=MySQL} 承载原子 SQL
 * （INSERT IGNORE / ON DUPLICATE KEY UPDATE / LAST_INSERT_ID）。
 *
 * <p>多连接池（HikariCP）承载：并发用例的每个线程取得<b>独立连接</b>，
 * 跨连接的行锁与会话级 LAST_INSERT_ID 语义才真正被测到——早期版本用
 * SingleConnectionDataSource，四个「并发」线程实为同一连接串行，是假并发。
 * 命名内存库 + {@code DB_CLOSE_DELAY=-1} 保证 DDL 与所有连接共享同一数据库；
 * 每个用例前清表，用例间互不污染。真实 MySQL 的方言差异（若有）
 * 不在本案例覆盖面内——契约测试锁的是<b>语义</b>，不是某个版本的方言。</p>
 */
class MysqlStateStoreContractTest extends StateStoreContractTest {

    private static HikariDataSource dataSource;
    private static JdbcTemplate jdbc;

    @BeforeAll
    static void initSchema() throws Exception {
        HikariDataSource pool = new HikariDataSource();
        pool.setJdbcUrl("jdbc:h2:mem:guardrails;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        pool.setUsername("sa");
        pool.setPassword("");
        pool.setMaximumPoolSize(8);
        dataSource = pool;
        jdbc = new JdbcTemplate(dataSource);
        String ddl = new String(
                MysqlStateStoreContractTest.class.getResourceAsStream("/app-state-schema.sql").readAllBytes(),
                StandardCharsets.UTF_8);
        for (String stmt : ddl.split(";")) {
            if (!stmt.isBlank()) {
                jdbc.execute(stmt);
            }
        }
    }

    @AfterAll
    static void shutdown() {
        if (dataSource != null) {
            dataSource.close();
        }
    }

    @Override
    protected StateStore createStore() {
        jdbc.execute("DELETE FROM app_state");
        return new MysqlStateStore(jdbc, JSON);
    }
}
