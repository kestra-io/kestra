<template>
    <div :class="classes">
        <Handle type="source" class="custom-handle" :position="sourcePosition" />
        <div class="dot" :class="classes">
            <CircleIcon :class="{'text-danger': data.node.branchType === 'ERROR'}" class="circle" alt="circle" :size="5" />
        </div>
        <Handle type="target" class="custom-handle" :position="targetPosition" />
    </div>
</template>

<script setup>
    import Utils from "../utils/utils";
</script>

<script>
    import {Handle} from "@vue-flow/core";
    import CircleIcon from "vue-material-design-icons/Circle.vue";

    export default {
        name: "Dot",
        components: {Handle, CircleIcon},
        inheritAttrs: false,
        props: {
            data: {
                type: Object,
                required: true,
            },
            sourcePosition: {
                type: String,
                required: true,
            },
            targetPosition: {
                type: String,
                required: true,
            },
            label: {
                type: String,
                default: undefined,
            },
        },
        computed: {
            classes() {
                return {
                    "unused-path": this.data.unused,
                    [Utils.afterLastDot(this.data.node.type)]: true,
                };
            },
        },
    }
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
