export const CLUSTER_PREFIX = "cluster_"

export const EVENTS = {
    EDIT: "edit",
    DELETE: "delete",
    SHOW_DESCRIPTION: "showDescription",
    COLLAPSE: "collapse",
    EXPAND: "expand",
    OPEN_LINK: "openLink",
    ADD_TASK: "addTask",
    SHOW_LOGS: "showLogs",
    MOUSE_OVER: "mouseover",
    MOUSE_LEAVE: "mouseleave",
    ADD_ERROR: "addError",
    EXPAND_DEPENDENCIES: "expandDependencies",
    SHOW_CONDITION: "showCondition",
    RUN_TASK: "runTask",
    SHOW_CUSTOM_ACTION: "showCustomAction",
    SHOW_DETAILS: "showDetails",
} as const

export interface CustomActionConfig {
    label: string;
    taskProp: string;
    lang: string;
}

export interface ShowDetailsConfig {
    label: string;
    taskProp: string;
    lang: string;
}

export const NODE_SIZES = {
    TASK_WIDTH: 218,
    TASK_WIDTH_EXECUTION: 273,
    TASK_HEIGHT: 56,
    TRIGGER_WIDTH: 218,
    TRIGGER_HEIGHT: 56,
    DOT_WIDTH: 5,
    DOT_HEIGHT: 5,
    COLLAPSED_CLUSTER_WIDTH: 150,
    COLLAPSED_CLUSTER_HEIGHT: 40,
    TRIGGER_CLUSTER_WIDTH: 350,
    TRIGGER_CLUSTER_HEIGHT: 180,
} as const

export const CLUSTER_TAG_STATUS: Record<string, string> = {
    triggers: "success",
    subflow: "running",
    "flowable-task": "info",
    errors: "error",
}
