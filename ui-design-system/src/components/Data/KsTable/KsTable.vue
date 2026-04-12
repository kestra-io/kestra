<script setup lang="ts">
    import {ref} from "vue"
    import {ElTable, provideGlobalConfig} from "element-plus"
    import {useFilteredProps} from "../../../utils/filteredProps"

    provideGlobalConfig({namespace: "kel"})

    defineOptions({inheritAttrs: false})

    const props = withDefaults(defineProps<{
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        data?: any[]
        tableLayout?: "fixed" | "auto"
        size?: "large" | "default" | "small"
        rowKey?: string | ((row: any) => string)
        emptyText?: string
        defaultSort?: {prop: string; order: "ascending" | "descending" | null}
        showHeader?: boolean
        maxHeight?: string | number
        fit?: boolean
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        cellClassName?: string | ((data: any) => string)
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        rowClassName?: string | ((data: any) => string)
    }>(), {
        showHeader: undefined,
        fit: undefined,
    })

    const emit = defineEmits<{
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        selectionChange: [selection: any[]]
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        select: [selection: any[], row: any]
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        sortChange: [sort: {column: any; prop: string; order: string | null}]
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        rowClick: [row: any, column: any, event: Event]
        // eslint-disable-next-line @typescript-eslint/no-explicit-any
        rowDblclick: [row: any, column: any, event: Event]
    }>()

    defineSlots<{
        default?(): unknown
    }>()

    const tableRef = ref<InstanceType<typeof ElTable>>()

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

    const filteredProps = useFilteredProps(props)
</script>

<template>
    <el-table
        ref="tableRef"
        v-bind="({...filteredProps(), ...$attrs} as any)"
        @selection-change="(selection) => emit('selectionChange', selection)"
        @select="(selection, row) => emit('select', selection, row)"
        @sort-change="(e) => emit('sortChange', e)"
        @row-click="(row, column, event) => emit('rowClick', row, column, event)"
        @row-dblclick="(row, column, event) => emit('rowDblclick', row, column, event)"
    >
        <template v-if="$slots.default" #default><slot /></template>
    </el-table>
</template>

<style lang="scss">
    @use '../../../assets/styles/el-ns';
    @use 'element-plus/theme-chalk/src/table';

    .kel-table {
        --kel-table-border-color: var(--ks-border-primary);
        --kel-table-border: 1px solid var(--ks-border-primary);

        --kel-table-header-text-color: var(--ks-content-primary);
        --kel-table-header-bg-color: var(--ks-background-table-header);
        --kel-table-row-hover-bg-color: var(--ks-background-table-row-hover);
        --kel-table-tr-bg-color: var(--ks-background-table-row);

        outline: 1px solid var(--ks-border-primary);
        border-radius: var(--kel-border-radius-round);
        background-color: var(--ks-gray-100-lighten-2);
        border-bottom-width: 0;
        font-size: var(--kel-font-size-small);

        &--striped {
            .kel-table__body tr.kel-table__row--striped:not(:hover) td.kel-table__cell {
                background: var(--ks-gray-100-darken-2);

                html.dark & {
                    background: var(--ks-background-body);
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
            color: var(--ks-content-tertiary) !important;
        }

        th {
            white-space: nowrap;

            div.cell {
                word-break: normal;
                white-space: nowrap;
            }
        }

        th.row-action, td.row-action {
            width: 24px;

            .cell {
                white-space: nowrap;
            }

            a, button, .kicon, .kel-button {
                color: var(--ks-content-primary);
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
                background-color: var(--ks-tag-background);
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
                color: var(--ks-content-primary);
                &:hover{
                    text-decoration: underline;
                }
            }
        }
    }
</style>