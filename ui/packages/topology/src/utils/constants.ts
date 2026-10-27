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
    LOOP_STEP: "loopStep",
    LOOP_SCOPE_FAILED: "loopScopeFailed",
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
    TASK_HEIGHT_EXECUTION: 80,
    TRIGGER_WIDTH: 218,
    TRIGGER_HEIGHT: 56,
    DOT_WIDTH: 5,
    DOT_HEIGHT: 5,
    COLLAPSED_CLUSTER_WIDTH: 150,
    COLLAPSED_CLUSTER_HEIGHT: 40,
    TRIGGER_CLUSTER_WIDTH: 350,
    TRIGGER_CLUSTER_HEIGHT: 180,
    // dagre lays a cluster out tightly around its children — it has no notion of a label's own
    // height. Every lane header claims this much of the cluster's own box instead, and every
    // direct child is shifted down by the same amount so nothing sits under it.
    LANE_HEADER_HEIGHT: 32,
} as const

// dagre's own default. Spelled out because a lane's header is added to the cluster box after
// layout, so the separation has to be widened by exactly that much for the gap between two
// ranks to survive it (kestra-io/kestra#19787).
export const DAGRE_RANK_SEP = 50
export const DAGRE_NODE_SEP = 50

// vue-flow's own default: how far a smooth-step path runs straight out of a handle before it may
// turn, so a turn placed inside it would be ignored.
export const SMOOTH_STEP_OFFSET = 20

// vue-flow's default smooth-step corner radius.
export const SMOOTH_STEP_RADIUS = 5

// How far inside its lane border a loop's return path runs, parallel to it.
export const LOOP_RETURN_INSET = 12

// How far from its start marker's centre a loop's return arrowhead stops, so it does not cover the marker.
export const LOOP_RETURN_HEAD_GAP = 12

// Below PILL, only a minimal glanceable pill renders; above EXPANDED, the node also renders the
// `details` slot as an overlay. Neither ever changes `NODE_SIZES`, so crossing either threshold
// only repaints the node — it never re-runs dagre.
export const ZOOM_LOD = {
    PILL: 0.5,
    EXPANDED: 1.3,
} as const

// Everything drawn inside a pill shrinks with the canvas, so by the time a 13-task execution has
// been fitted to the view (~0.29) its 1px state ring is sub-pixel and the row reads as bare plugin
// icons (kestra-io/kestra#21619). The ring and the status glyph are scaled back by the inverse of
// the zoom, quantised so the nodes repaint a handful of times across the range rather than on
// every wheel tick, and capped — separately, because the two have different room to grow.
export const PILL_COMPENSATION = {
    /** Whole pixels: a browser snaps a fractional border width to a device pixel anyway, so a
     *  finer step would repaint every node without changing anything that gets drawn. */
    STEP: 1,
    /** The ring is the pill's border, which `box-sizing: border-box` thickens inwards inside the
     *  pill's fixed 2.5rem box, so widening it moves nothing and can be compensated nearly fully:
     *  4 holds it at ~1 screen pixel from the pill threshold down to 0.25 zoom. */
    RING_MAX: 4,
    /** The glyph only annotates the pill, so it has to stay smaller than the pill it sits on and
     *  cannot reach a constant screen size — this bounds it at a legible compromise instead. */
    GLYPH_MAX: 2.5,
} as const

/** Inverse-zoom scale for a pill's ring, quantised to {@link PILL_COMPENSATION.STEP}, never below
 *  1 and never above {@link PILL_COMPENSATION.RING_MAX}. A zoom that is not a usable positive
 *  number scales nothing. */
export function pillZoomCompensation(zoom: number): number {
    if (!Number.isFinite(zoom) || zoom <= 0) return 1
    const bounded = Math.min(1 / zoom, PILL_COMPENSATION.RING_MAX)
    return Math.max(1, Math.round(bounded / PILL_COMPENSATION.STEP) * PILL_COMPENSATION.STEP)
}

export type LodLevel = "pill" | "default" | "expanded"

export const CLUSTER_TAG_STATUS: Record<string, string> = {
    triggers: "success",
    subflow: "running",
    "flowable-task": "info",
    errors: "error",
}
