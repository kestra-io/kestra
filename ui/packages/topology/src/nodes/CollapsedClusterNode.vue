<template>
    <Handle type="source" :position="sourcePosition" />
    <LaneHeader
        v-if="data.isFlowableLane"
        class="collapsed-lane"
        layout="card"
        :taskNode="data.taskNode ?? null"
        :color="data.color"
        :childTaskIds="data.childTaskIds"
        :executionId="data.executionId"
        :isReadOnly="data.isReadOnly"
        :icons="icons"
        :loadIcon="loadIcon"
        @edit="emit(EVENTS.EDIT, $event)"
        @delete="emit(EVENTS.DELETE, $event)"
        @duplicate="emit(EVENTS.DUPLICATE, $event)"
        @showDescription="emit(EVENTS.SHOW_DESCRIPTION, $event)"
        @showCondition="emit(EVENTS.SHOW_CONDITION, $event)"
        @showLogs="emit(EVENTS.SHOW_LOGS, $event)"
        @showOutputs="emit(EVENTS.SHOW_OUTPUTS, $event)"
        @replayTask="emit(EVENTS.REPLAY_TASK, $event)"
        @addError="emit(EVENTS.ADD_ERROR, $event)"
        @loopStep="emit(EVENTS.LOOP_STEP, $event)"
        @loopScopeFailed="emit(EVENTS.LOOP_SCOPE_FAILED, $event)"
    >
        <template #lead>
            <span
                v-if="expandable"
                class="circle-button lane-expand"
                :style="{backgroundColor: `var(--ks-topology-btn-${data.color})`}"
                @click.stop="emit(EVENTS.EXPAND, {id})"
            >
                <KsTooltip :content="$t('expand')">
                    <UnfoldMoreHorizontal class="button-icon" alt="Expand lane" />
                </KsTooltip>
            </span>
        </template>
        <template #taskActions="taskActionProps">
            <slot name="taskActions" v-bind="taskActionProps" />
        </template>
        <template #loopScope="loopScopeProps">
            <slot name="loopScope" v-bind="loopScopeProps" />
        </template>
    </LaneHeader>
    <div v-else class="collapsed-cluster-node">
        <span
            class="cluster-badge"
            :style="badgeStyle"
        >{{ Utils.afterLastDot(id ?? "") }}</span>
        <div class="top-button-div">
            <span
                v-if="expandable"
                class="circle-button"
                :style="{backgroundColor: `var(--ks-topology-btn-${data.color})`}"
                @click="emit(EVENTS.EXPAND, {id})"
            >
                <KsTooltip :content="$t('expand')">
                    <UnfoldMoreHorizontal class="button-icon" alt="Expand task" />
                </KsTooltip>
            </span>
        </div>
    </div>
    <Handle type="target" :position="targetPosition" />
</template>

<script setup lang="ts">
    import {computed} from "vue"
    import {Handle, Position} from "@vue-flow/core"
    import UnfoldMoreHorizontal from "vue-material-design-icons/UnfoldMoreHorizontal.vue"
    import {KsTooltip, type PluginIconData, type PluginIconMap} from "@kestra-io/design-system"
    import {EVENTS, CLUSTER_TAG_STATUS} from "../utils/constants"
    import * as Utils from "../utils/utils"
    import LaneHeader, {type LaneTaskNode} from "./LaneHeader.vue"

    defineOptions({inheritAttrs: false})

    interface CollapsedClusterData {
        color: string;
        expandable?: boolean;
        isFlowableLane?: boolean;
        isReadOnly?: boolean;
        executionId?: string;
        childTaskIds?: string[];
        taskNode?: LaneTaskNode | null;
    }

    const {id, sourcePosition, targetPosition, data} = defineProps<{
        id?: string;
        sourcePosition?: Position;
        targetPosition?: Position;
        data: CollapsedClusterData;
        icons?: PluginIconMap;
        loadIcon?: (cls: string) => Promise<PluginIconData | undefined>;
    }>()

    const emit = defineEmits([
        EVENTS.EXPAND,
        EVENTS.EDIT,
        EVENTS.DELETE,
        EVENTS.DUPLICATE,
        EVENTS.SHOW_DESCRIPTION,
        EVENTS.SHOW_CONDITION,
        EVENTS.SHOW_LOGS,
        EVENTS.SHOW_OUTPUTS,
        EVENTS.REPLAY_TASK,
        EVENTS.ADD_ERROR,
        EVENTS.LOOP_STEP,
        EVENTS.LOOP_SCOPE_FAILED,
    ])

    const expandable = computed(() => data?.expandable || false)

    const badgeStyle = computed(() => {
        const status = CLUSTER_TAG_STATUS[data.color] ?? "info"
        return {
            backgroundColor: `color-mix(in srgb, var(--ks-status-${status}) 10%, var(--ks-bg-badge))`,
            color: `var(--ks-status-${status})`,
        }
    })
</script>

<style lang="scss" scoped>
    .collapsed-lane {
        width: 100%;
        height: 100%;
        box-sizing: border-box;
        border-radius: var(--ks-radius-base);
    }

    .lane-expand {
        flex-shrink: 0;
        color: var(--ks-white);
    }

    .collapsed-cluster-node {
        position: relative;
        display: flex;
        align-items: center;
        width: 100%;
        height: 100%;
        padding: var(--ks-spacing-2);
        box-sizing: border-box;
    }

    .cluster-badge {
        display: flex;
        flex: 1;
        align-items: center;
        justify-content: center;
        gap: var(--ks-spacing-1);
        padding: var(--ks-spacing-1) var(--ks-spacing-2);
        border-radius: var(--ks-radius-base);
        font-size: var(--ks-font-size-xs);
        font-weight: 600;
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
    }

    .circle-button {
        pointer-events: auto !important;
    }

    .button-icon {
        font-size: var(--ks-font-size-sm);
        transform: rotate(45deg);
    }
</style>
