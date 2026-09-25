export const CLUSTER_PREFIX = "cluster_"

// Low enough that zoom-to-fit really fits a large graph. Shared so the topology and the
// dependencies DAG cannot drift apart on how far out a user may zoom.
// 0.1 left a 30-task flow overflowing with the minus button already at its limit:
// https://github.com/kestra-io/kestra/issues/19686.
export const MIN_ZOOM = 0.02

// The dot grid every graph canvas paints, and that the image export reproduces. `color` is a
// design-system token, resolved with `cssVar` where it is used.
export const GRAPH_BACKGROUND = {
    gap: 20,
    size: 1,
    color: "--ks-topology-bg",
} as const

export const EVENTS = {
    EDIT: "edit",
    DELETE: "delete",
    DUPLICATE: "duplicate",
    SHOW_DESCRIPTION: "showDescription",
    COLLAPSE: "collapse",
    EXPAND: "expand",
    OPEN_LINK: "openLink",
    ADD_TASK: "addTask",
    ADD_TRIGGER: "addTrigger",
    EDIT_FLOW: "editFlow",
    SHOW_LOGS: "showLogs",
    SHOW_OUTPUTS: "showOutputs",
    REPLAY_TASK: "replayTask",
    MOUSE_OVER: "mouseover",
    MOUSE_LEAVE: "mouseleave",
    ADD_ERROR: "addError",
    EXPAND_DEPENDENCIES: "expandDependencies",
    SHOW_CONDITION: "showCondition",
    RUN_TASK: "runTask",
    SHOW_CUSTOM_ACTION: "showCustomAction",
    SHOW_DETAILS: "showDetails",
    CARD_CLICK: "cardClick",
    MOVE_TASK: "moveTask",
    TASK_DRAG_START: "taskDragStart",
    TASK_DRAG_END: "taskDragEnd",
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
