package io.kestra.core.models.flows;

import jakarta.annotation.Nullable;

/**
 * Represents a flow source code with an optional filename.
 * Used for flow validation where the source can come from a single YAML string
 * (potentially containing multiple documents separated by ---) or from uploaded files.
 *
 * @param filename Optional filename, null when validating raw YAML strings
 * @param content  The flow source code in YAML format
 */
public record FlowSource(
    @Nullable String filename,
    String content
) {
}
