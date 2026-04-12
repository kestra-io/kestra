<script setup lang="ts">
    import {ElMenu, provideGlobalConfig} from "element-plus"
    import {useFilteredProps} from "../../../utils/filteredProps"

    provideGlobalConfig({namespace: "kel"})

    defineOptions({inheritAttrs: false})

    const props = defineProps<{
        mode?: "horizontal" | "vertical"
        defaultActive?: string
        collapse?: boolean
        backgroundColor?: string
        textColor?: string
        activeTextColor?: string
        router?: boolean
    }>()

    const filteredProps = useFilteredProps(props)

    const emit = defineEmits<{
        select: [index: string, indexPath: string[]]
    }>()

    defineSlots<{
        default?(): unknown
    }>()
</script>

<template>
    <el-menu
        v-bind="({...filteredProps(), ...$attrs} as any)"
        @select="(index, indexPath) => emit('select', index, indexPath)"
    >
        <template v-if="$slots.default" #default><slot /></template>
    </el-menu>
</template>

<style lang="scss">
    @use '../../../assets/styles/el-ns';
    @use 'element-plus/theme-chalk/src/menu';
    @use 'element-plus/theme-chalk/src/menu-item-group';
    @use 'element-plus/theme-chalk/src/sub-menu';
</style>
