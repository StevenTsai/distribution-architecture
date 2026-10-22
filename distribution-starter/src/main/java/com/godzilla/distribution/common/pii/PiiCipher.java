package com.godzilla.distribution.common.pii;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.HexFormat;

/**
 * PII 加解密与脱敏：手机号等个人数据落库前加密，检索走 HMAC 键。
 *
 * <p>密文格式 {@code enc:v1:iv:tag:cipherText}（各段 hex）。无前缀的值视为历史明文，
 * 因此可以平滑兼容存量数据。未配置 {@code app.pii.key} 时开发环境降级为明文落库，
 * 生产环境启动即失败。</p>
 *
 * <p>密钥不是直接使用配置值，而是 {@code SHA-256(app.pii.key)} 固定为 32 字节，
 * 避免密钥长度不合法；检索键是 {@code HMAC-SHA256(key, "pii:" + 明文)} 截断到 32 hex，
 * 保证同一明文产生同一检索键，同时不可逆。</p>
 */
@Slf4j
@Component
public class PiiCipher {

    private static final String PREFIX = "enc:v1:";
    private static final int IV_LENGTH = 12;
    private static final int TAG_BITS = 128;
    private static final int TAG_BYTES = TAG_BITS / 8;
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final String configuredKey;
    private final Environment environment;
    private final SecureRandom random = new SecureRandom();
    private volatile boolean warned = false;

    public PiiCipher(@Value("${app.pii.key:}") String configuredKey, Environment environment) {
        this.configuredKey = configuredKey;
        this.environment = environment;
    }

    @PostConstruct
    void validateOnStartup() {
        if (StringUtils.isNotBlank(configuredKey)) {
            return;
        }
        if (isProduction()) {
            throw new IllegalStateException(
                    "app.pii.key 未配置：生产环境禁止手机号明文落库，请设置 PII 密钥");
        }
        log.warn("app.pii.key 未配置，PII 将以明文落库（仅允许用于开发/测试环境）");
    }

    /** 加密；空值返回空串，无密钥时原样返回（dev 降级）。 */
    public String encrypt(String value) {
        if (StringUtils.isEmpty(value)) {
            return "";
        }
        byte[] key = keyBytes();
        if (key == null) {
            warnOnce();
            return value;
        }
        try {
            byte[] iv = new byte[IV_LENGTH];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(TAG_BITS, iv));
            byte[] out = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
            byte[] cipherText = Arrays.copyOf(out, out.length - TAG_BYTES);
            byte[] tag = Arrays.copyOfRange(out, out.length - TAG_BYTES, out.length);
            HexFormat hex = HexFormat.of();
            return PREFIX + hex.formatHex(iv) + ":" + hex.formatHex(tag) + ":" + hex.formatHex(cipherText);
        } catch (Exception e) {
            throw new IllegalStateException("PII 加密失败", e);
        }
    }

    /** 解密；非 {@code enc:v1:} 前缀视为历史明文原样返回。 */
    public String decrypt(String value) {
        if (StringUtils.isEmpty(value)) {
            return "";
        }
        if (!value.startsWith(PREFIX)) {
            return value;
        }
        byte[] key = keyBytes();
        if (key == null) {
            warnOnce();
            return "";
        }
        try {
            String[] parts = value.substring(PREFIX.length()).split(":");
            if (parts.length != 3) {
                throw new IllegalArgumentException("无法识别的密文格式");
            }
            HexFormat hex = HexFormat.of();
            byte[] iv = hex.parseHex(parts[0]);
            byte[] tag = hex.parseHex(parts[1]);
            byte[] cipherText = hex.parseHex(parts[2]);
            byte[] combined = new byte[cipherText.length + tag.length];
            System.arraycopy(cipherText, 0, combined, 0, cipherText.length);
            System.arraycopy(tag, 0, combined, cipherText.length, tag.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(TAG_BITS, iv));
            return new String(cipher.doFinal(combined), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("PII 解密失败（密钥可能已变更）", e);
        }
    }

    /** 展示脱敏：{@code 13800138000 -> 138****8000}。 */
    public String mask(String value) {
        String plain = value == null ? "" : value;
        if (plain.isEmpty()) {
            return "";
        }
        if (plain.length() < 7) {
            return "****";
        }
        return plain.substring(0, 3) + "****" + plain.substring(plain.length() - 4);
    }

    /** 检索键：HMAC-SHA256 截断 32 hex；无密钥时退化为不加盐 SHA-256（仅 dev）。 */
    public String lookupKey(String value) {
        String message = "pii:" + (value == null ? "" : value);
        byte[] key = keyBytes();
        try {
            if (key == null) {
                return HexFormat.of().formatHex(
                        MessageDigest.getInstance("SHA-256").digest(message.getBytes(StandardCharsets.UTF_8))).substring(0, 32);
            }
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(key, HMAC_ALGORITHM));
            return HexFormat.of().formatHex(mac.doFinal(message.getBytes(StandardCharsets.UTF_8))).substring(0, 32);
        } catch (Exception e) {
            throw new IllegalStateException("PII 检索键计算失败", e);
        }
    }

    private byte[] keyBytes() {
        if (StringUtils.isBlank(configuredKey)) {
            return null;
        }
        try {
            return MessageDigest.getInstance("SHA-256").digest(configuredKey.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("PII 密钥派生失败", e);
        }
    }

    private boolean isProduction() {
        for (String profile : environment.getActiveProfiles()) {
            if ("prod".equalsIgnoreCase(profile) || "production".equalsIgnoreCase(profile)) {
                return true;
            }
        }
        return false;
    }

    private void warnOnce() {
        if (!warned) {
            warned = true;
            log.warn("PII 未加密落库：app.pii.key 未配置");
        }
    }
}
