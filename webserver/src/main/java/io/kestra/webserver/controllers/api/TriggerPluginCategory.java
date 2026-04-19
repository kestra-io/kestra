package io.kestra.webserver.controllers.api;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import io.kestra.core.models.triggers.PollingTriggerInterface;
import io.kestra.core.models.triggers.RealtimeTriggerInterface;
import io.kestra.core.plugins.RegisteredPlugin;
import io.kestra.core.utils.Enums;

/**
 * Category bucket for the "Add Trigger" catalog in the UI.
 *
 * <p>Derived from the owning {@link RegisteredPlugin#group()} and the interfaces the trigger class
 * implements:
 * <ul>
 *     <li>{@link #CORE} - trigger ships with Kestra Core (manifest {@code X-Kestra-Group} is null).</li>
 *     <li>{@link #REALTIME} - non-core trigger implementing {@link RealtimeTriggerInterface}.</li>
 *     <li>{@link #APP} - non-core trigger implementing {@link PollingTriggerInterface}.</li>
 * </ul>
 *
 * Core precedence wins over realtime/app so a core realtime trigger (e.g. Webhook) is still CORE.
 */
public enum TriggerPluginCategory {
    CORE,
    REALTIME,
    APP,
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
        if (plugin.group() == null) {
            return CORE;
        }

        if (RealtimeTriggerInterface.class.isAssignableFrom(triggerClass)) {
            return REALTIME;
        }

        if (PollingTriggerInterface.class.isAssignableFrom(triggerClass)) {
            return APP;
        }

        return APP;
    }
}
