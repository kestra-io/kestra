<script setup lang="ts">
    import {ElTimePicker, provideGlobalConfig} from "element-plus"
    import {useFilteredProps} from "../../utils/filteredProps"

    provideGlobalConfig({namespace: "kel"})

    defineOptions({inheritAttrs: false})

    const props = withDefaults(defineProps<{
        modelValue?: Date | string | null
        placeholder?: string
        disabled?: boolean
        clearable?: boolean
        format?: string
        valueFormat?: string
        size?: "large" | "default" | "small"
    }>(), {
        clearable: undefined,
    })

    const filteredProps = useFilteredProps(props)

    const emit = defineEmits<{
        "update:modelValue": [value: any]
        change: [value: any]
    }>()
</script>

<template>
    <el-time-picker
        v-bind="({...filteredProps(), ...$attrs} as any)"
        @update:model-value="emit('update:modelValue', $event)"
        @change="emit('change', $event)"
    />
</template>
