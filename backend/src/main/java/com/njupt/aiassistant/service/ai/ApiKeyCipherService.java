package com.njupt.aiassistant.service.ai;

import com.njupt.aiassistant.common.ErrorCode;
import com.njupt.aiassistant.config.AiConfigSecurityProperties;
import com.njupt.aiassistant.exception.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

@Service
public class ApiKeyCipherService {
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final String PREFIX = "v1:";

    private final byte[] masterKey;
    private final SecureRandom secureRandom = new SecureRandom();

    public ApiKeyCipherService(AiConfigSecurityProperties properties) {
        this.masterKey = decodeMasterKey(properties.masterKey());
    }

    public boolean isAvailable() {
        return masterKey != null;
    }

    public String encrypt(String plaintext) {
        requireAvailable();
        if (!StringUtils.hasText(plaintext)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "API Key 不能为空");
        }
        try {
            var iv = new byte[IV_BYTES];
            secureRandom.nextBytes(iv);
            var cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(masterKey, "AES"),
                    new GCMParameterSpec(TAG_BITS, iv));
            var ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            var payload = ByteBuffer.allocate(iv.length + ciphertext.length)
                    .put(iv).put(ciphertext).array();
            return PREFIX + Base64.getEncoder().encodeToString(payload);
        } catch (GeneralSecurityException exception) {
            throw new BusinessException(
                    ErrorCode.AI_CONFIG_STORAGE_UNAVAILABLE,
                    "API Key 加密失败",
                    exception
            );
        }
    }

    public String decrypt(String encrypted) {
        requireAvailable();
        if (!StringUtils.hasText(encrypted) || !encrypted.startsWith(PREFIX)) {
            throw new BusinessException(
                    ErrorCode.AI_CONFIG_STORAGE_UNAVAILABLE,
                    "已保存的 API Key 格式无效"
            );
        }
        try {
            var payload = Base64.getDecoder().decode(encrypted.substring(PREFIX.length()));
            if (payload.length <= IV_BYTES) {
                throw new GeneralSecurityException("invalid encrypted payload");
            }
            var iv = new byte[IV_BYTES];
            var ciphertext = new byte[payload.length - IV_BYTES];
            System.arraycopy(payload, 0, iv, 0, IV_BYTES);
            System.arraycopy(payload, IV_BYTES, ciphertext, 0, ciphertext.length);
            var cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(masterKey, "AES"),
                    new GCMParameterSpec(TAG_BITS, iv));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException | GeneralSecurityException exception) {
            throw new BusinessException(
                    ErrorCode.AI_CONFIG_STORAGE_UNAVAILABLE,
                    "已保存的 API Key 无法解密，请重新配置",
                    exception
            );
        }
    }

    private void requireAvailable() {
        if (!isAvailable()) {
            throw new BusinessException(ErrorCode.AI_CONFIG_STORAGE_UNAVAILABLE);
        }
    }

    private byte[] decodeMasterKey(String encoded) {
        if (!StringUtils.hasText(encoded)) {
            return null;
        }
        try {
            var decoded = Base64.getDecoder().decode(encoded);
            return decoded.length == 32 ? decoded : null;
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
