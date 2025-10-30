package io.kestra.core.storages.kv;

import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Getter
@EqualsAndHashCode
public class KVMetadata {
    private String description;
    private Instant expirationDate;

    public KVMetadata(String description, Duration ttl) {
        if (ttl != null && ttl.isNegative()) {
            throw new IllegalArgumentException("ttl cannot be negative");
        }
        
        this.description = description;
        if (ttl != null) {
            this.expirationDate = Instant.now().plus(ttl);
        }
    }
    
    public KVMetadata(String description, Instant expirationDate) {
        this.description = description;
        this.expirationDate = expirationDate;
    }
    
    public KVMetadata(Map<String, String> metadata) {
        if (metadata == null) {
            return;
        }

        this.description = metadata.get("description");
        this.expirationDate = Optional.ofNullable(metadata.get("expirationDate"))
            .map(Instant::parse)
            .orElse(null);
    }

    public Map<String, String> toMap() {
        Map<String, String> map = new HashMap<>();
        if (description != null) {
            map.put("description", description);
        }
        if (expirationDate != null) {
            map.put("expirationDate", expirationDate.toString());
        }
        return map;
    }
    
    @Override
    public String toString() {
        return "[description=" + description + ", expirationDate=" + expirationDate + "]";
    }
}
