<template>
    <ElRadioButton
        v-bind="({...filteredProps(), ...$attrs} as any)"
        @change="emit('change', $event)"
    >
        <template v-if="$slots.default" #default>
            <slot />
        </template>
    </ElRadioButton>
</template>

<script setup lang="ts">
    import {ElRadioButton} from "element-plus"
    import {useFilteredProps} from "../../../utils/filteredProps"

    defineOptions({inheritAttrs: false})

    const props = defineProps<{
        value?: string | number | boolean
        label?: string | number | boolean
        disabled?: boolean
    }>()

    const emit = defineEmits<{
        change: [value: any]
    }>()

    defineSlots<{
        default?(): unknown
    }>()

    const filteredProps = useFilteredProps(props)
</script>

<style lang="scss">
    @use '../../../assets/styles/el-ns';
    @use 'element-plus/theme-chalk/src/radio-button';
</style>
