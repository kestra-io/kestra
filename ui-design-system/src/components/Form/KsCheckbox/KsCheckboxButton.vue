<script setup lang="ts">
    import {ElCheckboxButton, provideGlobalConfig} from "element-plus"
    import {useFilteredProps} from "../../../utils/filteredProps"

    provideGlobalConfig({namespace: "kel"})

    defineOptions({inheritAttrs: false})

    const props = defineProps<{
        value?: boolean | string | number
        label?: string | boolean | number
        disabled?: boolean
        checked?: boolean
    }>()

    const filteredProps = useFilteredProps(props)

    const emit = defineEmits<{
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        change: [value: any]
    }>()

    defineSlots<{
        default?(): unknown
    }>()
</script>

<template>
    <el-checkbox-button
        v-bind="({...filteredProps(), ...$attrs} as any)"
        @change="emit('change', $event)"
    >
        <template v-if="$slots.default" #default><slot /></template>
    </el-checkbox-button>
</template>
