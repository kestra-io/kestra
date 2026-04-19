package io.kestra.webserver.controllers.api;

/**
 * Lightweight descriptor of a trigger plugin class for the "Add Trigger" catalog UI.
 *
 * @param type fully qualified class name (for example {@code io.kestra.plugin.core.trigger.Schedule})
 * @param name human-readable name (Schema#title if set, otherwise simple class name)
 * @param description one-line description from the plugin @Schema
 * @param group category bucket ({@code core}, {@code realtime}, or {@code app})
 * @param ee true when the trigger is only available in Enterprise Edition (bundled with EE core, or shipped by a plugin distributed under an Enterprise license)
 * @param icon icon key resolvable via {@code GET /api/v1/plugins/icons}
 * @param deprecated whether the trigger is deprecated
 */
public record TriggerPluginDto(
    String type,
    String name,
    String description,
    TriggerPluginCategory group,
    boolean ee,
    String icon,
    Boolean deprecated
) {
}
