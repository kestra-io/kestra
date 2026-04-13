<template>
    <TopNavBar v-if="!embed" :title="routeInfo.title" />
    <section v-if="ready" v-bind="$attrs" :class="{'container': !embed}" class="log-panel">
        <div class="log-content">
            <DataTable @page-changed="onPageChanged" ref="dataTable" :total="logsStore.total" :size="internalPageSize" :page="internalPageNumber" :embed="embed">
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
                    <div v-loading="isLoading">
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
                            <NoData :text="$t('no_logs_data_description')" />
                        </div>
                    </div>
                </template>
            </DataTable>
        </div>
    </section>
</template>

<script setup lang="ts">
    import {ref, computed, watch, useTemplateRef} from "vue";
    import {useRoute} from "vue-router";
    import {useI18n} from "vue-i18n";
    import _merge from "lodash/merge";
    import moment from "moment";
    import {useLogFilter} from "../filter/configurations";
    import KSFilter from "../filter/components/KSFilter.vue";
    import Sections from "../dashboard/sections/Sections.vue";
    import DataTable from "../../components/layout/DataTable.vue";
    import TopNavBar from "../../components/layout/TopNavBar.vue";
    import LogLine from "../logs/LogLine.vue";
    import NoData from "../layout/NoData.vue";
    import {storageKeys} from "../../utils/constants";
    import {
        decodeSearchParams,
        encodeFiltersToQuery,
        getUniqueFilters,
        isValidFilter,
        keyOfComparator
    } from "../filter/utils/helpers";
    import {AppliedFilter} from "../filter/utils/filterTypes";
    import {
        hasUnsupportedRouteLevelComparator,
        normalizeRouteLevelFilter,
        readAppliedLevelFilter,
        readRouteLevelFilter
    } from "../filter/utils/logLevelQuery";
    import {useRouteFilterPolicy} from "../filter/composables/useRouteFilterPolicy";
    import * as YAML_UTILS from "@kestra-io/ui-libs/flow-yaml-utils";
    import YAML_CHART from "../dashboard/assets/logs_timeseries_chart.yaml?raw";
    import {useLogsStore} from "../../stores/logs";
    import {useDataTableActions} from "../../composables/useDataTableActions";
    import useRouteContext from "../../composables/useRouteContext";

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
        restoreurl: undefined
    });
    defineEmits(["expand-subflow", "go-to-detail", "goToDetail"]);

    const route = useRoute();
    const {t} = useI18n();
    const logsStore = useLogsStore();
    const logFilter = useLogFilter();

    const routeInfo = computed(() => ({
        title: t("logs"),
    }));
    useRouteContext(routeInfo, props.embed);

    const isLoading = ref(false);
    const lastRefreshDate = ref(new Date());
    const showChart = ref(localStorage.getItem(storageKeys.SHOW_LOGS_CHART) !== "false");
    const dashboardRef = useTemplateRef("dashboard");

    const isFlowEdit = computed(() => route.name === "flows/update");
    const isNamespaceEdit = computed(() => route.name === "namespaces/update");
    const hasLevelFilterUI = computed(() => !props.embed || props.showFilters);
    const defaultLogLevel = computed(() =>
        typeof window !== "undefined"
            ? localStorage.getItem("defaultLogLevel") || "INFO"
            : "INFO"
    );
    const {
        effectiveValue: effectiveLogLevel,
        syncFromAppliedFilters: syncLevelFromAppliedFilters
    } = useRouteFilterPolicy<string>({
        enabled: () => !props.filters && hasLevelFilterUI.value,
        explicitValue: () => props.logLevel,
        defaultValue: () => defaultLogLevel.value,
        applyDefaultIfMissing: () => true,
        fallbackValue: () => undefined,
        readFromRoute: readRouteLevelFilter,
        writeToRoute: normalizeRouteLevelFilter,
        hasUnsupportedRouteValue: hasUnsupportedRouteLevelComparator,
        readFromAppliedFilters: readAppliedLevelFilter,
        shouldSyncFromAppliedFilters: (filters, routeQuery) => {
            const encodedFilters = encodeFiltersToQuery(
                getUniqueFilters(filters.filter(isValidFilter)),
                keyOfComparator
            );

            return !Object.entries(encodedFilters).some(
                ([key, value]) =>
                    !key.startsWith("filters[level][") &&
                    routeQuery[key] !== value
            );
        }
    });
    const selectedTimeRange = computed(() => {
        if (route.query.timeRange) {
            return route.query.timeRange as string;
        }

        const decodedParams = decodeSearchParams(route.query);
        const timeRangeFilter = decodedParams.find(item => item?.field === "timeRange");
        const rawValue = timeRangeFilter?.value;

        if (Array.isArray(rawValue)) {
            return rawValue[0];
        }

        return rawValue as string | undefined;
    });
    const endDate = computed(() => {
        if (route.query.endDate) {
            return route.query.endDate;
        }
        if (selectedTimeRange.value) {
            return moment().toISOString(true);
        }
        return undefined;
    });
    const startDate = computed(() => {
        // we mention the last refresh date here to trick
        // VueJs fine grained reactivity system and invalidate
        // computed property startDate
        if (route.query.startDate && lastRefreshDate.value) {
            return route.query.startDate;
        }
        if (selectedTimeRange.value) {
            return moment().subtract(moment.duration(selectedTimeRange.value).as("milliseconds")).toISOString(true);
        }

        // the default is PT30D
        return moment().subtract(7, "days").toISOString(true);
    });
    const flowId = computed(() => route.params.id);
    const routeNamespace = computed(() => route.params.namespace ?? route.params.id);
    const charts = computed(() => [
        {...YAML_UTILS.parse(YAML_CHART), content: YAML_CHART}
    ]);

    const loadQuery = (base: any) => {
        let queryFilter = props.filters ?? queryWithFilter();

        if (isFlowEdit.value) {
            queryFilter["filters[namespace][EQUALS]"] = routeNamespace.value;
            queryFilter["filters[flowId][EQUALS]"] = flowId.value;
        } else if (isNamespaceEdit.value) {
            queryFilter["filters[namespace][EQUALS]"] = routeNamespace.value;
        }

        // Level filter is a minimum threshold. Always normalize to a single EQUALS query.
        if (!props.filters) {
            queryFilter = normalizeRouteLevelFilter(queryFilter, effectiveLogLevel.value);
        }

        if (!queryFilter["startDate"] || !queryFilter["endDate"]) {
            queryFilter["startDate"] = startDate.value;
            queryFilter["endDate"] = endDate.value;
        }

        delete queryFilter["level"];

        return _merge(base, queryFilter);
    };

    const loadData = (callback?: () => void) => {
        isLoading.value = true;

        logsStore.findLogs(loadQuery({
            page: parseInt(route.query?.page as string ?? "1"),
            size: parseInt(route.query?.size as string ?? "25"),
            minLevel: props.filters ? null : effectiveLogLevel.value,
            sort: "timestamp:desc"
        }))
            .finally(() => {
                isLoading.value = false;
                if (callback) callback();
            });
    };

    const onFilterRouteSync = (filters: AppliedFilter[]) => {
        if (props.filters || !hasLevelFilterUI.value) {
            return;
        }

        syncLevelFromAppliedFilters(filters);
    };

    const {onPageChanged, queryWithFilter, internalPageNumber, internalPageSize, ready} = useDataTableActions({
        loadData
    });

    const showStatChart = () => showChart.value;

    const onShowChartChange = (value: boolean) => {
        showChart.value = value;
        localStorage.setItem(storageKeys.SHOW_LOGS_CHART, value.toString());
        if (showStatChart()) {
            loadData();
        }
    };

    const refresh = () => {
        lastRefreshDate.value = new Date();
        if (dashboardRef.value) {
            dashboardRef.value.refreshCharts();
        }
        loadData();
    };

    watch(() => props.reloadLogs, (newValue) => {
        if (newValue) refresh();
    });
</script>
<style scoped lang="scss">
    @import "@kestra-io/ui-libs/src/scss/variables";

    .shadow {
        box-shadow: 0px 2px 4px 0px var(--ks-card-shadow) !important;
    }

    .log-panel {
        > div.log-content {
            margin-bottom: 1rem;
            .navbar {
                border: 1px solid var(--ks-border-primary);
            }

            .el-empty {
                background-color: transparent;
            }
        }

        .logs-wrapper {
            margin-bottom: 1rem;
            border-radius: var(--bs-border-radius-lg);
            overflow: hidden;
            padding: $spacer;
            padding-top: .5rem;
            background-color: var(--ks-background-card);
            border: 1px solid var(--ks-border-primary);

            html.dark & {
                background-color: var(--bs-gray-100);
            }

            > * + * {
                border-top: 1px solid var(--ks-border-primary);
            }
        }
    }
</style>
