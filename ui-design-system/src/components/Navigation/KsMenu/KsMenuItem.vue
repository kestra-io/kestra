<script setup lang="ts">
    import {ElMenuItem, provideGlobalConfig} from "element-plus"
    import {useFilteredProps} from "../../../utils/filteredProps"

    provideGlobalConfig({namespace: "kel"})

    defineOptions({inheritAttrs: false})

    const props = defineProps<{
        index?: string
        disabled?: boolean
        route?: string | object
    }>()

    const filteredProps = useFilteredProps(props)

    const emit = defineEmits<{
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        click: [item: any]
    }>()

    defineSlots<{
        default?(): unknown
        title?(): unknown
    }>()
</script>

<template>
    <el-menu-item
        v-bind="({...filteredProps(), ...$attrs} as any)"
        @click="emit('click', $event)"
    >
        <template v-if="$slots.default" #default><slot /></template>
        <template v-if="$slots.title" #title><slot name="title" /></template>
    </el-menu-item>
</template>
