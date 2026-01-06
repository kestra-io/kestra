<template>
    <TopNavBar v-if="!embed" :title="routeInfo.title" />
    <section v-bind="$attrs" :class="{'container': !embed}" class="log-panel">
        <div class="mb-3">
            <KSFilter
                :configuration="logFilter"
                :tableOptions="{
                    chart: {shown: true, value: showChart, callback: onShowChartChange},
                    refresh: {shown: true, callback: refresh},
                    columns: {shown: false}
                }"
                :defaultScope="false"
            />
        </div>

        <div v-if="showStatChart()" class="mb-4">
            <Sections ref="dashboard" :charts :dashboard="{id: 'default', charts: []}" showDefault />
        </div>

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
                <div
                    v-for="log in logsStore.logs"
                    :key="log.id"
                    class="log-row d-flex align-items-start p-3 border-bottom font-monospace"
                    :class="{'bg-selected': selectedLogs.includes(log.id)}"
                >
                    <div class="me-3 pt-1">
                        <ElCheckbox
                            :modelValue="selectedLogs.includes(log.id)"
                            @change="(val) => toggleRow(log.id, val)"
                        />
                    </div>

                    <div class="me-3 pt-1">
                        <span class="badge badge-level" :class="levelClass(log.level)">
                            {{ log.level }}
                        </span>
                    </div>

                    <div class="flex-grow-1 overflow-hidden">
                        <div class="log-meta mb-1">
                            <span class="text-muted me-3">{{ formatTimestamp(log.timestamp) }}</span>

                            <span v-if="log.namespace" class="me-3">
                                <span class="meta-label">namespace:</span>
                                <span class="meta-value">{{ log.namespace }}</span>
                            </span>

                            <span v-if="log.flowId" class="me-3">
                                <span class="meta-label">flowId:</span>
                                <router-link :to="{name: 'flows/update', params: {namespace: log.namespace, id: log.flowId}}" class="meta-value link">
                                    {{ log.flowId }}
                                </router-link>
                            </span>

                            <span v-if="log.taskId" class="me-3">
                                <span class="meta-label">taskId:</span>
                                <span class="meta-value-neutral">{{ log.taskId }}</span>
                            </span>

                            <span v-if="log.executionId" class="me-3">
                                <span class="meta-label">executionId:</span>
                                <router-link
                                    :to="{name: 'executions/update', params: {namespace: log.namespace, flowId: log.flowId, id: log.executionId}}"
                                    class="meta-value link"
                                >
                                    {{ log.executionId }}
                                </router-link>
                            </span>
                        </div>

                        <div class="log-message">
                            {{ log.message }}
                        </div>
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

    // --- HELPER FUNCTIONS ---
    const formatTimestamp = (dateString: string) => {
        return moment(dateString).format("YYYY-MM-DD HH:mm:ss.SSS");
    };

    const levelClass = (level: string) => {
        switch (level) {
        case "ERROR": return "level-error";
        case "CRITICAL": return "level-error";
        case "WARN": return "level-warning";
        case "WARNING": return "level-warning";
        case "DEBUG": return "level-debug";
        case "TRACE": return "level-trace";
        case "INFO": return "level-info";
        default: return "level-default";
        }
    };

    // --- SELECTION LOGIC ---
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

    // --- DELETE LOGIC ---
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

    // --- STANDARD KESTRA DATA LOGIC ---
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

        /* --- LOG HEADER --- */
        .log-header {
            background-color: var(--ks-background-table-header);
            min-height: 48px;
            transition: background-color 0.2s;

            &.bg-active {
                background-color: var(--ks-selection) !important;
            }
        }

        /* --- HIDE DUPLICATE CHECKBOX IN BULK SELECT --- */
        /* This deep selector targets the internal checkbox of BulkSelect to prevent duplication */
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

            /* --- BADGES --- */
            .badge-level {
                display: inline-block;
                width: 60px;
                text-align: center;
                border-radius: 2px;
                padding: 3px 0;
                font-weight: 700;
                color: #000;

                &.level-debug { background-color: #93c5fd; }
                &.level-info  { background-color: #86efac; }
                &.level-warning { background-color: #fde047; }
                &.level-error { background-color: #fca5a5; }
                &.level-default { background-color: #e5e7eb; }
            }

            /* --- METADATA --- */
            .log-meta {
                white-space: nowrap;
                overflow-x: auto;

                .meta-label {
                    color: var(--ks-content-secondary);
                    margin-right: 4px;
                }

                .meta-value {
                    color: var(--ks-content-link);

                    &.link {
                        text-decoration: none;
                        &:hover { text-decoration: underline; }
                    }
                }

                .meta-value-neutral {
                     color: var(--ks-content-primary);
                }
            }

            /* --- MESSAGE --- */
            .log-message {
                color: var(--ks-content-primary);
                white-space: pre-wrap;
                word-break: break-all;
                line-height: 1.5;
                margin-top: 2px;
            }
        }
    }
</style>