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
            {{ data.value }}
        </div>
    </EdgeLabelRenderer>

    <EdgeLabelRenderer v-if="path?.length && addTarget">
        <button
            type="button"
            class="edge-add-button"
            :class="{
                'edge-add-button--visible': hovered || isDropTarget,
                'edge-add-button--standby': isDraggingNode && !isDropTarget,
                'edge-add-button--drop': isDropTarget,
            }"
            :style="{transform: `translate(${addButtonX}px, ${addButtonY}px) translate(-50%, -50%)`}"
            :aria-label="$t('topology-graph.add-task')"
            data-test="topology-edge-add-task"
            @click.stop="emit('add-task', addTarget)"
            @keydown.enter.stop.prevent="emit('add-task', addTarget)"
            @keydown.space.stop.prevent="emit('add-task', addTarget)"
            @mouseenter="hovered = true"
            @mouseleave="hovered = false"
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
    />
</template>

<script lang="ts" setup>
    import {computed, inject, ref} from "vue"
    import type {PropType} from "vue"
    import {getSmoothStepPath, EdgeLabelRenderer} from "@vue-flow/core"
    import Plus from "vue-material-design-icons/Plus.vue"
    import type {AddTaskTarget} from "../utils/vueFlowUtils"
    import {DRAGGING_NODE_INJECTION_KEY, DROP_EDGE_INJECTION_KEY} from "../injectionKeys"

    const props = defineProps({
        id: {type: String, default: undefined},
        data: {type: Object as PropType<any>, default: undefined},
        sourceX: {type: Number, default: undefined},
        sourceY: {type: Number, default: undefined},
        targetX: {type: Number, default: undefined},
        targetY: {type: Number, default: undefined},
        markerEnd: {type: String, default: undefined},
        sourcePosition: {type: String, default: undefined},
        targetPosition: {type: String, default: undefined},
    })

    const emit = defineEmits<{
        (event: "add-task", data: AddTaskTarget): void
    }>()

    const hovered = ref(false)

    const dropEdgeId = inject(DROP_EDGE_INJECTION_KEY, undefined)
    const isDropTarget = computed(() => Boolean(props.id) && dropEdgeId?.value === props.id)

    const draggingNode = inject(DRAGGING_NODE_INJECTION_KEY, undefined)
    const isDraggingNode = computed(() => Boolean(draggingNode?.value))

    // The graph already computed where a `+` on this edge should insert and relative to which
    // task — `undefined` when the edge sits on a read-only boundary or a cluster's own wiring.
    const addTarget = computed<AddTaskTarget | undefined>(() => props.data?.haveAdd)

    const classes = computed(() => {
        return props.data
            ? {
                "vue-flow__edge-path": true,
                ["stroke-" + props.data.color]: props.data.color,
                "unused-path": props.data.unused,
            }
            : {}
    })

    const path = computed(() => getSmoothStepPath(props as any))

    const showCaseLabel = computed(
        () => props.data?.relationType === "CHOICE" && Boolean(props.data?.value),
    )

    const CASE_LABEL_GAP = 18
    const caseLabelX = computed(() => {
        const tx = props.targetX ?? 0
        if (props.targetPosition === "left") return tx - CASE_LABEL_GAP
        if (props.targetPosition === "right") return tx + CASE_LABEL_GAP
        return tx
    })
    const caseLabelY = computed(() => {
        const ty = props.targetY ?? 0
        if (props.targetPosition === "top") return ty - CASE_LABEL_GAP
        if (props.targetPosition === "bottom") return ty + CASE_LABEL_GAP
        return ty
    })

    const addButtonX = computed(() => path.value?.[1] ?? 0)
    const addButtonY = computed(() => path.value?.[2] ?? 0)

    const labelAnchor = computed(() => {
        switch (props.targetPosition) {
        case "left": return "translate(-100%, -50%)"
        case "right": return "translate(0, -50%)"
        case "top": return "translate(-50%, -100%)"
        case "bottom": return "translate(-50%, 0)"
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
        transition: opacity 0.12s, color 0.12s;
    }

    /* The button itself must never take the pointer during a drag, or it would shadow the edge
       hit area the drop target is resolved from. */
    .edge-add-button--standby {
        opacity: 0.85;
    }

    .edge-add-button-dot {
        display: flex;
        align-items: center;
        justify-content: center;
        width: 1.125rem;
        height: 1.125rem;
        background: var(--ks-bg-elevated);
        border: 1px solid var(--ks-border-strong);
        border-radius: 50%;
        transition: border-color 0.12s, transform 0.15s ease, box-shadow 0.15s ease;
    }

    .edge-add-button--standby .edge-add-button-dot {
        transform: scale(0.8);
        border-style: dashed;
    }

    .edge-add-button--visible,
    .edge-add-button:focus-visible {
        opacity: 1;
        pointer-events: auto;
    }

    .edge-add-button--drop {
        color: var(--ks-text-link);
        /* The dragged card is z-index 1 in the same stacking context and comes later in the DOM,
           so the marker has to outrank it or it is buried under the card heading for it. */
        z-index: 10;
    }

    .edge-add-button--drop .edge-add-button-dot {
        background: var(--ks-bg-info);
        border-color: var(--ks-border-focus);
        transform: scale(1.3);
        animation: edge-drop-pulse 1.2s ease-in-out infinite;
    }

    @keyframes edge-drop-pulse {
        0%, 100% { box-shadow: 0 0 0 0 var(--ks-bg-info); }
        50% { box-shadow: 0 0 0 0.375rem var(--ks-bg-info); }
    }

    @media (prefers-reduced-motion: reduce) {
        .edge-add-button,
        .edge-add-button-dot {
            transition: none;
        }

        .edge-add-button--drop .edge-add-button-dot {
            animation: none;
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
