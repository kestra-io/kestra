<template>
    <ElCascaderPanel
        v-model="model"
        v-bind="({...filteredProps(), ...$attrs} as Record<string, unknown>)"
        ref="cascaderPanelRef"
        @change="emit('change', $event as CascaderValue)"
    >
        <template v-if="$slots.default" #default="scope">
            <slot v-bind="scope" />
        </template>
    </ElCascaderPanel>
</template>

<script setup lang="ts">
    import {useTemplateRef} from "vue"
    import {ElCascaderPanel, type CascaderValue} from "element-plus"
    import {useFilteredProps} from "../../utils/filteredProps"

    const cascader = useTemplateRef<{
        getCheckedNodes: (leafOnly: boolean) => unknown[]
    }>("cascaderPanelRef")

    defineOptions({inheritAttrs: false})

    const model = defineModel<CascaderValue>()

    const props = defineProps<{
        options?: unknown[]
    }>()

    const emit = defineEmits<{
        change: [value: CascaderValue]
    }>()

    defineSlots<{
        default?: (scope: {data: unknown; node: unknown}) => unknown
    }>()

    defineExpose({
        cascader,
    })

    const filteredProps = useFilteredProps(props)
</script>

<style lang="scss">
    @use '../../assets/styles/el-ns';
    @use 'element-plus/theme-chalk/src/cascader-panel';

    .kel-cascader-panel {


        .kel-cascader-node.in-active-path, .kel-cascader-node.is-selectable.in-checked-path, .kel-cascader-node.is-active {
            font-weight: normal;
        }
    }
</style>
