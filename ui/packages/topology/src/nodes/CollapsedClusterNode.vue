<template>
    <Handle type="source" :position="sourcePosition" />
    <div class="collapsed-cluster-node">
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
    import {KsTooltip} from "@kestra-io/design-system"
    import {EVENTS, CLUSTER_TAG_STATUS} from "../utils/constants"
    import * as Utils from "../utils/utils"

    defineOptions({inheritAttrs: false})

    const {id, sourcePosition, targetPosition, data} = defineProps<{
        id?: string;
        sourcePosition: Position;
        targetPosition: Position;
        data: any;
    }>()

    const emit = defineEmits([EVENTS.EXPAND])

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

    .button-icon {
        font-size: 0.75rem;
        transform: rotate(45deg);
    }
</style>
