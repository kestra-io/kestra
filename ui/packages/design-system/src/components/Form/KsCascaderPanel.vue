<template>
    <ElCascaderPanel
        v-model="model"
        v-bind="({...filteredProps(), ...$attrs} as Record<string, unknown>)"
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
    import type {CascaderOption, CascaderNode, CascaderValue} from "element-plus"
    import {useFilteredProps} from "../../utils/filteredProps"

    const cascader = useTemplateRef<{
        getCheckedNodes: (leafOnly: boolean) => unknown[]
    }>("cascaderPanelRef")

    defineOptions({inheritAttrs: false})

    const model = defineModel<CascaderValue | null | undefined>()

    const props = defineProps<{
        options?: CascaderOption[]
    }>()

    const emit = defineEmits<{
        change: [value: CascaderValue | null | undefined]
    }>()

    defineSlots<{
        default?: (scope: {data: CascaderOption; node: CascaderNode}) => unknown
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
