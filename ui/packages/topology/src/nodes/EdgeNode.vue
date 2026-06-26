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
                transform: `translate(-50%, -50%) translate(${caseLabelX}px, ${caseLabelY}px)`,
            }"
        >
            {{ data.value }}
        </div>
    </EdgeLabelRenderer>
</template>

<script lang="ts" setup>
    import {computed} from "vue"
    import type {PropType} from "vue"
    import {getSmoothStepPath, EdgeLabelRenderer} from "@vue-flow/core"

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
</style>
