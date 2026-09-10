<template>
    <ul class="structural-impact" :aria-label="$t('failureDebugPanel.structuralImpact.title')">
        <li
            v-for="node in nodes"
            :key="node.taskRun.id"
            class="structural-node"
            :class="{'is-focused': node.taskRun.id === focusedId}"
        >
            <button
                type="button"
                class="structural-node__button"
                :aria-current="node.taskRun.id === focusedId ? 'true' : undefined"
                @click="node.taskRun.id !== focusedId && emit('focus', node.taskRun.id)"
            >
                <span class="structural-node__icon">
                    <TaskIcon :cls="taskTypeById[node.taskRun.id]" onlyIcon :loadIcon="pluginsStore.loadIcon" />
                </span>
                <span class="structural-node__body">
                    <code class="structural-node__name">{{ node.taskRun.taskId }}</code>
                    <span class="structural-node__relation">{{ $t(`failureDebugPanel.structuralImpact.relation.${node.relation}`) }}</span>
                </span>
                <KsExecutionStatus size="small" :status="node.taskRun.state.current" tabindex="-1" />
            </button>
        </li>
    </ul>
</template>

<script setup lang="ts">
    import {computed} from "vue"
    import {KsExecutionStatus} from "@kestra-io/design-system"
    import TaskIcon from "../../plugins/TaskIcon.vue"
    import * as FlowUtils from "../../../utils/flowUtils"
    import {usePluginsStore} from "../../../stores/plugins"
    import type {StructuralNode} from "./types"

    const props = defineProps<{
        nodes: StructuralNode[]
        focusedId?: string
        flow?: unknown
    }>()

    const emit = defineEmits<{
        focus: [taskRunId: string]
    }>()

    const pluginsStore = usePluginsStore()

    const taskTypeById = computed<Record<string, string | undefined>>(() =>
        Object.fromEntries(
            props.nodes.map((node) => [node.taskRun.id, FlowUtils.findTaskById(props.flow, node.taskRun.taskId)?.type]),
        ),
    )
</script>

<style scoped lang="scss">
    .structural-impact {
        display: flex;
        flex-direction: column;
        gap: var(--ks-spacing-1);
        margin: 0;
        padding: 0;
        list-style: none;
    }

    .structural-node__button {
        display: flex;
        align-items: center;
        width: 100%;
        gap: var(--ks-spacing-3);
        padding: var(--ks-spacing-2) var(--ks-spacing-3);
        background: var(--ks-bg-surface);
        border: 1px solid var(--ks-border-default);
        border-radius: var(--ks-radius-base);
        cursor: pointer;
        text-align: left;

        &:hover {
            background: var(--ks-bg-hover);
        }
    }

    .is-focused .structural-node__button {
        background: var(--ks-status-background-failed);
        border-color: var(--ks-status-border-failed);
        cursor: default;
    }

    .structural-node__icon {
        flex-shrink: 0;
        display: inline-flex;
        align-items: center;
        justify-content: center;
        width: 1.5rem;
        height: 1.5rem;
        padding: var(--ks-spacing-1);
        border: 1px solid var(--ks-border-default);
        border-radius: 0.5rem;
        background: var(--ks-white);
    }

    .structural-node__body {
        display: flex;
        flex-direction: column;
        min-width: 0;
        flex: 1;
    }

    .structural-node__name {
        color: var(--ks-text-primary);
        font-size: var(--ks-font-size-sm);
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
    }

    .structural-node__relation {
        color: var(--ks-text-secondary);
        font-size: var(--ks-font-size-xs);
    }
</style>
