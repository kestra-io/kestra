<template>
    <path
        v-if="path?.length"
        :id="id"
        :class="classes"
        :style="data.haveDashArray ? {strokeDasharray: '2'} : {}"
        :d="path[0]"
        :marker-end="markerEnd"
    />

    <EdgeLabelRenderer style="z-index: 10" v-if="path?.length">
        <div
            :style="{
                pointerEvents: 'all',
                position: 'absolute',
                transform: `translate(-50%, -50%) translate(${path[1] + labelXOffset}px,${path[2] + labelYOffset}px)`,
            }"
        >
            <KsTooltip :content="title">
                <AddTaskButton
                    v-if="data.haveAdd"
                    :addTask="true"
                    @click="$emit(EVENTS.ADD_TASK, data.haveAdd)"
                    :class="{'text-danger': data.color}"
                />
            </KsTooltip>
        </div>
    </EdgeLabelRenderer>
</template>

<script lang="ts" setup>
    import {computed} from "vue"
    import type {PropType} from "vue"
    import {useI18n} from "vue-i18n"
    import {EdgeLabelRenderer, getSmoothStepPath} from "@vue-flow/core"
    import AddTaskButton from "../buttons/AddTaskButton.vue"
    import {EVENTS} from "../utils/constants"
    import {KsTooltip} from "@kestra-io/design-system"

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

    let t = (s: string) => s
    try {
        const {t: realT} = useI18n()
        t = realT
    } catch {
        // not in an i18n context
    }

    const title = computed(() => {
        const {haveAdd} = props.data ?? {}
        return `${t("add task")} ${haveAdd?.length === 2 ? `${t(haveAdd[1])} ${haveAdd[0]}` : ""}`.trim()
    })

    const path = computed(() => getSmoothStepPath(props as any))

    const isVertical = computed(() => {
        const dx = (props.targetX ?? 0) - (props.sourceX ?? 0)
        const dy = (props.targetY ?? 0) - (props.sourceY ?? 0)
        return Math.abs(dy) >= Math.abs(dx)
    })

    const OFFSET = 14
    const labelYOffset = computed(() => {
        if (!isVertical.value) return 0
        const boundary = props.data?.edgeBoundary
        if (boundary === "top") return -OFFSET
        if (boundary === "bottom") return OFFSET
        return 0
    })

    const labelXOffset = computed(() => 0)

    defineOptions({inheritAttrs: false})
</script>

<style scoped>
    .stroke-danger { stroke: var(--ks-border-error); }
    .stroke-error { stroke: var(--ks-border-error); }
    .stroke-warning { stroke: var(--ks-border-warning); }
    .vue-flow__edge-path { stroke-dasharray: 3 5; }
</style>
