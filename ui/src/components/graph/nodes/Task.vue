<script setup>
    import {Handle} from "@vue-flow/core"
    import TreeTaskNode from "../TreeTaskNode.vue";

    const emit = defineEmits(["follow", "mouseover", "mouseleave", "edit"])

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

    const forwardEvent = (type, event) => {
        emit(type, event);
    };
</script>

<script>
    export default {
        inheritAttrs: false,
    }
</script>

<template>
    <Handle type="source" :position="sourcePosition" />
    <TreeTaskNode
        :n="data.node"
        :namespace="data.namespace"
        :flow-id="data.flowId"
        :revision="data.revision"
        @follow="forwardEvent('follow', $event)"
        @edit="forwardEvent('edit', $event)"
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