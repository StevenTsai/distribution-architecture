package com.distribution.casestudy.guardrails.state;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 条件装配测试：档位 → 实现的映射是配置语义的一部分，必须被锁定。
 *
 * <p>特别覆盖「非法档位 → 零实现」：静默落到某个默认实现是最危险的失败模式
 * （拼写错误被吞掉，运行期才在第一次读状态时爆炸）——
 * 这里断言它在装配期就是空的，由 StartupGuard 在启动期拒绝。</p>
 */
class StateStoreWiringTest {

    @Configuration
    static class WiringFixture {
        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }

        /* 匿名子类跳过 InitializingBean 校验：装配测试只验证条件注解选中了哪个实现，
           既不触库也不连 Redis；no-arg 构造本身会被 afterPropertiesSet 拒绝 */
        @Bean
        JdbcTemplate jdbcTemplate() {
            return new JdbcTemplate() {
                @Override
                public void afterPropertiesSet() {
                    /* skip DataSource validation */
                }
            };
        }

        @Bean
        StringRedisTemplate stringRedisTemplate() {
            return new StringRedisTemplate() {
                @Override
                public void afterPropertiesSet() {
                    /* skip connection-factory validation */
                }
            };
        }
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(WiringFixture.class, StateStoreConfig.class);

    @Test
    @DisplayName("未配置 → MySQL 默认档（matchIfMissing）")
    void defaultsToMysql() {
        runner.run(ctx -> assertThat(ctx.getBean(StateStore.class))
                .isInstanceOf(MysqlStateStore.class));
    }

    @Test
    @DisplayName("app.state.store=mysql → MysqlStateStore")
    void mysqlSelected() {
        runner.withPropertyValues("app.state.store=mysql")
                .run(ctx -> assertThat(ctx.getBean(StateStore.class))
                        .isInstanceOf(MysqlStateStore.class));
    }

    @Test
    @DisplayName("app.state.store=redis → RedisStateStore（回切零代码）")
    void redisSelected() {
        runner.withPropertyValues("app.state.store=redis")
                .run(ctx -> assertThat(ctx.getBean(StateStore.class))
                        .isInstanceOf(RedisStateStore.class));
    }

    @Test
    @DisplayName("app.state.store=memory → InMemoryStateStore")
    void memorySelected() {
        runner.withPropertyValues("app.state.store=memory")
                .run(ctx -> assertThat(ctx.getBean(StateStore.class))
                        .isInstanceOf(InMemoryStateStore.class));
    }

    @Test
    @DisplayName("非法档位 → 零 StateStore bean（不静默回落）")
    void unknownStoreYieldsNothing() {
        runner.withPropertyValues("app.state.store=redus")
                .run(ctx -> assertThat(ctx.getBeansOfType(StateStore.class)).isEmpty());
    }
}
