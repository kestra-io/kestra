<script setup lang="ts">
    import {ElRadioButton, provideGlobalConfig} from "element-plus"
    import {useFilteredProps} from "../../../utils/filteredProps"

    provideGlobalConfig({namespace: "kel"})

    defineOptions({inheritAttrs: false})

    const props = defineProps<{
        value?: string | number | boolean
        label?: string | number | boolean
        disabled?: boolean
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
    <el-radio-button
        v-bind="({...filteredProps(), ...$attrs} as any)"
        @change="emit('change', $event)"
    >
        <template v-if="$slots.default" #default><slot /></template>
    </el-radio-button>
</template>

<style lang="scss">
    @use '../../../assets/styles/el-ns';
    @use 'element-plus/theme-chalk/src/radio-button';
</style>
