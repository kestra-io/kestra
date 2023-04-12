<script setup>
    import {Handle} from "@vue-flow/core"
    import TreeTaskNode from "../TreeTaskNode.vue";

    const emit = defineEmits(["follow", "mouseover", "mouseleave", "edit", "delete", "addFlowableError"])

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
        isReadOnly: {
            type: Boolean,
            required: true
        },
        isAllowedEdit: {
            type: Boolean,
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
        :is-flowable="data.isFlowable"
        :is-read-only="props.isReadOnly"
        :is-allowed-edit="props.isAllowedEdit"
        @follow="forwardEvent('follow', $event)"
        @edit="forwardEvent('edit', $event)"
        @delete="forwardEvent('delete', $event)"
        @addFlowableError="forwardEvent('addFlowableError', $event)"
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