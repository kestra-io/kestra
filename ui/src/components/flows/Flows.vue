<template>
    <TopNavBar v-if="topbar" :title="routeInfo.title">
        <template #actions>
            <ul class="header-actions-list">
                <li>
                    <ks-button v-if="canRead" :icon="Download" @click="exportFlowsAsStream()">
                        {{ $t('export_csv') }}
                    </ks-button>
                </li>
                <li>
                    <ks-button :icon="Upload" @click="file?.click()">
                        {{ $t("import") }}
                    </ks-button>
                    <input ref="file" type="file" accept=".zip, .yml, .yaml" @change="importFlows()" class="d-none">
                </li>
                <li>
                    <router-link :to="{name: 'flows/search'}">
                        <ks-button :icon="TextBoxSearch">
                            {{ $t("source search") }}
                        </ks-button>
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
                        <ks-button :icon="Plus" type="primary">
                            {{ $t("create") }}
                        </ks-button>
                    </router-link>
                </li>
            </ul>
        </template>
    </TopNavBar>
    <section :class="{container: topbar}">
        <div>
            <ks-data-table
                ref="dataTable"
                :loadData="loadData"
                :data="flowStore.flows"
                :total="flowStore.total"
                :defaultSort="{prop: 'id', order: 'ascending'}"
                @page-changed="({page, size}) => router.push({query: {...route.query, page: String(page), size: String(size)}})"
                @ready="ready = true"
                @row-dblclick="onRowDoubleClick"
                @sort-change="({prop, order}) => router.push({query: {...route.query, sort: `${prop}:${order === 'descending' ? 'desc' : 'asc'}`}})"
                :rowClassName="rowClasses"
                :selectable="canCheck"
                :selectionMapper="selectionMapper"
                :no-data-text="$t('no_results.flows')"
                class="flows-table"
                :rowKey="(row: any) => `${row.namespace}-${row.id}`"
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
                        :defaultScope="defaultScopeFilter"
                    />
                </template>

                <template #bulk-actions>
                    <ks-button v-if="canRead" :icon="Download" @click="exportFlows()">
                        {{ $t("export") }}
                    </ks-button>
                    <ks-button v-if="canDelete" @click="deleteFlows" :icon="TrashCan">
                        {{ $t("delete") }}
                    </ks-button>
                    <ks-button
                        v-if="canUpdate && anyFlowDisabled()"
                        @click="enableFlows"
                        :icon="FileDocumentCheckOutline"
                    >
                        {{ $t("enable") }}
                    </ks-button>
                    <ks-button
                        v-if="canUpdate && anyFlowEnabled()"
                        @click="disableFlows"
                        :icon="FileDocumentRemoveOutline"
                    >
                        {{ $t("disable") }}
                    </ks-button>
                </template>

                <ks-table-column
                    prop="id"
                    sortable="custom"
                    :sortOrders="['ascending', 'descending']"
                    :label="$t('id')"
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
                </ks-table-column>

                <template v-for="colProp in displayColumns" :key="colProp">
                    <ks-table-column
                        v-if="colProp === 'labels'"
                        :label="$t('labels')"
                    >
                        <template #default="scope">
                            <Labels :labels="scope.row.labels" />
                        </template>
                    </ks-table-column>

                    <ks-table-column
                        v-else-if="colProp === 'namespace'"
                        prop="namespace"
                        sortable="custom"
                        :sortOrders="['ascending', 'descending']"
                        :label="$t('namespace')"
                        :formatter="(_: any, __: any, cellValue: string) =>
                            FILTERS.invisibleSpace(cellValue)
                        "
                    />

                    <ks-table-column
                        v-else-if="colProp === 'state.startDate' && user?.hasAny(permission.EXECUTION)"
                        prop="state.startDate"
                        :label="$t('last execution date')"
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
                                <ks-date-ago :date="getLastExecution(scope.row)?.startDate" inverted />
                            </router-link>
                        </template>
                    </ks-table-column>

                    <ks-table-column
                        v-else-if="colProp === 'state.current' && user?.hasAny(permission.EXECUTION)"
                        prop="state.current"
                        :label="$t('last execution status')"
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
                                    <KsExecutionStatus :status="getLastExecution(scope.row).status" size="small" />
                                </router-link>
                            </div>
                        </template>
                    </ks-table-column>

                    <ks-table-column
                        v-else-if="colProp === 'state' && user?.hasAny(permission.EXECUTION)"
                        prop="state"
                        :label="$t('execution statistics')"
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
                    </ks-table-column>

                    <ks-table-column
                        v-else-if="colProp === 'triggers'"
                        :label="$t('triggers')"
                        className="row-action"
                    >
                        <template #default="scope">
                            <TriggerAvatar :flow="scope.row" />
                        </template>
                    </ks-table-column>
                </template>

                <ks-table-column columnKey="action" className="row-action" :label="$t('actions')">
                    <template #default="scope">
                        <div class="flow-actions-cell">
                            <KsIconButton
                                v-if="canExecute(scope.row)"
                                :tooltip="t('execute')"
                                @click="openExecuteModal(scope.row)"
                            >
                                <Play />
                            </KsIconButton>
                            <KsIconButton
                                :tooltip="$t('details')"
                                :to="{
                                    name: 'flows/update',
                                    params: {
                                        namespace: scope.row.namespace,
                                        id: scope.row.id,
                                    },
                                }"
                            >
                                <TextSearch />
                            </KsIconButton>
                        </div>
                    </template>
                </ks-table-column>
            </ks-data-table>
        </div>

        <ks-dialog
            v-model="showRunModal"
            destroyOnClose
            appendToBody
            width="70%"
        >
            <template #header>
                <span v-if="selectedFlow.id" v-html="$t('execute the flow', {id: selectedFlow.id})" />
            </template>
            <FlowRun
                v-if="executionsStore.flow"
                :redirect="false"
                @execution-trigger="handleExecutionStart"
            />
        </ks-dialog>
    </section>
</template>


<script setup lang="ts">
    import {ref, computed, useTemplateRef, watch} from "vue";
    import {useRoute, useRouter} from "vue-router";
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
    import Play from "vue-material-design-icons/Play.vue";

    import {KsExecutionStatus, KsIconButton} from "@kestra-io/ui-design-system";
    import Labels from "../layout/Labels.vue";
    import TriggerAvatar from "./TriggerAvatar.vue";

    //@ts-expect-error no declaration file
    import FlowRun from "./FlowRun.vue";
    import KSFilter from "../filter/components/KSFilter.vue";
    import MarkdownTooltip from "../layout/MarkdownTooltip.vue";
    import TimeSeries from "../dashboard/sections/TimeSeries.vue";
    import TopNavBar from "../../components/layout/TopNavBar.vue";

    import action from "../../models/action";
    import permission from "../../models/permission";

    import {useToast} from "../../utils/toast";

    import {useFlowStore} from "../../stores/flow";
    import {useApiStore} from "../../stores/api";
    import {useAuthStore} from "override/stores/auth";
    import {useMiscStore} from "override/stores/misc";
    import {useExecutionsStore} from "../../stores/executions";

    import {useTableColumns} from "../../composables/useTableColumns";

    const props = withDefaults(defineProps<{
        topbar?: boolean;
        namespace?: string;
        id?: string | null;
        defaultScopeFilter?: boolean,
    }>(), {
        topbar: true,
        namespace: undefined,
        id: undefined,
        defaultScopeFilter: false,
    });

    const flowStore = useFlowStore();
    const apiStore = useApiStore();
    const authStore = useAuthStore();
    const executionsStore = useExecutionsStore();
    const miscStore = useMiscStore();

    const route = useRoute();
    const router = useRouter();

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
    const canCreate = computed(() => user?.value?.hasAnyActionOnAnyNamespace(permission.FLOW, action.CREATE));
    const routeNamespace = computed(() => route.query.namespace as string | undefined);
    const canRead = computed(() => user?.value?.isAllowed(permission.FLOW, action.READ, routeNamespace.value));
    const canDelete = computed(() => user?.value?.isAllowed(permission.FLOW, action.DELETE, routeNamespace.value));
    const canUpdate = computed(() => user?.value?.isAllowed(permission.FLOW, action.UPDATE, routeNamespace.value));
    const canExecute = (flow: Record<string, any>) => flow && !flow.deleted && user?.value?.isAllowed(permission.EXECUTION, action.CREATE, flow.namespace);

    const routeInfo = computed(() => ({title: t("flows")}));

    const dataTable = useTemplateRef("dataTable");

    const ready = ref(false);

    async function loadData({page, size, sort}: {page: number; size: number; sort?: string}) {
        await flowStore
            .findFlows(
                loadQuery({
                    size,
                    page,
                    sort: sort ?? String(route.query.sort ?? "id:asc"),
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
            });
    }

    const onRowDoubleClick = (item: any) => router.push({
        name: route.name?.toString().replace("/list", "/update"),
        params: {...item, tenant: route.params.tenant}
    });

    const filterQuery = computed(() => {
        const {page: _p, size: _s, sort: _so, ...filters} = route.query;
        return filters;
    });

    watch(filterQuery, () => {
        dataTable.value?.resetAndReload();
    }, {deep: true});

    function selectionMapper({id, namespace, disabled}: {id: string; namespace: string; disabled: boolean}) {
        return {
            id,
            namespace,
            enabled: !disabled,
        };
    }

    const selection = computed(() => dataTable.value?.selection ?? []);
    const queryBulkAction = computed(() => dataTable.value?.queryBulkAction ?? false);
    const toggleAllUnselected = () => dataTable.value?.toggleAllUnselected();

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

    const showRunModal = ref(false);
    const selectedFlow = ref<any | null>(null);

    async function openExecuteModal(flow: any) {
        apiStore.posthogEvents({
            type: "FLOW_EXECUTION",
            action: "open_modal",
        });
        selectedFlow.value = flow;

        await executionsStore.loadFlowForExecution({
            namespace: flow.namespace,
            flowId: flow.id,
            store: true
        });

        showRunModal.value = true;
    }

    function handleExecutionStart() {
        showRunModal.value = false;
        toast.success(t("execution_started"));
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
                        dataTable.value?.reload();
                    });
                } else {
                    return flowStore.disableFlowByIds({ids: selectionIds.value}).then((r: any) => {
                        toast.success(t("flows disabled", {count: r.data.count}));
                        toggleAllUnselected();
                        dataTable.value?.reload();
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
                        dataTable.value?.reload();
                    });
                } else {
                    return flowStore.enableFlowByIds({ids: selectionIds.value}).then((r: any) => {
                        toast.success(t("flows enabled", {count: r.data.count}));
                        toggleAllUnselected();
                        dataTable.value?.reload();
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
                        dataTable.value?.reload();
                    });
                } else {
                    return flowStore.deleteFlowByIds({ids: selectionIds.value}).then((r: any) => {
                        toast.success(t("flows deleted", {count: r.data.count}));
                        toggleAllUnselected();
                        dataTable.value?.reload();
                    });
                }
            }
        );
    }

    function importFlows() {
        const formData = new FormData();
        if (file.value && file.value.files && file.value.files[0]) {
            formData.append("fileUpload", file.value.files[0]);
            flowStore.importFlows({file: formData, failOnError: true}).then((res: any) => {
                if (res.data.length > 0) {
                    toast.warning(t("flows not imported") + ": " + res.data.join(", "));
                } else {
                    toast.success(t("flows imported"));
                }
                if (file.value) file.value.value = "";
                dataTable.value?.reload();
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
        const {page: _p, size: _s, sort: _so, ...queryFilter} = route.query as Record<string, any>;
        if (props.namespace) {
            queryFilter["filters[namespace][PREFIX]"] = route.params.id || props.namespace;
        }
        return _merge(base, queryFilter);
    }

    function refresh() {
        dataTable.value?.reload();
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
        const DEFAULT_DURATION = miscStore.configs?.chartDefaultDuration ?? "PT24H";
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
}

.flows-table .kel-table__cell {
    vertical-align: middle;
}

:deep(.flows-table) .kel-scrollbar__thumb {
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

.flow-actions-cell {
    display: flex;
    align-items: center;
    gap: 0.25rem;
}
</style>
