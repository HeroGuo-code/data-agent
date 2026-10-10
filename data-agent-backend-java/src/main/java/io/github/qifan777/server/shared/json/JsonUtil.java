package io.github.qifan777.server.shared.json;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

/** 简单 JSON 工具，独立 ObjectMapper 实例，便于静态调用。 */
public final class JsonUtil {
    private static final ObjectMapper MAPPER = new ObjectMapper()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private JsonUtil() {}

    public static String toJson(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (Exception e) {
            throw new RuntimeException("JSON serialization error", e);
        }
    }

    public static <T> T fromJson(String json, Class<T> clazz) {
        if (json == null || json.isBlank()) return null;
        try {
            return MAPPER.readValue(json, clazz);
        } catch (Exception e) {
            throw new RuntimeException("JSON deserialization error", e);
        }
    }

    public static <T> T fromJson(String json, TypeReference<T> type) {
        if (json == null || json.isBlank()) return null;
        try {
            return MAPPER.readValue(json, type);
        } catch (Exception e) {
            throw new RuntimeException("JSON deserialization error", e);
        }
    }

    public static ObjectMapper mapper() {
        return MAPPER;
    }
}
