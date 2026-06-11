<template>
    <ElTable
        ref="tableRef"
        v-bind="({...filteredProps(), ...$attrs} as any)"
        @selection-change="(selection) => emit('selectionChange', selection)"
        @select="(selection, row) => emit('select', selection, row)"
        @sort-change="(e) => emit('sortChange', e)"
        @row-click="(row, column, event) => emit('rowClick', row, column, event)"
        @row-dblclick="(row, column, event) => emit('rowDblclick', row, column, event)"
    >
        <template v-if="$slots.default" #default>
            <slot />
        </template>
        <template #empty>
            <slot name="empty">
                <KsTableEmpty :title="emptyText" />
            </slot>
        </template>
    </ElTable>
</template>

<script setup lang="ts">
    import {ref} from "vue"
    import {ElTable} from "element-plus"
    import type {TableInstance} from "element-plus"
    import {useFilteredProps} from "../../../utils/filteredProps"
    import KsTableEmpty from "../KsTableEmpty.vue"

    defineOptions({inheritAttrs: false})

    const props = withDefaults(defineProps<{
        data?: any[]
        tableLayout?: "fixed" | "auto"
        size?: "large" | "default" | "small"
        rowKey?: string | ((row: any) => string)
        emptyText?: string
        defaultSort?: {prop: string; order: "ascending" | "descending" | null}
        showHeader?: boolean
        maxHeight?: string | number
        fit?: boolean
        cellClassName?: string | ((data: any) => string)
        rowClassName?: string | ((data: any) => string)
    }>(), {
        data: undefined,
        tableLayout: undefined,
        size: undefined,
        rowKey: undefined,
        emptyText: undefined,
        defaultSort: undefined,
        showHeader: undefined,
        maxHeight: undefined,
        fit: undefined,
        cellClassName: undefined,
        rowClassName: undefined,
    })

    const emit = defineEmits<{
        selectionChange: [selection: any[]]
        select: [selection: any[], row: any]
        sortChange: [sort: {column: any; prop: string | null; order: string | null}]
        rowClick: [row: any, column: any, event: Event]
        rowDblclick: [row: any, column: any, event: Event]
    }>()

    defineSlots<{
        default?(): unknown
        empty?(): unknown
    }>()

    const tableRef = ref<TableInstance>()

    const filteredProps = useFilteredProps(props)

    defineExpose({
        clearSelection: () => tableRef.value?.clearSelection(),
        toggleRowSelection: (row: any, selected?: boolean) => tableRef.value?.toggleRowSelection(row, selected),
        toggleAllSelection: () => tableRef.value?.toggleAllSelection(),
        getSelectionRows: () => tableRef.value?.getSelectionRows() ?? [],
        toggleRowExpansion: (row: any, expanded?: boolean) => tableRef.value?.toggleRowExpansion(row, expanded),
        setCurrentRow: (row: any) => tableRef.value?.setCurrentRow(row),
        clearSort: () => tableRef.value?.clearSort(),
        sort: (prop: string, order: string) => tableRef.value?.sort(prop, order),
    })
</script>

<style lang="scss">
    @use '../../../assets/styles/el-ns';
    @use 'element-plus/theme-chalk/src/table';

    .kel-table {
        --kel-table-border-color: transparent;
        --kel-table-border: none;

        --kel-table-bg-color: var(--ks-bg-overlay);
        --kel-table-header-text-color: var(--ks-text-secondary);
        --kel-table-header-bg-color: var(--ks-bg-overlay);
        --kel-table-row-hover-bg-color: var(--ks-bg-hover);
        --kel-table-tr-bg-color: var(--ks-bg-overlay);
        --kel-table-current-row-bg-color: var(--ks-bg-overlay);

        outline: 1px solid var(--ks-border-default);
        border-radius: 0;
        background-color: var(--ks-bg-overlay);
        border: none;
        font-size: var(--ks-font-size-sm);
        height: 100%;

        &--striped {
            .kel-table__body tr.kel-table__row--striped:not(:hover) td.kel-table__cell {
                background: var(--ks-bg-tag);

                html.dark & {
                    background: var(--ks-bg-base);
                }
            }
        }

        .cell {
            padding: 0 8px;
            word-break: break-word;
            font-weight: 400;
        }

        .kel-table__inner-wrapper::before {
            display: none;
        }

        .kel-table__empty-text {
            color: var(--ks-text-dim) !important;
        }

        .kel-table__body tr:hover > td.kel-table__cell,
        .kel-table__body tr.hover-row > td.kel-table__cell {
            background-color: var(--ks-bg-hover);
        }

        th {
            white-space: nowrap;
            background-color: var(--ks-bg-overlay);
            border-bottom: 1px solid var(--ks-border-default);
            color: var(--ks-text-secondary);
            font-weight: 600;
            font-size: var(--ks-font-size-sm);

            div.cell {
                word-break: normal;
                white-space: nowrap;
                font-weight: 600;
                color: var(--ks-text-secondary);
                font-size: var(--ks-font-size-sm);
            }
        }

        th.row-action, td.row-action {
            width: 24px;

            .cell {
                white-space: nowrap;
            }

            a, button, .kicon, .kel-button {
                color: var(--ks-text-primary);
                width: 24px;
                height: 24px;
                border-radius: var(--kel-border-radius-base);
                text-align: center;
                display: flex;
                justify-content: center;
                align-items: center;
                background-color: transparent;
                border: none;
                box-shadow: none;
                padding: 0;
                cursor: pointer;

                .material-design-icon__svg {
                    bottom: 0;
                    width: 16px;
                    height: 16px;
                    transform: translateY(1px) translateX(-0.5px);
                }
            }

            a:hover,
            button:hover,
            .kicon:hover,
            .kel-button:hover {
                background-color: var(--ks-bg-tag);
            }

        }

        th.shrink {
            width: 16px;
        }

        td.shrink {
            white-space: nowrap;
        }

        th.row-graph {
            width: 250px;
            min-width: 250px;
        }

        td.row-graph {
            padding: 0.75rem 0 0;
            vertical-align: bottom;
        }

        tr.disabled {
            td {
                opacity: 0.5;
            }
        }

        td {
            .kel-tag {
                margin-right: .3rem;
            }

            a {
                color: var(--ks-text-primary);
                &:hover{
                    text-decoration: underline;
                }
            }
        }
    }
</style>
