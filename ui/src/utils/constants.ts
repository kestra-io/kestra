export const logDisplayTypes = {
    ALL: "all",
    ERROR: "error",
    HIDDEN: "hidden",
    DEFAULT: "all",
} as const

export const editorViewTypes = {
    STORAGE_KEY: "view-type",
    SOURCE: "source",
    SOURCE_TOPOLOGY: "source-topology",
    SOURCE_DOC: "source-doc",
    TOPOLOGY: "topology",
    SOURCE_BLUEPRINTS: "source-blueprints",
} as const

export const storageKeys = {
    DISPLAY_EXECUTIONS_COLUMNS: "displayExecutionsColumns",
    DISPLAY_FLOW_EXECUTIONS_COLUMNS: "displayFlowExecutionsColumns",
    DISPLAY_KV_COLUMNS: "displayKvColumns",
    DISPLAY_SECRETS_COLUMNS: "displaySecretsColumns",
    DISPLAY_NAMESPACE_SECRETS_COLUMNS: "displayNamespaceSecretsColumns",
    DISPLAY_TRIGGERS_COLUMNS: "displayTriggersColumns",
    DISPLAY_MCP_TOOLS_COLUMNS: "displayMcpToolsColumns",
    DISPLAY_ASSETS_COLUMNS: "displayAssetsColumns",
    DISPLAY_ASSET_EXECUTIONS_COLUMNS: "displayAssetExecutionsColumns",
    DISPLAY_CASES_COLUMNS: "displayCasesColumns",
    CASES_VIEW_MODE: "casesViewMode",
    CASES_BOARD_GROUP_BY: "casesBoardGroupBy",
    SELECTED_TENANT: "selectedTenant",
    EXECUTE_FLOW_BEHAVIOUR: "executeFlowBehaviour",
    SHOW_CHART: "showChart",
    SHOW_FLOWS_CHART: "showFlowsChart",
    SHOW_LOGS_CHART: "showLogsChart",
    DEFAULT_NAMESPACE: "defaultNamespace",
    FLOW_TEMPLATE: "flowTemplate",
    LATEST_NAMESPACE: "latestNamespace",
    PAGINATION_SIZE: "paginationSize",
    IMPERSONATE: "impersonate",
    EDITOR_VIEW_TYPE: "editorViewType",
    NOCODE_ENGINE: "nocodeEngine",
    TASK_EDIT_DEFAULT_MODE: "taskEditDefaultMode",
    TASK_EDIT_MODE_HINT_DISMISSED: "taskEditModeHintDismissed",
    NAMESPACE_FILES_TIP_DISMISSED: "namespaceFilesTipDismissed",
    AUTO_REFRESH_INTERVAL: "autoRefreshInterval",
    AUTO_REFRESH_ENABLED: "autoRefreshEnabled",
    DATE_FORMAT_STORAGE_KEY: "dateFormat",
    TIMEZONE_STORAGE_KEY: "timezone",
    SAVED_FILTERS_PREFIX: "saved_filters",
    EXECUTE_FORM_VALUES_PREFIX: "executeFormValues",
    FILTER_DATA_OPTIONS_PREFIX: "filterDataOptions",
    FILTER_ORDER_PREFIX: "filter-order",
    LOGS_VIEW_TYPE: "logsViewType",
    SCROLL_MEMORY_PREFIX: "scroll",
    TOPOLOGY_ORIENTATION: "topology-orientation",
    DEFAULT_TOPOLOGY_ORIENTATION: "defaultTopologyOrientation",
    DEFAULT_LOG_LEVEL: "defaultLogLevel",
    LOG_DISPLAY: "logDisplay",
    LOGS_FONT_SIZE: "logsFontSize",
    EDITOR_FONT_FAMILY: "editorFontFamily",
    EDITOR_FONT_SIZE: "editorFontSize",
    AUTOFOLD_TEXT_EDITOR: "autofoldTextEditor",
    HOVER_TEXT_EDITOR: "hoverTextEditor",
    EDITOR_PLAYGROUND: "editorPlayground",
    FLOW_DEFAULT_TAB: "flowDefaultTab",
    EXECUTION_DEFAULT_TAB: "executeDefaultTab",
    TRIGGERS_DEFAULT_TAB: "triggersDefaultTab",
    LANG: "lang",
    THEME: "theme",
    UID: "uid",
    LAST_NEWS_READ_DATE: "feeds",
    ENV_NAME: "envName",
    ENV_COLOR: "envColor",
    MENU_COLLAPSED: "menuCollapsed",
    LOGS_DENSITY: "logsDensity",
    LOGS_BODY_CLAMP: "logsBodyClamp",
    LOGS_PRETTY_JSON: "logsPrettyJson",
    LOGS_EXPAND_BY_DEFAULT: "logsExpandByDefault",
    EDITOR_SPLIT_ORIENTATION: "editor-split-orientation",
    NAMESPACE_FILES_SIDEBAR_SIZE: "namespace-files-editor-view-sidebar-size",
    NAMESPACE_FILES_EDITOR_SIZE: "namespace-files-editor-view-editor-size",
    BASIC_AUTH_SETUP_IN_PROGRESS: "basicAuthSetupInProgress",
    BASIC_AUTH_SETUP_COMPLETED: "basicAuthSetupCompleted",
    BASIC_AUTH_SETUP_COMPLETED_AT: "basicAuthSetupCompletedAt",
    BASIC_AUTH_USER_CREATED: "basicAuthUserCreated",
    BASIC_AUTH_SURVEY_DATA: "basicAuthSurveyData",
    SETUP_START_TIME: "setupStartTime",
    SHOW_SURVEY_DIALOG_AFTER_LOGIN: "showSurveyDialogAfterLogin",
    SESSION_ACTIVE: "sessionActive",
    APPS_CATALOG_VIEW_MODE: "apps-catalog-view-mode",
} as const

export const executeFlowBehaviours = {
    SAME_TAB: "same tab",
    NEW_TAB: "new tab",
} as const

export const taskEditDefaultModes = {
    MODAL: "MODAL",
    TAB: "TAB",
} as const

export const topologyOrientations = {
    VERTICAL: "VERTICAL",
    HORIZONTAL: "HORIZONTAL",
} as const

export const stateDisplayValues = {
    INPROGRESS: "IN-PROGRESS",
} as const

export const SECTIONS_MAP = {
    tasks: "tasks",
    triggers: "triggers",
    "error handlers": "errors",
    finally: "finally",
    "after execution": "afterExecution",
} as const

export const groupMemberships = {
    OWNER: "OWNER",
    MEMBER: "MEMBER",
} as const

export const TUTORIAL_NAMESPACE = "tutorial"
