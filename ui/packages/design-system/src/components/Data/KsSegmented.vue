<template>
    <ElSegmented
        v-model="model"
        :class="props.disabled ? 'is-disabled' : undefined"
        v-bind="({...filteredProps(), ...$attrs} as any)"
        @change="emit('change', $event)"
    />
</template>

<script setup lang="ts">
    import {ElSegmented} from "element-plus"
    import {useFilteredProps} from "../../utils/filteredProps"

    defineOptions({inheritAttrs: false})

    const model = defineModel<string | number | boolean>()

    const props = defineProps<{
        options?: Array<string | number | {label: string; value: string | number | boolean; disabled?: boolean}>
        size?: "large" | "default" | "small"
        disabled?: boolean
        block?: boolean
    }>()

    const emit = defineEmits<{
        change: [value: string | number | boolean]
    }>()

    const filteredProps = useFilteredProps(props)
</script>

<style lang="scss">
    @use '../../assets/styles/el-ns';
    @use 'element-plus/theme-chalk/src/segmented';

    .el-segmented__item-selected {
        font-weight: 500;
    }
</style>
