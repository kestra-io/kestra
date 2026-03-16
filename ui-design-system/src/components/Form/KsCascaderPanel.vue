<script setup lang="ts">
    import {ElCascaderPanel, provideGlobalConfig} from "element-plus"
    import {useFilteredProps} from "../../utils/filteredProps"

    provideGlobalConfig({namespace: "kel"})

    defineOptions({inheritAttrs: false})

    const props = defineProps<{
        modelValue?: any
        options?: any[]
    }>()

    const filteredProps = useFilteredProps(props)

    const emit = defineEmits<{
        "update:modelValue": [value: any]
        change: [value: any]
    }>()

    defineSlots<{
        default?: (scope: {data: any; node: any}) => unknown
    }>()
</script>

<template>
    <el-cascader-panel
        v-bind="({...filteredProps(), ...$attrs} as any)"
        @update:model-value="emit('update:modelValue', $event)"
        @change="emit('change', $event)"
    >
        <template v-if="$slots.default" #default="scope"><slot v-bind="scope" /></template>
    </el-cascader-panel>
</template>
