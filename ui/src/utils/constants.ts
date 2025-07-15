export const stateGlobalChartTypes = {
    EXECUTIONS: "executions",
    TASKRUNS: "taskruns"
}

export const logDisplayTypes = {
    ALL: "all",
    ERROR: "error",
    HIDDEN: "hidden",
    DEFAULT: "all"
}

export const editorViewTypes = {
    STORAGE_KEY: "view-type",
    SOURCE: "source",
    SOURCE_TOPOLOGY: "source-topology",
    SOURCE_DOC: "source-doc",
    TOPOLOGY: "topology",
    SOURCE_BLUEPRINTS: "source-blueprints"
}

export const storageKeys = {
    DISPLAY_EXECUTIONS_COLUMNS: "displayExecutionsColumns",
    DISPLAY_FLOW_EXECUTIONS_COLUMNS: "displayFlowExecutionsColumns",
    SELECTED_TENANT: "selectedTenant",
    EXECUTE_FLOW_BEHAVIOUR: "executeFlowBehaviour",
    SHOW_CHART: "showChart",
    SHOW_FLOWS_CHART: "showFlowsChart",
    SHOW_LOGS_CHART: "showLogsChart",
    DEFAULT_NAMESPACE: "defaultNamespace",
    LATEST_NAMESPACE: "latestNamespace",
    PAGINATION_SIZE: "paginationSize",
    IMPERSONATE: "impersonate",
    EDITOR_VIEW_TYPE: "editorViewType",
    AUTO_REFRESH_INTERVAL: "autoRefreshInterval",
}

export const executeFlowBehaviours = {
    SAME_TAB: "same tab",
    NEW_TAB: "new tab"
}

export const stateDisplayValues = {
    INPROGRESS: "IN-PROGRESS"
}

export const PLUGIN_DEFAULTS_SECTION = "plugin defaults"

export const SECTIONS_MAP = {
        tasks: "tasks",
        triggers: "triggers",
        "error handlers": "errors",
        finally: "finally",
        "after execution": "afterExecution",
        [PLUGIN_DEFAULTS_SECTION]: "pluginDefaults",
} as const;

