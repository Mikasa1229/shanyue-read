package com.shanyuefang.agent.service;

import com.shanyuefang.agent.config.AgentProperties;
import com.shanyuefang.common.exception.BusinessException;
import com.shanyuefang.common.result.ResultCode;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

@Component
public class ApiKeyCipher {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH = 128;
    private final AgentProperties properties;

    public ApiKeyCipher(AgentProperties properties) {
        this.properties = properties;
    }

    public String encrypt(String value) {
        try {
            byte[] iv = new byte[IV_LENGTH];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key(), new GCMParameterSpec(TAG_LENGTH, iv));
            byte[] encrypted = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
            byte[] payload = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, payload, 0, iv.length);
            System.arraycopy(encrypted, 0, payload, iv.length, encrypted.length);
            return Base64.getEncoder().encodeToString(payload);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "Unable to protect model key");
        }
    }

    public String decrypt(String value) {
        try {
            byte[] payload = Base64.getDecoder().decode(value);
            if (payload.length <= IV_LENGTH) {
                throw new IllegalArgumentException("Invalid cipher payload");
            }
            byte[] iv = new byte[IV_LENGTH];
            byte[] encrypted = new byte[payload.length - IV_LENGTH];
            System.arraycopy(payload, 0, iv, 0, IV_LENGTH);
            System.arraycopy(payload, IV_LENGTH, encrypted, 0, encrypted.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(TAG_LENGTH, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "Stored model key is invalid");
        }
    }

    private SecretKey key() throws Exception {
        String raw = properties.getEncryptionKey();
        if (raw == null || raw.isBlank()) {
            throw new BusinessException(ResultCode.INTERNAL_ERROR, "AGENT_ENCRYPTION_KEY is not configured");
        }
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
        return new SecretKeySpec(digest, "AES");
    }
}
