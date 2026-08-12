package com.shanyuefang.common.result;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RJsonTest {
    @Test
    void deserializesTheSharedFeignResponseEnvelope() throws Exception {
        R<Map<String, Object>> response = new ObjectMapper().readValue(
                "{\"code\":200,\"message\":\"ok\",\"data\":{\"title\":\"fixture\"},\"timestamp\":123}",
                new TypeReference<>() { });

        assertEquals(200, response.getCode());
        assertEquals("fixture", response.getData().get("title"));
        assertEquals(123L, response.getTimestamp());
    }
}
