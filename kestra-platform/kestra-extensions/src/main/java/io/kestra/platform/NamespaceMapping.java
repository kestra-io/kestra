package io.kestra.platform;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Builder;
import lombok.Data;
import lombok.extern.jackson.Jacksonized;

import java.time.Instant;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Maps namespace patterns to worker groups.
 *
 * Uses regex patterns to determine which worker group should handle
 * tasks from specific namespaces.
 */
@Data
@Builder
@Jacksonized
@JsonIgnoreProperties(ignoreUnknown = true)
public class NamespaceMapping {

    /**
     * Unique identifier for this mapping
     */
    private UUID id;

    /**
     * Regex pattern to match namespaces (e.g., "^enterprise\.client1\..*$")
     */
    private String namespacePattern;

    /**
     * Compiled regex pattern for efficient matching
     */
    private transient Pattern compiledPattern;

    /**
     * Worker group ID that should handle matching namespaces
     */
    private UUID workerGroupId;

    /**
     * Priority (higher number = higher priority)
     * When multiple patterns match, highest priority wins
     */
    private Integer priority;

    /**
     * Description of this mapping
     */
    private String description;

    /**
     * Whether this mapping is currently enabled
     */
    private Boolean enabled;

    /**
     * Timestamp when created
     */
    private Instant createdAt;

    /**
     * Timestamp when last updated
     */
    private Instant updatedAt;

    /**
     * Get the compiled regex pattern, compiling if necessary
     */
    public Pattern getCompiledPattern() {
        if (compiledPattern == null && namespacePattern != null) {
            compiledPattern = Pattern.compile(namespacePattern);
        }
        return compiledPattern;
    }

    /**
     * Check if this mapping matches the given namespace
     */
    public boolean matches(String namespace) {
        if (!Boolean.TRUE.equals(enabled)) {
            return false;
        }

        Pattern pattern = getCompiledPattern();
        return pattern != null && pattern.matcher(namespace).matches();
    }

    /**
     * Check if this mapping is currently enabled
     */
    public boolean isEnabled() {
        return Boolean.TRUE.equals(enabled);
    }
}
