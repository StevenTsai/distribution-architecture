package com.distribution.casestudy.guardrails.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * StartupGuard 的拒绝矩阵——每条生产红线一个可执行断言。
 *
 * <p>护栏的价值全部在「拒绝路径」上：通过路径人人都会写，
 * 只有把每条红线钉成测试，后续重构才不可能悄悄放开任何一条。</p>
 */
class StartupGuardTest {

    private GuardProperties props;
    private MockEnvironment env;

    @BeforeEach
    void setUp() {
        props = new GuardProperties();
        env = new MockEnvironment();
        /* 生产档全量配置的默认态：各用例只破坏自己关注的一项 */
        props.getStorage().setBaseUrl("https://cdn.example.com");
    }

    private void runCheck() {
        new StartupGuard(props, env).check();
    }

    private void asProd() {
        env.setActiveProfiles("prod");
    }

    private void asDev() {
        env.setActiveProfiles("dev");
    }

    @Nested
    @DisplayName("存储档位枚举")
    class StoreEnum {

        @Test
        @DisplayName("非法档位：任何环境都拒绝启动")
        void unknownStoreRejectedEverywhere() {
            props.getState().setStore("redus"); // 拼写错误
            IllegalStateException e = assertThrows(IllegalStateException.class, this::runCheckOrParent);
            assertTrue(e.getMessage().contains("app.state.store"));
            assertTrue(e.getMessage().contains("redus"));
        }

        private void runCheckOrParent() {
            StartupGuardTest.this.runCheck();
        }

        @Test
        @DisplayName("合法档位：mysql / redis / memory 均放行")
        void allowedStores() {
            asProd();
            props.getJwt().setSecret("a-real-production-secret-value");
            for (String store : new String[]{"mysql", "redis", "memory"}) {
                props.getState().setStore(store);
                assertDoesNotThrow(this::runCheckOrParent, "档位 " + store + " 应放行");
            }
        }
    }

    @Nested
    @DisplayName("JWT 占位密钥")
    class JwtPlaceholder {

        @Test
        @DisplayName("dev + 占位密钥：警告放行")
        void devPlaceholderAllowed() {
            asDev();
            /* 默认 secret 即 change-me 占位值 */
            assertDoesNotThrow(StartupGuardTest.this::runCheck);
        }

        @Test
        @DisplayName("prod + 占位密钥：拒绝，消息点名后果")
        void prodPlaceholderRejected() {
            asProd();
            IllegalStateException e = assertThrows(IllegalStateException.class,
                    StartupGuardTest.this::runCheck);
            assertTrue(e.getMessage().contains("app.jwt.secret"));
            assertTrue(e.getMessage().contains("伪造"), "拒绝信息必须写明后果");
        }

        @Test
        @DisplayName("prod + 空密钥：拒绝")
        void prodBlankRejected() {
            asProd();
            props.getJwt().setSecret("");
            assertThrows(IllegalStateException.class, StartupGuardTest.this::runCheck);
        }

        @Test
        @DisplayName("prod + 大小写变体占位密钥（Change-Me）：同样拒绝")
        void prodMixedCasePlaceholderRejected() {
            asProd();
            props.getJwt().setSecret("Change-Me-Development-Only");
            IllegalStateException e = assertThrows(IllegalStateException.class,
                    StartupGuardTest.this::runCheck);
            assertTrue(e.getMessage().contains("app.jwt.secret"), "大小写变体不得绕过占位密钥红线");
        }
    }

    @Nested
    @DisplayName("短信 debug 开关")
    class SmsDebug {

        @Test
        @DisplayName("prod + debug=true：拒绝")
        void prodDebugRejected() {
            asProd();
            props.getJwt().setSecret("a-real-production-secret-value");
            props.getSms().setDebug(true);
            IllegalStateException e = assertThrows(IllegalStateException.class,
                    StartupGuardTest.this::runCheck);
            assertTrue(e.getMessage().contains("app.sms.debug"));
        }

        @Test
        @DisplayName("dev + debug=true：放行")
        void devDebugAllowed() {
            asDev();
            props.getSms().setDebug(true);
            assertDoesNotThrow(StartupGuardTest.this::runCheck);
        }
    }

    @Nested
    @DisplayName("生产必备配置")
    class MissingRequired {

        @Test
        @DisplayName("prod + 存储基址缺失：拒绝并列进缺失清单")
        void prodMissingBaseUrlRejected() {
            asProd();
            props.getJwt().setSecret("a-real-production-secret-value");
            props.getStorage().setBaseUrl("");
            IllegalStateException e = assertThrows(IllegalStateException.class,
                    StartupGuardTest.this::runCheck);
            assertTrue(e.getMessage().contains("app.storage.base-url"));
            assertTrue(e.getMessage().contains("Host"), "缺失项须写明后果");
        }

        @Test
        @DisplayName("prod + 全部就绪：放行")
        void prodAllConfiguredAllowed() {
            asProd();
            props.getJwt().setSecret("a-real-production-secret-value");
            assertDoesNotThrow(StartupGuardTest.this::runCheck);
        }
    }

    @Nested
    @DisplayName("混合 profile（生产档不可被 dev/test 旁路）")
    class MixedProfiles {

        @Test
        @DisplayName("prod,test + 占位密钥：仍拒绝（严格模式，不豁免）")
        void prodTestWithPlaceholderRejected() {
            env.setActiveProfiles("prod", "test");
            IllegalStateException e = assertThrows(IllegalStateException.class,
                    StartupGuardTest.this::runCheck);
            assertTrue(e.getMessage().contains("app.jwt.secret"));
        }

        @Test
        @DisplayName("prod,test + debug=true：仍拒绝")
        void prodTestWithSmsDebugRejected() {
            env.setActiveProfiles("prod", "test");
            props.getJwt().setSecret("a-real-production-secret-value");
            props.getSms().setDebug(true);
            IllegalStateException e = assertThrows(IllegalStateException.class,
                    StartupGuardTest.this::runCheck);
            assertTrue(e.getMessage().contains("app.sms.debug"));
        }

        @Test
        @DisplayName("prod,test + 存储基址缺失：仍拒绝")
        void prodTestWithMissingBaseUrlRejected() {
            env.setActiveProfiles("prod", "test");
            props.getJwt().setSecret("a-real-production-secret-value");
            props.getStorage().setBaseUrl("");
            IllegalStateException e = assertThrows(IllegalStateException.class,
                    StartupGuardTest.this::runCheck);
            assertTrue(e.getMessage().contains("app.storage.base-url"));
        }

        @Test
        @DisplayName("prod,test + 全部就绪：放行（严格模式检查通过，而非跳过）")
        void prodTestAllConfiguredAllowed() {
            env.setActiveProfiles("prod", "test");
            props.getJwt().setSecret("a-real-production-secret-value");
            assertDoesNotThrow(StartupGuardTest.this::runCheck);
        }

        @Test
        @DisplayName("production 别名：同样触发严格模式")
        void productionAliasEnforced() {
            env.setActiveProfiles("production");
            IllegalStateException e = assertThrows(IllegalStateException.class,
                    StartupGuardTest.this::runCheck);
            assertTrue(e.getMessage().contains("app.jwt.secret"));
        }

        @Test
        @DisplayName("dev,test（无生产档）：维持豁免")
        void devTestExempted() {
            env.setActiveProfiles("dev", "test");
            assertDoesNotThrow(StartupGuardTest.this::runCheck);
        }
    }
}
