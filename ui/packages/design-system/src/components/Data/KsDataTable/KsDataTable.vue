<template>
    <template v-if="$slots.empty && hasEmpty && showEmpty">
        <slot name="empty" />
    </template>

    <div class="ks-data-table-wrapper" :class="{'no-pagination-gutter': noPaginationGutter, 'no-gutter': noGutter}" v-else>
        <nav v-if="hasNavBar" class="ks-data-table-navbar">
            <slot name="navbar" />
        </nav>

        <div style="flex: 1; display: flex; flex-direction: column;" v-ks-loading="isLoading">
            <div v-if="$slots.top" class="ks-data-table-top">
                <slot name="top" />
            </div>

            <template v-if="hasTableSlot">
                <slot name="table" />
            </template>

            <template v-else>
                <div ref="container" class="ks-data-table-content" :class="{'no-selection-gutter': !hasSelectionColumn && !noFirstColumnGutter}" @click.capture="(e: MouseEvent) => isShiftPressed = e.shiftKey">
                    <div v-if="hasSelection && data && data.length && hasBulkActions" class="bulk-select-header">
                        <KsBulkSelect
                            :selectAll="queryBulkAction"
                            :selectionCount="mappedSelection.length"
                            :total
                            @toggle-all="toggleAllSelection"
                            @unselect="toggleAllUnselected"
                        >
                            <slot name="bulk-actions" />
                        </KsBulkSelect>
                    </div>
                    <div v-else-if="hasSelection && data && data.length" class="bulk-select-header">
                        <slot name="select-actions" />
                    </div>

                    <KsTable
                        ref="tableRef"
                        v-bind="$attrs"
                        :tableLayout="tableLayout"
                        fixed
                        :data
                        :rowKey
                        :expandRowKeys="composedExpandRowKeys"
                        :rowClassName="composedRowClassName"
                        :emptyText="noDataText"
                        @selection-change="selectionChanged"
                        @select="onSelect"
                        @sort-change="onSortChange"
                        @row-dblclick="(row, column, event) => emit('row-dblclick', row, column, event)"
                    >
                        <KsTableColumn v-if="selectable && showSelection" type="selection" reserveSelection />
                        <slot />
                        <template #empty>
                            <KsTableEmpty :title="noDataText" />
                        </template>
                    </KsTable>
                </div>
            </template>

            <KsPagination
                v-if="showPagination"
                :currentPage="currentPageValue"
                :pageSize="currentSizeValue"
                :total
                layout="sizes, prev, pager, next, total"
                size="small"
                :pageSizes="pageSizeOptions"
                @current-change="onPageChange"
                @size-change="onSizeChange"
                class="my-3"
            />
        </div>
    </div>
</template>

<script setup lang="ts">
    import {ref, computed, useAttrs, useSlots, onMounted, onUnmounted, onUpdated, nextTick, watch} from "vue"

    import {vKsLoading} from "../../Feedback/KsLoading"
    import KsTable from "../KsTable/KsTable.vue"
    import KsTableColumn from "../KsTable/KsTableColumn.vue"
    import KsPagination from "../KsPagination.vue"
    import KsBulkSelect from "./KsBulkSelect.vue"
    import KsTableEmpty from "../KsTableEmpty.vue"

    defineOptions({inheritAttrs: false})

    const DEFAULT_PAGE_SIZE = 25
    const MAX_PAGE_SIZE = 1000
    const MAX_PAGE = 1_000_000

    const props = withDefaults(defineProps<{
        data?: any[]
        total?: number
        currentPage?: number
        pageSize?: number
        loading?: boolean
        selectable?: boolean
        showSelection?: boolean
        rowKey?: string | ((row: any) => string)
        noDataText?: string
        pageSizeOptions?: number[]
        loadData?: (params: {page: number; size: number; sort?: string}) => void | Promise<void>
        selectionMapper?: (element: any) => any
        forceExpandedRowKeys?: string[]
        noPaginationGutter?: boolean
        noGutter?: boolean
        noFirstColumnGutter?: boolean
        tableLayout?: "fixed" | "auto"
    }>(), {
        data: () => [],
        total: 0,
        currentPage: 1,
        pageSize: 25,
        loading: false,
        selectable: false,
        showSelection: true,
        rowKey: "id",
        noDataText: undefined,
        pageSizeOptions: () => [10, 25, 50, 100],
        loadData: undefined,
        selectionMapper: undefined,
        forceExpandedRowKeys: () => [],
        noPaginationGutter: false,
        noGutter: false,
        noFirstColumnGutter: false,
        tableLayout: "auto",
    })

    export interface SortItem {
        column: any; 
        prop: string | null; 
        order: string | null
    }
    

    const emit = defineEmits<{
        "page-changed": [payload: {page: number; size: number}]
        "update:currentPage": [page: number]
        "update:pageSize": [size: number]
        "sort-change": [sort: SortItem]
        "selection-change": [selection: any[]]
        "row-dblclick": [row: any, column: any, event: Event]
        "ready": []
        "loaded": []
    }>()

    defineSlots<{
        default?(): unknown
        navbar?(): unknown
        top?(): unknown
        table?(): unknown
        empty?(): unknown
        "bulk-actions"?(): unknown
        "select-actions"?(): unknown
    }>()

    const slots = useSlots()
    const attrs = useAttrs()
    const hasNavBar = computed(() => !!slots["navbar"])
    const hasSelectionColumn = computed(() => props.selectable && props.showSelection)
    const hasTableSlot = computed(() => !!slots["table"])
    const hasBulkActions = computed(() => !!slots["bulk-actions"])
    const hasEmpty = computed(() => !!slots["empty"])

    const composedExpandRowKeys = computed<string[] | undefined>(() => {
        const forced = props.forceExpandedRowKeys ?? []
        const userKeys = (attrs.expandRowKeys as string[] | undefined) ?? []
        if (!forced.length && !userKeys.length) return undefined
        return Array.from(new Set([...userKeys, ...forced]))
    })

    const composedRowClassName = computed(() => {
        const forced = new Set(props.forceExpandedRowKeys ?? [])
        const userClass = attrs.rowClassName as ((arg: any) => string) | string | undefined

        if (!forced.size && !userClass) return undefined

        return (arg: {row: any}) => {
            const base = typeof userClass === "function" ? userClass(arg) : (userClass ?? "")
            if (!forced.size) return base
            const key = typeof props.rowKey === "function"
                ? (props.rowKey as (row: any) => string)(arg.row)
                : (arg.row as any)?.[props.rowKey as string]
            return [base, forced.has(String(key)) ? "ks-row-force-expanded" : ""].filter(Boolean).join(" ")
        }
    })

    const isLoading = ref(props.loading)
    const isReady = ref(false)

    const normalizePage = (value: number | undefined): number => {
        const page = Math.floor(Number(value))
        if (!Number.isFinite(page) || page < 1) return 1
        return Math.min(page, MAX_PAGE)
    }
    const normalizeSize = (value: number | undefined): number => {
        const size = Math.floor(Number(value))
        if (!Number.isFinite(size) || size < 1) return DEFAULT_PAGE_SIZE
        return Math.min(size, MAX_PAGE_SIZE)
    }
    const currentPageValue = computed(() => normalizePage(props.currentPage))
    const currentSizeValue = computed(() => normalizeSize(props.pageSize))
    const internalSort = ref<string>()

    const tableRef = ref<InstanceType<typeof KsTable>>()
    const container = ref<HTMLElement | null>(null)
    const hasSelection = ref(false)
    const lastCheckedIndex = ref<number | null>(null)
    const isShiftPressed = ref(false)
    const queryBulkAction = ref(false)
    const mappedSelection = ref<any[]>([])

    const selectionChanged = (rawSelection: any[]) => {
        hasSelection.value = rawSelection.length > 0

        const mapper = props.selectionMapper ?? ((e: any) => e)
        mappedSelection.value = rawSelection.map(mapper)

        if (queryBulkAction.value && props.data && rawSelection.length < props.data.length) {
            queryBulkAction.value = false
        }

        emit("selection-change", rawSelection)
    }

    const onSelect = async (selection: any[], row: any) => {
        const data = props.data ?? []
        const currentIndex = data.indexOf(row)
        const rowKey = props.rowKey

        const isChecked = selection.some(s =>
            typeof rowKey === "function"
                ? rowKey(s) === rowKey(row)
                : s[rowKey as string] === row[rowKey as string],
        )

        if (isShiftPressed.value && lastCheckedIndex.value !== null) {
            const start = Math.min(lastCheckedIndex.value, currentIndex)
            const end = Math.max(lastCheckedIndex.value, currentIndex)
            for (let i = start; i <= end; i++) {
                tableRef.value?.toggleRowSelection(data[i], isChecked)
            }
            await nextTick()
            const finalSelection = tableRef.value?.getSelectionRows() ?? []
            selectionChanged(finalSelection)
            window.getSelection()?.removeAllRanges()
        }

        lastCheckedIndex.value = currentIndex
    }

    const clearSelection = () => {
        tableRef.value?.clearSelection()
        hasSelection.value = false
        lastCheckedIndex.value = null
        mappedSelection.value = []
    }

    const toggleAllUnselected = () => {
        clearSelection()
        queryBulkAction.value = false
    }

    const setSelection = (selection: any[]) => {
        tableRef.value?.clearSelection()
        if (Array.isArray(selection)) {
            const isFunction = typeof props.rowKey === "function"
            selection.forEach(sel => {
                const row = props.data.find(r => isFunction
                    ? (props.rowKey as (row: any) => any)(r) === (props.rowKey as (row: any) => any)(sel)
                    : r[props.rowKey as string] === sel[props.rowKey as string])
                if (row) tableRef.value?.toggleRowSelection(row, true)
            })
        }
        selectionChanged(selection)
    }

    const toggleRowExpansion = (row: any, expand?: boolean) => {
        tableRef.value?.toggleRowExpansion(row, expand)
    }

    const getSelectionRows = () => tableRef.value?.getSelectionRows() ?? []

    const toggleAllSelection = () => {
        const current = getSelectionRows()
        if (current.length < props.data.length) {
            tableRef.value?.toggleAllSelection()
        }
        queryBulkAction.value = true
    }

    const waitTableRender = () => nextTick()

    const computeHeaderSize = () => {
        if (!tableRef.value?.$el || !container.value) return
        const tableElement = tableRef.value.$el as HTMLElement
        container.value.style.setProperty("--table-header-width", `${tableElement.clientWidth}px`)
        const thead = tableElement.querySelector("thead")
        if (thead) {
            container.value.style.setProperty("--table-header-height", `${thead.clientHeight}px`)
        }
    }

    const callLoad = async () => {
        if (!props.loadData) return
        isLoading.value = true
        try {
            await props.loadData({
                page: currentPageValue.value,
                size: currentSizeValue.value,
                sort: internalSort.value,
            })
        } finally {
            isLoading.value = false
            if (!isReady.value) {
                isReady.value = true
                emit("ready")
            }
            await nextTick()
            emit("loaded")
        }
    }

    const showEmpty = computed(() => props.data.length === 0 && !isLoading.value)

    const showPagination = computed(() => {
        if (!props.total || props.total <= 0) return false
        const minSize = props.pageSizeOptions.length ? Math.min(...props.pageSizeOptions) : DEFAULT_PAGE_SIZE
        return props.total > minSize
    })

    const reload = () => callLoad()

    const resetAndReload = () => {
        if (currentPageValue.value !== 1) {
            emit("update:currentPage", 1)
            emit("page-changed", {page: 1, size: currentSizeValue.value})
        } else {
            callLoad()
        }
    }

    onMounted(() => {
        window.addEventListener("resize", computeHeaderSize)
        callLoad()
    })
    onUnmounted(() => window.removeEventListener("resize", computeHeaderSize))
    onUpdated(() => computeHeaderSize())

    watch(() => props.data, () => {
        if (!props.data || props.data.length === 0) {
            hasSelection.value = false
            tableRef.value?.clearSelection()
            lastCheckedIndex.value = null
        } else {
            const currentSelection = tableRef.value?.getSelectionRows() ?? []
            const rowKey = props.rowKey
            const validSelection = currentSelection.filter((sel: unknown) => {
                const isFunction = typeof rowKey === "function"
                return props.data.some(r => isFunction
                    ? (rowKey as (row: any) => any)(r) === (rowKey as (row: any) => any)(sel)
                    : r[rowKey as string] === (sel as Record<string, unknown>)[rowKey as string])
            })
            if (validSelection.length !== currentSelection.length) {
                tableRef.value?.clearSelection()
                hasSelection.value = false
                lastCheckedIndex.value = null
            } else if (tableRef.value) {
                selectionChanged(currentSelection)
            }
        }
    }, {immediate: true})

    watch(() => props.loading, (val) => { isLoading.value = val })

    watch([currentPageValue, currentSizeValue], () => callLoad(), {flush: "post"})

    const onPageChange = (page: number) => {
        emit("update:currentPage", page)
        emit("page-changed", {page, size: currentSizeValue.value})
    }

    const onSizeChange = (size: number) => {
        emit("update:currentPage", 1)
        emit("update:pageSize", size)
        emit("page-changed", {page: 1, size})
    }

    const onSortChange = (sort: {column: any; prop: string | null; order: string | null}) => {
        if (sort.prop && sort.order) {
            internalSort.value = `${sort.prop}:${sort.order === "descending" ? "desc" : "asc"}`
        } else {
            internalSort.value = undefined
        }
        emit("sort-change", sort)
        callLoad()
    }

    defineExpose({
        isLoading,
        isReady,
        reload,
        resetAndReload,
        setSelection,
        clearSelection,
        toggleAllUnselected,
        toggleRowExpansion,
        waitTableRender,
        getSelectionRows,
        toggleAllSelection,
        queryBulkAction,
        selection: mappedSelection,
    })
</script>

<style lang="scss">
    .ks-data-table-wrapper {
        --ks-data-table-gutter: var(--ks-spacing-4);
        height: 100%;
        display: flex;
        flex-direction: column;

        > .ks-data-table-navbar,
        .ks-data-table-top {
            padding-inline: var(--ks-data-table-gutter);
        }

        .kel-pagination {
            display: flex;
            padding-inline: var(--ks-data-table-gutter);

            .kel-pagination__sizes {
                display: flex;
                flex: 1;
            }
        }

        &.no-pagination-gutter .kel-pagination {
            padding-inline: 0;
        }

        &.no-gutter {
            > .ks-data-table-navbar,
            .ks-data-table-top,
            .kel-pagination {
                padding-inline: 0;
            }
        }

        .kel-checkbox__inner {
            width: 16px;
            height: 16px;
            border-radius: 4.8px;
            background: transparent;
            border: 0.8px solid var(--ks-border-strong);
        }

        .kel-scrollbar__view {
            height: 100%;
        }
    }

    .ks-data-table-content {
        position: relative;
        height:100%;

        &.no-selection-gutter {
            .kel-table th.kel-table__cell:first-child > .cell,
            .kel-table td.kel-table__cell:first-child > .cell {
                padding-left: var(--ks-spacing-5);
            }
        }

        .bulk-select-header {
            z-index: 1;
            position: absolute;
            height: calc(var(--table-header-height) - 1px);
            width: calc(var(--table-header-width) - 1px);
            background-color: var(--ks-bg-overlay);
            border-radius: 0;
            overflow-x: auto;

            & ~ .kel-table {
                z-index: 0;
            }
        }

        .kel-table__empty-text {
            @media (max-width: 500px) {
                overflow: hidden;
                text-overflow: ellipsis;
                white-space: nowrap;
            }
        }

        .kel-table tr.ks-row-force-expanded .kel-table__expand-icon {
            visibility: hidden;
            pointer-events: none;
        }
    }
</style>
