package com.shanyuefang.agent.service;

import com.shanyuefang.agent.config.AgentProperties;
import com.shanyuefang.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ApiKeyCipherTest {
    @Test
    void encryptsWithRandomIvAndRoundTrips() {
        AgentProperties properties = new AgentProperties();
        properties.setEncryptionKey("a-long-test-only-encryption-key");
        ApiKeyCipher cipher = new ApiKeyCipher(properties);

        String first = cipher.encrypt("sk-test-value");
        String second = cipher.encrypt("sk-test-value");

        assertNotEquals(first, second);
        assertEquals("sk-test-value", cipher.decrypt(first));
    }

    @Test
    void rejectsMissingEncryptionKey() {
        assertThrows(BusinessException.class, () -> new ApiKeyCipher(new AgentProperties()).encrypt("value"));
    }
}
