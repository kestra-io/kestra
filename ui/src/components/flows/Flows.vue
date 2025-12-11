<template>
    <TopNavBar v-if="topbar" :title="routeInfo.title">
        <template #additional-right>
            <ul class="header-actions-list">
                <li>
                    <el-button v-if="canRead" :icon="Download" @click="exportFlowsAsStream()">
                        {{ t('export_csv') }}
                    </el-button>
                </li>
                <li>
                    <el-button :icon="Upload" @click="file?.click()">
                        {{ t("import") }}
                    </el-button>
                    <input ref="file" type="file" accept=".zip, .yml, .yaml" @change="importFlows()" class="d-none">
                </li>
                <li>
                    <router-link :to="{name: 'flows/search'}">
                        <el-button :icon="TextBoxSearch">
                            {{ t("source search") }}
                        </el-button>
                    </router-link>
                </li>
                <li>
                    <router-link
                        :to="{
                            name: 'flows/create',
                            query: {namespace: $route.query.namespace},
                        }"
                        v-if="canCreate"
                    >
                        <el-button :icon="Plus" type="primary">
                            {{ t("create") }}
                        </el-button>
                    </router-link>
                </li>
            </ul>
        </template>
    </TopNavBar>
    <section :class="{container: topbar}" v-if="ready">
        <div>
            <DataTable
                @page-changed="onPageChanged"
                ref="dataTable"
                :total="flowStore.total"
            >
                <template #navbar>
                    <KSFilter
                        :configuration="flowFilter"
                        :properties="{
                            shown: true,
                            columns: optionalColumns,
                            storageKey: 'flows'
                        }"
                        :prefix="'flows'"
                        :tableOptions="{
                            columns: {shown: true},
                            chart: {shown: false},
                            refresh: {shown: true, callback: refresh}
                        }"
                        @update-properties="updateDisplayColumns"
                        :defaultScope="!route.name?.toString().startsWith('namespaces/')"
                    />
                </template>

                <template #table>
                    <SelectTable
                        ref="selectTable"
                        :data="flowStore.flows"
                        :defaultSort="{prop: 'id', order: 'ascending'}"
                        tableLayout="auto"
                        fixed
                        @row-dblclick="onRowDoubleClick"
                        @sort-change="onSort"
                        :rowClassName="rowClasses"
                        @selection-change="handleSelectionChange"
                        :selectable="canCheck"
                        :no-data-text="t('no_results.flows')"
                        class="flows-table"
                        :rowKey="(row: any) => `${row.namespace}-${row.id}`"
                    >
                        <template #select-actions>
                            <BulkSelect
                                :selectAll="queryBulkAction"
                                :selections="selection"
                                :total="flowStore.total"
                                @update:select-all="toggleAllSelection"
                                @unselect="toggleAllUnselected"
                            >
                                <el-button v-if="canRead" :icon="Download" @click="exportFlows()">
                                    {{ t("export") }}
                                </el-button>
                                <el-button v-if="canDelete" @click="deleteFlows" :icon="TrashCan">
                                    {{ t("delete") }}
                                </el-button>
                                <el-button
                                    v-if="canUpdate && anyFlowDisabled()"
                                    @click="enableFlows"
                                    :icon="FileDocumentCheckOutline"
                                >
                                    {{ t("enable") }}
                                </el-button>
                                <el-button
                                    v-if="canUpdate && anyFlowEnabled()"
                                    @click="disableFlows"
                                    :icon="FileDocumentRemoveOutline"
                                >
                                    {{ t("disable") }}
                                </el-button>
                            </BulkSelect>
                        </template>
                        <template #default>
                            <el-table-column
                                prop="id"
                                sortable="custom"
                                :sortOrders="['ascending', 'descending']"
                                :label="t('id')"
                            >
                                <template #default="scope">
                                    <div class="flow-id">
                                        <router-link
                                            :to="{
                                                name: 'flows/update',
                                                params: {
                                                    namespace:
                                                        scope.row.namespace,
                                                    id: scope.row.id,
                                                },
                                            }"
                                            class="me-1"
                                        >
                                            {{
                                                FILTERS.invisibleSpace(
                                                    scope.row.id,
                                                )
                                            }}
                                        </router-link>
                                        <MarkdownTooltip
                                            :id="scope.row.namespace +
                                                '-' +
                                                scope.row.id
                                            "
                                            :description="scope.row.description"
                                            :title="scope.row.namespace +
                                                '.' +
                                                scope.row.id
                                            "
                                        />
                                    </div>
                                </template>
                            </el-table-column>

                            <template v-for="colProp in displayColumns" :key="colProp">
                                <el-table-column
                                    v-if="colProp === 'labels'"
                                    :label="t('labels')"
                                >
                                    <template #default="scope">
                                        <Labels :labels="scope.row.labels" />
                                    </template>
                                </el-table-column>

                                <el-table-column
                                    v-else-if="colProp === 'namespace'"
                                    prop="namespace"
                                    sortable="custom"
                                    :sortOrders="['ascending', 'descending']"
                                    :label="t('namespace')"
                                    :formatter="(_: any, __: any, cellValue: string) =>
                                        FILTERS.invisibleSpace(cellValue)
                                    "
                                />

                                <el-table-column
                                    v-else-if="colProp === 'state.startDate' && user.hasAny(permission.EXECUTION)"
                                    prop="state.startDate"
                                    :label="t('last execution date')"
                                >
                                    <template #default="scope">
                                        <router-link
                                            v-if="lastExecutionByFlowReady && getLastExecution(scope.row)"
                                            :to="{
                                                name: 'executions/update',
                                                params: {
                                                    namespace: scope.row.namespace,
                                                    flowId: scope.row.id,
                                                    id: getLastExecution(scope.row).id
                                                }
                                            }"
                                            class="table-link"
                                        >
                                            <DateAgo :date="getLastExecution(scope.row)?.startDate" inverted />
                                        </router-link>
                                    </template>
                                </el-table-column>

                                <el-table-column
                                    v-else-if="colProp === 'state.current' && user.hasAny(permission.EXECUTION)"
                                    prop="state.current"
                                    :label="t('last execution status')"
                                >
                                    <template #default="scope">
                                        <div
                                            v-if="lastExecutionByFlowReady && getLastExecution(scope.row)"
                                            class="d-flex justify-content-between align-items-center"
                                        >
                                            <router-link
                                                :to="{
                                                    name: 'executions/update',
                                                    params: {
                                                        namespace: scope.row.namespace,
                                                        flowId: scope.row.id,
                                                        id: getLastExecution(scope.row).id
                                                    }
                                                }"
                                                class="table-link"
                                            >
                                                <Status :status="getLastExecution(scope.row).status" size="small" />
                                            </router-link>
                                        </div>
                                    </template>
                                </el-table-column>

                                <el-table-column
                                    v-else-if="colProp === 'state' && user.hasAny(permission.EXECUTION)"
                                    prop="state"
                                    :label="t('execution statistics')"
                                    className="row-graph"
                                >
                                    <template #default="scope">
                                        <TimeSeries
                                            :chart="mappedChart(scope.row.id, scope.row.namespace)"
                                            :filters="chartFilters()"
                                            showDefault
                                            short
                                            :flow="scope.row.id"
                                            :namespace="scope.row.namespace"
                                        />
                                    </template>
                                </el-table-column>

                                <el-table-column
                                    v-else-if="colProp === 'triggers'"
                                    :label="t('triggers')"
                                    className="row-action"
                                >
                                    <template #default="scope">
                                        <TriggerAvatar :flow="scope.row" />
                                    </template>
                                </el-table-column>
                            </template>

                            <el-table-column columnKey="action" className="row-action" :label="t('actions')">
                                <template #default="scope">
                                    <router-link
                                        :to="{
                                            name: 'flows/update',
                                            params: {
                                                namespace: scope.row.namespace,
                                                id: scope.row.id,
                                            },
                                        }"
                                    >
                                        <Kicon :tooltip="t('details')" placement="left">
                                            <TextSearch />
                                        </Kicon>
                                    </router-link>
                                </template>
                            </el-table-column>
                        </template>
                    </SelectTable>
                </template>
            </DataTable>
        </div>
    </section>
</template>


<script setup lang="ts">
    import {ref, computed, useTemplateRef} from "vue";
    import {useRoute} from "vue-router";
    import {useI18n} from "vue-i18n";
    import _merge from "lodash/merge";
    import * as FILTERS from "../../utils/filters";
    import * as YAML_UTILS from "@kestra-io/ui-libs/flow-yaml-utils";
    import {useFlowFilter} from "../filter/configurations";

    import Plus from "vue-material-design-icons/Plus.vue";
    import Upload from "vue-material-design-icons/Upload.vue";
    import Download from "vue-material-design-icons/Download.vue";
    import TrashCan from "vue-material-design-icons/TrashCan.vue";
    import TextSearch from "vue-material-design-icons/TextSearch.vue";
    import TextBoxSearch from "vue-material-design-icons/TextBoxSearch.vue";
    import FileDocumentCheckOutline from "vue-material-design-icons/FileDocumentCheckOutline.vue";
    import FileDocumentRemoveOutline from "vue-material-design-icons/FileDocumentRemoveOutline.vue";

    import Kicon from "../Kicon.vue";
    import {Status} from "@kestra-io/ui-libs";
    import Labels from "../layout/Labels.vue";
    import DateAgo from "../layout/DateAgo.vue";
    import TriggerAvatar from "./TriggerAvatar.vue";
    import DataTable from "../layout/DataTable.vue";
    import BulkSelect from "../layout/BulkSelect.vue";
    //@ts-expect-error no declaration file
    import SelectTable from "../layout/SelectTable.vue";
    import KSFilter from "../filter/components/KSFilter.vue";
    import MarkdownTooltip from "../layout/MarkdownTooltip.vue";
    import TimeSeries from "../dashboard/sections/TimeSeries.vue";
    import TopNavBar from "../../components/layout/TopNavBar.vue";

    import action from "../../models/action";
    import permission from "../../models/permission";

    import {useToast} from "../../utils/toast";

    import {useFlowStore} from "../../stores/flow";
    import {useAuthStore} from "override/stores/auth";
    import {useMiscStore} from "override/stores/misc";
    import {useExecutionsStore} from "../../stores/executions";

    import {useTableColumns} from "../../composables/useTableColumns";
    import {DataTableRef, useDataTableActions} from "../../composables/useDataTableActions";
    import {useSelectTableActions} from "../../composables/useSelectTableActions";

    const props = withDefaults(defineProps<{
        topbar?: boolean;
        namespace?: string;
        id?: string | null;
    }>(), {
        topbar: true,
        namespace: undefined,
        id: undefined,
    });

    const flowStore = useFlowStore();
    const authStore = useAuthStore();
    const executionsStore = useExecutionsStore();
    const miscStore = useMiscStore();

    const route = useRoute();

    const {t} = useI18n();
    const toast = useToast()

    const flowFilter = useFlowFilter();

    const lastExecutionByFlowReady = ref(false);
    const latestExecutions = ref<any[]>([]);
    const file = ref<HTMLInputElement | null>(null);

    const optionalColumns = ref([
        {
            label: t("labels"),
            prop: "labels",
            default: true,
            description: t("filter.table_column.flows.labels")
        },
        {
            label: t("namespace"),
            prop: "namespace",
            default: true,
            description: t("filter.table_column.flows.namespace")
        },
        {
            label: t("last execution date"),
            prop: "state.startDate",
            default: true,
            description: t("filter.table_column.flows.last execution date")
        },
        {
            label: t("last execution status"),
            prop: "state.current",
            default: true,
            description: t("filter.table_column.flows.last execution status")
        },
        {
            label: t("execution statistics"),
            prop: "state",
            default: true,
            description: t("filter.table_column.flows.execution statistics")
        },
        {
            label: t("triggers"),
            prop: "triggers",
            default: true,
            description: t("filter.table_column.flows.triggers")
        },
    ]);

    const {
        visibleColumns: displayColumns,
        updateVisibleColumns
    } = useTableColumns({
        columns: optionalColumns.value,
        storageKey: "flows",
        initialVisibleColumns: []
    });

    const user = computed(() => authStore.user);
    const canCheck = computed(() => canRead.value || canDelete.value || canUpdate.value);
    const canCreate = computed(() => user.value?.hasAnyActionOnAnyNamespace(permission.FLOW, action.CREATE));
    const canRead = computed(() => user.value?.isAllowed(permission.FLOW, action.READ, route.query.namespace));
    const canDelete = computed(() => user.value?.isAllowed(permission.FLOW, action.DELETE, route.query.namespace));
    const canUpdate = computed(() => user.value?.isAllowed(permission.FLOW, action.UPDATE, route.query.namespace));

    const routeInfo = computed(() => ({title: t("flows")}));

    const dataTableRef = useTemplateRef<DataTableRef>("dataTable");
    const selectTableRef = useTemplateRef<typeof SelectTable>("selectTable");

    function loadData(callback?: () => void) {
        const q = route.query;
        flowStore
            .findFlows(
                loadQuery({
                    size: parseInt(q.size as string ?? "25"),
                    page: parseInt(q.page as string ?? "1"),
                    sort: (q.sort as string) ?? "id:asc",
                })
            )
            .then((data: any) => {
                if (user.value?.hasAnyActionOnAnyNamespace(permission.EXECUTION, action.READ)) {
                    executionsStore.loadLatestExecutions({
                        flowFilters: data.results.map((flow: any) => ({id: flow.id, namespace: flow.namespace})),
                    }).then((latestExecs: any) => {
                        latestExecutions.value = latestExecs;
                        lastExecutionByFlowReady.value = true;
                    });
                }
            })
            .finally(() => callback?.());
    }

    const {
        queryWithFilter,
        onPageChanged,
        onRowDoubleClick,
        onSort,
        ready
    } = useDataTableActions({
        dblClickRouteName: "flows/update",
        dataTableRef,
        loadData
    });

    function selectionMapper({id, namespace, disabled}: {id: string; namespace: string; disabled: boolean}) {
        return {
            id,
            namespace,
            enabled: !disabled,
        };
    }

    const {
        selection,
        queryBulkAction,
        handleSelectionChange,
        toggleAllUnselected,
        toggleAllSelection
    } = useSelectTableActions({
        dataTableRef: selectTableRef,
        selectionMapper
    });

    const selectionIds = computed(() => selection.value.map((flow) => ({id: flow.id, namespace: flow.namespace})));

    interface ChartDefinition {
        id: string;
        type: string;
        chartOptions: {
            displayName: string;
            description: string;
            legend: {enabled: boolean};
            column: string;
            colorByColumn: string;
            width: number;
        };
        data: {
            type: string;
            columns: {
                date: {field: string; displayName: string};
                state: {field: string};
                total: {displayName: string; agg: string};
                duration: {field: string; displayName: string; agg: string};
            };
            where: {field: string; type: string; value: string}[];
        };
        content?: string;
    }

    // Chart definition for mappedChart
    const CHART_DEFINITION: ChartDefinition = {
        id: "total_executions_timeseries",
        type: "io.kestra.plugin.core.dashboard.chart.TimeSeries",
        chartOptions: {
            displayName: "Total Executions",
            description: "Executions duration and count per date",
            legend: {enabled: false},
            column: "date",
            colorByColumn: "state",
            width: 12,
        },
        data: {
            type: "io.kestra.plugin.core.dashboard.data.Executions",
            columns: {
                date: {field: "START_DATE", displayName: "Date"},
                state: {field: "STATE"},
                total: {displayName: "Executions", agg: "COUNT"},
                duration: {field: "DURATION", displayName: "Duration", agg: "SUM"},
            },
            where: [
                {field: "NAMESPACE", type: "EQUAL_TO", value: "${namespace}"},
                {field: "FLOW_ID", type: "EQUAL_TO", value: "${flow_id}"},
            ],
        },
    };
    CHART_DEFINITION.content = YAML_UTILS.stringify(CHART_DEFINITION);



    function updateDisplayColumns(newColumns: string[]) {
        updateVisibleColumns(newColumns);
    }

    function exportFlows() {
        toast.confirm(
            t("flow export", {flowCount: queryBulkAction.value ? flowStore.total : selection.value.length}),
            () => {
                const flowCount = queryBulkAction.value ? flowStore.total : selection.value.length;
                if (queryBulkAction.value) {
                    return flowStore.exportFlowByQuery(loadQuery()).then(() => {
                        toast.success(t("flows exported", {count: flowCount}));
                        toggleAllUnselected();
                    });
                } else {
                    return flowStore.exportFlowByIds({ids: selection.value}).then(() => {
                        toast.success(t("flows exported", {count: flowCount}));
                        toggleAllUnselected();
                    });
                }
            }
        );
    }

    function disableFlows() {
        toast.confirm(
            t("flow disable", {flowCount: queryBulkAction.value ? flowStore.total : selection.value.length}),
            () => {
                if (queryBulkAction.value) {
                    return flowStore.disableFlowByQuery(loadQuery()).then((r: any) => {
                        toast.success(t("flows disabled", {count: r.data.count}));
                        toggleAllUnselected();
                        loadData(() => { });
                    });
                } else {
                    return flowStore.disableFlowByIds({ids: selectionIds.value}).then((r: any) => {
                        toast.success(t("flows disabled", {count: r.data.count}));
                        toggleAllUnselected();
                        loadData(() => { });
                    });
                }
            }
        );
    }

    function anyFlowDisabled() {
        return selection.value.some((flow: any) => !flow.enabled);
    }
    function anyFlowEnabled() {
        return selection.value.some((flow: any) => flow.enabled);
    }

    function enableFlows() {

        toast.confirm(
            t("flow enable", {flowCount: queryBulkAction.value ? flowStore.total : selection.value.length}),
            () => {
                if (queryBulkAction.value) {
                    return flowStore.enableFlowByQuery(loadQuery()).then((r: any) => {
                        toast.success(t("flows enabled", {count: r.data.count}));
                        toggleAllUnselected();
                        loadData(() => { });
                    });
                } else {
                    return flowStore.enableFlowByIds({ids: selectionIds.value}).then((r: any) => {
                        toast.success(t("flows enabled", {count: r.data.count}));
                        toggleAllUnselected();
                        loadData(() => { });
                    });
                }
            }
        );
    }

    function deleteFlows() {
        toast.confirm(
            t("flow delete", {flowCount: queryBulkAction.value ? flowStore.total : selection.value.length}),
            () => {
                if (queryBulkAction.value) {
                    return flowStore.deleteFlowByQuery(loadQuery()).then((r: any) => {
                        toast.success(t("flows deleted", {count: r.data.count}));
                        toggleAllUnselected();
                        loadData(() => { });
                    });
                } else {
                    return flowStore.deleteFlowByIds({ids: selectionIds.value}).then((r: any) => {
                        toast.success(t("flows deleted", {count: r.data.count}));
                        toggleAllUnselected();
                        loadData(() => { });
                    });
                }
            }
        );
    }

    function importFlows() {
        const formData = new FormData();
        if (file.value && file.value.files && file.value.files[0]) {
            formData.append("fileUpload", file.value.files[0]);
            flowStore.importFlows(formData as any).then((res: any) => {
                if (res.data.length > 0) {
                    toast.warning(t("flows not imported") + ": " + res.data.join(", "));
                } else {
                    toast.success(t("flows imported"));
                }
                if (file.value) file.value.value = "";
                loadData(() => { });
            });
        }
    }

    function getLastExecution(row: any) {
        if (!latestExecutions.value || !row) return null;
        return latestExecutions.value.find(
            (e: any) => e.flowId === row.id && e.namespace === row.namespace
        ) ?? null;
    }

    function loadQuery(base?: any) {
        let queryFilter = queryWithFilter(undefined, []);
        if (props.namespace) {
            queryFilter["filters[namespace][PREFIX]"] = route.params.id || props.namespace;
        }
        return _merge(base, queryFilter);
    }

    function refresh() {
        loadData(() => {});
    }

    function rowClasses(row: any) {
        return row && row.row && row.row.disabled ? "disabled" : "";
    }

    function mappedChart(id: string, namespace: string) {
        let MAPPED_CHARTS = JSON.parse(JSON.stringify(CHART_DEFINITION));
        MAPPED_CHARTS.content = MAPPED_CHARTS.content.replace("${namespace}", namespace).replace("${flow_id}", id);
        return MAPPED_CHARTS;
    }

    function chartFilters() {
        const DEFAULT_DURATION = miscStore.configs?.chartDefaultDuration ?? "P30D";
        return [{
            field: "timeRange",
            value: DEFAULT_DURATION,
            operation: "EQUALS"
        }];
    }

    async function exportFlowsAsStream() {
        await flowStore.exportFlowAsCSV(
            route.query
        )
    }
</script>

<style scoped lang="scss">
.shadow {
    box-shadow: 0px 2px 4px 0px var(--ks-card-shadow) !important;
}

:deep(nav .dropdown-menu) {
    display: flex;
    width: 20rem;
}

.flow-id {
    min-width: 200px;

    & a {
        color: var(--bs-code-color)
    }
}

.flows-table .el-table__cell {
    vertical-align: middle;
}

:deep(.flows-table) .el-scrollbar__thumb {
    background-color: var(--ks-border-active) !important;
}
.header-actions-list {
    display: flex;
    list-style: none;
    padding: 0;
    margin: 0;
    gap: 0.5rem;

    @media (max-width: 570px) {
        flex-direction: column;
        align-items: flex-end;
    }
}

.table-link {
    cursor: pointer;

    & :deep(button) {
        cursor: pointer !important;
    }

    &:hover {
        text-decoration: none;
    }
}
</style>