<template>
    <div :class="classes">
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
                    <UnfoldLessHorizontal class="button-icon" alt="Collapse task" />
                </KsTooltip>
            </span>
        </div>
    </div>
</template>
<script setup lang="ts">
    import {computed} from "vue"
    import UnfoldLessHorizontal from "vue-material-design-icons/UnfoldLessHorizontal.vue"
    import Plus from "vue-material-design-icons/Plus.vue"
    import {KsTooltip} from "@kestra-io/design-system"
    import {EVENTS, CLUSTER_TAG_STATUS} from "../utils/constants"
    import * as Utils from "../utils/utils"

    defineOptions({inheritAttrs: false})

    const props = defineProps<{
        id?: string;
        data: any;
    }>()

    const badgeStyle = computed(() => {
        const status = CLUSTER_TAG_STATUS[props.data.color] ?? "info"
        return {
            backgroundColor: `color-mix(in srgb, var(--ks-status-${status}) 10%, var(--ks-bg-badge))`,
            color: `var(--ks-status-${status})`,
        }
    })

    const emit = defineEmits([EVENTS.COLLAPSE, EVENTS.ADD_TRIGGER])

    const collapse = () => emit(EVENTS.COLLAPSE, props.id)

    const classes = computed(() => ({"unused-path": props.data.unused}))

    const clusterName = computed(() => {
        const taskNode = props.data.taskNode
        if (taskNode?.type?.endsWith("SubflowGraphTask")) {
            const subflowIdContainer = taskNode.task.subflowId ?? taskNode.task
            return subflowIdContainer.namespace + " " + subflowIdContainer.flowId
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
        color: white;
        font-size: var(--ks-font-size-xs);
        font-weight: 600;
        padding: var(--ks-spacing-1) var(--ks-spacing-2);
    }

    .top-button-div {
        align-items: center;
    }

</style>
