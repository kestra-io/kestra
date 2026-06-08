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
    </BasicNode>
    <Handle type="target" :position="targetPosition" />
</template>

<script setup lang="ts">
    import {computed} from "vue"
    import {Handle, Position} from "@vue-flow/core"
    import BasicNode from "./BasicNode.vue"
    import {EVENTS} from "../utils/constants"

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

    const color = computed(() => data.color ?? "primary")
    const formattedData = computed(() => ({
        ...data,
        unused: data.node?.triggerDeclaration?.disabled || data.node?.trigger?.disabled,
    }))
</script>
