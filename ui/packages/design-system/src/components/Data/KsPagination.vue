<template>
    <ElPagination
        v-bind="({...filteredProps(), ...$attrs} as any)"
        @update:current-page="emit('update:currentPage', $event)"
        @update:page-size="emit('update:pageSize', $event)"
        @current-change="emit('currentChange', $event)"
        @size-change="emit('sizeChange', $event)"
    />
</template>

<script setup lang="ts">
    import {ElPagination} from "element-plus"
    import {useFilteredProps} from "../../utils/filteredProps"

    defineOptions({inheritAttrs: false})

    const props = defineProps<{
        total?: number
        currentPage?: number
        pageSize?: number
        pagerCount?: number
        layout?: string
        size?: "small" | "default" | "large"
        background?: boolean
        disabled?: boolean
    }>()

    const filteredProps = useFilteredProps(props)

    const emit = defineEmits<{
        "update:currentPage": [page: number]
        "update:pageSize": [size: number]
        currentChange: [page: number]
        sizeChange: [size: number]
    }>()
</script>

<style lang="scss">
    @use '../../assets/styles/el-ns';
    @use 'element-plus/theme-chalk/src/pagination';

    .kel-pagination {
        --kel-pagination-bg-color: transparent;
        --kel-pagination-text-color: var(--ks-content-primary);
        --kel-pagination-button-color: var(--ks-content-link);
        --kel-pagination-hover-color: var(--ks-content-link-hover);

        li, button {
            border: 1px solid var(--ks-border-inactive);
            margin-right: 3px;

            &.is-active {
                border: 1px solid var(--ks-border-active);
            }
        }
    }
</style>
