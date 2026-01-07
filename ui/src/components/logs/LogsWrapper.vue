<template>
    <TopNavBar v-if="!embed" :title="routeInfo.title" />
    <section v-bind="$attrs" :class="{'container': !embed}" class="log-panel">
        <div class="mb-3">
            <KSFilter
                :configuration="logFilter"
                :tableOptions="{
                    chart: {shown: true, value: showChart, callback: onShowChartChange},
                    refresh: {refreshing: isLoading, callback: refresh},
                    columns: {shown: false}
                }"
                :defaultScope="false"
            />
        </div>

        <template v-if="showStatChart() && logsStore.logs && logsStore.logs.length > 0">
            <Sections ref="dashboard" :charts :dashboard="{id: 'default', charts: []}" showDefault class="mb-4" />
        </template>

        <div v-loading="isLoading" class="logs-container">
            <div class="log-header p-2 d-flex align-items-center border-bottom" :class="{'bg-active': selectedLogs.length > 0}">
                <div class="ps-2 me-3">
                    <ElCheckbox
                        :modelValue="isPageSelected"
                        :indeterminate="isIndeterminate"
                        @change="togglePageSelection"
                    />
                </div>

                <div class="flex-grow-1 d-flex align-items-center hide-bulk-checkbox">
                    <BulkSelect
                        v-if="selectedLogs.length > 0 || isSelectAll"
                        :selectAll="isSelectAll"
                        :selections="selectedLogs"
                        :total="logsStore.total"
                        @update:select-all="onSelectAll"
                        @unselect="onClearSelection"
                    >
                        <ElButton :icon="Delete" @click="deleteLogs" type="danger" plain>
                            {{ $t('delete') }}
                        </ElButton>
                    </BulkSelect>
                </div>
            </div>

            <template v-if="logsStore.logs && logsStore.logs.length > 0">
                <div class="logs-wrapper">
                    <div
                        v-for="(log, i) in logsStore.logs"
                        :key="log.id"
                        class="log-row d-flex align-items-start border-bottom font-monospace"
                        :class="{'bg-selected': selectedLogs.includes(log.id), 'log-0': i === 0}"
                    >
                        <div class="ps-3 pt-2 me-1">
                            <ElCheckbox
                                :modelValue="selectedLogs.includes(log.id)"
                                @change="(val) => toggleRow(log.id, val)"
                            />
                        </div>

                        <LogLine
                            class="flex-grow-1"
                            level="TRACE"
                            filter=""
                            :excludeMetas="isFlowEdit ? ['namespace', 'flowId'] : []"
                            :log="log"
                        />
                    </div>
                </div>
            </template>

            <div v-else-if="!isLoading">
                <NoData :text="$t('no_logs_data_description')" />
            </div>

            <div class="p-3 d-flex justify-content-end border-top" v-if="logsStore.total > 0">
                <ElPagination
                    v-model:currentPage="internalPageNumber"
                    v-model:pageSize="internalPageSize"
                    :total="logsStore.total"
                    layout="total, sizes, prev, pager, next"
                    @current-change="onPageChanged"
                    @size-change="onPageChanged"
                    background
                    size="small"
                />
            </div>
        </div>
    </section>
</template>

<script setup lang="ts">
    import {ref, computed, onMounted, watch, useTemplateRef} from "vue";
    import {useRoute} from "vue-router";
    import {useI18n} from "vue-i18n";
    import _merge from "lodash/merge";
    import moment from "moment";
    import axios from "axios";

    import {ElButton, ElMessageBox, ElCheckbox, ElPagination} from "element-plus";
    import Delete from "vue-material-design-icons/Delete.vue";
    import LogLine from "./LogLine.vue";

    import {useLogFilter} from "../filter/configurations";
    import KSFilter from "../filter/components/KSFilter.vue";
    import Sections from "../dashboard/sections/Sections.vue";
    import TopNavBar from "../../components/layout/TopNavBar.vue";
    import NoData from "../layout/NoData.vue";
    import BulkSelect from "../layout/BulkSelect.vue";

    import {storageKeys} from "../../utils/constants";
    import {useToast} from "../../utils/toast";
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
    const toast = useToast();
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

    const selectedLogs = ref<string[]>([]);
    const isSelectAll = ref(false);

    const isPageSelected = computed(() => {
        if (!logsStore.logs || logsStore.logs.length === 0) return false;
        return logsStore.logs.every(log => selectedLogs.value.includes(log.id));
    });

    const isIndeterminate = computed(() => {
        if (!logsStore.logs || logsStore.logs.length === 0) return false;
        if (selectedLogs.value.length === 0) return false;

        const visibleIds = logsStore.logs.map(l => l.id);
        const visibleSelectedCount = visibleIds.filter(id => selectedLogs.value.includes(id)).length;

        return visibleSelectedCount > 0 && visibleSelectedCount < visibleIds.length;
    });

    const toggleRow = (id: string, checked: any) => {
        if (checked) {
            selectedLogs.value.push(id);
        } else {
            selectedLogs.value = selectedLogs.value.filter(s => s !== id);
            isSelectAll.value = false;
        }
    };

    const togglePageSelection = (checked: any) => {
        if (!logsStore.logs) return;
        const pageIds = logsStore.logs.map(l => l.id);
        const currentSet = new Set(selectedLogs.value);

        if (checked) {
            pageIds.forEach(id => currentSet.add(id));
        } else {
            pageIds.forEach(id => currentSet.delete(id));
            isSelectAll.value = false;
        }
        selectedLogs.value = Array.from(currentSet);
    };

    // --- BULK SELECT EVENTS ---
    const onSelectAll = (value: boolean) => {
        isSelectAll.value = value;
        if (value && logsStore.logs) {
            const pageIds = logsStore.logs.map(l => l.id);
            const currentSet = new Set(selectedLogs.value);
            pageIds.forEach(id => currentSet.add(id));
            selectedLogs.value = Array.from(currentSet);
        } else if (!value) {
            selectedLogs.value = [];
        }
    };

    const onClearSelection = () => {
        selectedLogs.value = [];
        isSelectAll.value = false;
    };

    const deleteLogs = () => {
        if (selectedLogs.value.length === 0 && !isSelectAll.value) return;

        const count = isSelectAll.value ? logsStore.total : selectedLogs.value.length;

        ElMessageBox.confirm(
            t("Are you sure you want to delete the selected logs?", {count}),
            t("confirmation"),
            {
                confirmButtonText: t("delete"),
                cancelButtonText: t("cancel"),
                type: "warning",
            }
        ).then(() => {
            const deletePromise = isSelectAll.value
                ? axios.delete("/api/v1/logs/search", {
                    params: loadQuery({minLevel: props.filters ? null : selectedLogLevel.value})
                })
                : axios.delete("/api/v1/logs/bulk", {data: selectedLogs.value});

            deletePromise.then(() => {
                toast.success(t("deleted"));
                onClearSelection();
                refresh();
            });
        });
    };

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
        if (route.query.startDate && lastRefreshDate.value) {
            return route.query.startDate;
        }
        if (route.query.timeRange) {
            return moment().subtract(moment.duration(route.query.timeRange as string).as("milliseconds")).toISOString(true);
        }
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
        if (!props.embed) {
            loadData();
        }
    });
</script>

<style scoped lang="scss">
    @import "@kestra-io/ui-libs/src/scss/variables";

    .log-panel {
        /* --- LOGS CONTAINER --- */
        .logs-container {
            border: 1px solid var(--ks-border-primary);
            border-radius: var(--bs-border-radius-lg);
            background-color: var(--ks-background-left-menu) !important;
            overflow: hidden;

            html.dark & {
                 border-color: #2c2c2e;
            }
        }

        .log-header {
            background-color: var(--ks-background-table-header);
            min-height: 48px;
            transition: background-color 0.2s;

            &.bg-active {
                background-color: var(--ks-selection) !important;
            }
        }

        .hide-bulk-checkbox :deep(.el-checkbox) {
            display: none !important;
        }

        .font-monospace {
             font-family: 'Source Code Pro', monospace, var(--bs-font-monospace) !important;
             font-size: 0.875rem;
        }

        .log-row {
            border-bottom: 1px solid var(--ks-border-primary);

            html.dark & {
                border-bottom: 1px solid #2c2c2e;
            }

            transition: background-color 0.1s ease;

            &:hover {
                background-color: rgba(255, 255, 255, 0.02);
            }

            &.bg-selected {
                background-color: var(--ks-selection) !important;
            }
        }
    }
</style>