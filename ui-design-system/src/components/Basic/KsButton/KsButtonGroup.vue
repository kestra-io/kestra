<script setup lang="ts">
    import {ElButtonGroup, provideGlobalConfig} from "element-plus"
    import {useFilteredProps} from "../../../utils/filteredProps"

    provideGlobalConfig({namespace: "kel"})

    defineOptions({inheritAttrs: false})

    const props = defineProps<{
        size?: "small" | "default" | "large" | ""
        direction?: "horizontal" | "vertical"
    }>()

    const filteredProps = useFilteredProps(props)

    defineSlots<{
        default?(): unknown
    }>()
</script>

<template>
    <el-button-group v-bind="({...filteredProps(), ...$attrs} as any)">
        <template v-if="$slots.default" #default><slot /></template>
    </el-button-group>
</template>

<style lang="scss">
    @use '../../../assets/styles/el-ns';
    @use 'element-plus/theme-chalk/src/button-group';
</style>
