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
    // 56 (icon row, where the new type line already fits) + 24 for the footer slot #19665 fills
    // with the duration bar — a constant added once, for every task node, so it never depends on
    // zoom or execution state.
    TASK_HEIGHT: 80,
    TRIGGER_WIDTH: 218,
    TRIGGER_HEIGHT: 56,
    DOT_WIDTH: 5,
    DOT_HEIGHT: 5,
    COLLAPSED_CLUSTER_WIDTH: 150,
    COLLAPSED_CLUSTER_HEIGHT: 40,
    TRIGGER_CLUSTER_WIDTH: 350,
    TRIGGER_CLUSTER_HEIGHT: 180,
    // dagre lays a cluster out tightly around its children — it has no notion of a label's own
    // height. A flowable lane's header claims this much of the cluster's own box instead, and
    // every direct child is shifted down by the same amount so nothing sits under it.
    LANE_HEADER_HEIGHT: 32,
} as const

// Below PILL, only a minimal glanceable pill renders; above EXPANDED, the node also renders the
// `details` slot as an overlay. Neither ever changes `NODE_SIZES`, so crossing either threshold
// only repaints the node — it never re-runs dagre.
export const ZOOM_LOD = {
    PILL: 0.5,
    EXPANDED: 1.3,
} as const

export type LodLevel = "pill" | "default" | "expanded"

export const CLUSTER_TAG_STATUS: Record<string, string> = {
    triggers: "success",
    subflow: "running",
    "flowable-task": "info",
    errors: "error",
}
