package io.kestra.core.plugins;

import java.util.List;
import java.util.Set;

/**
 * Result of the auto-install detection endpoint.
 *
 * @param enabled whether auto-install is enabled on this instance.
 * @param missingTypes FQCNs referenced in the flow YAML that are not registered.
 * @param artifacts Maven artifacts that provide the missing types, ready for installation.
 */
public record PluginAutoInstallDetectResult(
    boolean enabled,
    Set<String> missingTypes,
    List<PluginArtifact> artifacts) {
}
