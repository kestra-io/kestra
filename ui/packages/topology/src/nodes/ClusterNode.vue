<template>
    <div :class="classes">
        <LaneHeader
            v-if="data.isFlowableLane"
            :taskNode="data.taskNode"
            :color="data.color"
            :height="NODE_SIZES.LANE_HEADER_HEIGHT"
            :childTaskIds="data.childTaskIds"
            :executionId="data.executionId"
            :isReadOnly="data.isReadOnly"
            :replayEnabled="replayEnabled"
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
        >
            <template #lead>
                <span
                    v-if="data.collaspsible"
                    class="circle-button lane-collapse"
                    :style="{backgroundColor: `var(--ks-topology-btn-${data.color})`}"
                    @click.stop="collapse()"
                >
                    <KsTooltip :content="$t('collapse')">
                        <UnfoldLessHorizontal class="button-icon" :alt="$t('collapse')" />
                    </KsTooltip>
                </span>
            </template>
        </LaneHeader>
        <div v-else class="cluster-heading" :style="{height: `${NODE_SIZES.LANE_HEADER_HEIGHT}px`}">
            <span
                class="cluster-badge text-color"
                :style="badgeStyle"
            >{{ clusterName }}</span>
            <div class="top-button-div">
                <span
                    v-if="data.canAddTrigger"
                    class="circle-button"
                    :style="{backgroundColor: `var(--ks-topology-btn-${data.color})`}"
                    data-test="topology-add-trigger"
                    @click="emit(EVENTS.ADD_TRIGGER)"
                >
                    <KsTooltip :content="$t('topology-graph.add-trigger')">
                        <Plus class="button-icon" />
                    </KsTooltip>
                </span>
                <span
                    v-if="data.collaspsible"
                    class="circle-button"
                    :style="{backgroundColor: `var(--ks-topology-btn-${data.color})`}"
                    @click="collapse()"
                >
                    <KsTooltip :content="$t('collapse')">
                        <UnfoldLessHorizontal class="button-icon" :alt="$t('collapse')" />
                    </KsTooltip>
                </span>
            </div>
        </div>
    </div>
</template>
<script setup lang="ts">
    import {computed} from "vue"
    import {KsTooltip} from "@kestra-io/design-system"
    import UnfoldLessHorizontal from "vue-material-design-icons/UnfoldLessHorizontal.vue"
    import Plus from "vue-material-design-icons/Plus.vue"
    import {EVENTS, CLUSTER_TAG_STATUS, NODE_SIZES} from "../utils/constants"
    import * as Utils from "../utils/utils"
    import LaneHeader, {type LaneTaskNode} from "./LaneHeader.vue"

    defineOptions({inheritAttrs: false})

    interface ClusterData {
        color: string;
        collaspsible?: boolean;
        canAddTrigger?: boolean;
        unused?: boolean;
        isFlowableLane?: boolean;
        isReadOnly?: boolean;
        executionId?: string;
        childTaskIds?: string[];
        taskNode: LaneTaskNode | null;
    }

    const props = defineProps<{
        id?: string;
        data: ClusterData;
        replayEnabled?: boolean;
        icons?: Record<string, unknown>;
        loadIcon?: (cls: string) => Promise<unknown>;
    }>()

    const emit = defineEmits([
        EVENTS.COLLAPSE,
        EVENTS.ADD_TRIGGER,
        EVENTS.EDIT,
        EVENTS.DELETE,
        EVENTS.DUPLICATE,
        EVENTS.SHOW_DESCRIPTION,
        EVENTS.SHOW_CONDITION,
        EVENTS.SHOW_LOGS,
        EVENTS.SHOW_OUTPUTS,
        EVENTS.REPLAY_TASK,
        EVENTS.ADD_ERROR,
    ])

    const badgeStyle = computed(() => {
        const status = CLUSTER_TAG_STATUS[props.data.color] ?? "info"
        return {
            backgroundColor: `color-mix(in srgb, var(--ks-status-${status}) 10%, var(--ks-bg-badge))`,
            color: `var(--ks-status-${status})`,
        }
    })

    const collapse = () => emit(EVENTS.COLLAPSE, props.id)

    const classes = computed(() => ({"unused-path": props.data.unused}))

    const clusterName = computed(() => {
        const taskNode = props.data.taskNode
        if (taskNode?.task?.type?.toString().endsWith("SubflowGraphTask")) {
            const subflowIdContainer = (taskNode.task.subflowId as Record<string, unknown> | undefined) ?? taskNode.task
            return `${subflowIdContainer.namespace} ${subflowIdContainer.flowId}`
        }
        return Utils.afterLastDot(props.id ?? "")
    })
</script>
<style scoped lang="scss">
    .circle-button {
        pointer-events: auto !important;
    }

    .button-icon {
        font-size: var(--ks-font-size-sm);
        transform: rotate(45deg);
    }

    .cluster-heading {
        display: flex;
        align-items: center;
        justify-content: space-between;
        gap: var(--ks-spacing-2);
        box-sizing: border-box;
    }

    .cluster-badge {
        position: relative;
        display: inline-block;
        max-width: 100%;
        border-radius: var(--ks-radius-base);
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
    }

    .text-color {
        font-size: var(--ks-font-size-xs);
        font-weight: 600;
        padding: var(--ks-spacing-1) var(--ks-spacing-2);
    }

    .top-button-div {
        display: flex;
        align-items: center;
    }

    .lane-collapse {
        flex-shrink: 0;
        color: var(--ks-white);
    }
</style>
