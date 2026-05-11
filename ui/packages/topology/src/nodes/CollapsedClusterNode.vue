<template>
    <Handle type="source" :position="sourcePosition" />
    <div class="collapsed-cluster-node">
        <span class="node-text">
            <LightningBolt :style="{color: `var(--ks-node-${data.color})`}" class="node-icon" />
            {{ Utils.afterLastDot(id ?? "") }}
        </span>
        <div class="top-button-div">
            <slot name="badge-button-before" />
            <span
                v-if="expandable"
                class="circle-button"
                :style="{backgroundColor: `var(--ks-node-${data.color})`}"
                @click="emit(EVENTS.EXPAND, {id})"
            >
                <KsTooltip :content="$t('expand')">
                    <ArrowExpand class="button-icon" alt="Expand task" />
                </KsTooltip>
            </span>
            <slot name="badge-button-after" />
        </div>
    </div>
    <Handle type="target" :position="targetPosition" />
</template>

<script setup lang="ts">
    import {computed} from "vue"
    import {Handle, Position} from "@vue-flow/core"
    import ArrowExpand from "vue-material-design-icons/ArrowExpand.vue"
    import LightningBolt from "vue-material-design-icons/LightningBolt.vue"
    import {KsTooltip} from "@kestra-io/design-system"
    import {EVENTS} from "../utils/constants"
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
</script>

<style lang="scss" scoped>
    .collapsed-cluster-node {
        display: flex;
        width: 150px;
        height: 44px;
        padding: 8px;
    }

    .node-icon {
        margin-right: 0.5rem;
    }

    .node-text {
        color: black;
        font-size: 0.90rem;
        display: flex;
        align-items: center;

        html.dark & {
            color: white;
        }
    }

    .button-icon {
        font-size: 0.75rem;
    }

</style>
