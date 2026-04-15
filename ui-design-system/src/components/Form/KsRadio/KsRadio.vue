<template>
    <ElRadio
        v-bind="({...filteredProps(), ...$attrs} as any)"
        @update:model-value="emit('update:modelValue', $event)"
        @change="emit('change', $event)"
    >
        <template v-if="$slots.default" #default>
            <slot />
        </template>
    </ElRadio>
</template>

<script setup lang="ts">
    import {ElRadio, provideGlobalConfig} from "element-plus"
    import {useFilteredProps} from "../../../utils/filteredProps"

    provideGlobalConfig({namespace: "kel"})

    defineOptions({inheritAttrs: false})

    const props = defineProps<{
        modelValue?: string | number | boolean
        value?: string | number | boolean
        label?: string | number | boolean
        disabled?: boolean
    }>()

    const filteredProps = useFilteredProps(props)

    const emit = defineEmits<{
         
        "update:modelValue": [value: any]
         
        change: [value: any]
    }>()

    defineSlots<{
        default?(): unknown
    }>()
</script>

<style lang="scss">
@use '../../../assets/styles/el-ns';
@use 'element-plus/theme-chalk/src/radio';
</style>
