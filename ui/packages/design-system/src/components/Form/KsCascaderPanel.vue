<template>
    <ElCascaderPanel
        v-model="model"
        v-bind="({...filteredProps(), ...$attrs} as any)"
        ref="cascaderPanelRef"
        @change="emit('change', $event)"
    >
        <template v-if="$slots.default" #default="scope">
            <slot v-bind="scope" />
        </template>
    </ElCascaderPanel>
</template>

<script setup lang="ts">
    import {useTemplateRef} from "vue"
    import {ElCascaderPanel} from "element-plus"
    import {useFilteredProps} from "../../utils/filteredProps"

    const cascader = useTemplateRef<{
        getCheckedNodes: (leafOnly: boolean) => unknown[]
    }>("cascaderPanelRef")

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
