<template>
    <ElOption v-bind="({...filteredProps(), ...$attrs} as any)">
        <template v-if="$slots['default']" #default>
            <slot />
        </template>
    </ElOption>
</template>

<script setup lang="ts">
    import {ElOption} from "element-plus"
    import {useFilteredProps} from "../../../utils/filteredProps"

    defineOptions({inheritAttrs: false})

    const props = defineProps<{
        value: any
        label?: string | number
        disabled?: boolean
    }>()

    defineSlots<{
        default?(): unknown
    }>()

    const filteredProps = useFilteredProps(props)
</script>

<style lang="scss">
    @use '../../../assets/styles/el-ns';
    @use 'element-plus/theme-chalk/src/option';
</style>
