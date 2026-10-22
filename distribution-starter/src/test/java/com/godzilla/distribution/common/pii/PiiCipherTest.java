package com.godzilla.distribution.common.pii;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PII 加解密测试：验证密文格式、随机 IV、历史明文兼容、检索键和脱敏。
 */
class PiiCipherTest {

    private static final String PHONE = "13800138000";

    private PiiCipher cipherWithKey() {
        return new PiiCipher("unit-test-key", new MockEnvironment());
    }

    @Test
    void encryptThenDecrypt_ReturnsOriginalValue() {
        PiiCipher cipher = cipherWithKey();

        String encrypted = cipher.encrypt(PHONE);

        assertNotEquals(PHONE, encrypted);
        assertEquals(PHONE, cipher.decrypt(encrypted));
    }

    @Test
    void encrypt_UsesVersionedFormat() {
        String encrypted = cipherWithKey().encrypt(PHONE);

        assertTrue(encrypted.startsWith("enc:v1:"));
        assertEquals(3, encrypted.substring("enc:v1:".length()).split(":").length);
    }

    @Test
    void encrypt_RandomIvProducesDifferentCipherTexts() {
        PiiCipher cipher = cipherWithKey();

        String first = cipher.encrypt(PHONE);
        String second = cipher.encrypt(PHONE);

        assertNotEquals(first, second);
        assertEquals(PHONE, cipher.decrypt(first));
        assertEquals(PHONE, cipher.decrypt(second));
    }

    @Test
    void decrypt_LegacyPlainTextPassesThrough() {
        assertEquals(PHONE, cipherWithKey().decrypt(PHONE));
    }

    @Test
    void encrypt_EmptyValueReturnsEmpty() {
        assertEquals("", cipherWithKey().encrypt(null));
        assertEquals("", cipherWithKey().encrypt(""));
    }

    @Test
    void lookupKey_IsDeterministicAndValueSpecific() {
        PiiCipher cipher = cipherWithKey();

        String first = cipher.lookupKey(PHONE);
        String second = cipher.lookupKey(PHONE);
        String other = cipher.lookupKey("13900139000");

        assertEquals(first, second);
        assertNotEquals(first, other);
        assertEquals(32, first.length());
    }

    @Test
    void mask_HidesMiddleDigits() {
        assertEquals("138****8000", cipherWithKey().mask(PHONE));
        assertEquals("****", cipherWithKey().mask("123"));
    }

    @Test
    void withoutKey_DegradesToPlainTextInDevProfile() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("dev");
        PiiCipher cipher = new PiiCipher("", environment);

        cipher.validateOnStartup();

        assertEquals(PHONE, cipher.encrypt(PHONE));
    }

    @Test
    void withoutKey_FailsFastInProdProfile() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");
        PiiCipher cipher = new PiiCipher("", environment);

        assertThrows(IllegalStateException.class, cipher::validateOnStartup);
    }
}
