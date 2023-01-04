<script lang="ts" setup>
    import type {EdgeProps, Position} from '@vue-flow/core'
    import {EdgeLabelRenderer, getSmoothStepPath} from '@vue-flow/core'
    import type {CSSProperties} from 'vue'
    import {computed} from 'vue'
    import Help from "vue-material-design-icons/Help.vue";
    import HelpCircle from "vue-material-design-icons/HelpCircle.vue";
    import Exclamation from "vue-material-design-icons/Exclamation.vue";
    import Reload from "vue-material-design-icons/Reload.vue";
    import ViewParallelOutline from "vue-material-design-icons/ViewParallelOutline.vue";

    interface CustomEdgeProps<T = any> extends EdgeProps<T> {
        id: string
        sourceX: number
        sourceY: number
        targetX: number
        targetY: number
        sourcePosition: Position
        targetPosition: Position
        data: T
        markerEnd: string
        style: CSSProperties,
    }

    const props = defineProps<CustomEdgeProps>()

    const getEdgeLabel = (relation) => {
        let label = "";

        if (relation.relationType && relation.relationType !== "SEQUENTIAL") {
            label = relation.relationType.toLowerCase();
            if (relation.value) {
                label += ` : ${relation.value}`;
            }
        }
        return label;
    };

    const getEdgeIcon = (relation) => {
        if (relation.relationType) {
            if (relation.relationType === "ERROR") {
                return Exclamation;
            } else if (relation.relationType === "DYNAMIC") {
                return Reload;
            } else if (relation.relationType === "CHOICE") {
                return Help;
            } else if (relation.relationType === "PARALLEL") {
                return ViewParallelOutline;
            }
        }

        return HelpCircle;
    };

    const path = computed(() => getSmoothStepPath(props))
</script>

<script lang="ts">
    export default {
        inheritAttrs: false,
    }
</script>

<template>
    <path :id="id" :style="style" class="vue-flow__edge-path" :class="props.data.edge.relation.relationType" :d="path[0]" :marker-end="markerEnd" />

    <EdgeLabelRenderer style="z-index: 10">
        <div
            v-if="getEdgeLabel(props.data.edge.relation) !== ''"
            :style="{
                pointerEvents: 'all',
                position: 'absolute',
                transform: `translate(-50%, -50%) translate(${path[1]}px,${path[2]}px)`,
            }"
            class="nodrag nopan"
            :class="props.data.edge.relation.relationType"
        >
            <el-tooltip placement="bottom" :persistent="false">
                <template #content>
                    {{ getEdgeLabel(props.data.edge.relation) }}
                </template>

                <el-button :icon="getEdgeIcon(props.data.edge.relation)" link />
            </el-tooltip>
        </div>
    </EdgeLabelRenderer>
</template>

<style lang="scss">
    .vue-flow__edge-path {
        &.ERROR {
            stroke: var(--bs-danger);
        }

        &.DYNAMIC {
            stroke: var(--bs-teal);
        }

        &.CHOICE {
            stroke: var(--bs-orange);
        }
    }

    .vue-flow__edge-labels > div {
        border-radius: 50%;
        height: 18px;
        width: 18px;
        background: var(--bs-purple);

        .el-button {
            margin-top: -9px;
            margin-left: -1px;
            font-size: var(--font-size-sm);
        }

        &.ERROR {
            background: var(--bs-danger);
        }

        &.DYNAMIC {
            background: var(--bs-teal);
        }

        &.CHOICE {
            background: var(--bs-orange);
        }
    }
</style>
