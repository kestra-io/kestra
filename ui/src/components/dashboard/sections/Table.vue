<template>
    <div class="table-root">
        <TableQuickFilter :chart="props.chart" :showUpcoming @change="onQuickFilterChange" />

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
                    :rowKey
                    :total="isPaginationEnabled(activeChart) ? data.total : 0"
                    :currentPage="pageNumber"
                    :pageSize="pageSize"
                    :height="240"
                    tableLayout="fixed"
                    noPaginationGutter
                    noFirstColumnGutter
                    @page-changed="handlePageChange"
                >
                    <KsTableColumn
                        v-for="[key, value] in Object.entries(activeChart.data?.columns ?? {})"
                        :key
                        :label="value.displayName || key"
                        :width="value.field === 'STATE' ? 140 : undefined"
                        :minWidth="ENTITY_LINK_FIELDS.includes(value.field ?? '') ? 140 : undefined"
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

            <KsNoData v-else :title="EMPTY_TEXT" class="empty" />
        </Motion>
    </div>
</template>

<script setup lang="ts">
    import {computed, ref, watch} from "vue"
    import {useRoute} from "vue-router"

    import {Motion} from "motion-v"
    import {KsExecutionStatus} from "@kestra-io/design-system"

    import type {Chart, ChartResults, Column} from "../types.ts"
    import {isPaginationEnabled, useChartGenerator} from "../composables/useDashboards"
    import TableQuickFilter from "./TableQuickFilter.vue"
    import {UPCOMING_TAB, hasQuickFilters, stateFilterForTab, upcomingChartFor, type TabKey} from "./quickFilters"
    import Date from "./table/columns/Date.vue"
    import Duration from "./table/columns/Duration.vue"
    import Link from "./table/columns/Link.vue"
    import Namespace from "./table/columns/Namespace.vue"
    import {useStateFilter} from "../../filter/composables/useStateFilter"
    import {QueryFilter} from "@kestra-io/kestra-sdk"
    import {useI18n} from "vue-i18n"

    const {navigateToStateFilter} = useStateFilter()
    const {t} = useI18n({useScope: "global"})

    const props = withDefaults(defineProps<{
        dashboardId?: string;
        chart: Chart;
        filters?: QueryFilter[];
        showDefault?: boolean;
    }>(), {
        dashboardId: undefined,
        filters: () => [],
        showDefault: false,
    })

    const route = useRoute()

    const containerID = `${props.chart.id}__${Math.random()}`

    // Identifier columns get a larger share of the flexible width so namespace and flow ids stay readable; dates truncate recoverably behind their tooltip.
    const ENTITY_LINK_FIELDS = ["NAMESPACE", "FLOW_ID"]

    const activeTab = ref<TabKey>("all")

    // Only a bundled definition renders through the preview endpoint, the one path that can serve a chart the dashboard never declared.
    const showUpcoming = computed(() => props.showDefault && hasQuickFilters(props.chart))

    const upcomingChart = computed(() => upcomingChartFor(props.chart, {
        namespace: t("namespace"),
        flow: t("flow"),
        trigger: t("trigger"),
        nextExecution: t("next_execution"),
    }))

    const activeChart = computed(() => activeTab.value === UPCOMING_TAB.key ? upcomingChart.value : props.chart)

    const hasIdColumn = computed(() =>
        Object.values(activeChart.value.data?.columns ?? {}).some((c: Column) => c?.field === "ID"),
    )

    // A data source without an ID column (triggers, for one) would key every row on the same
    // undefined, which the table reports as duplicate keys and then mis-patches on a tab change.
    const rowKey = (row: Record<string, unknown>) =>
        String(row.id ?? Object.values(row).join("|"))

    const resolvedComponent = (field: string | undefined) => {
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

    const resolvedProps = (field: string | undefined, key: string, row: Record<string, unknown>) => {
        const baseProps = {field: key, row, columns: activeChart.value.data?.columns ?? {}}

        switch (field) {
        case "ID":
            return {...baseProps, execution: true}
        case "FLOW_ID":
            return {...baseProps, flow: true, colored: !hasIdColumn.value}
        case "NAMESPACE":
            return {field: row[key] as string | undefined}
        case "STATE":
            return {
                size: "small",
                status: String(row[key]),
                clickable: true,
                onClick: () => navigateToStateFilter(String(row[key])),
            }
        case "DURATION":
            return {field: row[key] as number | undefined, startDate: row["start_date"] as string | undefined}
        default:
            if (field?.toLowerCase().includes("date")) {
                return {field: row[key] as string | undefined, relative: field === "NEXT_EXECUTION_DATE"}
            }
            return {}
        }
    }

    const data = ref<ChartResults>()
    const stateFilter = ref<QueryFilter | null>(stateFilterForTab(props.chart, "all"))
    const pageNumber = ref(1)

    const pageSizeKey = computed(() => `dashboard-page-size:${props.dashboardId ?? "default"}:${activeChart.value.id}`)
    const pageSize = ref(readStoredPageSize())

    function readStoredPageSize() {
        const stored = Number(sessionStorage.getItem(pageSizeKey.value))
        return stored > 0 ? stored : 25
    }

    watch(pageSize, (size) => sessionStorage.setItem(pageSizeKey.value, String(size)))

    // generate() reads these at call time, so plain getters keep the request on the tab's chart.
    const chartSource = {
        get chart() {
            return activeChart.value
        },
        get filters() {
            return props.filters
        },
        get showDefault() {
            return props.showDefault
        },
    }

    const {EMPTY_TEXT, generate} = useChartGenerator(props.dashboardId, chartSource, false)

    // A fast All -> Upcoming -> All sequence can still resolve out of order behind the load queue,
    // so a response is only kept when the tab that asked for it is still the active one.
    let pendingRequest = 0

    const getData = async () => {
        const requestId = ++pendingRequest
        const pagination = isPaginationEnabled(activeChart.value)
            ? {pageNumber: pageNumber.value, pageSize: pageSize.value}
            : undefined
        const append = stateFilter.value ? [stateFilter.value] : undefined
        const results = await generate(pagination, undefined, append)
        if (requestId !== pendingRequest) return
        data.value = results
    }

    const onQuickFilterChange = (filter: QueryFilter | null, tab: TabKey) => {
        stateFilter.value = filter
        activeTab.value = tab
        pageNumber.value = 1
        pageSize.value = readStoredPageSize()
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

    function exportParameters() {
        return {
            ...(isPaginationEnabled(activeChart.value) ? {pageNumber: pageNumber.value, pageSize: pageSize.value} : {}),
            filters: stateFilter.value ? [stateFilter.value] : [],
        }
    }

    function exportChart() {
        return activeChart.value
    }

    defineExpose({refresh, exportParameters, exportChart})

    watch(() => route.fullPath, () => refresh(), {immediate: true})
</script>

<style scoped>
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
