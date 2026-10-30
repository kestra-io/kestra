<template>
    <ElConfigProvider :locale="paginationLocale" namespace="kel">
        <ElPagination
            v-bind="({...filteredProps(), ...$attrs} as PaginationProps)"
            @update:current-page="emit('update:currentPage', $event)"
            @update:page-size="emit('update:pageSize', $event)"
            @current-change="emit('currentChange', $event)"
            @size-change="emit('sizeChange', $event)"
        />
    </ElConfigProvider>
</template>

<script setup lang="ts">
    import {computed} from "vue"
    import {ElConfigProvider, ElPagination, type PaginationProps} from "element-plus"
    import de from "element-plus/es/locale/lang/de"
    import en from "element-plus/es/locale/lang/en"
    import es from "element-plus/es/locale/lang/es"
    import fr from "element-plus/es/locale/lang/fr"
    import hi from "element-plus/es/locale/lang/hi"
    import it from "element-plus/es/locale/lang/it"
    import ja from "element-plus/es/locale/lang/ja"
    import ko from "element-plus/es/locale/lang/ko"
    import pl from "element-plus/es/locale/lang/pl"
    import pt from "element-plus/es/locale/lang/pt"
    import ptBr from "element-plus/es/locale/lang/pt-br"
    import ru from "element-plus/es/locale/lang/ru"
    import zhCn from "element-plus/es/locale/lang/zh-cn"
    import {designSystemLocale} from "../../i18n"
    import {useFilteredProps} from "../../utils/filteredProps"

    // Element Plus locale pack for each app language; anything else falls back to English.
    const elementLocales: Record<string, typeof en> = {
        de, en, es, fr, hi, it, ja, ko, pl, pt, pt_BR: ptBr, ru, zh_CN: zhCn,
    }

    const englishLocale = {
        ...en,
        el: {
            ...en.el,
            pagination: {
                ...en.el.pagination,
                pagesize: " per page",
            },
        },
    }

    const paginationLocale = computed(() => {
        const locale = elementLocales[designSystemLocale.value]
        return !locale || locale === en ? englishLocale : locale
    })

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
</style>

<style>
    .kel-pagination {
        --kel-pagination-bg-color: var(--ks-btn-secondary-bg-default);
        --kel-pagination-text-color: var(--ks-text-primary);
        --kel-pagination-button-color: var(--ks-text-link);
        --kel-pagination-hover-color: var(--ks-text-link);
        --kel-pagination-item-gap: 0;
        --kel-pagination-font-size-small: var(--ks-font-size-xs);
        flex-wrap: wrap;
        gap: 8px;

        & li, & button, & .btn-prev, & .btn-next {
            border-radius: 4px;
        }

        & .kel-pager {
            gap: 8px;
        }

        & .kel-pagination__sizes {
            & .kel-select {
                width: auto;
                min-width: 130px;
            }
        }
    }

    .kel-pagination__total {
        color: var(--ks-text-secondary);
        font-size: var(--ks-font-size-2xs);
    }

    .kel-pagination {
        & .kel-pager li {
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
