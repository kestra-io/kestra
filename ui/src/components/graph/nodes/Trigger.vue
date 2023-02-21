<script setup>
    import {Handle} from "@vue-flow/core"
    import TreeTriggerNode from "../TreeTriggerNode.vue";

    const emit = defineEmits(["mouseover", "mouseleave"])

    const props = defineProps({
        sourcePosition: {
            type: String,
            required: true
        },
        targetPosition: {
            type: String,
            required: true
        },
        data: {
            type: Object,
            required: true
        },
    })

    const mouseover = () => {
        emit("mouseover", props.data.node);
    };

    const mouseleave = () => {
        emit("mouseleave", props.data.node);
    };
</script>

<script>
    export default {
        inheritAttrs: false,
    }
</script>

<template>
    <Handle type="source" :position="sourcePosition" />
    <TreeTriggerNode
        :n="data.node"
        :namespace="data.namespace"
        :flow-id="data.flowId"
        :revision="data.revision"
        @mouseover="mouseover"
        @mouseleave="mouseleave"
    />
    <Handle type="target" :position="targetPosition" />
</template>

<style lang="scss">
    .vue-flow__node-task {
        border: 1px solid var(--bs-border-color);
    }
</style>