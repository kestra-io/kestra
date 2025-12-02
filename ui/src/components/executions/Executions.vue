<template>
    <TopNavBar v-if="topbar" :title="routeInfo.title">
        <template #additional-right v-if="displayButtons">
            <ul>
                <template v-if="$route.name === 'executions/list'">
                    <li>
                        <el-button :icon="Download" @click="exportExecutionsAsStream()">
                            {{ t('export_csv') }}
                        </el-button>
                    </li>
                    <li>
                        <template v-if="hasAnyExecute">
                            <TriggerFlow />
                        </template>
                    </li>
                </template>
                <template v-if="$route.name === 'flows/update'">
                    <li>
                        <template v-if="isAllowedEdit">
                            <el-button :icon="Pencil" size="large" @click="editFlow" :disabled="isReadOnly">
                                {{ $t("edit flow") }}
                            </el-button>
                        </template>
                    </li>
                    <li>
                        <TriggerFlow
                            v-if="flowStore.flow"
                            :disabled="flowStore.flow?.disabled || isReadOnly"
                            :flowId="flowStore.flow?.id"
                            :namespace="flowStore.flow?.namespace"
                        />
                    </li>
                </template>
            </ul>
        </template>
    </TopNavBar>
    <section :class="{'container padding-bottom': topbar}" v-if="ready">
        <DataTable
            @page-changed="onPageChanged"
            ref="dataTable"
            :total="executionsStore.total"
            :embed="embed"
        >
            <template #navbar v-if="isDisplayedTop">
                <KSFilter
                    :configuration="namespace === undefined || flowId === undefined ? executionFilter : flowExecutionFilter"
                    :properties="{
                        shown: true,
                        columns: optionalColumns,
                        displayColumns,
                        storageKey: storageKey
                    }"
                    :prefix="'executions'"
                    :tableOptions="{
                        chart: {shown: true, value: showChart, callback: onShowChartChange},
                        refresh: {shown: true, callback: refresh}
                    }"
                    @update-properties="updateDisplayColumns"
                    :defaultScope="defaultScopeFilter"
                />
            </template>

            <template v-if="showStatChart()" #top>
                <Sections ref="dashboardComponent" :dashboard="{id: 'default', charts: []}" :charts showDefault class="mb-4" />
            </template>

            <template #table>
                <SelectTable
                    ref="selectTable"
                    :data="executionsStore.executions"
                    :defaultSort="{prop: 'state.startDate', order: 'descending'}"
                    tableLayout="auto"
                    fixed
                    @row-dblclick="(row: any) => onRowDoubleClick(executionParams(row))"
                    @sort-change="onSort"
                    @selection-change="handleSelectionChange"
                    :selectable="!hidden?.includes('selection') && canCheck"
                    :no-data-text="$t('no_results.executions')"
                    :rowKey="(row: any) => row.id"
                >
                    <template #select-actions>
                        <BulkSelect
                            :selectAll="queryBulkAction"
                            :selections="selection"
                            :total="executionsStore.total"
                            @update:select-all="toggleAllSelection"
                            @unselect="toggleAllUnselected"
                        >
                            <!-- Always visible buttons -->
                            <el-button v-if="canUpdate" :icon="StateMachine" @click="changeStatusDialogVisible = !changeStatusDialogVisible">
                                {{ $t("change state") }}
                            </el-button>
                            <el-button v-if="canUpdate" :icon="Restart" @click="restartExecutions()">
                                {{ $t("restart") }}
                            </el-button>
                            <el-button v-if="canCreate" :icon="PlayBoxMultiple" @click="isOpenReplayModal = !isOpenReplayModal">
                                {{ $t("replay") }}
                            </el-button>
                            <el-button v-if="canUpdate" :icon="StopCircleOutline" @click="killExecutions()">
                                {{ $t("kill") }}
                            </el-button>
                            <el-button v-if="canDelete" :icon="Delete" @click="deleteExecutions()">
                                {{ $t("delete") }}
                            </el-button>

                            <el-dropdown>
                                <el-button>
                                    <DotsVertical />
                                </el-button>
                                <template #dropdown>
                                    <el-dropdown-menu>
                                        <el-dropdown-item v-if="canUpdate" :icon="LabelMultiple" @click=" isOpenLabelsModal = !isOpenLabelsModal">
                                            {{ $t("Set labels") }}
                                        </el-dropdown-item>
                                        <el-dropdown-item v-if="canUpdate" :icon="PlayBox" @click="resumeExecutions()">
                                            {{ $t("resume") }}
                                        </el-dropdown-item>
                                        <el-dropdown-item v-if="canUpdate" :icon="PauseBox" @click="pauseExecutions()">
                                            {{ $t("pause") }}
                                        </el-dropdown-item>
                                        <el-dropdown-item v-if="canUpdate" :icon="QueueFirstInLastOut" @click="unqueueDialogVisible = true">
                                            {{ $t("unqueue") }}
                                        </el-dropdown-item>
                                        <el-dropdown-item v-if="canUpdate" :icon="RunFast" @click="forceRunExecutions()">
                                            {{ $t("force run") }}
                                        </el-dropdown-item>
                                    </el-dropdown-menu>
                                </template>
                            </el-dropdown>
                        </BulkSelect>
                        <el-dialog
                            v-if="isOpenLabelsModal"
                            v-model="isOpenLabelsModal"
                            destroyOnClose
                            :appendToBody="true"
                            alignCenter
                        >
                            <template #header>
                                <h5>{{ $t("Set labels") }}</h5>
                            </template>

                            <template #footer>
                                <el-button @click="isOpenLabelsModal = false">
                                    {{ $t("cancel") }}
                                </el-button>
                                <el-button type="primary" @click="setLabels()">
                                    {{ $t("ok") }}
                                </el-button>
                            </template>

                            <el-form>
                                <ElFormItem :label="$t('execution labels')">
                                    <LabelInput v-model:labels="executionLabels" />
                                </ElFormItem>
                            </el-form>
                        </el-dialog>
                    </template>
                    <template #default>
                        <el-table-column
                            prop="id"
                            sortable="custom"
                            :sortOrders="['ascending', 'descending']"
                            :label="$t('id')"
                        >
                            <template #default="scope">
                                <RouterLink
                                    :to="{
                                        name: 'executions/update',
                                        params: {
                                            namespace: scope.row?.namespace,
                                            flowId: scope.row?.flowId,
                                            id: scope.row?.id
                                        }
                                    }"
                                    class="execution-id"
                                >
                                    <Id :value="scope.row?.id" :shrink="true" />
                                </RouterLink>
                            </template>
                        </el-table-column>

                        <el-table-column
                            v-for="col in visibleColumns"
                            :key="col.prop"
                            :prop="col.prop"
                            :label="col.label"
                            :class="col.prop === 'flowRevision' ? 'shrink' : ''"
                            :align="col.prop === 'inputs' || col.prop === 'outputs' ? 'center' : undefined"
                            :formatter="col.prop === 'namespace' ? ((_ : any, __: any, cellValue: string) => invisibleSpace(cellValue)) : undefined"
                            :sortable="isColumnSortable(col.prop) ? 'custom' : false"
                            :sortOrders="isColumnSortable(col.prop) ? ['ascending', 'descending'] : []"
                        >
                            <template #default="scope">
                                <template v-if="col.prop === 'state.startDate'">
                                    <DateAgo :inverted="true" :date="scope.row?.state?.startDate" />
                                </template>
                                <template v-else-if="col.prop === 'state.endDate'">
                                    <DateAgo :inverted="true" :date="scope.row?.state?.endDate" />
                                </template>
                                <template v-else-if="col.prop === 'state.duration'">
                                    <Duration :field="scope.row?.state?.duration" :startDate="scope.row?.state?.startDate" />
                                </template>
                                <template v-else-if="col.prop === 'namespace' && $route.name !== 'flows/update'">
                                    <span :title="invisibleSpace(scope.row?.namespace)">{{ invisibleSpace(scope.row?.namespace) }}</span>
                                </template>
                                <template v-else-if="col.prop === 'flowId' && $route.name !== 'flows/update'">
                                    <router-link
                                        :to="{name: 'flows/update', params: {namespace: scope.row?.namespace, id: scope.row?.flowId}}"
                                    >
                                        {{ invisibleSpace(scope.row?.flowId) }}
                                    </router-link>
                                </template>
                                <template v-else-if="col.prop === 'labels'">
                                    <Labels :labels="filteredLabels(scope.row?.labels)" />
                                </template>
                                <template v-else-if="col.prop === 'state.current'">
                                    <Status :status="scope.row?.state?.current" size="small" />
                                </template>
                                <template v-else-if="col.prop === 'flowRevision'">
                                    <code class="code-text">{{ scope.row?.flowRevision }}</code>
                                </template>
                                <template v-else-if="col.prop === 'inputs'">
                                    <el-tooltip effect="light">
                                        <template #content>
                                            <pre class="mb-0">{{ JSON.stringify(scope.row?.inputs, null, "\t") }}</pre>
                                        </template>
                                        <div>
                                            <Import v-if="scope.row?.inputs" class="fs-5" />
                                        </div>
                                    </el-tooltip>
                                </template>
                                <template v-else-if="col.prop === 'outputs'">
                                    <el-tooltip effect="light">
                                        <template #content>
                                            <pre class="mb-0">{{ JSON.stringify(scope.row?.outputs, null, "\t") }}</pre>
                                        </template>
                                        <div>
                                            <Export v-if="scope.row?.outputs" class="fs-5" />
                                        </div>
                                    </el-tooltip>
                                </template>
                                <template v-else-if="col.prop === 'taskRunList.taskId'">
                                    <code class="code-text">
                                        {{ scope.row?.taskRunList?.slice(-1)[0]?.taskId }}
                                        {{
                                            scope.row?.taskRunList?.slice(-1)[0]?.attempts?.length > 1 ? `(${scope.row?.taskRunList?.slice(-1)[0]?.attempts?.length})` : ""
                                        }}
                                    </code>
                                </template>
                                <template v-else-if="col.prop === 'trigger'">
                                    <TriggerAvatar :execution="scope.row" />
                                </template>
                            </template>
                            <template v-if="col.prop === 'taskRunList.taskId'" #header="scope">
                                <el-tooltip :content="$t('taskid column details')" effect="light">
                                    {{ scope.column.label }}
                                </el-tooltip>
                            </template>
                        </el-table-column>

                        <el-table-column
                            columnKey="action"
                            className="row-action"
                            :label="$t('actions')"
                        >
                            <template #default="scope">
                                <router-link
                                    :to="{name: 'executions/update', params: {namespace: scope.row?.namespace, flowId: scope.row?.flowId, id: scope.row?.id}, query: {revision: scope.row?.flowRevision}}"
                                >
                                    <Kicon :tooltip="$t('details')" placement="left">
                                        <TextSearch />
                                    </Kicon>
                                </router-link>
                            </template>
                        </el-table-column>
                    </template>
                </SelectTable>
            </template>
        </DataTable>
    </section>

    <el-dialog v-if="changeStatusDialogVisible" v-model="changeStatusDialogVisible" :id="Utils.uid()" destroyOnClose :appendToBody="true" alignCenter>
        <template #header>
            <h5>{{ $t("confirmation") }}</h5>
        </template>

        <template #default>
            <p v-html="changeStatusToast()" />

            <el-select
                :required="true"
                v-model="selectedStatus"
                :persistent="false"
            >
                <el-option
                    v-for="item in states"
                    :key="item.code"
                    :value="item.code"
                >
                    <template #default>
                        <Status size="small" :label="false" class="me-1" :status="item.code" />
                        <span v-html="item.label" />
                    </template>
                </el-option>
            </el-select>
        </template>

        <template #footer>
            <el-button @click="changeStatusDialogVisible = false">
                {{ $t('cancel') }}
            </el-button>
            <el-button
                type="primary"
                @click="changeStatus()"
            >
                {{ $t('ok') }}
            </el-button>
        </template>
    </el-dialog>

    <el-dialog v-if="unqueueDialogVisible" v-model="unqueueDialogVisible" destroyOnClose :appendToBody="true">
        <template #header>
            <h5>{{ $t("confirmation") }}</h5>
        </template>

        <template #default>
            <p v-html="$t('unqueue title multiple', {count: queryBulkAction ? executionsStore.total : selection.length})" />

            <el-select
                :required="true"
                v-model="selectedStatus"
                :persistent="false"
            >
                <el-option
                    v-for="item in unQueuestates"
                    :key="item.code"
                    :value="item.code"
                >
                    <template #default>
                        <Status size="small" :label="false" class="me-1" :status="item.code" />
                        <span v-html="item.label" />
                    </template>
                </el-option>
            </el-select>
        </template>

        <template #footer>
            <el-button @click="unqueueDialogVisible = false">
                {{ $t('cancel') }}
            </el-button>
            <el-button
                type="primary"
                @click="unqueueExecutions()"
            >
                {{ $t('ok') }}
            </el-button>
        </template>
    </el-dialog>

    <el-dialog v-if="isOpenReplayModal" v-model="isOpenReplayModal" :id="Utils.uid()" destroyOnClose :appendToBody="true" alignCenter>
        <template #header>
            <h5>{{ $t("confirmation") }}</h5>
        </template>

        <template #default>
            <p v-html="changeReplayToast()" />
        </template>

        <template #footer>
            <el-button @click="isOpenReplayModal = false">
                {{ $t('cancel') }}
            </el-button>
            <el-button @click="replayExecutions(true)">
                {{ $t('replay latest revision') }}
            </el-button>
            <el-button
                type="primary"
                @click="replayExecutions(false)"
            >
                {{ $t('ok') }}
            </el-button>
        </template>
    </el-dialog>
</template>

<script setup lang="ts">
    import _merge from "lodash/merge";
    import {useI18n} from "vue-i18n";
    import {useRoute, useRouter} from "vue-router";
    import {ref, computed, watch, h, useTemplateRef} from "vue";
    import * as YAML_UTILS from "@kestra-io/ui-libs/flow-yaml-utils";
    import {ElMessageBox, ElSwitch, ElFormItem, ElAlert, ElCheckbox} from "element-plus";

    import Delete from "vue-material-design-icons/Delete.vue";
    import Pencil from "vue-material-design-icons/Pencil.vue";
    import Import from "vue-material-design-icons/Import.vue";
    import Export from "vue-material-design-icons/Export.vue";
    import Restart from "vue-material-design-icons/Restart.vue";
    import RunFast from "vue-material-design-icons/RunFast.vue";
    import PlayBox from "vue-material-design-icons/PlayBox.vue";
    import PauseBox from "vue-material-design-icons/PauseBox.vue";
    import TextSearch from "vue-material-design-icons/TextSearch.vue";
    import DotsVertical from "vue-material-design-icons/DotsVertical.vue";
    import StateMachine from "vue-material-design-icons/StateMachine.vue";
    import LabelMultiple from "vue-material-design-icons/LabelMultiple.vue";
    import PlayBoxMultiple from "vue-material-design-icons/PlayBoxMultiple.vue";
    import StopCircleOutline from "vue-material-design-icons/StopCircleOutline.vue";
    import QueueFirstInLastOut from "vue-material-design-icons/QueueFirstInLastOut.vue";
    import Download from "vue-material-design-icons/Download.vue";

    import Id from "../Id.vue";
    import Kicon from "../Kicon.vue";
    import {State, Status} from "@kestra-io/ui-libs";
    import Labels from "../layout/Labels.vue";
    import DateAgo from "../layout/DateAgo.vue";
    import DataTable from "../layout/DataTable.vue";
    import BulkSelect from "../layout/BulkSelect.vue";
    import SelectTable from "../layout/SelectTable.vue";
    import KSFilter from "../filter/components/KSFilter.vue";
    import Sections from "../dashboard/sections/Sections.vue";
    import TopNavBar from "../../components/layout/TopNavBar.vue";
    import LabelInput from "../../components/labels/LabelInput.vue";
    //@ts-expect-error no declaration file
    import TriggerFlow from "../../components/flows/TriggerFlow.vue";
    import TriggerAvatar from "../../components/flows/TriggerAvatar.vue";

    import {filterValidLabels} from "./utils";
    import {useToast} from "../../utils/toast";
    import {storageKeys} from "../../utils/constants";
    import {invisibleSpace} from "../../utils/filters";
    import Utils from "../../utils/utils";
    import Duration from "../../components/dashboard/sections/table/columns/Duration.vue";

    import action from "../../models/action";
    import permission from "../../models/permission";

    import useRouteContext from "../../composables/useRouteContext";
    import {useTableColumns} from "../../composables/useTableColumns";
    import {useDataTableActions} from "../../composables/useDataTableActions";
    import {useSelectTableActions} from "../../composables/useSelectTableActions";

    import {useFlowStore} from "../../stores/flow";
    import {useAuthStore} from "override/stores/auth";
    import {useMiscStore} from "override/stores/misc";
    import {Label, useExecutionsStore} from "../../stores/executions";

    import {useExecutionFilter, useFlowExecutionFilter} from "../filter/configurations";
    import YAML_CHART from "../dashboard/assets/executions_timeseries_chart.yaml?raw";

    const {t} = useI18n();
    const toast = useToast();

    const executionFilter = useExecutionFilter();
    const flowExecutionFilter = useFlowExecutionFilter();

    const props = withDefaults(defineProps<{
        embed?: boolean;
        filter?: boolean;
        topbar?: boolean;
        id?: string | null;
        statuses?: string[];
        isReadOnly?: boolean;
        isConcurrency?: boolean;
        visibleCharts?: boolean;
        hidden?: string[] | null;
        flowId?: string | undefined;
        namespace?: string | undefined;
        defaultScopeFilter?: boolean;
    }>(), {
        embed: false,
        filter: true,
        topbar: true,
        id: null,
        statuses: () => [],
        isReadOnly: false,
        isConcurrency: false,
        visibleCharts: false,
        hidden: null,
        flowId: undefined,
        namespace: undefined,
        defaultScopeFilter: undefined
    });

    const emit = defineEmits<{
        "state-count": [payload: { runningCount: number; totalCount: number }];
    }>();

    const route = useRoute();
    const router = useRouter();

    const authStore = useAuthStore();
    const flowStore = useFlowStore();
    const miscStore = useMiscStore();
    const executionsStore = useExecutionsStore();

    const executionLabels = ref<Label[]>([]);
    const recomputeInterval = ref(false);
    const isOpenLabelsModal = ref(false);
    const isOpenReplayModal = ref(false);
    const selectedStatus = ref(undefined);
    const lastRefreshDate = ref(new Date());
    const unqueueDialogVisible = ref(false);
    const changeStatusDialogVisible = ref(false);
    const actionOptions = ref<Record<string, any>>({});
    const dblClickRouteName = ref("executions/update");
    const showChart = ref(localStorage.getItem(storageKeys.SHOW_CHART) !== "false");

    const optionalColumns = ref([
        {
            label: t("start date"),
            prop: "state.startDate",
            default: true,
            description: t("filter.table_column.executions.start-date")
        },
        {
            label: t("end date"),
            prop: "state.endDate",
            default: true,
            description: t("filter.table_column.executions.end-date")
        },
        {
            label: t("duration"),
            prop: "state.duration",
            default: true,
            description: t("filter.table_column.executions.duration")
        },
        {
            label: t("namespace"),
            prop: "namespace",
            default: true,
            description: t("filter.table_column.executions.namespace")
        },
        {
            label: t("flow"),
            prop: "flowId",
            default: true,
            description: t("filter.table_column.executions.flow")
        },
        {
            label: t("labels"),
            prop: "labels",
            default: true,
            description: t("filter.table_column.executions.labels")
        },
        {
            label: t("state"),
            prop: "state.current",
            default: true,
            description: t("filter.table_column.executions.state")
        },
        {
            label: t("revision"),
            prop: "flowRevision",
            default: false,
            description: t("filter.table_column.executions.revision")
        },
        {
            label: t("inputs"),
            prop: "inputs",
            default: false,
            description: t("filter.table_column.executions.inputs")
        },
        {
            label: t("outputs"),
            prop: "outputs",
            default: false,
            description: t("filter.table_column.executions.outputs")
        },
        {
            label: t("task id"),
            prop: "taskRunList.taskId",
            default: false,
            description: t("filter.table_column.executions.task-id")
        },
        {
            label: t("triggers"), 
            prop: "trigger", 
            default: true, 
            description: t("filter.table_column.executions.trigger")
        }
    ]);

    const storageKey = computed(() =>
        route.name === "flows/update"
            ? storageKeys.DISPLAY_FLOW_EXECUTIONS_COLUMNS
            : storageKeys.DISPLAY_EXECUTIONS_COLUMNS
    );

    const {visibleColumns: displayColumns, updateVisibleColumns: updateDisplayColumns} = useTableColumns({
        columns: optionalColumns.value,
        storageKey: storageKey.value
    });

    const visibleColumns = computed(() =>
        displayColumns.value
            .map(prop => optionalColumns.value.find(c => c.prop === prop))
            .filter(Boolean) as any[]
    );

    const isColumnSortable = (prop: string) => {
        return !["labels", "flowRevision", "inputs", "outputs", "taskRunList.taskId", "trigger"].includes(prop);
    };

    const selectionMapper = (execution: any) => {
        return execution.id;
    };

    const loadData = (callback?: () => void) => {
        lastRefreshDate.value = new Date();

        executionsStore.findExecutions(loadQuery({
            size: parseInt(route.query?.size as string ?? "25"),
            page: parseInt(route.query?.page as string ?? "1"),
            sort: route.query?.sort as string ?? "state.startDate:desc",
            state: route.query?.state ? [route.query?.state] : props.statuses
        })).then(() => {
            if (props.isConcurrency) {
                emitStateCount();
            }
        }).finally(callback);
    };

    const routeInfo = computed(() => ({title: t("executions")}));
    useRouteContext(routeInfo, props.embed);

    const dataTableRef = ref(null);
    const selectTableRef = useTemplateRef<typeof SelectTable>("selectTable");

    const {
        ready,
        onSort,
        onRowDoubleClick,
        onPageChanged,
        queryWithFilter,
        load,
        onDataLoaded
    } = useDataTableActions({
        dblClickRouteName: dblClickRouteName.value,
        embed: props.embed,
        dataTableRef,
        loadData: loadData
    });

    const {
        queryBulkAction,
        selection,
        handleSelectionChange,
        toggleAllUnselected,
        toggleAllSelection
    } = useSelectTableActions({
        dataTableRef: selectTableRef,
        selectionMapper: selectionMapper
    });

    const displayButtons = computed(() => {
        return (route.name === "flows/update") || (route.name === "executions/list");
    });

    const canCheck = computed(() => {
        return canDelete.value || canUpdate.value;
    });

    const canCreate = computed(() => {
        return authStore.user?.isAllowed(permission.EXECUTION, action.CREATE, props.namespace);
    });

    const canUpdate = computed(() => {
        return authStore.user?.isAllowed(permission.EXECUTION, action.UPDATE, props.namespace);
    });

    const canDelete = computed(() => {
        return authStore.user?.isAllowed(permission.EXECUTION, action.DELETE, props.namespace);
    });

    const isAllowedEdit = computed(() => {
        return authStore.user?.isAllowed(permission.FLOW, action.UPDATE, flowStore.flow?.namespace);
    });

    const hasAnyExecute = computed(() => {
        return authStore.user?.hasAnyActionOnAnyNamespace(permission.EXECUTION, action.CREATE);
    });

    const isDisplayedTop = computed(() => {
        if (props.visibleCharts) return true;
        else return props.embed === false && props.filter;
    });

    const states = computed(() => {
        return [State.FAILED, State.SUCCESS, State.WARNING, State.CANCELLED].map(value => ({
            code: value,
            label: t("mark as", {status: value})
        }));
    });

    const unQueuestates = computed(() => {
        return [State.RUNNING, State.CANCELLED, State.FAILED].map(value => ({
            code: value,
            label: t("unqueue as", {status: value}),
        }));
    });

    const charts = computed(() => {
        return [
            {...YAML_UTILS.parse(YAML_CHART), content: YAML_CHART}
        ];
    });

    const filteredLabels = (labels: any[]) => {
        const toIgnore = miscStore.configs?.hiddenLabelsPrefixes || [];

        const queryLabels = route.query?.labels;
        const allowedLabels = queryLabels ? (Array.isArray(queryLabels) ? queryLabels : [queryLabels]).filter((label): label is string => label !== null).map((label: string) => label.split(":")[0]) : [];

        return labels?.filter(label => {
            return !toIgnore.some((prefix: string) => label.key.startsWith(prefix)) || allowedLabels.includes(label.key);
        });
    };

    const executionParams = (row: any) => {
        return {
            namespace: row?.namespace,
            flowId: row?.flowId,
            id: row?.id
        };
    };

    const onShowChartChange = (value: boolean) => {
        showChart.value = value;
        localStorage.setItem(storageKeys.SHOW_CHART, value.toString());
    };

    const showStatChart = () => {
        return isDisplayedTop.value && showChart.value;
    };

    const refresh = () => {
        recomputeInterval.value = !recomputeInterval.value;
        const dashboardComponent = selectTableRef.value?.$refs?.dashboardComponent;
        if (dashboardComponent) {
            dashboardComponent.refreshCharts();
        }
        load(onDataLoaded);
    };

    const loadQuery = (base: any) => {
        let queryFilter = queryWithFilter();

        if (props.namespace) {
            queryFilter["filters[namespace][PREFIX]"] = props.namespace;
        }

        if (props.flowId) {
            queryFilter["filters[flowId][EQUALS]"] = props.flowId;
        }

        const hasStateFilters = Object.keys(queryFilter).some(key => key.startsWith("filters[state]")) || queryFilter.state;
        if (!hasStateFilters && props.statuses?.length > 0) {
            queryFilter["filters[state][IN]"] = props.statuses.join(",");
        }

        return _merge(base, queryFilter);
    };

    const genericConfirmAction = (message: string, queryAction: string, byIdAction: string, success: string, showCancelButton = true) => {
        toast.confirm(
            t(message, {"executionCount": queryBulkAction.value ? executionsStore.total : selection.value.length}),
            () => genericConfirmCallback(queryAction, byIdAction, success),
            "warning",
            showCancelButton
        );
    };

    const genericConfirmCallback = (queryAction: string, byIdAction: string, success: string, params?: any) => {
        const actionMap: Record<string, () => any> = {
            "queryResumeExecution": () => executionsStore.queryResumeExecution,
            "bulkResumeExecution": () => executionsStore.bulkResumeExecution,
            "queryPauseExecution": () => executionsStore.queryPauseExecution,
            "bulkPauseExecution": () => executionsStore.bulkPauseExecution,
            "queryUnqueueExecution": () => executionsStore.queryUnqueueExecution,
            "bulkUnqueueExecution": () => executionsStore.bulkUnqueueExecution,
            "queryForceRunExecution": () => executionsStore.queryForceRunExecution,
            "bulkForceRunExecution": () => executionsStore.bulkForceRunExecution,
            "queryRestartExecution": () => executionsStore.queryRestartExecution,
            "bulkRestartExecution": () => executionsStore.bulkRestartExecution,
            "queryReplayExecution": () => executionsStore.queryReplayExecution,
            "bulkReplayExecution": () => executionsStore.bulkReplayExecution,
            "queryChangeExecutionStatus": () => executionsStore.queryChangeExecutionStatus,
            "bulkChangeExecutionStatus": () => executionsStore.bulkChangeExecutionStatus,
            "queryDeleteExecution": () => executionsStore.queryDeleteExecution,
            "bulkDeleteExecution": () => executionsStore.bulkDeleteExecution,
            "queryKill": () => executionsStore.queryKill,
            "bulkKill": () => executionsStore.bulkKill,
        };

        if (queryBulkAction.value) {
            const query = loadQuery({
                sort: route.query.sort as string || "state.startDate:desc",
                state: route.query.state ? [route.query.state] : props.statuses,
            });
            let options = {...query, ...actionOptions.value};
            if (params) {
                options = {...options, ...params};
            }

            const action = actionMap[queryAction]();
            return action(options)
                .then((r: any) => {
                    toast.success(t(success, {executionCount: r.data.count}));
                    toggleAllUnselected();
                    loadData();
                });
        } else {
            const selectionData = {executionsId: selection.value};
            let options = {...selectionData, ...actionOptions.value};
            if (params) {
                options = {...options, ...params};
            }

            const action = actionMap[byIdAction]();
            return action(options)
                .then((r: any) => {
                    toast.success(t(success, {executionCount: r.data.count}));
                    toggleAllUnselected();
                    loadData();
                }).catch((e: any) => {
                    toast.error(e?.invalids.map((exec: any) => {
                        return {message: t(exec.message, {executionId: exec.invalidValue})};
                    }), t(e.message));
                });
        }
    };

    const resumeExecutions = () => {
        genericConfirmAction(
            "bulk resume",
            "queryResumeExecution",
            "bulkResumeExecution",
            "executions resumed",
            false
        );
    };

    const pauseExecutions = () => {
        genericConfirmAction(
            "bulk pause",
            "queryPauseExecution",
            "bulkPauseExecution",
            "executions paused"
        );
    };

    const unqueueExecutions = () => {
        unqueueDialogVisible.value = false;
        actionOptions.value.newStatus = selectedStatus.value;

        genericConfirmCallback(
            "queryUnqueueExecution",
            "bulkUnqueueExecution",
            "executions unqueue"
        );
    };

    const forceRunExecutions = () => {
        genericConfirmAction(
            "bulk force run",
            "queryForceRunExecution",
            "bulkForceRunExecution",
            "executions force run"
        );
    };

    const restartExecutions = () => {
        genericConfirmAction(
            "bulk restart",
            "queryRestartExecution",
            "bulkRestartExecution",
            "executions restarted"
        );
    };

    const replayExecutions = (latestRevision: boolean) => {
        isOpenReplayModal.value = false;

        genericConfirmCallback(
            "queryReplayExecution",
            "bulkReplayExecution",
            "executions replayed",
            {latestRevision: latestRevision}
        );
    };

    const changeReplayToast = () => {
        return t("bulk replay", {"executionCount": queryBulkAction.value ? executionsStore.total : selection.value.length});
    };

    const changeStatus = () => {
        changeStatusDialogVisible.value = false;
        actionOptions.value.newStatus = selectedStatus.value;

        genericConfirmCallback(
            "queryChangeExecutionStatus",
            "bulkChangeExecutionStatus",
            "executions state changed"
        );
    };

    const changeStatusToast = () => {
        return t("bulk change state", {"executionCount": queryBulkAction.value ? executionsStore.total : selection.value.length});
    };

    const deleteExecutions = () => {
        const includeNonTerminated = ref(false);
        const deleteLogs = ref(true);
        const deleteMetrics = ref(true);
        const deleteStorage = ref(true);

        const message = () => h("div", null, [
            h(
                "p",
                {innerHTML: t("bulk delete", {"executionCount": queryBulkAction.value ? executionsStore.total : selection.value.length})}
            ),
            h(ElFormItem, {
                class: "mt-3",
                label: t("execution-include-non-terminated")
            }, [
                h(ElSwitch, {
                    modelValue: includeNonTerminated.value,
                    "onUpdate:modelValue": (val: any) => {
                        includeNonTerminated.value = Boolean(val);
                    },
                }),
            ]),
            includeNonTerminated.value ? h(ElAlert, {
                title: t("execution-warn-title"),
                description: t("execution-warn-deleting-still-running"),
                type: "warning",
                showIcon: true,
                closable: false,
                class: "custom-warning"
            }) : null,
            h(ElCheckbox, {
                modelValue: deleteLogs.value,
                label: t("execution_deletion.logs"),
                "onUpdate:modelValue": (val: any) => (deleteLogs.value = Boolean(val)),
            }),
            h(ElCheckbox, {
                modelValue: deleteMetrics.value,
                label: t("execution_deletion.metrics"),
                "onUpdate:modelValue": (val: any) => (deleteMetrics.value = Boolean(val)),
            }),
            h(ElCheckbox, {
                modelValue: deleteStorage.value,
                label: t("execution_deletion.storage"),
                "onUpdate:modelValue": (val: any) => (deleteStorage.value = Boolean(val)),
            }),
        ]);
        ElMessageBox.confirm(message, t("confirmation")).then(() => {
            actionOptions.value.includeNonTerminated = includeNonTerminated.value;
            actionOptions.value.deleteLogs = deleteLogs.value;
            actionOptions.value.deleteMetrics = deleteMetrics.value;
            actionOptions.value.deleteStorage = deleteStorage.value;

            genericConfirmCallback(
                "queryDeleteExecution",
                "bulkDeleteExecution",
                "executions deleted"
            );
        });
    };

    const killExecutions = () => {
        genericConfirmAction(
            "bulk kill",
            "queryKill",
            "bulkKill",
            "executions killed"
        );
    };

    const setLabels = () => {
        const filtered = filterValidLabels(executionLabels.value);

        if (filtered.error) {
            toast.error(t("wrong labels"), t("error"));
            return;
        }

        ElMessageBox.confirm(
            t("bulk set labels", {"executionCount": queryBulkAction.value ? executionsStore.total : selection.value.length}),
            t("confirmation")
        ).then(() => {
            if (queryBulkAction.value) {
                return executionsStore
                    .querySetLabels({
                        params: loadQuery({
                            sort: route.query.sort as string || "state.startDate:desc",
                            state: route.query.state ? [route.query.state] : props.statuses
                        }),
                        data: filtered.labels
                    })
                    .then((r: any) => {
                        toast.success(t("Set labels done", {executionCount: r.data.count}));
                        toggleAllUnselected();
                        loadData();
                    });
            } else {
                return executionsStore
                    .bulkSetLabels({
                        executionsId: selection.value,
                        executionLabels: filtered.labels
                    })
                    .then((r: any) => {
                        toast.success(t("Set labels done", {executionCount: r.data.count}));
                        toggleAllUnselected();
                        loadData();
                    }).catch((e: any) => toast.error(e.invalids.map((exec: any) => {
                        return {message: t(exec.message, {executionId: exec.invalidValue})};
                    }), t(e.message)));
            }
        },
        );
        isOpenLabelsModal.value = false;
    };

    const editFlow = () => {
        router.push({
            name: "flows/update",
            params: {
                namespace: flowStore.flow?.namespace,
                id: flowStore.flow?.id,
                tab: "edit",
                tenant: route.params?.tenant
            }
        });
    };

    const emitStateCount = () => {
        const runningCount = executionsStore.executions?.filter(execution =>
            execution?.state?.current === State.RUNNING
        )?.length ?? 0;
        const totalCount = executionsStore.total;
        emit("state-count", {runningCount, totalCount});
    };

    watch(isOpenLabelsModal, (opening) => {
        if (opening) {
            executionLabels.value = [];
        }
    });

    async function exportExecutionsAsStream() {
        await executionsStore.exportExecutionsAsCSV(
            route.query
        )
    }
</script>


<style scoped lang="scss">
.shadow {
    box-shadow: 0px 2px 4px 0px var(--ks-card-shadow) !important;
}

.padding-bottom {
    padding-bottom: 4rem;
}

.custom-warning {
    border: 1px solid var(--ks-chart-border-warning);
    border-radius: 7px;
    box-shadow: 1px 1px 3px 1px var(--ks-chart-border-warning);

    :deep(.el-alert__title) {
        font-size: 16px;
        color: var(--ks-content-warning);
        font-weight: bold;
    }

    :deep(.el-alert__description) {
        font-size: 12px;
    }

    :deep(.el-alert__icon) {
        color: var(--ks-content-warning);
    }
}

.code-text {
    color: var(--ks-content-primary);
}

:deep(a.execution-id) code {
    color: var(--bs-code-color) !important;
}
</style>