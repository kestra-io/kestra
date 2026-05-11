<template>
    <Handle type="source" :position="sourcePosition" />
    <BasicNode
        :id="id"
        :data="formattedData"
        :color="color"
        :icons="icons"
        :iconComponent="iconComponent"
        @show-description="emit(EVENTS.SHOW_DESCRIPTION, $event)"
        @expand="emit(EVENTS.EXPAND, {id})"
    >
        <template #badge-button-before v-if="!data.isReadOnly">
            <span
                v-if="!execution"
                class="circle-button"
                :style="{backgroundColor: `var(--ks-node-${color})`}"
                @click="emit(EVENTS.EDIT, {task: data.node.triggerDeclaration, section: SECTIONS.TRIGGERS})"
            >
                <KsTooltip :content="$t('edit')">
                    <Pencil class="button-icon" alt="Edit task" />
                </KsTooltip>
            </span>
            <span
                v-if="!execution"
                class="circle-button"
                :style="{backgroundColor: `var(--ks-node-${color})`}"
                @click="emit(EVENTS.DELETE, {id: triggerId, section: SECTIONS.TRIGGERS})"
            >
                <KsTooltip :content="$t('delete')">
                    <Delete class="button-icon" alt="Delete task" />
                </KsTooltip>
            </span>
        </template>
    </BasicNode>
    <Handle type="target" :position="targetPosition" />
</template>

<script setup lang="ts">
    import {computed, inject} from "vue"
    import {Handle, Position} from "@vue-flow/core"
    import Pencil from "vue-material-design-icons/Pencil.vue"
    import Delete from "vue-material-design-icons/Delete.vue"
    import BasicNode from "./BasicNode.vue"
    import {KsTooltip, SECTIONS} from "@kestra-io/design-system"
    import {EVENTS} from "../utils/constants"
    import * as Utils from "../utils/utils"

    import {EXECUTION_INJECTION_KEY} from "../injectionKeys"

    defineOptions({name: "Task", inheritAttrs: false})

    const {data, sourcePosition, targetPosition, id, icons, iconComponent} = defineProps<{
        data: any;
        sourcePosition: Position;
        targetPosition: Position;
        id: string;
        icons?: Record<string, any>;
        iconComponent?: any;
    }>()

    const emit = defineEmits([EVENTS.DELETE, EVENTS.EDIT, EVENTS.SHOW_DESCRIPTION, EVENTS.EXPAND])

    const execution = inject(EXECUTION_INJECTION_KEY)

    const color = computed(() => data.color ?? "primary")
    const triggerId = computed(() => Utils.afterLastDot(id))
    const formattedData = computed(() => ({
        ...data,
        unused: data.node?.triggerDeclaration?.disabled || data.node?.trigger?.disabled,
    }))
</script>
