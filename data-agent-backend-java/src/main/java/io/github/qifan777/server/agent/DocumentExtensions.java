package io.github.qifan777.server.agent;

import java.util.Map;
import java.util.UUID;

public final class DocumentExtensions {
    private DocumentExtensions() {}

    public static UUID uuidOrNull(Map<String, Object> metadata, String key) {
        Object value = metadata.get(key);
        if (value instanceof UUID) return (UUID) value;
        if (value instanceof String s) {
            if (s.isBlank()) return null;
            try { return UUID.fromString(s); } catch (Exception e) { return null; }
        }
        return null;
    }
}
