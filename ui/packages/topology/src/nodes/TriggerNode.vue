<template>
    <Handle type="source" :position="sourcePosition" />
    <BasicNode
        :id="id"
        :data="formattedData"
        :color="color"
        :icons="icons"
        :iconComponent="iconComponent"
        @show-description="forwardEvent(EVENTS.SHOW_DESCRIPTION, $event)"
        @expand="forwardEvent(EVENTS.EXPAND, {id})"
    >
        <template #badge-button-before v-if="!data.isReadOnly">
            <span
                v-if="!execution"
                class="circle-button"
                :class="[`bg-${color}`]"
                @click="$emit(EVENTS.EDIT, {task: data.node.triggerDeclaration, section: SECTIONS.TRIGGERS})"
            >
                <KsTooltip :content="$t('edit')">
                    <Pencil class="button-icon" alt="Edit task" />
                </KsTooltip>
            </span>
            <span
                v-if="!execution"
                class="circle-button"
                :class="[`bg-${color}`]"
                @click="$emit(EVENTS.DELETE, {id: triggerId, section: SECTIONS.TRIGGERS})"
            >
                <KsTooltip :content="$t('delete')">
                    <Delete class="button-icon" alt="Delete task" />
                </KsTooltip>
            </span>
        </template>
    </BasicNode>
    <Handle type="target" :position="targetPosition" />
</template>
<script setup>
    import BasicNode from "./BasicNode.vue";
</script>
<script>
    import {Handle} from "@vue-flow/core";
    import {EVENTS} from "../utils/constants";
    import {SECTIONS} from "../utils/constants";
    import Pencil from "vue-material-design-icons/Pencil.vue";
    import Delete from "vue-material-design-icons/Delete.vue";
    import KsTooltip from "../components/Tooltip.vue";
    import Utils from "../utils/utils";
    import {EXECUTION_INJECTION_KEY} from "../injectionKeys";

    export default {
        name: "Task",
        inheritAttrs: false,
        inject: {
            execution: {
                from: EXECUTION_INJECTION_KEY,
            },
        },
        computed: {
            SECTIONS() {
                return SECTIONS
            },
            EVENTS() {
                return EVENTS
            },
            color() {
                return this.data.color ?? "primary"
            },
            formattedData() {
                return {
                    ...this.data,
                    unused: this.data.node?.triggerDeclaration?.disabled || this.data.node?.trigger?.disabled
                }
            },
            triggerId() {
                return Utils.afterLastDot(this.id);
            }
        },
        emits: [
            EVENTS.DELETE,
            EVENTS.EDIT,
            EVENTS.SHOW_DESCRIPTION
        ],
        components: {
            Delete, Pencil, Handle, KsTooltip
        },
        props: {
            data: {
                type: Object,
                required: true,
            },
            sourcePosition: {
                type: String,
                required: true
            },
            targetPosition: {
                type: String,
                required: true
            },
            id: {
                type: String,
                required: true
            },
            icons: {
                type: Object,
                default: undefined
            },
            iconComponent: {
                type: Object,
                default: undefined
            }
        },
        methods: {
            forwardEvent(event, payload) {
                this.$emit(event, payload)
            }
        }
    }
</script>
