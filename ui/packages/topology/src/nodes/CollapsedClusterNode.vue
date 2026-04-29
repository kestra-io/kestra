<template>
    <Handle type="source" :position="sourcePosition" />
    <div class="collapsed-cluster-node d-flex">
        <span class="node-text">
            <component v-if="data.iconComponent" :is="data.iconComponent" :class="`text-${data.color} me-2`" />
            {{ Utils.afterLastDot(id) }}
        </span>
        <div class="text-white top-button-div">
            <slot name="badge-button-before" />
            <span
                v-if="expandable"
                class="circle-button"
                :class="[`bg-${data.color}`]"
                @click="$emit(EVENTS.EXPAND, {id})"
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

<script setup>
    import Utils from "../utils/utils";
</script>

<script>
    import {EVENTS} from "../utils/constants"
    import ArrowExpand from "vue-material-design-icons/ArrowExpand.vue";
    import LightningBolt from "vue-material-design-icons/LightningBolt.vue";
    import {Handle} from "@vue-flow/core";
    import KsTooltip from "../components/Tooltip.vue";

    export default {
        components: {
            KsTooltip,
            Handle,
            ArrowExpand,
            LightningBolt
        },
        inheritAttrs: false,
        props: {
            id: {
                type: String,
                default: undefined
            },
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
        },
        data() {
            return {
                filter: undefined,
                isOpen: false,
            };
        },
        computed: {
            EVENTS() {
                return EVENTS
            },
            expandable() {
                return this.data?.expandable || false
            },
            description() {
                const node = this.data.node.task ?? this.data.node.trigger ?? null
                if (node) {
                    return node.description ?? null
                }
                return null
            }
        },
    }
</script>

<style lang="scss" scoped>
    .collapsed-cluster-node {
        width: 150px;
        height: 44px;
        padding: 8px;
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
