<template>
    <ElTableColumn
        v-bind="({...filteredProps(), ...$attrs} as ColumnProps)"
    >
        <template v-if="$slots.default" #default="scope">
            <slot v-if="scope.$index !== -1" v-bind="scope" />
        </template>
        <template v-if="$slots.header" #header="scope">
            <slot name="header" v-bind="scope" />
        </template>
    </ElTableColumn>
</template>

<script setup lang="ts">
    import {ElTableColumn, type TableColumnCtx, type TableColumnInstance, type TableProps} from "element-plus"
    import {useFilteredProps} from "../../../utils/filteredProps"

    defineOptions({inheritAttrs: false})

    type TableRow = NonNullable<TableProps["data"]>[number]
    type ColumnProps = TableColumnInstance["$props"]

    const props = withDefaults(defineProps<{
        type?: string
        prop?: string
        label?: string
        width?: string | number
        minWidth?: string | number
        sortable?: boolean | "custom"
        sortOrders?: string[]
        columnKey?: string
        className?: string
        fixed?: boolean | "left" | "right"
        reserveSelection?: boolean
    }>(), {
        type: undefined,
        prop: undefined,
        label: undefined,
        width: undefined,
        minWidth: undefined,
        sortable: undefined,
        sortOrders: undefined,
        columnKey: undefined,
        className: undefined,
        fixed: undefined,
        reserveSelection: undefined,
    })

    defineSlots<{
        default?: (scope: {row: TableRow; column: TableColumnCtx<TableRow>; $index: number}) => unknown
        header?: (scope: {column: TableColumnCtx<TableRow>; $index: number}) => unknown
    }>()

    const filteredProps = useFilteredProps(props)
</script>

<style lang="scss">
    @use '../../../assets/styles/el-ns';
    @use 'element-plus/theme-chalk/src/table-column';
</style>
