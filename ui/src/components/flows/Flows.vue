<template>
    <TopNavBar v-if="topbar" :title="routeInfo.title">
        <template #actions>
            <NavBarActions>
                <NavBarAction v-if="canRead" :icon="Download" :label="$t('export_csv')" @click="exportFlowsAsStream(route.query)" />
                <NavBarAction :icon="Upload" :label="$t('import')" @click="file?.click()" />
                <NavBarAction :icon="TextBoxSearch" :to="{name: 'flows/search'}" :label="$t('source search')" />

                <template #primary>
                    <input ref="file" type="file" accept=".zip, .yml, .yaml" @change="importFlows()" class="d-none">
                    <NavBarAction
                        v-if="canCreate"
                        type="primary"
                        :icon="Plus"
                        :to="{name: 'flows/create', query: {namespace: $route.query.namespace}}"
                        :label="$t('create')"
                        data-test="flows-create"
                    />
                </template>
            </NavBarActions>
        </template>
    </TopNavBar>
    <section :class="{'full-container': fitHeightResolved}">
        <KsDataTable
            ref="dataTable"
            :loadData="loadData"
            :data="flowStore.flows"
            :total="flowStore.total"
            :currentPage="urlPage"
            :pageSize="urlSize"
            :defaultSort="{prop: 'id', order: 'ascending'}"
            @page-changed="({page, size}: {page: number; size: number}) => router.push({query: {...route.query, page: String(page), size: String(size)}})"
            @ready="ready = true"
            @row-click="onRowClick"
            @sort-change="({prop, order}: {prop: string | null; order: string | null}) => router.push({query: {...route.query, sort: `${prop}:${order === 'descending' ? 'desc' : 'asc'}`}})"
            :rowClassName="rowClasses"
            :selectable="canCheck"
            :selectionMapper="selectionMapper"
            :no-data-text="$t('no_results.flows')"
            class="flows-table"
            :rowKey="(row: any) => `${row.namespace}-${row.id}`"
            :fitHeight="fitHeightResolved"
        >
            <template #top>
                <KSFilter
                    :configuration="filterConfiguration ?? flowFilter"
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
                <KsButton v-if="canRead" :icon="Download" @click="exportFlows()">
                    {{ $t("export") }}
                </KsButton>
                <KsButton v-if="canDelete" @click="deleteFlows" :icon="TrashCan">
                    {{ $t("delete") }}
                </KsButton>
                <KsButton
                    v-if="canUpdate && anyFlowDisabled"
                    @click="enableFlows"
                    :icon="FileDocumentCheckOutline"
                >
                    {{ $t("enable") }}
                </KsButton>
                <KsButton
                    v-if="canUpdate && anyFlowEnabled"
                    @click="disableFlows"
                    :icon="FileDocumentRemoveOutline"
                >
                    {{ $t("disable") }}
                </KsButton>
                <component
                    :is="flowsExtension.bulkAction"
                    v-if="flowsExtension.bulkAction && !dataTable?.queryBulkAction"
                    :flows="dataTable?.selection ?? []"
                />
            </template>

            <KsTableColumn
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
                                    namespace: scope.row.namespace,
                                    id: scope.row.id,
                                },
                            }"
                            class="me-1"
                        >
                            <BreakableText :value="scope.row.id" />
                        </router-link>
                        <KsTag size="small" v-if="scope.row.draft" class="me-1" plain>
                            <CircleOpacity />
                            {{ $t('draft') }}
                        </KsTag>
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
            </KsTableColumn>

            <template v-for="colProp in displayColumns" :key="colProp">
                <KsTableColumn
                    v-if="colProp === 'labels'"
                    :label="$t('labels')"
                >
                    <template #default="scope">
                        <Labels :labels="scope.row.labels" :max="3" @click.prevent.stop />
                    </template>
                </KsTableColumn>

                <KsTableColumn
                    v-else-if="colProp === 'namespace'"
                    prop="namespace"
                    sortable="custom"
                    :sortOrders="['ascending', 'descending']"
                    :label="$t('namespace')"
                >
                    <template #default="scope">
                        <KsEntityLink
                            v-if="scope.row?.namespace"
                            entity="namespace"
                            :value="scope.row.namespace"
                            :to="{name: 'namespaces/update', params: {id: scope.row.namespace}}"
                        />
                    </template>
                </KsTableColumn>

                <KsTableColumn
                    v-else-if="colProp === 'state.startDate' && user?.hasAny(resource.EXECUTION)"
                    prop="state.startDate"
                    :label="$t('last execution date')"
                >
                    <template #default="scope">
                        <div @click.prevent.stop>
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
                            >
                                <KsDateAgo :date="getLastExecution(scope.row)?.startDate" inverted />
                            </router-link>
                        </div>
                    </template>
                </KsTableColumn>

                <KsTableColumn
                    v-else-if="colProp === 'state.current' && user?.hasAny(resource.EXECUTION)"
                    prop="state.current"
                    :label="$t('last execution status')"
                >
                    <template #default="scope">
                        <div
                            @click.prevent.stop
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
                            >
                                <KsExecutionStatus :status="getLastExecution(scope.row).status" size="small" />
                            </router-link>
                        </div>
                    </template>
                </KsTableColumn>

                <KsTableColumn
                    v-else-if="colProp === 'state' && user?.hasAny(resource.EXECUTION)"
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
                </KsTableColumn>

                <KsTableColumn
                    v-else-if="colProp === 'triggers'"
                    :label="$t('triggers')"
                    className="row-action"
                >
                    <template #default="scope">
                        <TriggerAvatar :flow="scope.row" />
                    </template>
                </KsTableColumn>

                <KsTableColumn
                    v-else-if="colProp === 'updated'"
                    prop="updated"
                    sortable="custom"
                    :sortOrders="['ascending', 'descending']"
                    :label="$t('last modified')"
                >
                    <template #default="scope">
                        <KsDateAgo v-if="scope.row.updated" :date="scope.row.updated" inverted />
                    </template>
                </KsTableColumn>

                <KsTableColumn
                    v-else-if="extensionColumn(colProp)"
                    :label="extensionColumn(colProp)!.label"
                >
                    <template #header>
                        <component :is="extensionColumn(colProp)!.header" v-if="extensionColumn(colProp)!.header" />
                        <template v-else>{{ extensionColumn(colProp)!.label }}</template>
                    </template>
                    <template #default="scope">
                        <component :is="extensionColumn(colProp)!.cell" :row="scope.row" />
                    </template>
                </KsTableColumn>

            </template>

            <KsTableColumn columnKey="action" className="row-action" :label="$t('actions')">
                <template #default="scope">
                    <div class="flow-actions-cell">
                        <KsIconButton
                            v-if="canExecute(scope.row)"
                            :tooltip="$t('execute')"
                            @click="openExecuteModal(scope.row)"
                        >
                            <Play />
                        </KsIconButton>
                    </div>
                </template>
            </KsTableColumn>
        </KsDataTable>

        <KsDialog
            v-model="showRunModal"
            destroyOnClose
            appendToBody
            scrollable
            large
        >
            <template #header>
                <span v-if="selectedFlow.id" v-html="$t('execute the flow', {id: selectedFlow.id})" />
            </template>
            <FlowRun
                v-if="executionsStore.flow"
                ref="flowRunRef"
                :embed="true"
                :redirect="false"
                @execution-trigger="handleExecutionStart"
            />
            <template #footer>
                <FlowRunActions :flowRun="flowRunRef" />
            </template>
        </KsDialog>
    </section>
</template>

<script setup lang="ts">
    import {ref, computed, useTemplateRef, watch} from "vue"
    import {useRoute, useRouter} from "vue-router"
    import {useI18n} from "vue-i18n"
    import _merge from "lodash/merge"
    import BreakableText from "../BreakableText"
    import * as YAML_UTILS from "@kestra-io/topology/flow-yaml-utils"
    import {useFlowFilter} from "../filter/configurations"
    import useRestoreUrl from "../../composables/useRestoreUrl"

    const {loadInit} = useRestoreUrl()

    import Plus from "vue-material-design-icons/Plus.vue"
    import Upload from "vue-material-design-icons/Upload.vue"
    import Download from "vue-material-design-icons/Download.vue"
    import TrashCan from "vue-material-design-icons/TrashCan.vue"
    import TextBoxSearch from "vue-material-design-icons/TextBoxSearch.vue"
    import CircleOpacity from "vue-material-design-icons/CircleOpacity.vue"

    import NavBarActions from "../layout/NavBarActions.vue"
    import NavBarAction from "../layout/NavBarAction.vue"
    import FileDocumentCheckOutline from "vue-material-design-icons/FileDocumentCheckOutline.vue"
    import FileDocumentRemoveOutline from "vue-material-design-icons/FileDocumentRemoveOutline.vue"
    import Play from "vue-material-design-icons/Play.vue"

    import {KsExecutionStatus, KsIconButton} from "@kestra-io/design-system"
    import Labels from "../layout/Labels.vue"
    import TriggerAvatar from "./TriggerAvatar.vue"

    import FlowRun from "./FlowRun.vue"
    import FlowRunActions from "./FlowRunActions.vue"
    import {KsFilter as KSFilter, type FilterConfiguration} from "@kestra-io/design-system"
    import MarkdownTooltip from "../layout/MarkdownTooltip.vue"
    import TimeSeries from "../dashboard/sections/TimeSeries.vue"
    import type {Chart} from "../dashboard/types"
    import TopNavBar from "../../components/layout/TopNavBar.vue"

    import action from "../../models/action"
    import resource from "../../models/resource"

    import {useToast} from "../../utils/toast"

    import {useFlowStore} from "../../stores/flow"
    import {useApiStore} from "../../stores/api"
    import {useAuthStore} from "override/stores/auth"
    import {useMiscStore} from "override/stores/misc"
    import {useExecutionsStore} from "../../stores/executions"

    import {useTableColumns, type ColumnConfig} from "../../composables/useTableColumns"
    import useRouteContext from "../../composables/useRouteContext"
    import {useFlowsTableExtension} from "override/components/flows/flowsTableExtension"
    import {QueryFilter} from "@kestra-io/kestra-sdk"
    import useFlowsBulkActions from "./useFlowsBulkActions"

    const NON_NAVIGATING_TARGETS = "a, button, input, canvas, [role='button']"

    const props = withDefaults(defineProps<{
        topbar?: boolean;
        fitHeight?: boolean;
        namespace?: string;
        id?: string | null;
        defaultScopeFilter?: boolean,
        embed?: boolean;
        filterConfiguration?: FilterConfiguration;
    }>(), {
        topbar: true,
        fitHeight: undefined,
        namespace: undefined,
        id: undefined,
        defaultScopeFilter: false,
        embed: false,
        filterConfiguration: undefined,
    })

    const fitHeightResolved = computed(() => props.fitHeight ?? props.topbar)

    const flowStore = useFlowStore()
    const apiStore = useApiStore()
    const authStore = useAuthStore()
    const executionsStore = useExecutionsStore()
    const miscStore = useMiscStore()

    const route = useRoute()
    const router = useRouter()

    const {t} = useI18n()
    const toast = useToast()

    const flowFilter = useFlowFilter()

    const lastExecutionByFlowReady = ref(false)
    const latestExecutions = ref<any[]>([])
    const file = ref<HTMLInputElement | null>(null)

    const optionalColumns = ref<ColumnConfig[]>([
        {
            label: t("labels"),
            prop: "labels",
            default: true,
            description: t("filter.table_column.flows.labels"),
        },
        {
            label: t("namespace"),
            prop: "namespace",
            default: true,
            description: t("filter.table_column.flows.namespace"),
        },
        {
            label: t("last execution date"),
            prop: "state.startDate",
            default: true,
            description: t("filter.table_column.flows.last execution date"),
        },
        {
            label: t("last execution status"),
            prop: "state.current",
            default: true,
            description: t("filter.table_column.flows.last execution status"),
        },
        {
            label: t("execution statistics"),
            prop: "state",
            default: true,
            description: t("filter.table_column.flows.execution statistics"),
        },
        {
            label: t("triggers"),
            prop: "triggers",
            default: true,
            description: t("filter.table_column.flows.triggers"),
        },
        {
            label: t("last modified"),
            prop: "updated",
            default: false,
            description: t("filter.table_column.flows.last modified"),
        },
    ])

    const flowsExtension = useFlowsTableExtension()
    optionalColumns.value.push(...flowsExtension.columns)

    const extensionColumn = (prop: string) => flowsExtension.columns.find(column => column.prop === prop)
    const extensionColumnsVisible = computed(() =>
        flowsExtension.columns.some(column => displayColumns.value.includes(column.prop)),
    )
    const loadExtensionData = (rows: {id: string; namespace: string}[]) => {
        if (extensionColumnsVisible.value) {
            flowsExtension.load?.(rows.map(row => ({id: row.id, namespace: row.namespace})))
        }
    }

    const {
        visibleColumns: displayColumns,
        updateVisibleColumns,
    } = useTableColumns({
        columns: optionalColumns.value,
        storageKey: "flows",
        initialVisibleColumns: [],
    })

    watch(extensionColumnsVisible, visible => {
        if (visible && flowStore.flows?.length) {
            loadExtensionData(flowStore.flows)
        }
    })

    const user = computed(() => authStore.user)
    const canCheck = computed(() => canRead.value || canDelete.value || canUpdate.value)
    const canCreate = computed(() => user?.value?.hasAnyActionOnAnyNamespace(resource.FLOW, action.CREATE))
    const routeNamespace = computed(() => route.query.namespace as string | undefined)
    const canRead = computed(() => user?.value?.isAllowed(resource.FLOW, action.VIEW, routeNamespace.value))
    const canDelete = computed(() => user?.value?.isAllowed(resource.FLOW, action.DELETE, routeNamespace.value))
    const canUpdate = computed(() => user?.value?.isAllowed(resource.FLOW, action.UPDATE, routeNamespace.value))
    const canExecute = (flow: Record<string, any>) => flow && !flow.deleted && user?.value?.isAllowed(resource.FLOW, action.EXECUTE, flow.namespace)

    const routeInfo = computed(() => ({title: t("flows")}))

    useRouteContext(routeInfo, props.embed)

    const dataTable = useTemplateRef("dataTable")

    const ready = ref(false)

    async function loadData({page, size, sort}: {page: number; size: number; sort?: string}) {
        if (!loadInit.value) return
        await flowStore
            .findFlows(
                loadQuery({
                    size,
                    page,
                    sort: sort ?? String(route.query.sort ?? "id:asc"),
                }),
            )
            .then((data: any) => {
                if (user.value?.hasAnyActionOnAnyNamespace(resource.EXECUTION, action.LIST)) {
                    executionsStore.loadLatestExecutions({
                        flowFilters: data.results.map((flow: any) => ({id: flow.id, namespace: flow.namespace})),
                    }).then((latestExecs: any) => {
                        latestExecutions.value = latestExecs
                        lastExecutionByFlowReady.value = true
                    })
                }
                loadExtensionData(data.results)
            })
    }

    const onRowClick = (item: any, column: any, event: Event) => {
        if (column?.type === "selection") return

        const click = event as MouseEvent
        // a modifier-click is the browser's own open-in-a-new-tab gesture, which RouterLink deliberately
        // lets through without preventDefault, so the current tab must stay where it is
        if (click.metaKey || click.ctrlKey || click.shiftKey || click.altKey || click.button !== 0) return
        if ((event.target as HTMLElement)?.closest(NON_NAVIGATING_TARGETS)) return

        router.push({
            name: route.name?.toString().replace("/list", "/update"),
            params: {...item, tenant: route.params.tenant},
        })
    }

    const filterQueryKey = computed(() => {
        const {page: _p, size: _s, sort: _so, ...filters} = route.query
        return JSON.stringify(filters)
    })

    const urlPage = computed(() => Number(route.query.page) || 1)
    const urlSize = computed(() => Number(route.query.size) || 25)

    watch(filterQueryKey, () => {
        dataTable.value?.resetAndReload()
    })

    function selectionMapper({id, namespace, disabled}: {id: string; namespace: string; disabled: boolean}) {
        return {
            id,
            namespace,
            enabled: !disabled,
        }
    }


    // Chart definition for mappedChart
    const CHART_DEFINITION: Chart = {
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
                date: {
                    field: "START_DATE",
                    displayName: "Date",
                },
                state: {
                    field: "STATE",
                },
                total: {
                    displayName: "Executions",
                    agg: "COUNT",
                    graphStyle: "BARS",
                },
                duration: {
                    field: "DURATION",
                    displayName: "Duration",
                    agg: "SUM",
                    graphStyle: "LINES",
                },
            },
            where: [
                {
                    field: "NAMESPACE",
                    type: "EQUAL_TO",
                    value: "${namespace}",
                },
                {
                    field: "FLOW_ID",
                    type: "EQUAL_TO",
                    value: "${flow_id}",
                },
            ],
        },
    }
    CHART_DEFINITION.content = YAML_UTILS.stringify(CHART_DEFINITION)

    function updateDisplayColumns(newColumns: string[]) {
        updateVisibleColumns(newColumns)
    }

    const showRunModal = ref(false)
    const flowRunRef = ref<InstanceType<typeof FlowRun> | null>(null)
    const selectedFlow = ref<any | null>(null)

    async function openExecuteModal(flow: any) {
        apiStore.posthogEvents({
            type: "FLOW_EXECUTION",
            action: "open_modal",
        })
        selectedFlow.value = flow

        await executionsStore.loadFlowForExecution({
            namespace: flow.namespace,
            flowId: flow.id,
            store: true,
        })

        showRunModal.value = true
    }

    function handleExecutionStart() {
        showRunModal.value = false
        toast.success(t("execution_started"))
    }

    function getLastExecution(row: any) {
        if (!latestExecutions.value || !row) return null
        return latestExecutions.value.find(
            (e: any) => e.flowId === row.id && e.namespace === row.namespace,
        ) ?? null
    }

    function loadQuery(base?: any) {
        const {page: _p, size: _s, sort: _so, ...queryFilter} = route.query as Record<string, any>
        if (props.namespace) {
            queryFilter["filters[namespace][PREFIX]"] = route.params.id || props.namespace
        }
        return _merge(base, queryFilter)
    }

    function refresh() {
        dataTable.value?.reload()
    }

    function rowClasses(row: any) {
        if (!row || !row.row) return ""
        const classes = []
        if (row.row.disabled) classes.push("disabled")
        if (row.row.draft) classes.push("draft")
        return classes.join(" ")
    }

    function mappedChart(id: string, namespace: string) {
        let MAPPED_CHARTS = JSON.parse(JSON.stringify(CHART_DEFINITION))
        MAPPED_CHARTS.content = MAPPED_CHARTS.content.replace("${namespace}", namespace).replace("${flow_id}", id)
        MAPPED_CHARTS.data.where = MAPPED_CHARTS.data.where.map((condition: any) => ({
            ...condition,
            value: condition.value.replace("${namespace}", namespace).replace("${flow_id}", id),
        }))
        return MAPPED_CHARTS
    }

    function chartFilters(): QueryFilter[] {
        const DEFAULT_DURATION = miscStore.configs?.chartDefaultDuration ?? "PT24H"
        return [{
            field: "timeRange",
            value: DEFAULT_DURATION,
            operation: "EQUALS",
        }]
    }

    const {
        anyFlowDisabled,
        anyFlowEnabled,
        deleteFlows,
        enableFlows,
        disableFlows,
        exportFlows,
        exportFlowsAsStream,
        importFlows,
    } = useFlowsBulkActions({
        loadQuery,
        dataTable,
        file,
    })
</script>

<style scoped lang="scss">
.full-container {
    min-height: 0;
    display: flex;
    flex-direction: column;

    > * {
        flex: 1;
    }
}

.shadow {
    box-shadow: 0px 2px 4px 0px var(--ks-shadow-element) !important;
}

:deep(nav .dropdown-menu) {
    display: flex;
    width: 20rem;
}

.flow-id {
    min-width: 200px;
    display: flex;
    align-items: center;
    gap: 0.25rem;
}

.flows-table .kel-table__cell {
    vertical-align: middle;
}

:deep(.flows-table) .kel-table__row {
    cursor: pointer;
}

:deep(.flows-table) th.row-action .cell {
    padding-right: var(--ks-spacing-4);
}

.header-actions-list {
    display: flex;
    list-style: none;
    padding: 0;
    margin: 0;
    gap: 0.5rem;
}

.flow-actions-cell {
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 0.25rem;
    padding-right: var(--ks-spacing-4);
}
</style>
