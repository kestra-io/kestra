<template>
    <div :class="classes">
        <span
            class="badge rounded-pill text-truncate text-color position-reliative"
            :class="[`bg-${data.color}`]"
        >{{ clusterName }}</span>
        <div class="top-button-div text-white d-flex">
            <span
                v-if="data.collapsable"
                class="circle-button"
                :class="[`bg-${data.color}`]"
                @click="collapse()"
            >
                <KsTooltip :content="$t('collapse')">
                    <ArrowCollapse class="button-icon" alt="Collapse task" />
                </KsTooltip>
            </span>
        </div>
    </div>
</template>
<script setup>
    import ArrowCollapse from "vue-material-design-icons/ArrowCollapse.vue"
    import {EVENTS} from "../utils/constants";
    import {Position} from "@vue-flow/core";
    import Utils from "../utils/utils";

    const props = defineProps({
        "data": {
            type: Object,
            default: undefined
        },
        "sourcePosition": {
            type: Position,
            default: undefined
        },
        "targetPosition": {
            type: Position,
            default: undefined
        },
        "id": {
            type: String,
            default: undefined
        }
    });
    const emit = defineEmits([EVENTS.COLLAPSE])

    const collapse = () => {
        emit(EVENTS.COLLAPSE, props.id)
    };
</script>
<script>
    import KsTooltip from "../components/Tooltip.vue";

    export default {
        inheritAttrs: false,
        components: {KsTooltip},
        data() {
            return {
                tooltips: [],
            }
        },
        computed: {
            classes() {
                return {"unused-path": this.data.unused}
            },
            clusterName() {
                const taskNode = this.data.taskNode;
                if (taskNode?.type?.endsWith("SubflowGraphTask")) {
                    const subflowIdContainer = taskNode.task.subflowId ?? taskNode.task
                    return subflowIdContainer.namespace + " " + subflowIdContainer.flowId;
                }

                return Utils.afterLastDot(this.id);
            }
        }
    }
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

    .badge {
        top: -3px;
        position: relative;
        left: -3px;
        display: inline-block;
        max-width: 100%;
    }

    .text-color {
        color: var(--bs-white);
        font-size: 0.5rem;
        font-weight: 700;
        padding: 0.25rem 0.5rem;
    }

    .top-button-div {
        position: absolute;
        top: -0.5rem;
        right: -0.5rem;
        justify-content: center;
        padding-right: 3px;
        display: flex
    }

</style>
