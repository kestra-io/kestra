export const logDisplayTypes = {
    ALL: "all",
    ERROR: "error",
    HIDDEN: "hidden",
    DEFAULT: "all",
} as const;

export const editorViewTypes = {
    STORAGE_KEY: "view-type",
    SOURCE: "source",
    SOURCE_TOPOLOGY: "source-topology",
    SOURCE_DOC: "source-doc",
    TOPOLOGY: "topology",
    SOURCE_BLUEPRINTS: "source-blueprints",
} as const;

export const storageKeys = {
    DISPLAY_EXECUTIONS_COLUMNS: "displayExecutionsColumns",
    DISPLAY_FLOW_EXECUTIONS_COLUMNS: "displayFlowExecutionsColumns",
    DISPLAY_KV_COLUMNS: "displayKvColumns",
    DISPLAY_SECRETS_COLUMNS: "displaySecretsColumns",
    DISPLAY_TRIGGERS_COLUMNS: "displayTriggersColumns",
    DISPLAY_ASSETS_COLUMNS: "displayAssetsColumns",
    DISPLAY_ASSET_EXECUTIONS_COLUMNS: "displayAssetExecutionsColumns",
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
    AUTO_REFRESH_ENABLED: "autoRefreshEnabled",
    DATE_FORMAT_STORAGE_KEY: "dateFormat",
    TIMEZONE_STORAGE_KEY: "timezone",
    SAVED_FILTERS_PREFIX: "saved_filters",
    FILTER_DATA_OPTIONS_PREFIX: "filterDataOptions",
    FILTER_ORDER_PREFIX: "filter-order",
    LOGS_VIEW_TYPE: "logsViewType",
    SCROLL_MEMORY_PREFIX: "scroll",
} as const;

export const executeFlowBehaviours = {
    SAME_TAB: "same tab",
    NEW_TAB: "new tab",
} as const;

export const stateDisplayValues = {
    INPROGRESS: "IN-PROGRESS",
} as const;

export const PLUGIN_DEFAULTS_SECTION = "plugin defaults";

export const SECTIONS_MAP = {
    tasks: "tasks",
    triggers: "triggers",
    "error handlers": "errors",
    finally: "finally",
    "after execution": "afterExecution",
    [PLUGIN_DEFAULTS_SECTION]: "pluginDefaults",
} as const;

export const groupMemberships = {
    OWNER: "OWNER",
    MEMBER: "MEMBER",
} as const;
