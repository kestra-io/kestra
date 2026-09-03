package io.kestra.webserver.controllers.api;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;

import io.kestra.core.models.triggers.PollingTriggerInterface;
import io.kestra.core.models.triggers.RealtimeTriggerInterface;
import io.kestra.core.plugins.RegisteredPlugin;
import io.kestra.core.utils.Enums;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Category bucket for the "Add Trigger" catalog in the UI.
 *
 * <p>
 * Derived from the owning {@link RegisteredPlugin}'s provenance and the interfaces the trigger
 * class implements:
 * <ul>
 * <li>{@link #CORE} - trigger is bundled with Kestra (no external plugin location).</li>
 * <li>{@link #REALTIME} - external trigger implementing {@link RealtimeTriggerInterface}.</li>
 * <li>{@link #APP} - external trigger, typically implementing {@link PollingTriggerInterface}.</li>
 * </ul>
 *
 * Core precedence wins over realtime/app so a bundled core trigger (for example Webhook) is CORE
 * even if it otherwise looks like a realtime or polling trigger.
 */
public enum TriggerPluginCategory {
    @JsonProperty("core")
    CORE,
    @JsonProperty("realtime")
    REALTIME,
    @JsonProperty("app")
    APP,
    // UNKNOWN is the deserialization fallback and serializes to null (never a wire value),
    // so it is hidden from the generated OpenAPI schema / SDK enum.
    @Schema(hidden = true)
    UNKNOWN;

    @JsonValue
    public String jsonValue() {
        return this == UNKNOWN ? null : name().toLowerCase();
    }

    @JsonCreator
    public static TriggerPluginCategory fromString(final String value) {
        if (value == null) {
            return UNKNOWN;
        }
        return Enums.getForNameIgnoreCase(value, TriggerPluginCategory.class, UNKNOWN);
    }

    public static TriggerPluginCategory classify(RegisteredPlugin plugin, Class<?> triggerClass) {
        // Core = bundled with Kestra. External plugins always have an ExternalPlugin location.
        // PluginRegistry marks bundled plugins with null externalPlugin (see DefaultPluginRegistry
        // .PluginBundleIdentifier#of). Relying on manifest X-Kestra-Group is unreliable because the
        // uber-jar sets it for bundled core too.
        if (plugin.getExternalPlugin() == null) {
            return CORE;
        }

        // Some EE modules extend the core trigger surface (for example
        // io.kestra.plugin.core.trigger.AssetEvent). They ship as separate
        // plugin jars but are conceptually part of "core" from the user's
        // perspective, so show them in the Core bucket with the EE badge.
        String packageName = triggerClass.getPackageName();
        if (
            packageName.startsWith("io.kestra.plugin.core.")
                || packageName.startsWith("io.kestra.core.")
        ) {
            return CORE;
        }

        if (RealtimeTriggerInterface.class.isAssignableFrom(triggerClass)) {
            return REALTIME;
        }

        return APP;
    }
}
