package io.kestra.core.models.kv;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;

public enum KVType {
        STRING,
        NUMBER,
        BOOLEAN,
        DATETIME,
        DATE,
        DURATION,
        JSON;

        public static KVType from(Object value) {
            if (value == null) return KVType.STRING;

            return switch (value) {
                case String ignored -> STRING;
                case Number ignored -> NUMBER;
                case Boolean ignored -> BOOLEAN;
                case LocalDateTime ignored -> DATETIME;
                case Instant ignored -> DATETIME;
                case LocalDate ignored -> DATE;
                case Duration ignored -> DURATION;
                default -> JSON;
            };
        }
    }