<template>
    <section v-if="data?.results?.length" id="table">
        <KsDataTable
            :id="containerID"
            :data="data.results"
            :total="isPaginationEnabled(props.chart) ? data.total : 0"
            :currentPage="pageNumber"
            :pageSize="pageSize"
            :height="240"
            size="small"
            @page-changed="handlePageChange"
        >
            <KsTableColumn
                v-for="[key, value] in Object.entries( props.chart.data?.columns ?? {} )"
                :label="value.displayName || key"
                :key
                :width="value.field === 'STATE' ? 140 : undefined"
            >
                <template #default="scope">
                    <template v-if="resolvedComponent(value.field) === undefined">
                        {{ scope.row[key] }}
                    </template>
                    <component v-else :is="resolvedComponent(value.field)" v-bind="resolvedProps(value.field, key, scope.row)" />
                </template>
            </KsTableColumn>
        </KsDataTable>
    </section>

    <KsEmpty v-else :description="EMPTY_TEXT" />
</template>

<script setup lang="ts">
    import {PropType, watch, ref} from "vue"

    import type {Chart} from "../types.ts"
    import {isPaginationEnabled, useChartGenerator} from "../composables/useDashboards"

    import Date from "./table/columns/Date.vue"
    import Duration from "./table/columns/Duration.vue"
    import Link from "./table/columns/Link.vue"
    import Namespace from "./table/columns/Namespace.vue"
    import {KsExecutionStatus} from "@kestra-io/design-system"

    const props = defineProps({
        dashboardId: {type: String, required: false, default: undefined},
        chart: {type: Object as PropType<Chart>, required: true},
        filters: {type: Array as PropType<FilterObject[]>, default: () => []},
        showDefault: {type: Boolean, default: false},
    })

    const containerID = `${props.chart.id}__${Math.random()}`

    const resolvedComponent = (field: string) => {
        switch (field) {
        case "ID":
        case "FLOW_ID":
            return Link
        case "NAMESPACE":
            return Namespace
        case "STATE":
            return KsExecutionStatus
        case "DURATION":
            return Duration
        default:
            if (field?.toLowerCase().includes("date")) return Date
            return undefined
        }
    }

    const resolvedProps = (field: string, key: string, row: Record<string, any>) => {
        const baseProps = {field: key, row, columns: props.chart.data?.columns ?? {}}

        switch (field) {
        case "ID":
            return {...baseProps, execution: true}
        case "FLOW_ID":
            return {...baseProps, flow: true}
        case "NAMESPACE":
            return {field: row[key]}
        case "STATE":
            return {
                size: "small",
                status: row[key].toString(),
            }
        case "DURATION":
            return {field: row[key], startDate: row["start_date"]}
        default:
            if (field.toLowerCase().includes("date")) {
                return {field: row[key]}
            }
            return {}
        }
    }

    const data = ref()

    import {useRoute} from "vue-router"
    import {FilterObject} from "../../../utils/filters"
    const route = useRoute()
    const {EMPTY_TEXT, generate} = useChartGenerator(props.dashboardId, props, false)

    const getData = async () => (data.value = await generate(
        isPaginationEnabled(props.chart) ? {pageNumber: pageNumber.value, pageSize: pageSize.value} : undefined,
    ))

    const pageNumber = ref(1)
    const pageSize = ref(25)

    const handlePageChange = (options: { page?: number; size?: number | string }) => {
        if (pageNumber.value === options.page && pageSize.value === options.size) return

        pageNumber.value = options.page ?? 1
        const sizeNumber = typeof options.size === "string" ? parseInt(options.size, 10) : options.size
        if (sizeNumber && isNaN(sizeNumber)) {
            pageSize.value = 25
            return
        };
        pageSize.value = sizeNumber ?? 25

        return getData()
    }

    function refresh() {
        return getData()
    }

    defineExpose({
        refresh,
    })

    watch(() => route.params.filters, () => {
        refresh()
    }, {deep: true, immediate: true})
</script>

<style lang="scss" scoped>
section#table :deep(.kel-scrollbar__thumb) {
    background-color: var(--ks-btn-primary-bg-default) !important;
}
</style>
