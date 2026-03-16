<script setup lang="ts">
    import {ElInput, provideGlobalConfig} from "element-plus"
    import {useFilteredProps} from "../../utils/filteredProps"

    provideGlobalConfig({namespace: "kel"})

    defineOptions({inheritAttrs: false})

    const props = defineProps<{
        modelValue?: string | number
        type?: string
        placeholder?: string
        disabled?: boolean
        showPassword?: boolean
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        suffixIcon?: any
        clearable?: boolean
        size?: "large" | "default" | "small"
        name?: string
        id?: string
        required?: boolean
        rows?: number
    }>()

    const filteredProps = useFilteredProps(props)

    const emit = defineEmits<{
        "update:modelValue": [value: string | number]
        change: [value: string | number]
    }>()

    defineSlots<{
        prepend?(): unknown
        suffix?(): unknown
        default?(): unknown
    }>()
</script>

<template>
    <el-input
        v-bind="({...filteredProps(), ...$attrs} as any)"
        @update:model-value="emit('update:modelValue', $event)"
        @change="emit('change', $event)"
    >
        <template v-if="$slots.prepend" #prepend><slot name="prepend" /></template>
        <template v-if="$slots.suffix" #suffix><slot name="suffix" /></template>
        <template v-if="$slots.default" #default><slot /></template>
    </el-input>
</template>

<style lang="scss">
    .kel-textarea, .kel-input {
        --kel-input-border-color: var(--ks-border-primary);
        --kel-input-bg-color: var(--ks-background-input);
    }

    .kel-input {
        background-color: var(--ks-background-body);
        width: 100%;
    }
</style>