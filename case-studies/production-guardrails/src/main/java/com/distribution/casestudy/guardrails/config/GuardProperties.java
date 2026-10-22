package com.distribution.casestudy.guardrails.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 护栏校验涉及的配置面。前缀 {@code app.}——刻意中性，不携带任何业务域语义。
 */
@ConfigurationProperties(prefix = "app")
public class GuardProperties {

    private final State state = new State();
    private final Jwt jwt = new Jwt();
    private final Sms sms = new Sms();
    private final Storage storage = new Storage();

    public State getState() {
        return state;
    }

    public Jwt getJwt() {
        return jwt;
    }

    public Sms getSms() {
        return sms;
    }

    public Storage getStorage() {
        return storage;
    }

    public static class State {
        /** mysql（默认）| redis | memory；非法值由 StartupGuard 拒绝启动 */
        private String store = "mysql";

        public String getStore() {
            return store;
        }

        public void setStore(String store) {
            this.store = store;
        }
    }

    public static class Jwt {
        /** 占位前缀 change-me 开头视为未配置真实密钥 */
        private String secret = "change-me-development-only";

        public String getSecret() {
            return secret;
        }

        public void setSecret(String secret) {
            this.secret = secret;
        }
    }

    public static class Sms {
        /** true = 验证码仅打印日志不走真实通道；禁止在非 dev/test 开启 */
        private boolean debug = false;

        public boolean isDebug() {
            return debug;
        }

        public void setDebug(boolean debug) {
            this.debug = debug;
        }
    }

    public static class Storage {
        /** 固件/文件下载的对外基址；缺失时 URL 会回落请求 Host 头（Host 注入面） */
        private String baseUrl = "";

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }
    }
}
