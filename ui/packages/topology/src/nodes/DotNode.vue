<template>
    <div :class="classes">
        <Handle type="source" class="custom-handle" :position="sourcePosition" />
        <div class="dot" :class="classes">
            <CircleIcon :class="{'text-danger': data.node.branchType === 'ERROR'}" class="circle" alt="circle" :size="5" />
        </div>
        <Handle type="target" class="custom-handle" :position="targetPosition" />
    </div>
</template>

<script setup lang="ts">
    import {computed} from "vue"
    import {Handle, Position} from "@vue-flow/core"
    import CircleIcon from "vue-material-design-icons/Circle.vue"
    import * as Utils from "../utils/utils"

    defineOptions({name: "Dot", inheritAttrs: false})

    const {data, sourcePosition, targetPosition} = defineProps<{
        data: any;
        sourcePosition: Position;
        targetPosition: Position;
        label?: string;
    }>()

    const classes = computed(() => ({
        "unused-path": data.unused,
        [Utils.afterLastDot(data.node.type) as string]: true,
    }))
</script>

<style scoped>
    .custom-handle {
        visibility: hidden;
    }

    .dot {
        display: flex;
        flex-direction: column;
        align-items: center;
        font-size: 5px;

        &.GraphClusterRoot { color: var(--ks-border-created); }
        &.GraphClusterFinally { color: var(--ks-border-warning); }
        &.GraphClusterEnd { color: var(--ks-border-active); }
    }
</style>
