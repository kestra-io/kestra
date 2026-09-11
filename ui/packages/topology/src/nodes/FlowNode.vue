<template>
    <Handle type="source" :position="sourcePosition" />
    <button type="button" class="flow-node" data-test="topology-flow-node" @click="emit(EVENTS.EDIT_FLOW)">
        <FileCogOutline class="flow-node-icon" />
        <span class="flow-node-text">
            <span class="flow-node-id">{{ data.flowId }}</span>
            <span class="flow-node-namespace">{{ data.namespace }}</span>
        </span>
    </button>
    <Handle type="target" :position="targetPosition" />
</template>

<script lang="ts" setup>
    import {Handle, Position} from "@vue-flow/core"
    import FileCogOutline from "vue-material-design-icons/FileCogOutline.vue"
    import {EVENTS} from "../utils/constants"

    defineOptions({name: "FlowNode", inheritAttrs: false})

    withDefaults(
        defineProps<{
            data: {flowId?: string; namespace?: string};
            sourcePosition?: Position;
            targetPosition?: Position;
        }>(),
        {sourcePosition: Position.Bottom, targetPosition: Position.Top},
    )

    const emit = defineEmits([EVENTS.EDIT_FLOW])
</script>

<style scoped lang="scss">
    .flow-node {
        /* The node is neither draggable nor selectable, so vue-flow gives its wrapper
           `pointer-events: none`; the card opts back in so it stays clickable. */
        pointer-events: auto;
        display: flex;
        align-items: center;
        gap: var(--ks-spacing-2);
        width: 218px;
        height: 56px;
        padding: var(--ks-spacing-2) var(--ks-spacing-3);
        text-align: left;
        background: var(--ks-bg-elevated);
        border: 1px dashed var(--ks-border-strong);
        border-radius: var(--ks-radius-base);
        box-shadow: 0 2px 4px var(--ks-shadow-surface);
        cursor: pointer;
        transition: border-color 0.15s, background-color 0.15s;

        &:hover {
            background: var(--ks-bg-hover-elevated);
            border-color: var(--ks-border-focus);
        }

        &:focus-visible {
            outline: 2px solid var(--ks-border-focus);
            outline-offset: 2px;
        }
    }

    .flow-node-icon {
        display: flex;
        flex-shrink: 0;
        color: var(--ks-icon-default);
    }

    .flow-node-text {
        display: flex;
        flex-direction: column;
        min-width: 0;
    }

    .flow-node-id {
        font-size: var(--ks-font-size-sm);
        color: var(--ks-text-primary);
        overflow: hidden;
        white-space: nowrap;
        text-overflow: ellipsis;
    }

    .flow-node-namespace {
        font-size: var(--ks-font-size-xs);
        color: var(--ks-text-secondary);
        overflow: hidden;
        white-space: nowrap;
        text-overflow: ellipsis;
    }
</style>
