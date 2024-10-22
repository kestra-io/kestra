<template>
    <top-nav-bar v-if="topbar" :title="routeInfo.title">
        <template #additional-right v-if="displayButtons">
            <ul>
                <template v-if="$route.name === 'executions/list'">
                    <li>
                        <template v-if="hasAnyExecute">
                            <trigger-flow />
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
                        <trigger-flow
                            v-if="flow"
                            :disabled="flow.disabled || isReadOnly"
                            :flow-id="flow.id"
                            :namespace="flow.namespace"
                        />
                    </li>
                </template>
            </ul>
        </template>
    </top-nav-bar>
    <section data-component="FILENAME_PLACEHOLDER" :class="{'container padding-bottom': topbar}" v-if="ready">
        <data-table
            @page-changed="onPageChanged"
            ref="dataTable"
            :total="total"
            :size="pageSize"
            :page="pageNumber"
            :embed="embed"
        >
            <template #navbar v-if="isDisplayedTop">
                <el-form-item>
                    <search-field />
                </el-form-item>
                <el-form-item v-if="$route.name !== 'flows/update'">
                    <namespace-select
                        :value="selectedNamespace"
                        data-type="flow"
                        :disabled="!!namespace"
                        @update:model-value="onDataTableValue('namespace', $event)"
                    />
                </el-form-item>
                <el-form-item>
                    <status-filter-buttons
                        :value="Utils.asArray($route.query.state)"
                        @update:model-value="onDataTableValue('state', $event)"
                    />
                </el-form-item>
                <el-form-item>
                    <date-filter
                        @update:is-relative="onDateFilterTypeChange"
                        @update:filter-value="onDataTableValue"
                    />
                </el-form-item>
                <el-form-item>
                    <scope-filter-buttons
                        :label="$t('executions')"
                        :value="$route.query.scope"
                        :system="namespace === 'system'"
                        @update:model-value="onDataTableValue('scope', $event)"
                    />
                </el-form-item>
                <el-form-item>
                    <label-filter
                        :model-value="$route.query.labels"
                        @update:model-value="onDataTableValue('labels', $event)"
                    />
                </el-form-item>
                <el-form-item>
                    <el-input
                        :placeholder="$t('trigger execution id')"
                        clearable
                        :model-value="$route.query.triggerExecutionId"
                        @update:model-value="onDataTableValue('triggerExecutionId', $event)"
                    />
                </el-form-item>
                <el-form-item>
                    <el-select
                        :placeholder="$t('trigger filter.title')"
                        v-model="childFilter"
                        :persistent="false"
                        @update:model-value="onDataTableValue('childFilter', $event === 'ALL' ? undefined : $event)"
                    >
                        <el-option
                            v-for="(col, val) in $tm('trigger filter.options')"
                            :key="val"
                            :label="col"
                            :value="val"
                        />
                    </el-select>
                </el-form-item>
                <el-form-item>
                    <el-select
                        v-model="displayColumns"
                        multiple
                        collapse-tags
                        collapse-tags-tooltip
                        @change="onDisplayColumnsChange"
                    >
                        <el-option
                            v-for="col in optionalColumns"
                            :key="col.label"
                            :label="$t(col.label)"
                            :value="col.prop"
                        />
                    </el-select>
                </el-form-item>
                <el-form-item>
                    <el-switch
                        :model-value="showChart"
                        @update:model-value="onShowChartChange"
                        :active-text="$t('show chart')"
                    />
                </el-form-item>
                <el-form-item>
                    <filters :storage-key="filterStorageKey" />
                </el-form-item>
                <el-form-item>
                    <refresh-button
                        :can-auto-refresh="canAutoRefresh"
                        class="float-right"
                        @refresh="refresh"
                    />
                </el-form-item>
            </template>

            <template #top>
                <el-card v-if="showStatChart()" shadow="never" class="mb-4">
                    <ExecutionsBar v-if="daily" :data="daily" :total="executionsCount" />
                </el-card>
            </template>

            <template #table v-if="executions.length">
                <select-table
                    ref="selectTable"
                    :data="executions"
                    :default-sort="{prop: 'state.startDate', order: 'descending'}"
                    stripe
                    table-layout="auto"
                    fixed
                    @row-dblclick="row => onRowDoubleClick(executionParams(row))"
                    @sort-change="onSort"
                    @selection-change="handleSelectionChange"
                    :selectable="!hidden?.includes('selection') && canCheck"
                >
                    <template #select-actions>
                        <bulk-select
                            :select-all="queryBulkAction"
                            :selections="selection"
                            :total="total"
                            @update:select-all="toggleAllSelection"
                            @unselect="toggleAllUnselected"
                        >
                            <el-button v-if="canUpdate" :icon="Restart" @click="restartExecutions()">
                                {{ $t("restart") }}
                            </el-button>
                            <el-button v-if="canCreate" :icon="PlayBoxMultiple" @click="replayExecutions()">
                                {{ $t("replay") }}
                            </el-button>
                            <el-button v-if="canUpdate" :icon="StateMachine" @click="changeStatusDialogVisible = !changeStatusDialogVisible">
                                {{ $t("change state") }}
                            </el-button>
                            <el-button v-if="canUpdate" :icon="StopCircleOutline" @click="killExecutions()">
                                {{ $t("kill") }}
                            </el-button>
                            <el-button v-if="canDelete" :icon="Delete" type="default" @click="deleteExecutions()">
                                {{ $t("delete") }}
                            </el-button>
                            <el-button
                                v-if="canUpdate"
                                :icon="LabelMultiple"
                                @click="isOpenLabelsModal = !isOpenLabelsModal"
                            >
                                {{ $t("Set labels") }}
                            </el-button>
                            <el-button v-if="canUpdate" :icon="PlayBox" @click="resumeExecutions()">
                                {{ $t("resume") }}
                            </el-button>
                            <el-button v-if="canUpdate" :icon="PauseBox" @click="pauseExecutions()">
                                {{ $t("pause") }}
                            </el-button>
                        </bulk-select>
                        <el-dialog
                            v-if="isOpenLabelsModal"
                            v-model="isOpenLabelsModal"
                            destroy-on-close
                            :append-to-body="true"
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
                                <el-form-item :label="$t('execution labels')">
                                    <label-input
                                        :key="executionLabels"
                                        v-model:labels="executionLabels"
                                    />
                                </el-form-item>
                            </el-form>
                        </el-dialog>
                    </template>
                    <template #default>
                        <el-table-column
                            prop="id"
                            sortable="custom"
                            :sort-orders="['ascending', 'descending']"
                            :label="$t('id')"
                        >
                            <template #default="scope">
                                <id
                                    :value="scope.row.id"
                                    :shrink="true"
                                    @click="onRowDoubleClick(executionParams(scope.row))"
                                />
                            </template>
                        </el-table-column>

                        <el-table-column
                            prop="state.startDate"
                            v-if="displayColumn('state.startDate')"
                            sortable="custom"
                            :sort-orders="['ascending', 'descending']"
                            :label="$t('start date')"
                        >
                            <template #default="scope">
                                <date-ago :inverted="true" :date="scope.row.state.startDate" />
                            </template>
                        </el-table-column>

                        <el-table-column
                            prop="state.endDate"
                            v-if="displayColumn('state.endDate')"
                            sortable="custom"
                            :sort-orders="['ascending', 'descending']"
                            :label="$t('end date')"
                        >
                            <template #default="scope">
                                <date-ago :inverted="true" :date="scope.row.state.endDate" />
                            </template>
                        </el-table-column>

                        <el-table-column
                            prop="state.duration"
                            v-if="displayColumn('state.duration')"
                            sortable="custom"
                            :sort-orders="['ascending', 'descending']"
                            :label="$t('duration')"
                        >
                            <template #default="scope">
                                <span v-if="isRunning(scope.row)">{{
                                    $filters.humanizeDuration(durationFrom(scope.row))
                                }}</span>
                                <span v-else>{{ $filters.humanizeDuration(scope.row.state.duration) }}</span>
                            </template>
                        </el-table-column>

                        <el-table-column
                            v-if="$route.name !== 'flows/update' && displayColumn('namespace')"
                            prop="namespace"
                            sortable="custom"
                            :sort-orders="['ascending', 'descending']"
                            :label="$t('namespace')"
                            :formatter="(_, __, cellValue) => $filters.invisibleSpace(cellValue)"
                        />

                        <el-table-column
                            v-if="$route.name !== 'flows/update' && displayColumn('flowId')"
                            prop="flowId"
                            sortable="custom"
                            :sort-orders="['ascending', 'descending']"
                            :label="$t('flow')"
                        >
                            <template #default="scope">
                                <router-link
                                    :to="{name: 'flows/update', params: {namespace: scope.row.namespace, id: scope.row.flowId}}"
                                >
                                    {{ $filters.invisibleSpace(scope.row.flowId) }}
                                </router-link>
                            </template>
                        </el-table-column>

                        <el-table-column v-if="displayColumn('labels')" :label="$t('labels')">
                            <template #default="scope">
                                <labels :labels="scope.row.labels" />
                            </template>
                        </el-table-column>

                        <el-table-column
                            prop="state.current"
                            v-if="displayColumn('state.current')"
                            sortable="custom"
                            :sort-orders="['ascending', 'descending']"
                            :label="$t('state')"
                        >
                            <template #default="scope">
                                <status :status="scope.row.state.current" size="small" />
                            </template>
                        </el-table-column>

                        <el-table-column
                            prop="flowRevision"
                            v-if="displayColumn('flowRevision')"
                            :label="$t('revision')"
                            class-name="shrink"
                        >
                            <template #default="scope">
                                <code>{{ scope.row.flowRevision }}</code>
                            </template>
                        </el-table-column>

                        <el-table-column
                            prop="inputs"
                            v-if="displayColumn('inputs')"
                            :label="$t('inputs')"
                            align="center"
                        >
                            <template #default="scope">
                                <el-tooltip effect="light">
                                    <template #content>
                                        <pre class="mb-0">{{ JSON.stringify(scope.row.inputs, null, "\t") }}</pre>
                                    </template>
                                    <Import v-if="scope.row.inputs" class="fs-5" />
                                </el-tooltip>
                            </template>
                        </el-table-column>

                        <el-table-column
                            prop="taskRunList.taskId"
                            v-if="displayColumn('taskRunList.taskId')"
                            :label="$t('task id')"
                        >
                            <template #header="scope">
                                <el-tooltip :content="$t('taskid column details')" effect="light">
                                    {{ scope.column.label }}
                                </el-tooltip>
                            </template>
                            <template #default="scope">
                                <code>
                                    {{ scope.row.taskRunList?.slice(-1)[0].taskId }}
                                    {{
                                        scope.row.taskRunList?.slice(-1)[0].attempts?.length > 1 ? `(${scope.row.taskRunList?.slice(-1)[0].attempts.length})` : ""
                                    }}
                                </code>
                            </template>
                        </el-table-column>

                        <el-table-column column-key="action" class-name="row-action">
                            <template #default="scope">
                                <router-link
                                    :to="{name: 'executions/update', params: {namespace: scope.row.namespace, flowId: scope.row.flowId, id: scope.row.id}}"
                                >
                                    <kicon :tooltip="$t('details')" placement="left">
                                        <TextSearch />
                                    </kicon>
                                </router-link>
                            </template>
                        </el-table-column>
                    </template>
                </select-table>
            </template>
        </data-table>
    </section>

    <el-dialog v-if="changeStatusDialogVisible" v-model="changeStatusDialogVisible" :id="uuid" destroy-on-close :append-to-body="true">
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
                        <status size="small" :label="false" class="me-1" :status="item.code" />
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
</template>

<script setup>
    import BulkSelect from "../layout/BulkSelect.vue";
    import SelectTable from "../layout/SelectTable.vue";
    import PlayBox from "vue-material-design-icons/PlayBox.vue";
    import PlayBoxMultiple from "vue-material-design-icons/PlayBoxMultiple.vue";
    import Restart from "vue-material-design-icons/Restart.vue";
    import Delete from "vue-material-design-icons/Delete.vue";
    import StopCircleOutline from "vue-material-design-icons/StopCircleOutline.vue";
    import Pencil from "vue-material-design-icons/Pencil.vue";
    import Import from "vue-material-design-icons/Import.vue";
    import Utils from "../../utils/utils";
    import LabelMultiple from "vue-material-design-icons/LabelMultiple.vue";
    import StateMachine from "vue-material-design-icons/StateMachine.vue";
    import PauseBox from "vue-material-design-icons/PauseBox.vue";
</script>

<script>
    import {mapState} from "vuex";
    import DataTable from "../layout/DataTable.vue";
    import TextSearch from "vue-material-design-icons/TextSearch.vue";
    import Status from "../Status.vue";
    import RouteContext from "../../mixins/routeContext";
    import TopNavBar from "../../components/layout/TopNavBar.vue";
    import DataTableActions from "../../mixins/dataTableActions";
    import SelectTableActions from "../../mixins/selectTableActions";
    import SearchField from "../layout/SearchField.vue";
    import NamespaceSelect from "../namespace/NamespaceSelect.vue";
    import LabelFilter from "../labels/LabelFilter.vue";
    import DateFilter from "./date-select/DateFilter.vue";
    import RefreshButton from "../layout/RefreshButton.vue"
    import Filters from "../saved-filters/Filters.vue";
    import StatusFilterButtons from "../layout/StatusFilterButtons.vue"
    import ScopeFilterButtons from "../layout/ScopeFilterButtons.vue"
    import Kicon from "../Kicon.vue"
    import Labels from "../layout/Labels.vue"
    import RestoreUrl from "../../mixins/restoreUrl";
    import State from "../../utils/state";
    import Id from "../Id.vue";
    import _merge from "lodash/merge";
    import permission from "../../models/permission";
    import action from "../../models/action";
    import TriggerFlow from "../../components/flows/TriggerFlow.vue";
    import {storageKeys} from "../../utils/constants";
    import LabelInput from "../../components/labels/LabelInput.vue";
    import {ElMessageBox, ElSwitch, ElFormItem, ElAlert, ElCheckbox} from "element-plus";
    import DateAgo from "../layout/DateAgo.vue";
    import {h, ref} from "vue";
    import ExecutionsBar from "../../components/dashboard/components/charts/executions/Bar.vue"

    import {filterLabels} from "./utils"

    export default {
        mixins: [RouteContext, RestoreUrl, DataTableActions, SelectTableActions],
        components: {
            Status,
            TextSearch,
            DataTable,
            SearchField,
            NamespaceSelect,
            LabelFilter,
            DateFilter,
            RefreshButton,
            Filters,
            StatusFilterButtons,
            ScopeFilterButtons,
            Kicon,
            Labels,
            Id,
            TriggerFlow,
            TopNavBar,
            LabelInput,
            ExecutionsBar
        },
        emits: ["state-count"],
        props: {
            hidden: {
                type: Array,
                default: null
            },
            statuses: {
                type: Array,
                default: () => []
            },
            isReadOnly: {
                type: Boolean,
                default: false
            },
            embed: {
                type: Boolean,
                default: false
            },
            topbar: {
                type: Boolean,
                default: true
            },
            filter: {
                type: Boolean,
                default: true
            },
            namespace: {
                type: String,
                required: false,
                default: undefined
            },
            flowId: {
                type: String,
                required: false,
                default: undefined
            },
            isConcurrency: {
                type: Boolean,
                default: false
            }
        },
        data() {
            return {
                isDefaultNamespaceAllow: true,
                dailyReady: false,
                dblClickRouteName: "executions/update",
                flowTriggerDetails: undefined,
                recomputeInterval: false,
                showChart: ["true", null].includes(localStorage.getItem(storageKeys.SHOW_CHART)),
                optionalColumns: [
                    {
                        label: "start date",
                        prop: "state.startDate",
                        default: true
                    },
                    {
                        label: "end date",
                        prop: "state.endDate",
                        default: true
                    },
                    {
                        label: "duration",
                        prop: "state.duration",
                        default: true
                    },
                    {
                        label: "state",
                        prop: "state.current",
                        default: true
                    },
                    {
                        label: "triggers",
                        prop: "triggers",
                        default: true
                    },
                    {
                        label: "labels",
                        prop: "labels",
                        default: true
                    },
                    {
                        label: "inputs",
                        prop: "inputs",
                        default: false
                    },
                    {
                        label: "namespace",
                        prop: "namespace",
                        default: true
                    },
                    {
                        label: "flow",
                        prop: "flowId",
                        default: true
                    },
                    {
                        label: "revision",
                        prop: "flowRevision",
                        default: false
                    },
                    {
                        label: "task id",
                        prop: "taskRunList.taskId",
                        default: false
                    }
                ],
                displayColumns: [],
                childFilter: "ALL",
                canAutoRefresh: false,
                storageKey: storageKeys.DISPLAY_EXECUTIONS_COLUMNS,
                isOpenLabelsModal: false,
                executionLabels: [],
                actionOptions: {},
                refreshDates: false,
                changeStatusDialogVisible: false,
                selectedStatus: undefined
            };
        },
        created() {
            // allow to have different storage key for flow executions list
            if (this.$route.name === "flows/update") {
                this.storageKey = storageKeys.DISPLAY_FLOW_EXECUTIONS_COLUMNS;
                this.optionalColumns = this.optionalColumns.filter(col => col.prop !== "namespace" && col.prop !== "flowId")
            }
            this.displayColumns = localStorage.getItem(this.storageKey)?.split(",")
                || this.optionalColumns.filter(col => col.default).map(col => col.prop);
            if (this.isConcurrency) {
                this.emitStateCount([State.RUNNING, State.PAUSED])
            }
        },
        computed: {
            ...mapState("execution", ["executions", "total"]),
            ...mapState("stat", ["daily"]),
            ...mapState("auth", ["user"]),
            ...mapState("flow", ["flow"]),
            routeInfo() {
                return {
                    title: this.$t("executions")
                };
            },
            endDate() {
                if (this.$route.query.endDate) {
                    return this.$route.query.endDate;
                }
                return undefined;
            },
            startDate() {
                this.refreshDates;
                if (this.$route.query.startDate) {
                    return this.$route.query.startDate;
                }
                if (this.$route.query.timeRange) {
                    return this.$moment().subtract(this.$moment.duration(this.$route.query.timeRange).as("milliseconds")).toISOString(true);
                }

                // the default is PT30D
                return this.$moment().subtract(30, "days").toISOString(true);
            },
            displayButtons() {
                return (this.$route.name === "flows/update") || (this.$route.name === "executions/list");
            },
            canCheck() {
                return this.canDelete || this.canUpdate;
            },
            canCreate() {
                return this.user && this.user.isAllowed(permission.EXECUTION, action.CREATE, this.namespace);
            },
            canUpdate() {
                return this.user && this.user.isAllowed(permission.EXECUTION, action.UPDATE, this.namespace);
            },
            canDelete() {
                return this.user && this.user.isAllowed(permission.EXECUTION, action.DELETE, this.namespace);
            },
            isAllowedEdit() {
                return this.user.isAllowed(permission.FLOW, action.UPDATE, this.flow.namespace);
            },
            hasAnyExecute() {
                return this.user.hasAnyActionOnAnyNamespace(permission.EXECUTION, action.CREATE);
            },
            isDisplayedTop() {
                return this.embed === false && this.filter
            },
            filterStorageKey() {
                return storageKeys.EXECUTIONS_FILTERS
            },
            states() {
                return [ State.FAILED, State.SUCCESS, State.WARNING, State.CANCELLED,].map(value => {
                    return {
                        code: value,
                        label: this.$t("mark as", {status: value})
                    };
                });
            },
            executionsCount() {
                return [...this.daily].reduce((a, b) => {
                    return a + Object.values(b.executionCounts).reduce((a, b) => a + b, 0);
                }, 0);
            },
            selectedNamespace(){
                return this.namespace !== null && this.namespace !== undefined ? this.namespace : this.$route.query?.namespace;
            }
        },
        beforeRouteEnter(to, from, next) {
            const defaultNamespace = localStorage.getItem(storageKeys.DEFAULT_NAMESPACE);
            const query = {...to.query};
            if (defaultNamespace) {
                query.namespace = defaultNamespace;
            } if (!query.scope) {
                query.scope = defaultNamespace === "system" ? ["SYSTEM"] : ["USER"];
            }
            next(vm => {
                vm.$router?.replace({query});
            });
        },
        methods: {
            executionParams(row) {
                return {
                    namespace: row.namespace,
                    flowId: row.flowId,
                    id: row.id
                }
            },
            onDisplayColumnsChange(event) {
                localStorage.setItem(this.storageKey, event);
                this.displayColumns = event;
            },
            displayColumn(column) {
                return this.hidden ? !this.hidden.includes(column) : this.displayColumns.includes(column);
            },
            onShowChartChange(value) {
                this.showChart = value;
                localStorage.setItem(storageKeys.SHOW_CHART, value);

                if (this.showChart) {
                    this.loadStats();
                }
            },
            showStatChart() {
                return this.isDisplayedTop && this.showChart;
            },
            refresh() {
                this.recomputeInterval = !this.recomputeInterval;
                this.load();
            },
            selectionMapper(execution) {
                return execution.id
            },
            isRunning(item) {
                return State.isRunning(item.state.current);
            },
            onStatusChange() {
                this.load(this.onDataLoaded);
            },
            loadQuery(base, stats) {
                let queryFilter = this.queryWithFilter();

                if (stats) {
                    delete queryFilter["timeRange"];
                    delete queryFilter["startDate"];
                    delete queryFilter["endDate"];
                } else if (queryFilter.timeRange) {
                    delete queryFilter["startDate"];
                    delete queryFilter["endDate"];
                }

                if (this.namespace) {
                    queryFilter["namespace"] = this.namespace;
                }

                if (this.flowId) {
                    queryFilter["flowId"] = this.flowId;
                }

                return _merge(base, queryFilter)
            },
            loadStats() {
                this.dailyReady = false;

                this.$store
                    .dispatch("stat/daily", this.loadQuery({
                        startDate: this.$moment(this.startDate).toISOString(true),
                        endDate: this.$moment(this.endDate).toISOString(true)
                    }, true))
                    .then(() => {
                        this.dailyReady = true;
                    });
            },
            loadData(callback) {
                this.refreshDates = !this.refreshDates;
                if (this.showStatChart()) {
                    this.loadStats();
                }

                this.$store.dispatch("execution/findExecutions", this.loadQuery({
                    size: parseInt(this.$route.query.size || this.internalPageSize),
                    page: parseInt(this.$route.query.page || this.internalPageNumber),
                    sort: this.$route.query.sort || "state.startDate:desc",
                    state: this.$route.query.state ? [this.$route.query.state] : this.statuses
                }, false)).finally(callback);
            },
            onDateFilterTypeChange(event) {
                this.canAutoRefresh = event;
            },
            durationFrom(item) {
                return (+new Date() - new Date(item.state.startDate).getTime()) / 1000
            },
            genericConfirmAction(toast, queryAction, byIdAction, success) {
                this.$toast().confirm(
                    this.$t(toast, {"executionCount": this.queryBulkAction ? this.total : this.selection.length}),
                    () => this.genericConfirmCallback(queryAction, byIdAction, success),
                    () => {}
                );
            },
            genericConfirmCallback(queryAction, byIdAction, success) {
                if (this.queryBulkAction) {
                    const query = this.loadQuery({
                        sort: this.$route.query.sort || "state.startDate:desc",
                        state: this.$route.query.state ? [this.$route.query.state] : this.statuses,
                    }, false);
                    const options = {...query, ...this.actionOptions};
                    return this.$store
                        .dispatch(queryAction, options)
                        .then(r => {
                            this.$toast().success(this.$t(success, {executionCount: r.data.count}));
                            this.loadData();
                        })
                } else {
                    const selection = {executionsId: this.selection};
                    const options = {...selection, ...this.actionOptions};
                    return this.$store
                        .dispatch(byIdAction, options)
                        .then(r => {
                            this.$toast().success(this.$t(success, {executionCount: r.data.count}));
                            this.loadData();
                        }).catch(e => {
                            this.$toast().error(e?.invalids.map(exec => {
                                return {message: this.$t(exec.message, {executionId: exec.invalidValue})}
                            }), this.$t(e.message))
                        })
                }
            },
            resumeExecutions() {
                this.genericConfirmAction(
                    "bulk resume",
                    "execution/queryResumeExecution",
                    "execution/bulkResumeExecution",
                    "executions resumed"
                );
            },
            pauseExecutions() {
                this.genericConfirmAction(
                    "bulk pause",
                    "execution/queryPauseExecution",
                    "execution/bulkPauseExecution",
                    "executions paused"
                );
            },
            restartExecutions() {
                this.genericConfirmAction(
                    "bulk restart",
                    "execution/queryRestartExecution",
                    "execution/bulkRestartExecution",
                    "executions restarted"
                );
            },
            replayExecutions() {
                this.genericConfirmAction(
                    "bulk replay",
                    "execution/queryReplayExecution",
                    "execution/bulkReplayExecution",
                    "executions replayed"
                );
            },
            changeStatus() {
                this.changeStatusDialogVisible = false;
                this.actionOptions.newStatus = this.selectedStatus;

                this.genericConfirmCallback(
                    "execution/queryChangeExecutionStatus",
                    "execution/bulkChangeExecutionStatus",
                    "executions state changed"
                );
            },
            changeStatusToast() {
                return this.$t("bulk change state", {"executionCount": this.queryBulkAction ? this.total : this.selection.length});
            },
            deleteExecutions() {
                const includeNonTerminated = ref(false);

                const deleteLogs = ref(true);
                const deleteMetrics = ref(true);
                const deleteStorage = ref(true);

                const message = () => h("div", null, [
                    h(
                        "p",
                        {innerHTML: this.$t("bulk delete", {"executionCount": this.queryBulkAction ? this.total : this.selection.length})}
                    ),
                    h(ElFormItem, {
                        class: "mt-3",
                        label: this.$t("execution-include-non-terminated")
                    }, [
                        h(ElSwitch, {
                            modelValue: includeNonTerminated.value,
                            "onUpdate:modelValue": (val) => {
                                includeNonTerminated.value = val
                            },
                        }),
                    ]),
                    h(ElAlert, {
                        title: this.$t("execution-warn-deleting-still-running"),
                        type: "warning",
                        showIcon: true,
                        closable: false
                    }),
                    h(ElCheckbox, {
                        modelValue: deleteLogs.value,
                        label: this.$t("execution_deletion.logs"),
                        "onUpdate:modelValue": (val) => (deleteLogs.value = val),
                    }),
                    h(ElCheckbox, {
                        modelValue: deleteMetrics.value,
                        label: this.$t("execution_deletion.metrics"),
                        "onUpdate:modelValue": (val) => (deleteMetrics.value = val),
                    }),
                    h(ElCheckbox, {
                        modelValue: deleteStorage.value,
                        label: this.$t("execution_deletion.storage"),
                        "onUpdate:modelValue": (val) => (deleteStorage.value = val),
                    }),
                ]);
                ElMessageBox.confirm(message, this.$t("confirmation"), {
                    type: "confirm",
                    inputType: "checkbox",
                    inputValue: "false",
                }).then(() => {
                    this.actionOptions.includeNonTerminated = includeNonTerminated.value;
                    this.actionOptions.deleteLogs = deleteLogs.value;
                    this.actionOptions.deleteMetrics = deleteMetrics.value;
                    this.actionOptions.deleteStorage = deleteStorage.value;

                    this.genericConfirmCallback(
                        "execution/queryDeleteExecution",
                        "execution/bulkDeleteExecution",
                        "executions deleted"
                    )
                });
            },
            killExecutions() {
                this.genericConfirmAction(
                    "bulk kill",
                    "execution/queryKill",
                    "execution/bulkKill",
                    "executions killed"
                );
            },
            setLabels() {
                const filtered = filterLabels(this.executionLabels)

                if(filtered.error) {
                    this.$toast().error(this.$t("wrong labels"))
                    return;
                }

                this.$toast().confirm(
                    this.$t("bulk set labels", {"executionCount": this.queryBulkAction ? this.total : this.selection.length}),
                    () => {
                        if (this.queryBulkAction) {
                            return this.$store
                                .dispatch("execution/querySetLabels", {
                                    params: this.loadQuery({
                                        sort: this.$route.query.sort || "state.startDate:desc",
                                        state: this.$route.query.state ? [this.$route.query.state] : this.statuses
                                    }, false),
                                    data: filtered.labels
                                })
                                .then(r => {
                                    this.$toast().success(this.$t("Set labels done", {executionCount: r.data.count}));
                                    this.loadData();
                                })
                        } else {
                            return this.$store
                                .dispatch("execution/bulkSetLabels", {
                                    executionsId: this.selection,
                                    executionLabels: filtered.labels
                                })
                                .then(r => {
                                    this.$toast().success(this.$t("Set labels done", {executionCount: r.data.count}));
                                    this.loadData();
                                }).catch(e => this.$toast().error(e.invalids.map(exec => {
                                    return {message: this.$t(exec.message, {executionId: exec.invalidValue})}
                                }), this.$t(e.message)))
                        }
                    },
                    () => {
                    }
                )
                this.isOpenLabelsModal = false;
            },
            editFlow() {
                this.$router.push({
                    name: "flows/update", params: {
                        namespace: this.flow.namespace,
                        id: this.flow.id,
                        tab: "editor",
                        tenant: this.$route.params.tenant
                    }
                })
            },
            emitStateCount(states) {
                this.$store.dispatch("execution/findExecutions", this.loadQuery({
                    size: parseInt(this.$route.query.size || this.internalPageSize),
                    page: parseInt(this.$route.query.page || this.internalPageNumber),
                    sort: this.$route.query.sort || "state.startDate:desc",
                    state: states
                }, false)).then(() => {
                    this.$emit("state-count", this.total);
                });
            }
        },
        watch: {
            isOpenLabelsModal(opening) {
                if (opening) {
                    this.executionLabels = [];
                }
            }
        },
    };
</script>

<style scoped lang="scss">
    .padding-bottom {
        padding-bottom: 4rem;
    }
</style>