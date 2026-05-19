<template>
    <ElConfigProvider :locale="paginationLocale" namespace="kel">
        <ElPagination
            v-bind="({...filteredProps(), ...$attrs} as any)"
            @update:current-page="emit('update:currentPage', $event)"
            @update:page-size="emit('update:pageSize', $event)"
            @current-change="emit('currentChange', $event)"
            @size-change="emit('sizeChange', $event)"
        />
    </ElConfigProvider>
</template>

<script setup lang="ts">
    import {ElConfigProvider, ElPagination} from "element-plus"
    import en from "element-plus/es/locale/lang/en"
    import {useFilteredProps} from "../../utils/filteredProps"

    const paginationLocale = {
        ...en,
        el: {
            ...en.el,
            pagination: {
                ...en.el.pagination,
                pagesize: " per page",
            },
        },
    }

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
        --kel-pagination-bg-color: var(--ks-bg-base);
        --kel-pagination-text-color: var(--ks-text-primary);
        --kel-pagination-button-color: var(--ks-text-link);
        --kel-pagination-hover-color: var(--ks-text-link);

        background-color: var(--ks-bg-base);
        gap: 8px;

        li, button, .btn-prev, .btn-next {
            border-radius: 4px;
        }

        .kel-pager {
            gap: 8px;
        }

        .kel-pagination__sizes {
            .kel-select {
                width: auto;
                min-width: 110px;
            }
        }

        .kel-pager li {
            border: 1px solid var(--ks-border-subtle);
            margin: 0;
            color: var(--ks-text-primary);
            font-weight: 400;
            font-size: var(--ks-font-size-xs);

            &.is-active {
                background: var(--ks-btn-secondary-bg-active);
                border: 1px solid var(--ks-border-focus);
            }
        }
    }
</style>
