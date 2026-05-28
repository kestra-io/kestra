<template>
    <div :class="classes">
        <span
            class="cluster-badge text-color"
            :style="{backgroundColor: `var(--ks-node-${data.color})`}"
        >{{ clusterName }}</span>
        <div class="top-button-div">
            <span
                v-if="data.collaspsible"
                class="circle-button"
                :style="{backgroundColor: `var(--ks-node-${data.color})`}"
                @click="collapse()"
            >
                <KsTooltip :content="$t('collapse')">
                    <ArrowCollapse class="button-icon" alt="Collapse task" />


                </KsTooltip>
            </span>
        </div>
    </div>
</template>
<script setup lang="ts">
    import {computed} from "vue"
    import ArrowCollapse from "vue-material-design-icons/ArrowCollapse.vue"
    import {KsTooltip} from "@kestra-io/design-system"
    import {EVENTS} from "../utils/constants"
    import * as Utils from "../utils/utils"

    defineOptions({inheritAttrs: false})

    const props = defineProps<{
        id?: string;
        data: any;
    }>()

    const emit = defineEmits([EVENTS.COLLAPSE])

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
        border-radius: 1rem;
        width: 1rem;
        height: 1rem;
        display: flex;
        justify-content: center;
        align-items: center;
        pointer-events: auto !important;
    }

    .button-icon {
        font-size: 0.75rem;
    }

    .cluster-badge {
        position: relative;
        top: -3px;
        left: -3px;
        display: inline-block;
        max-width: 100%;
        border-radius: var(--ks-border-radius-pill);
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
    }

    .text-color {
        color: white;
        font-size: 0.5rem;
        font-weight: 700;
        padding: 0.25rem 0.5rem;
    }

    .top-button-div {
        align-items: center;
    }

</style>
