<template>
    <div class="dot-node" :class="classes">
        <Handle type="source" class="custom-handle" :position="sourcePosition ?? Position.Right" />
        <div class="dot" :class="classes">
            <CircleIcon :class="{'text-danger': data.node.branchType === 'ERROR'}" class="circle" alt="circle" :size="5" />
        </div>
        <Handle type="target" class="custom-handle" :position="targetPosition ?? Position.Left" />
    </div>
</template>

<script setup lang="ts">
    import {computed} from "vue"
    import {Handle, Position} from "@vue-flow/core"
    import CircleIcon from "vue-material-design-icons/Circle.vue"
    import * as Utils from "../utils/utils"
    import type {MinimalNode} from "../utils/vueFlowUtils"

    defineOptions({name: "Dot", inheritAttrs: false})

    const {data, sourcePosition, targetPosition} = defineProps<{
        data: {node: Pick<MinimalNode, "type" | "branchType">; unused?: boolean};
        sourcePosition?: Position;
        targetPosition?: Position;
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

    .dot-node {
        position: absolute;
        inset: 0;
        display: flex;
        align-items: center;
        justify-content: center;
    }

    /* A joint in the line, not something to press: filled at full strength it outweighed the
       1.5px dashed stroke it punctuates, and read as the affordance the `+` actually is. */
    .dot {
        display: flex;
        flex-direction: column;
        align-items: center;
        color: var(--ks-topology-dash);
        opacity: 0.4;
    }
</style>
