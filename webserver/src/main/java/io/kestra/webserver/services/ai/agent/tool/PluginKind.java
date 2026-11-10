package io.kestra.webserver.services.ai.agent.tool;

import java.util.Arrays;
import java.util.Optional;

import io.kestra.core.plugins.RegisteredPlugin;

/**
 * The kind of a plugin type, one per class group of a {@link RegisteredPlugin}.
 */
public enum PluginKind {
    TASK(RegisteredPlugin.TASKS_GROUP_NAME),
    TRIGGER(RegisteredPlugin.TRIGGERS_GROUP_NAME),
    TASK_RUNNER(RegisteredPlugin.TASK_RUNNERS_GROUP_NAME),
    STORAGE(RegisteredPlugin.STORAGES_GROUP_NAME),
    SECRET(RegisteredPlugin.SECRETS_GROUP_NAME),
    LOG_DATA_STORE(RegisteredPlugin.LOG_DATA_STORES_GROUP_NAME),
    ASSET(RegisteredPlugin.ASSETS_GROUP_NAME),
    ASSET_EXPORTER(RegisteredPlugin.ASSETS_EXPORTERS_GROUP_NAME),
    APP(RegisteredPlugin.APPS_GROUP_NAME),
    APP_BLOCK(RegisteredPlugin.APP_BLOCKS_GROUP_NAME),
    CHART(RegisteredPlugin.CHARTS_GROUP_NAME),
    DATA_FILTER(RegisteredPlugin.DATA_FILTERS_GROUP_NAME),
    DATA_FILTER_KPI(RegisteredPlugin.DATA_FILTERS_KPI_GROUP_NAME),
    LOG_EXPORTER(RegisteredPlugin.LOG_EXPORTERS_GROUP_NAME),
    RULE(RegisteredPlugin.RULES_GROUP_NAME),
    ADDITIONAL_PLUGIN(RegisteredPlugin.ADDITIONAL_PLUGINS_GROUP_NAME),
    FILE_RENDERER(RegisteredPlugin.FILE_RENDERERS_GROUP_NAME),
    QUEUE_FACTORY(RegisteredPlugin.QUEUE_FACTORIES_GROUP_NAME);

    private final String group;

    PluginKind(final String group) {
        this.group = group;
    }

    static Optional<PluginKind> ofGroup(final String group) {
        return Arrays.stream(values()).filter(kind -> kind.group.equals(group)).findFirst();
    }
}
