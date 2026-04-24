<template>
    <ElCascaderPanel
        v-model="model"
        v-bind="({...filteredProps(), ...$attrs} as any)"
        @change="emit('change', $event)"
    >
        <template v-if="$slots.default" #default="scope">
            <slot v-bind="scope" />
        </template>
    </ElCascaderPanel>
</template>

<script setup lang="ts">
    import {ElCascaderPanel} from "element-plus"

    import {useFilteredProps} from "../../utils/filteredProps"

    defineOptions({inheritAttrs: false})

    const model = defineModel<any>()

    const props = defineProps<{
        options?: any[]
    }>()

    const emit = defineEmits<{
        change: [value: any]
    }>()

    defineSlots<{
        default?: (scope: {data: any; node: any}) => unknown
    }>()

    const filteredProps = useFilteredProps(props)
</script>

<style lang="scss">
    @use '../../assets/styles/el-ns';
    @use 'element-plus/theme-chalk/src/cascader-panel';
</style>
