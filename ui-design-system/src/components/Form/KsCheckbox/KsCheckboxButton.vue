<template>
    <ElCheckboxButton
        v-bind="({...filteredProps(), ...$attrs} as any)"
        @change="emit('change', $event)"
    >
        <template v-if="$slots.default" #default>
            <slot />
        </template>
    </ElCheckboxButton>
</template>

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
         
        change: [value: any]
    }>()

    defineSlots<{
        default?(): unknown
    }>()
</script>

<style lang="scss">
    @use '../../../assets/styles/el-ns';
    @use 'element-plus/theme-chalk/src/checkbox-button';
</style>
