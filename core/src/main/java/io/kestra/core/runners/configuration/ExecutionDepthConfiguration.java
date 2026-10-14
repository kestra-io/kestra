package io.kestra.core.runners.configuration;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.bind.annotation.Bindable;

/**
 * Configuration for the runtime cap on the Subflow/Flow-trigger execution chain depth.
 * <p>
 * This is a backstop against a cross-flow execution cycle that save-time validation cannot see (e.g. a
 * conditional Flow trigger loop), not the primary defense against it.
 *
 * @param maxDepth maximum number of Subflow/Flow-trigger hops between a root execution and one it
 *        (directly or indirectly) starts. Guards a cycle rather than bounding normal depth so it's generous by default.
 */
@ConfigurationProperties("kestra.execution.depth")
public record ExecutionDepthConfiguration(
    @Bindable(defaultValue = "100") int maxDepth) {
}
