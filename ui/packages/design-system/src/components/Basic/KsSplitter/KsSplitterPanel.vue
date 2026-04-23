<template>
    <ElSplitterPanel v-bind="({...filteredProps(), ...$attrs} as any)">
        <template v-if="$slots.default" #default>
            <slot />
        </template>
    </ElSplitterPanel>
</template>

<script setup lang="ts">
    import {ElSplitterPanel, provideGlobalConfig} from "element-plus"
    import {useFilteredProps} from "../../../utils/filteredProps"

    provideGlobalConfig({namespace: "kel"})

    defineOptions({inheritAttrs: false})

    const props = withDefaults(defineProps<{
        size?: string | number
        min?: string | number
        max?: string | number
        resizable?: boolean
        collapsible?: boolean
    }>(), {
        resizable: undefined,
    })

    const filteredProps = useFilteredProps(props)

    defineSlots<{
        default?(): unknown
    }>()
</script>

<style lang="scss">
    @use '../../../assets/styles/el-ns';
    @use 'element-plus/theme-chalk/src/splitter-panel';
</style>
