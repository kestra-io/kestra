<script setup lang="ts">
    import {ElCheckboxGroup, provideGlobalConfig} from "element-plus"
    import {useFilteredProps} from "../../../utils/filteredProps"

    provideGlobalConfig({namespace: "kel"})

    defineOptions({inheritAttrs: false})

    const props = defineProps<{
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        modelValue?: any[]
        disabled?: boolean
        size?: "large" | "default" | "small"
    }>()

    const filteredProps = useFilteredProps(props)

    const emit = defineEmits<{
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        "update:modelValue": [value: any[]]
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        change: [value: any[]]
    }>()

    defineSlots<{
        default?(): unknown
    }>()
</script>

<template>
    <el-checkbox-group
        :class="props.size ? `kel-checkbox-group--${props.size}` : undefined"
        v-bind="({...filteredProps(), ...$attrs} as any)"
        @update:model-value="emit('update:modelValue', $event)"
        @change="emit('change', $event)"
    >
        <template v-if="$slots.default" #default><slot /></template>
    </el-checkbox-group>
</template>
