<template>
    <ElSegmented
        v-model="model"
        :class="props.disabled ? 'is-disabled' : undefined"
        v-bind="({...filteredProps(), ...$attrs} as any)"
        @change="emit('change', $event)"
    >
        <template v-if="$slots.default" #default="scope">
            <slot v-bind="scope" />
        </template>
    </ElSegmented>
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

    // In light theme --ks-bg-hover-elevated equals --ks-bg-base, so the track
    // background is invisible against the page; --ks-bg-elevated gives contrast.
    html:not(.dark) .el-segmented,
    html:not(.dark) .kel-segmented {
        --el-fill-color-light: var(--ks-bg-elevated);
        --kel-fill-color-light: var(--ks-bg-elevated);
    }

    .el-segmented.is-disabled .el-segmented__item-selected,
    .kel-segmented.is-disabled .kel-segmented__item-selected {
        background-color: var(--ks-bg-inactive);
    }

    .el-segmented.is-disabled .el-segmented__item.is-selected,
    .kel-segmented.is-disabled .kel-segmented__item.is-selected {
        color: var(--ks-text-secondary);
    }
</style>
