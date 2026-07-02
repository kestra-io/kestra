package io.kestra.webserver.controllers.api;

import com.fasterxml.jackson.annotation.JsonCreator;
import io.kestra.core.utils.Enums;

/**
 * Requested format for a file download from execution outputs.
 */
public enum FileFormat {
    /** Return the raw file bytes as-is (default). */
    RAW,
    /** Convert Ion records to JSON Lines (one compact JSON object per line, {@code .jsonl}). */
    JSONL;

    @JsonCreator
    public static FileFormat fromString(final String value) {
        return Enums.getForNameIgnoreCase(value, FileFormat.class, RAW);
    }
}
