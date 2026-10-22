package com.distribution.casestudy.guardrails.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * 生产 fail-fast 护栏：关键配置在非 dev/test 环境不满足时，<b>拒绝启动</b>而非带病运行。
 *
 * <p>设计要点：
 * <ul>
 *   <li>占位密钥在 dev/test 只警告——本地联调的便利优先；生产档一律拒绝；</li>
 *   <li>每条拒绝信息都写明<b>后果</b>（"否则会发生什么"），让运维日志自解释；</li>
 *   <li>检查项全部集中在 @PostConstruct 一次执行——启动失败 = 上下文装配失败，
 *       这个语义是护栏能成立的全部理由（见 ADR: Spring Boot 单轨）。</li>
 * </ul>
 */
@Component
public class StartupGuard {

    private static final Logger log = LoggerFactory.getLogger(StartupGuard.class);
    private static final String PLACEHOLDER_SECRET_PREFIX = "change-me";
    private static final Set<String> ALLOWED_STORES = Set.of("mysql", "redis", "memory");

    private final GuardProperties props;
    private final Environment env;

    public StartupGuard(GuardProperties props, Environment env) {
        this.props = props;
        this.env = env;
    }

    @PostConstruct
    public void check() {
        List<String> profiles = Arrays.asList(env.getActiveProfiles());
        boolean localProfile = profiles.contains("dev") || profiles.contains("test");

        /* 1. 档位枚举：拼写错误不会静默落到某个实现（条件注解无匹配 = 无 bean），
              启动期就把问题钉死，好过运行期在第一次读状态时 NPE */
        String store = props.getState().getStore();
        if (!ALLOWED_STORES.contains(store)) {
            throw new IllegalStateException("拒绝启动：app.state.store 仅支持 mysql|redis|memory，当前值=\"" + store
                    + "\"（拼写错误不会装配任何 StateStore 实现）");
        }

        /* 2. 占位密钥：dev/test 警告放行，其余环境拒绝 */
        String secret = props.getJwt().getSecret();
        if (isBlank(secret) || secret.startsWith(PLACEHOLDER_SECRET_PREFIX)) {
            if (!localProfile) {
                throw new IllegalStateException("拒绝启动：app.jwt.secret 仍为占位值，非 dev/test 环境必须配置真实密钥"
                        + "（否则任何人可伪造令牌；当前 profiles=" + profiles + "）");
            }
            log.warn("app.jwt.secret 为占位值：仅限本机联调，上线必须替换");
        }

        /* 3. 以下检查仅生产档 */
        if (localProfile) {
            return;
        }

        List<String> missing = new ArrayList<>();
        if (isBlank(props.getStorage().getBaseUrl())) {
            missing.add("app.storage.base-url（缺失时下载 URL 回落请求 Host 头，存在 Host 注入与明文降级风险）");
        }
        if (!missing.isEmpty()) {
            throw new IllegalStateException("拒绝启动：非 dev/test 环境缺少生产必备配置（当前 profiles=" + profiles + "）：\n  - "
                    + String.join("\n  - ", missing));
        }

        if (props.getSms().isDebug()) {
            throw new IllegalStateException("拒绝启动：app.sms.debug=true 禁止在非 dev/test 环境启用"
                    + "（验证码仅打印日志，未走真实短信通道）");
        }

        log.info("生产配置检查通过：存储档位/密钥/存储基址均为生产档");
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
