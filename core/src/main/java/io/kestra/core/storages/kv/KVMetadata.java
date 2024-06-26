package io.kestra.core.storages.kv;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class KVMetadata {
    private Instant expirationDate;

    public KVMetadata(Duration ttl) {
        if (ttl != null && ttl.isNegative()) {
            throw new IllegalArgumentException("ttl cannot be negative");
        }

        if (ttl != null) {
            this.expirationDate = Instant.now().plus(ttl);
        }
    }

    public KVMetadata(Map<String, String> metadata) {
        this.expirationDate = Optional.ofNullable(metadata)
            .map(map -> map.get("expirationDate"))
            .map(Instant::parse)
            .orElse(null);
    }

    public Instant getExpirationDate() {
        return expirationDate;
    }

    public Map<String, String> toMap() {
        Map<String, String> map = new HashMap<>();
        if (expirationDate != null) {
            map.put("expirationDate", expirationDate.toString());
        }
        return map;
    }
}
