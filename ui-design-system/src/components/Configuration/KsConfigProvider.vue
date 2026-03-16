<script setup lang="ts">
    import type {Component} from "vue"
    import {ElConfigProvider, provideGlobalConfig} from "element-plus"
    import {useFilteredProps} from "../../utils/filteredProps"

    provideGlobalConfig({namespace: "kel"})

    defineOptions({inheritAttrs: false})

    const props = defineProps<{
        locale?: object
        size?: "large" | "default" | "small"
        zIndex?: number
        namespace?: string
        button?: {autoInsertSpace?: boolean}
        message?: {max?: number}
        experimentalFeatures?: object
        emptyValues?: any[]
        valueOnClear?: string | number | boolean | Function
        a11y?: {describedby?: string}
        icon?: Component
    }>()

    const filteredProps = useFilteredProps(props)

    defineSlots<{
        default?(): unknown
    }>()
</script>

<template>
    <el-config-provider v-bind="({...filteredProps(), ...$attrs} as any)">
        <template v-if="$slots.default" #default><slot /></template>
    </el-config-provider>
</template>
