<template>
    <path
        v-if="path?.length"
        :id="id"
        :class="classes"
        :d="path[0]"
        :marker-end="markerEnd"
    />
</template>

<script lang="ts" setup>
    import {computed} from "vue"
    import type {PropType} from "vue"
    import {getSmoothStepPath} from "@vue-flow/core"

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

    defineOptions({inheritAttrs: false})
</script>

<style scoped>
    .stroke-danger { stroke: var(--ks-border-error); }
    .stroke-error { stroke: var(--ks-border-error); }
    .stroke-warning { stroke: var(--ks-status-warning); }
    .vue-flow__edge-path { stroke-dasharray: 1.5 3; }
</style>
