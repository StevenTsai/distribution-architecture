package com.distribution.casestudy.guardrails.state;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import java.nio.charset.StandardCharsets;

/**
 * MySQL 实现的契约执行——H2 以 {@code MODE=MySQL} 承载原子 SQL
 * （INSERT IGNORE / ON DUPLICATE KEY UPDATE）。
 *
 * <p>SingleConnectionDataSource 保证 DDL 与用例同一连接可见；
 * 每个用例前清表，用例间互不污染。真实 MySQL 的方言差异（若有）
 * 不在本案例覆盖面内——契约测试锁的是<b>语义</b>，不是某个版本的方言。</p>
 */
class MysqlStateStoreContractTest extends StateStoreContractTest {

    private static SingleConnectionDataSource dataSource;
    private static JdbcTemplate jdbc;

    @BeforeAll
    static void initSchema() throws Exception {
        dataSource = new SingleConnectionDataSource(
                "jdbc:h2:mem:guardrails;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
                "sa", "", true);
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
            dataSource.destroy();
        }
    }

    @Override
    protected StateStore createStore() {
        jdbc.execute("DELETE FROM app_state");
        return new MysqlStateStore(jdbc, JSON);
    }
}
