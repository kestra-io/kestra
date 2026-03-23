<script setup lang="ts">
    import {ElTooltip, provideGlobalConfig} from "element-plus"
    import {useFilteredProps} from "../../utils/filteredProps"

    provideGlobalConfig({namespace: "kel"})

    defineOptions({inheritAttrs: false})

    const props = withDefaults(defineProps<{
        content?: string
        trigger?: "click" | "hover" | "focus" | "contextmenu"
        placement?: string
        effect?: "light" | "dark"
        enterable?: boolean
        rawContent?: boolean
        disabled?: boolean,
        autoClose?: boolean | number
    }>(), {
        enterable: undefined,
        autoClose: undefined,
    })

    const filteredProps = useFilteredProps(props)

    defineSlots<{
        default?(): unknown
        content?(): unknown
    }>()
</script>

<template>
    <el-tooltip
        :persistent="false"
        :hideAfter="0"
        transition=""
        v-bind="({...filteredProps(), ...$attrs} as any)"
    >
        <template v-if="$slots.default" #default><slot /></template>
        <template v-if="$slots.content" #content><slot name="content" /></template>
    </el-tooltip>
</template>
