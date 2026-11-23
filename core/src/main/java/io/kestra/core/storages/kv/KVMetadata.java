package io.kestra.core.storages.kv;

import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import io.kestra.core.models.kv.KVType;

import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@EqualsAndHashCode
public class KVMetadata {
    public static final Instant MAX_EXPIRATION_DATE = Instant.parse("9999-12-31T23:59:59.999999Z");

    private static final String TYPE_KEY = "type";

    private String description;
    private Instant expirationDate;
    private KVType type;

    public KVMetadata(String description, Duration ttl) {
        if (ttl != null && ttl.isNegative()) {
            throw new IllegalArgumentException("ttl cannot be negative");
        }

        this.description = description;
        if (ttl != null) {
            try {
                this.expirationDate = Instant.now().plus(ttl);
            } catch (ArithmeticException | DateTimeException e) {
                throw new IllegalArgumentException(
                    String.format("ttl '%s' is too large: the expiration date would be beyond the latest supported date", ttl),
                    e
                );
            }

            if (this.expirationDate.isAfter(MAX_EXPIRATION_DATE)) {
                throw new IllegalArgumentException(
                    String.format("ttl '%s' is too large: the expiration date would be beyond the latest supported date", ttl)
                );
            }
        }
    }

    public KVMetadata(String description, Instant expirationDate) {
        if (expirationDate != null && expirationDate.isAfter(MAX_EXPIRATION_DATE)) {
            throw new IllegalArgumentException(
                String.format("expirationDate '%s' is too large: the expiration date would be beyond the latest supported date", expirationDate)
            );
        }
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
        if (this.expirationDate != null && this.expirationDate.isAfter(MAX_EXPIRATION_DATE)) {
            throw new IllegalArgumentException(
                String.format("expirationDate '%s' is too large: the expiration date would be beyond the latest supported date", this.expirationDate)
            );
        }
        this.type = Optional.ofNullable(metadata.get(TYPE_KEY))
            .map(KVType::valueOf)
            .orElse(null);
    }

    public KVMetadata withType(final KVType type) {
        KVMetadata copy = new KVMetadata(this.description, this.expirationDate);
        copy.type = type;
        return copy;
    }

    public Map<String, String> toMap() {
        Map<String, String> map = new HashMap<>();
        if (description != null) {
            map.put("description", description);
        }
        if (expirationDate != null) {
            map.put("expirationDate", expirationDate.toString());
        }
        if (type != null) {
            map.put(TYPE_KEY, type.name());
        }
        return map;
    }

    @Override
    public String toString() {
        return "[description=" + description + ", expirationDate=" + expirationDate + "]";
    }
}
