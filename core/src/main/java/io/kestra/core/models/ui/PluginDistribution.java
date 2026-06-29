package io.kestra.core.models.ui;

import com.fasterxml.jackson.annotation.JsonCreator;
import io.kestra.core.utils.Enums;

/**
 * Indicates whether a plugin UI artifact is available in OSS or requires Enterprise Edition.
 * Absent or unrecognized values default to {@code OSS}.
 */
public enum PluginDistribution {
    OSS,
    EE;

    @JsonCreator
    public static PluginDistribution fromString(final String value) {
        return Enums.getForNameIgnoreCase(value, PluginDistribution.class, OSS);
    }
}
