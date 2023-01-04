<script setup>
    import {Handle} from "@vue-flow/core"

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
</script>

<script>
    export default {
        inheritAttrs: false,
    }
</script>

<template>
    <Handle type="source" :position="sourcePosition" />
    <div class="dot" />
    <Handle type="target" :position="targetPosition" />
</template>

<style lang="scss">
    .vue-flow__node-dot {
        border: 0 !important;
        .vue-flow__handle {
            opacity: 0;
        }

        div.dot {
            position: absolute;
            top: 50%;
            transform: translate(0, -50%);
            border-radius: 50%;
            height: 5px;
            width: 5px;
            background-color: var(--bs-cyan);
        }
    }
</style>