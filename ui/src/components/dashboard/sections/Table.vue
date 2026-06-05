<template>
    <div class="table-root">
        <TableQuickFilter :chart="props.chart" @change="onQuickFilterChange" />

        <Motion
            as="div"
            class="table-motion"
            :key="activeTab"
            :initial="{opacity: 0, y: 4}"
            :animate="{opacity: 1, y: 0}"
            :transition="{duration: 0.15, ease: 'easeOut'}"
        >
            <section v-if="data?.results?.length" id="table">
                <KsDataTable
                    :id="containerID"
                    :data="data.results"
                    :total="isPaginationEnabled(props.chart) ? data.total : 0"
                    :currentPage="pageNumber"
                    :pageSize="pageSize"
                    :height="240"
                    size="small"
                    tableLayout="fixed"
                    noPaginationGutter
                    noFirstColumnGutter
                    @page-changed="handlePageChange"
                >
                    <KsTableColumn
                        v-for="[key, value] in Object.entries(props.chart.data?.columns ?? {})"
                        :key
                        :label="value.displayName || key"
                        :width="value.field === 'STATE' ? 140 : undefined"
                    >
                        <template #default="scope">
                            <template v-if="resolvedComponent(value.field) === undefined">
                                {{ scope.row[key] }}
                            </template>
                            <component
                                v-else
                                :is="resolvedComponent(value.field)"
                                v-bind="resolvedProps(value.field, key, scope.row)"
                            />
                        </template>
                    </KsTableColumn>
                </KsDataTable>
            </section>

            <KsTableEmpty v-else :title="EMPTY_TEXT" class="empty" />
        </Motion>
    </div>
</template>

<script setup lang="ts">
    import {ref, watch} from "vue"
    import {useRoute} from "vue-router"

    import {Motion} from "motion-v"
    import {KsExecutionStatus} from "@kestra-io/design-system"

    import type {Chart} from "../types.ts"
    import {isPaginationEnabled, useChartGenerator} from "../composables/useDashboards"
    import {FilterObject} from "../../../utils/filters"
    import TableQuickFilter from "./TableQuickFilter.vue"
    import Date from "./table/columns/Date.vue"
    import Duration from "./table/columns/Duration.vue"
    import Link from "./table/columns/Link.vue"
    import Namespace from "./table/columns/Namespace.vue"
    import {useStateFilter} from "../../filter/composables/useStateFilter"

    const {navigateToStateFilter} = useStateFilter()

    const props = withDefaults(defineProps<{
        dashboardId?: string;
        chart: Chart;
        filters?: FilterObject[];
        showDefault?: boolean;
    }>(), {
        dashboardId: undefined,
        filters: () => [],
        showDefault: false,
    })

    const route = useRoute()

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
                clickable: true,
                onClick: () => navigateToStateFilter(row[key].toString()),
            }
        case "DURATION":
            return {field: row[key], startDate: row["start_date"]}
        default:
            if (field.toLowerCase().includes("date")) {
                return {field: row[key], relative: field === "NEXT_EXECUTION_DATE"}
            }
            return {}
        }
    }

    const data = ref()
    const activeTab = ref("all")
    const stateFilter = ref<FilterObject | null>(null)
    const pageNumber = ref(1)
    const pageSize = ref(25)

    const {EMPTY_TEXT, generate} = useChartGenerator(props.dashboardId, props, false)

    const getData = async () => {
        const pagination = isPaginationEnabled(props.chart)
            ? {pageNumber: pageNumber.value, pageSize: pageSize.value}
            : undefined
        const append = stateFilter.value ? [stateFilter.value] : undefined
        data.value = await generate(pagination, undefined, append)
    }

    const onQuickFilterChange = (filter: FilterObject | null, tab: string) => {
        stateFilter.value = filter
        activeTab.value = tab
        pageNumber.value = 1
        getData()
    }

    const handlePageChange = (options: { page?: number; size?: number | string }) => {
        if (pageNumber.value === options.page && pageSize.value === options.size) return

        pageNumber.value = options.page ?? 1
        const sizeNumber = typeof options.size === "string" ? parseInt(options.size, 10) : options.size
        if (sizeNumber && isNaN(sizeNumber)) {
            pageSize.value = 25
            return
        }
        pageSize.value = sizeNumber ?? 25

        return getData()
    }

    function refresh() {
        return getData()
    }

    defineExpose({refresh})

    watch(() => route.params.filters, () => refresh(), {deep: true, immediate: true})
</script>

<style scoped lang="scss">
    .table-root {
        display: flex;
        flex-direction: column;
        height: 100%;
    }
    
    .table-motion {
        flex: 1;
        min-height: 0;
    }

    .empty {
        min-height: 200px;
    }

    :deep(.ks-data-table-content) {
        border-top: 1px solid var(--ks-border-default);
        border-bottom: 1px solid var(--ks-border-default);
        padding: var(--ks-spacing-2) 0;
    }

    :deep(.kel-pagination) {
        margin-bottom: 0 !important;
        flex-wrap: wrap;
        row-gap: var(--ks-spacing-2);
    }
</style>
