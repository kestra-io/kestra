<script setup lang="ts">
    import {ElOption, provideGlobalConfig} from "element-plus"
    import {useFilteredProps} from "../../../utils/filteredProps"

    provideGlobalConfig({namespace: "kel"})

    defineOptions({inheritAttrs: false})

    const props = defineProps<{
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        value: any
        label?: string | number | boolean
        disabled?: boolean
    }>()

    const filteredProps = useFilteredProps(props)

    defineSlots<{
        default?(): unknown
    }>()
</script>

<template>
    <el-option v-bind="({...filteredProps(), ...$attrs} as any)">
        <template v-if="$slots['default']" #default>
            <slot />
        </template>
    </el-option>
</template>

<style lang="scss">
    @use '../../../assets/styles/el-ns';
    @use 'element-plus/theme-chalk/src/option';
</style>
