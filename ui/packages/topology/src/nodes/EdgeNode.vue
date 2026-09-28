<template>
    <path
        v-if="path?.length"
        :id="id"
        :class="classes"
        :d="path[0]"
        :marker-end="markerEnd"
    />

    <EdgeLabelRenderer v-if="path?.length && showCaseLabel">
        <div
            class="edge-case-label"
            :style="{
                transform: `${labelAnchor} translate(${caseLabelX}px, ${caseLabelY}px)`,
            }"
        >
            {{ data?.value }}
        </div>
    </EdgeLabelRenderer>

    <EdgeLabelRenderer v-if="path?.length && addTarget">
        <button
            type="button"
            class="edge-add-button"
            :class="{
                'edge-add-button--ambient': isAmbient,
                'edge-add-button--visible': hovered || isDropTarget,
                'edge-add-button--standby': isDraggingNode && !isDropTarget,
                'edge-add-button--drop': isDropTarget,
            }"
            :style="{transform: `translate(${addButtonX}px, ${addButtonY}px) translate(-50%, -50%)`}"
            :aria-label="$t('topology-graph.add-task')"
            data-test="topology-edge-add-task"
            @click.stop="emit('add-task', addTarget)"
            @mouseenter="hovered = true"
            @mouseleave="hovered = false"
            @dragenter.prevent="emit('drag-over-edge', id)"
            @dragover.prevent
            @dragleave="emit('drag-over-edge', undefined)"
            @drop.prevent="onDrop"
        >
            <span class="edge-add-button-dot"><Plus :size="12" /></span>
        </button>
    </EdgeLabelRenderer>

    <path
        v-if="path?.length && addTarget"
        class="edge-hit-area"
        :data-edge-id="id"
        :d="path[0]"
        @mouseenter="hovered = true"
        @mouseleave="hovered = false"
        @dragover.prevent
        @drop.prevent="onDrop"
    />
</template>

<script lang="ts" setup>
    import {computed, inject, ref} from "vue"
    import type {PropType} from "vue"
    import {getSmoothStepPath, EdgeLabelRenderer, Position} from "@vue-flow/core"
    import Plus from "vue-material-design-icons/Plus.vue"
    import type {AddTaskTarget} from "../utils/vueFlowUtils"
    import {
        CANVAS_HOVERED_INJECTION_KEY,
        DRAGGING_NODE_INJECTION_KEY,
        DROP_EDGE_INJECTION_KEY,
    } from "../injectionKeys"

    interface EdgeData {
        haveAdd?: AddTaskTarget | false;
        color?: string | null;
        unused?: boolean;
        value?: string;
        relationType?: string;
        fansOut?: boolean;
        laneGap?: {leaving: number; entering: number};
        bypass?: "source" | "target";
    }

    const props = defineProps({
        id: {type: String, default: undefined},
        data: {type: Object as PropType<EdgeData>, default: undefined},
        sourceX: {type: Number, required: true},
        sourceY: {type: Number, required: true},
        targetX: {type: Number, required: true},
        targetY: {type: Number, required: true},
        markerEnd: {type: String, default: undefined},
        sourcePosition: {type: String as PropType<Position>, default: undefined},
        targetPosition: {type: String as PropType<Position>, default: undefined},
    })

    const emit = defineEmits<{
        (event: "add-task", data: AddTaskTarget): void
        (event: "drop-task", payload: {taskId: string; target: AddTaskTarget}): void
        (event: "drag-over-edge", edgeId: string | undefined): void
    }>()

    function onDrop(event: DragEvent) {
        const taskId = event.dataTransfer?.getData("text/plain")
        emit("drag-over-edge", undefined)
        if (taskId && addTarget.value) emit("drop-task", {taskId, target: addTarget.value})
    }

    const hovered = ref(false)

    const dropEdgeId = inject(DROP_EDGE_INJECTION_KEY, undefined)
    const isDropTarget = computed(() => Boolean(props.id) && dropEdgeId?.value === props.id)

    const draggingNode = inject(DRAGGING_NODE_INJECTION_KEY, undefined)
    const isDraggingNode = computed(() => Boolean(draggingNode?.value))

    // Being anywhere on the canvas hints at every landing place; the drag state is louder because
    // by then the user is committed to putting something down.
    const canvasHovered = inject(CANVAS_HOVERED_INJECTION_KEY, undefined)
    const isAmbient = computed(
        () => Boolean(canvasHovered?.value) && !isDraggingNode.value && !hovered.value && !isDropTarget.value,
    )

    // The graph already computed where a `+` on this edge should insert and relative to which
    // task — `undefined` when the edge sits on a read-only boundary or a cluster's own wiring.
    const addTarget = computed<AddTaskTarget | undefined>(() => props.data?.haveAdd || undefined)

    const classes = computed(() => {
        return props.data
            ? {
                "vue-flow__edge-path": true,
                ["stroke-" + props.data.color]: props.data.color,
                "unused-path": props.data.unused,
            }
            : {}
    })

    const flowsHorizontally = computed(() => props.targetPosition === "left" || props.targetPosition === "right")

    // Where the edge turns. Left to itself it turns level with the border it just crossed, so the
    // run — and the add button sitting on it — reads as neither inside the lane nor outside it.
    // Centred on the gap the user actually sees, it is unambiguously between the two boxes.
    const laneTurn = computed<number | undefined>(() => {
        const gap = props.data?.laneGap
        const bypass = props.data?.bypass
        if (!gap && !bypass) return undefined
        const from = flowsHorizontally.value ? props.sourceX ?? 0 : props.sourceY ?? 0
        const to = flowsHorizontally.value ? props.targetX ?? 0 : props.targetY ?? 0
        const low = Math.min(from, to) + SMOOTH_STEP_OFFSET
        const high = Math.max(from, to) - SMOOTH_STEP_OFFSET
        if (low > high) return undefined
        if (bypass) return bypass === "source" ? (from < to ? low : high) : (from < to ? high : low)
        if (!gap) return undefined
        const centre = ((from + Math.sign(to - from) * (gap.leaving ?? 0)) + (to - Math.sign(to - from) * (gap.entering ?? 0))) / 2
        return Math.min(Math.max(centre, low), high)
    })

    const path = computed(() => getSmoothStepPath({
        ...props,
        centerX: flowsHorizontally.value ? laneTurn.value : undefined,
        centerY: flowsHorizontally.value ? undefined : laneTurn.value,
    }))

    const showCaseLabel = computed(
        () => props.data?.relationType === "CHOICE" && Boolean(props.data?.value),
    )

    // vue-flow's own default: how far a smooth-step path runs straight out of a handle before it
    // may turn, so a turn placed inside it would be ignored.
    const SMOOTH_STEP_OFFSET = 20

    const CASE_LABEL_GAP = 18
    const caseLabelX = computed(() => {
        const tx = props.targetX
        if (props.targetPosition === Position.Left) return tx - CASE_LABEL_GAP
        if (props.targetPosition === Position.Right) return tx + CASE_LABEL_GAP
        return tx
    })
    const caseLabelY = computed(() => {
        const ty = props.targetY
        if (props.targetPosition === Position.Top) return ty - CASE_LABEL_GAP
        if (props.targetPosition === Position.Bottom) return ty + CASE_LABEL_GAP
        return ty
    })

    // A fan-out's one button belongs on the run every branch still shares — between the lane's own
    // marker and the split — rather than on the drop into whichever branch happens to carry it.
    const splitPoint = computed(() => {
        const from = flowsHorizontally.value ? props.sourceX ?? 0 : props.sourceY ?? 0
        const turn = laneTurn.value ?? (((flowsHorizontally.value ? props.targetX ?? 0 : props.targetY ?? 0) + from) / 2)
        const middle = (from + turn) / 2
        return flowsHorizontally.value
            ? {x: middle, y: props.sourceY ?? 0}
            : {x: props.sourceX ?? 0, y: middle}
    })

    const addButtonX = computed(() => (props.data?.fansOut ? splitPoint.value.x : path.value?.[1] ?? 0))
    const addButtonY = computed(() => (props.data?.fansOut ? splitPoint.value.y : path.value?.[2] ?? 0))

    const labelAnchor = computed(() => {
        switch (props.targetPosition) {
        case Position.Left: return "translate(-100%, -50%)"
        case Position.Right: return "translate(0, -50%)"
        case Position.Top: return "translate(-50%, -100%)"
        case Position.Bottom: return "translate(-50%, 0)"
        default: return "translate(-50%, -50%)"
        }
    })

    defineOptions({inheritAttrs: false})
</script>

<style scoped>
    .stroke-danger { stroke: var(--ks-border-error); }
    .stroke-error { stroke: var(--ks-border-error); }
    .stroke-warning { stroke: var(--ks-status-warning); }
    .vue-flow__edge-path { stroke-dasharray: 1.5 3; }

    .edge-case-label {
        position: absolute;
        pointer-events: none;
        font-size: var(--ks-font-size-xs);
        font-family: var(--ks-font-family-mono);
        line-height: 1;
        padding: var(--ks-spacing-1) var(--ks-spacing-2);
        border-radius: var(--ks-radius-sm);
        background: var(--ks-bg-surface);
        color: var(--ks-text-primary);
        border: 1px solid var(--ks-border-default);
        white-space: nowrap;
        max-width: 10rem;
        overflow: hidden;
        text-overflow: ellipsis;
    }

    .edge-hit-area {
        fill: none;
        stroke: transparent;
        stroke-width: 20;
        cursor: pointer;
    }

    .edge-add-button {
        /* vue-flow paints .vue-flow__nodes (z-index 0) after the label layer, so without this lift
           an edge midpoint falling inside an adjacent node buries the button. */
        position: absolute;
        z-index: 1;
        display: flex;
        align-items: center;
        justify-content: center;
        /* The disc stays small so it does not hide the graph, but the target keeps the 24px
           WCAG 2.5.8 minimum by padding around it. */
        width: 1.5rem;
        height: 1.5rem;
        padding: 0;
        background: none;
        border: none;
        color: var(--ks-icon-default);
        cursor: pointer;
        opacity: 0;
        pointer-events: none;
        transition: opacity var(--ks-duration-fast) var(--ks-ease-standard), color var(--ks-duration-fast) var(--ks-ease-standard);
    }

    /* A quiet hint at rest: every edge that accepts a task, faint enough not to compete with the
       graph, and clickable so the nearest one can be used without hunting for its edge first. */
    .edge-add-button--ambient {
        opacity: 0.65;
        pointer-events: auto;
    }

    /* Shown for the whole drag so the eligible landing points are visible before the pointer
       reaches one; the edge's hit area underneath is what actually receives the drop. */
    .edge-add-button--standby {
        opacity: 1;
        color: var(--ks-text-link);
        /* It is the drop target, so it takes the pointer and grows an invisible margin to hit.
           Nodes share its z-index and come later in the DOM, so it also has to outrank them or a
           marker overlapping a card is both invisible and unreachable. */
        pointer-events: auto;
        width: 2.5rem;
        height: 2.5rem;
        z-index: 10;
    }

    .edge-add-button-dot {
        /* Purely decorative: entering it would count as leaving the button and cancel the target. */
        pointer-events: none;
        display: flex;
        align-items: center;
        justify-content: center;
        width: 1.125rem;
        height: 1.125rem;
        background: var(--ks-bg-elevated);
        border: 1px solid var(--ks-border-strong);
        border-radius: 50%;
        transition: border-color var(--ks-duration-fast) var(--ks-ease-standard), transform var(--ks-duration-fast) var(--ks-ease-standard);
    }

    .edge-add-button--standby .edge-add-button-dot {
        background: var(--ks-bg-info);
        border-color: var(--ks-border-info);
    }

    .edge-add-button--visible,
    .edge-add-button:focus-visible {
        opacity: 1;
        pointer-events: auto;
    }

    /* Same drop-target treatment as the No-code block cards: link colour plus a dashed edge. */
    .edge-add-button--drop {
        color: var(--ks-text-link);
        /* The dragged card is z-index 1 in the same stacking context and comes later in the DOM,
           so the marker has to outrank it or it is buried under the card heading for it. */
        z-index: 10;
    }

    .edge-add-button--drop .edge-add-button-dot {
        background: var(--ks-bg-info);
        border-color: var(--ks-text-link);
        border-style: dashed;
        transform: scale(1.3);
    }

    @media (prefers-reduced-motion: reduce) {
        .edge-add-button,
        .edge-add-button-dot {
            transition: none;
        }
    }

    .edge-add-button:hover,
    .edge-add-button:focus-visible {
        color: var(--ks-text-link);
    }

    .edge-add-button:hover .edge-add-button-dot,
    .edge-add-button:focus-visible .edge-add-button-dot {
        border-color: var(--ks-border-focus);
    }
</style>
