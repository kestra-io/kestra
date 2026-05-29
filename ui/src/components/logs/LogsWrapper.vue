<template>
    <TopNavBar v-if="!embed" :title="routeInfo.title" />
    <section v-bind="$attrs" :class="{'container': !embed}" class="log-panel">
        <div class="log-content">
            <KsDataTable
                ref="dataTable"
                :loadData="loadData"
                :currentPage="urlPage"
                :pageSize="urlSize"
                @ready="ready = true"
                @page-changed="onPageChanged"
                :total="logsStore.total"
            >
                <template #navbar v-if="!embed || showFilters">
                    <KSFilter
                        :configuration="logFilter"
                        :tableOptions="{
                            chart: {shown: true, value: showChart, callback: onShowChartChange},
                            refresh: {shown: true, callback: refresh},
                            columns: {shown: false}
                        }"
                        :defaultScope="false"
                        @filter="onFilterRouteSync"
                    />
                </template>

                <template v-if="showStatChart() && logsStore.logs && logsStore.logs.length > 0" #top>
                    <Sections ref="dashboard" :charts :dashboard="{id: 'default', charts: []}" showDefault class="mb-4" />
                </template>

                <template #table>
                    <div v-ks-loading="isLoading">
                        <div v-if="logsStore.logs !== undefined && logsStore.logs?.length > 0" class="logs-wrapper">
                            <LogLine
                                v-for="(log, i) in logsStore.logs"
                                :key="`${log.taskRunId}-${i}`"
                                level="TRACE"
                                filter=""
                                :excludeMetas="isFlowEdit ? ['namespace', 'flowId'] : []"
                                :log="log"
                                :class="{'log-0': i === 0}"
                            />
                        </div>

                        <div v-else-if="!isLoading">
                            <KsEmpty :description="$t('no_logs_data_description')" />
                        </div>
                    </div>
                </template>
            </KsDataTable>
        </div>
    </section>
</template>

<script setup lang="ts">
    import {ref, computed, watch, useTemplateRef} from "vue"
    import {useRoute, useRouter} from "vue-router"
    import {useI18n} from "vue-i18n"
    import _merge from "lodash/merge"
    import moment from "moment"
    import {useLogFilter} from "../filter/configurations"
    import useRestoreUrl from "../../composables/useRestoreUrl"
    import {KsFilter as KSFilter} from "@kestra-io/design-system"

    const {loadInit} = useRestoreUrl()
    import Sections from "../dashboard/sections/Sections.vue"
    import TopNavBar from "../../components/layout/TopNavBar.vue"
    import LogLine from "../logs/LogLine.vue"
    import {storageKeys} from "../../utils/constants"
    import {
        decodeSearchParams,
        encodeFiltersToQuery,
        getUniqueFilters,
        isValidFilter,
        keyOfComparator,
    } from "@kestra-io/design-system"
    import type {AppliedFilter} from "@kestra-io/design-system"
    import {
        hasUnsupportedRouteLevelComparator,
        normalizeRouteLevelFilter,
        readAppliedLevelFilter,
        readRouteLevelFilter,
    } from "@kestra-io/design-system"
    import {useRouteFilterPolicy} from "@kestra-io/design-system"
    import type {LevelFilterValue} from "@kestra-io/design-system"
    import {flowYamlUtils as YAML_UTILS} from "@kestra-io/topology"
    import YAML_CHART from "../dashboard/assets/logs_timeseries_chart.yaml?raw"
    import {useLogsStore} from "../../stores/logs"
    import useRouteContext from "../../composables/useRouteContext"

    const props = withDefaults(defineProps<{
        logLevel?: string;
        embed?: boolean;
        showFilters?: boolean;
        filters?: Record<string, any>;
        reloadLogs?: number;
        namespace?: string | null;
        restoreurl?: boolean;
    }>(), {
        embed: false,
        showFilters: false,
        filters: undefined,
        logLevel: undefined,
        reloadLogs: undefined,
        namespace: undefined,
        restoreurl: undefined,
    })
    defineEmits(["expand-subflow", "go-to-detail", "goToDetail"])

    const route = useRoute()
    const router = useRouter()
    const {t} = useI18n()
    const logsStore = useLogsStore()
    const logFilter = useLogFilter()
    const dataTable = useTemplateRef("dataTable")
    const ready = ref(false)

    const routeInfo = computed(() => ({
        title: t("logs"),
    }))
    useRouteContext(routeInfo, props.embed)

    const isLoading = ref(false)
    const lastRefreshDate = ref(new Date())
    const showChart = ref(localStorage.getItem(storageKeys.SHOW_LOGS_CHART) !== "false")
    const dashboardRef = useTemplateRef("dashboard")

    const isFlowEdit = computed(() => route.name === "flows/update")
    const isNamespaceEdit = computed(() => route.name === "namespaces/update")
    const hasLevelFilterUI = computed(() => !props.embed || props.showFilters)
    const defaultLogLevel = computed(() =>
        typeof window !== "undefined"
            ? localStorage.getItem("defaultLogLevel") || "INFO"
            : "INFO",
    )
    const {
        effectiveValue: effectiveLogLevel,
        syncFromAppliedFilters: syncLevelFromAppliedFilters,
    } = useRouteFilterPolicy<LevelFilterValue>({
        enabled: () => !props.filters && hasLevelFilterUI.value,
        explicitValue: () => props.logLevel ? {value: props.logLevel, direction: "min"} : undefined,
        defaultValue: () => ({value: defaultLogLevel.value, direction: "min"}),
        applyDefaultIfMissing: () => true,
        fallbackValue: () => undefined,
        readFromRoute: readRouteLevelFilter,
        writeToRoute: normalizeRouteLevelFilter,
        hasUnsupportedRouteValue: hasUnsupportedRouteLevelComparator,
        readFromAppliedFilters: readAppliedLevelFilter,
        shouldSyncFromAppliedFilters: (filters, routeQuery) => {
            const encodedFilters = encodeFiltersToQuery(
                getUniqueFilters(filters.filter(isValidFilter)),
                keyOfComparator,
            )

            return !Object.entries(encodedFilters).some(
                ([key, value]) =>
                    !key.startsWith("filters[level][") &&
                    routeQuery[key] !== value,
            )
        },
    })
    const selectedTimeRange = computed(() => {
        if (route.query.timeRange) {
            return route.query.timeRange as string
        }

        const decodedParams = decodeSearchParams(route.query)
        const timeRangeFilter = decodedParams.find(item => item?.field === "timeRange")
        const rawValue = timeRangeFilter?.value

        if (Array.isArray(rawValue)) {
            return rawValue[0]
        }

        return rawValue as string | undefined
    })
    const endDate = computed(() => {
        if (route.query.endDate) {
            return route.query.endDate
        }
        if (selectedTimeRange.value) {
            return moment().toISOString(true)
        }
        return undefined
    })
    const startDate = computed(() => {
        // we mention the last refresh date here to trick
        // VueJs fine grained reactivity system and invalidate
        // computed property startDate
        if (route.query.startDate && lastRefreshDate.value) {
            return route.query.startDate
        }
        if (selectedTimeRange.value) {
            return moment().subtract(moment.duration(selectedTimeRange.value).as("milliseconds")).toISOString(true)
        }

        // the default is PT30D
        return moment().subtract(7, "days").toISOString(true)
    })
    const flowId = computed(() => route.params.id)
    const routeNamespace = computed(() => route.params.namespace ?? route.params.id)
    const charts = computed(() => [
        {...YAML_UTILS.parse(YAML_CHART), content: YAML_CHART},
    ])

    const loadQuery = (base: any) => {
        const {page: _p, size: _s, sort: _so, logsPage: _lp, logsSize: _ls, ...routeFilters} = route.query
        let queryFilter = props.filters ?? {...routeFilters}

        if (isFlowEdit.value) {
            queryFilter["filters[namespace][EQUALS]"] = routeNamespace.value
            queryFilter["filters[flowId][EQUALS]"] = flowId.value
        } else if (isNamespaceEdit.value) {
            queryFilter["filters[namespace][EQUALS]"] = routeNamespace.value
        }

        // Level filter is a minimum threshold. Always normalize to a single EQUALS query.
        if (!props.filters) {
            queryFilter = normalizeRouteLevelFilter(queryFilter, effectiveLogLevel.value)
        }

        if (!queryFilter["startDate"] || !queryFilter["endDate"]) {
            queryFilter["startDate"] = startDate.value
            queryFilter["endDate"] = endDate.value
        }

        delete queryFilter["level"]

        return _merge(base, queryFilter)
    }

    const loadData = async ({page, size}: {page: number; size: number; sort?: string}) => {
        if (!loadInit.value) return
        isLoading.value = true

        await logsStore.findLogs(loadQuery({
            page,
            size,
            sort: "timestamp:desc",
        }))
            .finally(() => {
                isLoading.value = false
            })
    }

    const onFilterRouteSync = (filters: AppliedFilter[]) => {
        if (props.filters || !hasLevelFilterUI.value) {
            return
        }

        syncLevelFromAppliedFilters(filters)
    }

    const pageKey = props.embed ? "logsPage" : "page"
    const sizeKey = props.embed ? "logsSize" : "size"
    const urlPage = computed(() => Number(route.query[pageKey]) || 1)
    const urlSize = computed(() => Number(route.query[sizeKey]) || 25)

    const onPageChanged = ({page, size}: {page: number; size: number}) => {
        router.push({query: {...route.query, [pageKey]: String(page), [sizeKey]: String(size)}})
    }

    const filterQueryKey = computed(() => {
        const {page: _p, size: _s, sort: _so, logsPage: _lp, logsSize: _ls, ...filters} = route.query
        return JSON.stringify(filters)
    })
    watch(filterQueryKey, () => {
        dataTable.value?.resetAndReload()
    })

    const showStatChart = () => showChart.value

    const onShowChartChange = (value: boolean) => {
        showChart.value = value
        localStorage.setItem(storageKeys.SHOW_LOGS_CHART, value.toString())
        if (showStatChart()) {
            dataTable.value?.reload()
        }
    }

    const refresh = () => {
        lastRefreshDate.value = new Date()
        if (dashboardRef.value) {
            dashboardRef.value.refreshCharts()
        }
        dataTable.value?.reload()
    }

    watch(() => props.reloadLogs, (newValue) => {
        if (newValue) refresh()
    })
</script>
<style scoped lang="scss">

    .shadow {
        box-shadow: 0px 2px 4px 0px var(--ks-shadow-element) !important;
    }

    .log-panel {
        > div.log-content {
            margin-bottom: 1rem;
            .navbar {
                border: 1px solid var(--ks-border-default);
            }

            .kel-empty {
                background-color: transparent;
            }
        }

        .logs-wrapper {
            margin-bottom: 1rem;
            border-radius: var(--kel-border-radius-round);
            overflow: hidden;
            padding: 1rem;
            padding-top: .5rem;
            background-color: var(--ks-bg-surface);
            border: 1px solid var(--ks-border-default);

            html.dark & {
                background-color: var(--ks-bg-sidebar);
            }

            > * + * {
                border-top: 1px solid var(--ks-border-default);
            }
        }
    }
</style>
