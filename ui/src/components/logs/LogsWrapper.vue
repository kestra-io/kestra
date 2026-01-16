<template>
    <TopNavBar v-if="!embed" :title="routeInfo.title" />
    <section v-bind="$attrs" :class="{'container': !embed}" class="log-panel">
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
    import {ref, computed, onMounted, watch, useTemplateRef} from "vue";
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
    import {decodeSearchParams} from "../filter/utils/helpers";
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
    }>(), {
        embed: false,
        showFilters: false,
        filters: undefined,
        logLevel: undefined,
        reloadLogs: undefined
    });

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
    const selectedLogLevel = computed(() => {
        const decodedParams = decodeSearchParams(route.query);
        const levelFilters = decodedParams.filter(item => item?.field === "level");
        const decoded = levelFilters.length > 0 ? levelFilters[0]?.value : "INFO";
        return props.logLevel || decoded || localStorage.getItem("defaultLogLevel") || "INFO";
    });
    const endDate = computed(() => {
        if (route.query.endDate) {
            return route.query.endDate;
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
        if (route.query.timeRange) {
            return moment().subtract(moment.duration(route.query.timeRange as string).as("milliseconds")).toISOString(true);
        }

        // the default is PT30D
        return moment().subtract(7, "days").toISOString(true);
    });
    const flowId = computed(() => route.params.id);
    const namespace = computed(() => route.params.namespace ?? route.params.id);
    const charts = computed(() => [
        {...YAML_UTILS.parse(YAML_CHART), content: YAML_CHART}
    ]);

    const loadQuery = (base: any) => {
        let queryFilter = props.filters ?? queryWithFilter();

        if (isFlowEdit.value) {
            queryFilter["filters[namespace][EQUALS]"] = namespace.value;
            queryFilter["filters[flowId][EQUALS]"] = flowId.value;
        } else if (isNamespaceEdit.value) {
            queryFilter["filters[namespace][EQUALS]"] = namespace.value;
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

        const data = {
            page: props.filters ? internalPageNumber.value : route.query.page || internalPageNumber.value,
            size: props.filters ? internalPageSize.value : route.query.size || internalPageSize.value,
            ...props.filters
        };

        logsStore.findLogs(loadQuery({
            ...data,
            minLevel: props.filters ? null : selectedLogLevel.value,
            sort: "timestamp:desc"
        }))
            .finally(() => {
                isLoading.value = false;
                if (callback) callback();
            });
    };

    const {onPageChanged, queryWithFilter, internalPageNumber, internalPageSize} = useDataTableActions({
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

    watch(() => route.query, () => {
        loadData();
    }, {deep: true});

    watch(() => props.reloadLogs, (newValue) => {
        if (newValue) refresh();
    });

    onMounted(() => {
        // Load data on mount if not embedded
        if (!props.embed) {
            loadData();
        }
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
