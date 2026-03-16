<script setup lang="ts">
    import {ElSegmented, provideGlobalConfig} from "element-plus"
    import {useFilteredProps} from "../../utils/filteredProps"

    provideGlobalConfig({namespace: "kel"})

    defineOptions({inheritAttrs: false})

    const props = defineProps<{
        modelValue?: string | number | boolean
        options?: Array<string | number | {label: string; value: string | number | boolean; disabled?: boolean}>
        size?: "large" | "default" | "small"
        disabled?: boolean
        block?: boolean
    }>()

    const filteredProps = useFilteredProps(props)

    const emit = defineEmits<{
        "update:modelValue": [value: string | number | boolean]
        change: [value: string | number | boolean]
    }>()
</script>

<template>
    <el-segmented
        :class="props.disabled ? 'is-disabled' : undefined"
        v-bind="({...filteredProps(), ...$attrs} as any)"
        @update:model-value="emit('update:modelValue', $event)"
        @change="emit('change', $event)"
    />
</template>
