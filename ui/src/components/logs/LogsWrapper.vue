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
                @loaded="onLoaded"
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
                    <QuickFilters
                        v-if="!hasComplexFilters"
                        :levels="VALUES.LEVELS"
                        :level="effectiveLogLevel?.value"
                        :levelLabel="t('filter.level_log_executions.label')"
                        :showInterval="false"
                        @update:level="selectLevel"
                    />
                </template>

                <template v-if="showStatChart() && logsStore.logs && logsStore.logs.length > 0" #top>
                    <Sections ref="dashboard" :charts :dashboard="{id: 'default', charts: []}" showDefault class="mb-4" />
                </template>

                <template #table>
                    <div v-ks-loading="isLoading">
                        <div class="logs-toolbar">
                            <div class="logs-toolbar__left">
                                <LogLevelNavigator
                                    v-for="level in presentLevels"
                                    :key="level"
                                    filterMode
                                    :level="level"
                                    :totalCount="serverLevelCounts[level]"
                                    @select="selectLevel(level)"
                                />
                            </div>
                            <div class="logs-toolbar__actions">
                                <LogDisplaySettings />
                                <KsButton type="default" size="default" class="logs-toolbar__btn" :icon="Download" :aria-label="t('download logs')" :tooltip="t('download logs')" @click="openDownload" />
                                <KsButton type="default" size="default" class="logs-toolbar__btn" :icon="ContentCopy" :aria-label="t('copy logs')" :tooltip="t('copy logs')" @click="copyAllLogs" />
                            </div>
                        </div>
                        <div v-if="logsStore.logs !== undefined && logsStore.logs?.length > 0" class="logs-wrapper">
                            <LogLine
                                v-for="(log, i) in logsStore.logs"
                                :key="`${log.taskRunId}-${i}`"
                                level="TRACE"
                                filter=""
                                :highlight="searchTerm"
                                :excludeMetas="isFlowEdit ? ['namespace', 'flowId'] : []"
                                :log="log"
                                :class="{'log-0': i === 0}"
                                clickableLevel
                                @filter="onValueFilter"
                                @filter-level="selectLevel"
                            />
                        </div>

                        <div v-else-if="!isLoading">
                            <KsEmpty :description="$t('no_logs_data_description')" />
                        </div>
                    </div>
                </template>
            </KsDataTable>
        </div>

        <KsDialog v-model="downloadOpen" :title="t('download logs')" width="480px" destroyOnClose>
            <p class="download-hint">{{ t('download_logs_description') }}</p>
            <QuickFilters
                :levels="VALUES.LEVELS"
                :intervals="quickIntervals"
                :level="downloadLevel"
                :timeRange="downloadTimeRange"
                :levelLabel="t('filter.level_log_executions.label')"
                :intervalLabel="t('filter.timeRange_log.label')"
                @update:level="(value: string) => (downloadLevel = value)"
                @update:time-range="(value: string) => (downloadTimeRange = value)"
            />
            <template #footer>
                <KsButton @click="downloadOpen = false">{{ t('cancel') }}</KsButton>
                <KsButton type="primary" :loading="downloading" @click="downloadLogs">
                    {{ t('download') }}
                </KsButton>
            </template>
        </KsDialog>
    </section>
</template>

<script setup lang="ts">
    import {ref, computed, watch, useTemplateRef} from "vue"
    import {useRoute, useRouter} from "vue-router"
    import {useI18n} from "vue-i18n"
    import _merge from "lodash/merge"
    import moment from "moment"
    import {useLogFilter} from "../filter/configurations"
    import {useValues} from "../filter/composables/useValues"
    import {useComplexFilters} from "../filter/composables/useComplexFilters"
    import QuickFilters from "../filter/QuickFilters.vue"
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
    import * as Utils from "../../utils/utils"
    import {useToast} from "../../utils/toast"
    import Download from "vue-material-design-icons/Download.vue"
    import ContentCopy from "vue-material-design-icons/ContentCopy.vue"
    import LogDisplaySettings from "./LogDisplaySettings.vue"
    import LogLevelNavigator from "./LogLevelNavigator.vue"
    import {buildValueFilterQuery} from "./logValueFilter"

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
    const toast = useToast()
    const logsStore = useLogsStore()
    const logFilter = useLogFilter()
    const {VALUES} = useValues("logs")
    const {hasComplexFilters} = useComplexFilters()
    const quickIntervals = computed(() => [
        {label: t("datepicker.short.15m"), value: "PT15M"},
        {label: t("datepicker.short.1h"), value: "PT1H"},
        {label: t("datepicker.short.12h"), value: "PT12H"},
        {label: t("datepicker.short.1d"), value: "PT24H"},
        {label: t("datepicker.short.7d"), value: "PT168H"},
    ])
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
    const searchTerm = computed(() => {
        const key = Object.keys(route.query).find((k) => k.startsWith("filters[q]"))
        return key ? String(route.query[key] ?? "") : ""
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

    const downloadOpen = ref(false)
    const downloadLevel = ref<string | undefined>(undefined)
    const downloadTimeRange = ref<string | undefined>(undefined)
    const downloading = ref(false)

    const openDownload = () => {
        downloadLevel.value = effectiveLogLevel.value?.value
        downloadTimeRange.value = selectedTimeRange.value ?? undefined
        downloadOpen.value = true
    }

    const downloadLogs = () => {
        const {
            page: _p, size: _s, sort: _so, logsPage: _lp, logsSize: _ls,
            level: _l, startDate: _sd, endDate: _ed, ...routeFilters
        } = route.query
        const params: Record<string, any> = props.filters ? {...props.filters} : {...routeFilters}

        if (isFlowEdit.value) {
            params["filters[namespace][EQUALS]"] = routeNamespace.value
            params["filters[flowId][EQUALS]"] = flowId.value
        } else if (isNamespaceEdit.value) {
            params["filters[namespace][EQUALS]"] = routeNamespace.value
        }

        Object.keys(params)
            .filter((k) => k.startsWith("filters[level]"))
            .forEach((k) => delete params[k])
        if (downloadLevel.value) {
            params["filters[level][GREATER_THAN_OR_EQUAL_TO]"] = downloadLevel.value
        }

        if (downloadTimeRange.value) {
            params.startDate = moment()
                .subtract(moment.duration(downloadTimeRange.value).as("milliseconds"))
                .toISOString(true)
            params.endDate = moment().toISOString(true)
        } else {
            if (startDate.value) params.startDate = startDate.value
            if (endDate.value) params.endDate = endDate.value
        }
        params.sort = "timestamp:desc"

        downloading.value = true
        logsStore.downloadLogs(params)
            .then(() => (downloadOpen.value = false))
            .finally(() => (downloading.value = false))
    }

    const LEVEL_ORDER = ["TRACE", "DEBUG", "INFO", "WARN", "ERROR"]
    const serverLevelCounts = ref<Record<string, number>>({})
    const presentLevels = computed(() => LEVEL_ORDER.filter((level) => (serverLevelCounts.value[level] ?? 0) > 0))

    let lastCountedKey = ""
    const refreshLevelCounts = () => {
        if (!loadInit.value || lastCountedKey === filterQueryKey.value) return
        const key = filterQueryKey.value
        lastCountedKey = key
        logsStore.levelCounts(loadQuery({})).then((counts) => {
            if (key === filterQueryKey.value) serverLevelCounts.value = counts
        })
    }

    const selectLevel = (level: string) => {
        const query: Record<string, any> = {...route.query}
        Object.keys(query)
            .filter((key) => key.startsWith("filters[level]"))
            .forEach((key) => delete query[key])
        query["filters[level][GREATER_THAN_OR_EQUAL_TO]"] = level
        query[pageKey] = "1"
        router.push({query})
    }

    const onValueFilter = ({field, value, negate}: {field: string; value: string; negate: boolean}) => {
        const query = buildValueFilterQuery(route.query, field, value, negate, pageKey)
        if (query) router.push({query})
    }

    const copyAllLogs = () => {
        const text = (logsStore.logs ?? [])
            .map((l: any) => `${(l.level ?? "").padEnd(5)} ${l.timestamp} ${(l.message ?? "").replace(/\s+$/, "")}`)
            .join("\n")
        Utils.copy(text)
        toast.success(t("logs_copied"))
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

    const pinToBottom = ref(false)

    const onPageChanged = ({page, size}: {page: number; size: number}) => {
        pinToBottom.value = !props.embed
        router.push({query: {...route.query, [pageKey]: String(page), [sizeKey]: String(size)}})
    }

    const onLoaded = () => {
        refreshLevelCounts()
        if (!pinToBottom.value) return
        pinToBottom.value = false
        const main = document.querySelector("main")
        if (!main) return
        requestAnimationFrame(() => {
            main.scrollTop = main.scrollHeight
        })
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

    .download-hint {
        margin: 0 0 var(--ks-spacing-3);
        color: var(--ks-text-secondary);
        font-size: var(--ks-font-size-sm);
    }

    .shadow {
        box-shadow: 0px 2px 4px 0px var(--ks-shadow-element) !important;
    }

    .logs-toolbar {
        display: flex;
        flex-wrap: wrap;
        align-items: center;
        gap: var(--ks-spacing-2);
        position: sticky;
        top: 0;
        z-index: 10;
        margin: 0 var(--ks-spacing-5) var(--ks-spacing-3);
        padding: var(--ks-spacing-2) 0;
        background: var(--ks-bg-base);

        &__left {
            display: flex;
            flex-wrap: wrap;
            align-items: center;
            gap: var(--ks-spacing-2);
        }

        &__actions {
            display: flex;
            align-items: center;
            gap: var(--ks-spacing-2);
            margin-left: auto;
        }

        &__btn {
            margin: 0;
            padding: var(--ks-spacing-2);
            border-radius: var(--ks-radius-base);
        }
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
            margin: 0 var(--ks-spacing-5);
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
