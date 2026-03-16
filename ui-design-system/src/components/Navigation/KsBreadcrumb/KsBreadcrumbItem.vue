<script setup lang="ts">
    import {ElBreadcrumbItem, provideGlobalConfig} from "element-plus"
    import {useFilteredProps} from "../../../utils/filteredProps"

    provideGlobalConfig({namespace: "kel"})

    defineOptions({inheritAttrs: false})

    const props = defineProps<{
        to?: string | object
        replace?: boolean
    }>()

    const filteredProps = useFilteredProps(props)

    defineSlots<{
        default?(): unknown
    }>()
</script>

<template>
    <el-breadcrumb-item
        v-bind="({...filteredProps(), ...$attrs} as any)"
    >
        <template v-if="$slots.default" #default><slot /></template>
    </el-breadcrumb-item>
</template>
