<template>
    <top-nav-bar v-if="topbar" :title="routeInfo.title">
        <template #additional-right v-if="displayButtons">
            <ul>
                <template v-if="$route.name === 'flows/update'">
                    <li>
                        <template v-if="isAllowedEdit">
                            <el-button :icon="Pencil" size="large" @click="editFlow" :disabled="isReadOnly">
                                {{ $t('edit flow') }}
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
    <div :class="{'mt-3': topbar}" v-if="ready">
        <data-table @page-changed="onPageChanged" ref="dataTable" :total="total" :size="pageSize" :page="pageNumber">
            <template #navbar v-if="isDisplayedTop">
                <el-form-item>
                    <search-field />
                </el-form-item>
                <el-form-item>
                    <namespace-select
                        data-type="flow"
                        v-if="$route.name !== 'flows/update'"
                        :value="namespace"
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
                        @update:is-relative="onDateFilterTypeChange($event)"
                        @update:filter-value="onDataTableValue($event)"
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
                        @change="onDisplayColumnsChange($event)"
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
                    <el-input
                        :placeholder="$t('trigger execution id')"
                        clearable
                        :model-value="$route.query.triggerExecutionId"
                        @update:model-value="onDataTableValue('triggerExecutionId', $event)"
                    />
                </el-form-item>
                <el-form-item>
                    <label-filter
                        :model-value="$route.query.labels"
                        @update:model-value="onDataTableValue('labels', $event)"
                    />
                </el-form-item>
                <el-form-item>
                    <refresh-button
                        :can-auto-refresh="canAutoRefresh"
                        class="float-right"
                        @refresh="refresh"
                    />
                </el-form-item>
            </template>

            <template #top v-if="isDisplayedTop">
                <state-global-chart
                    v-if="daily"
                    class="mb-4"
                    :ready="dailyReady"
                    :data="daily"
                    :start-date="startDate"
                    :end-date="endDate"
                    :namespace="namespace"
                    :flow-id="flowId"
                />
            </template>

            <template #table>
                <select-table
                    ref="selectTable"
                    :data="executions"
                    :default-sort="{prop: 'state.startDate', order: 'descending'}"
                    stripe
                    table-layout="auto"
                    fixed
                    @row-dblclick="onRowDoubleClick"
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
                                {{ $t('restart') }}
                            </el-button>
                            <el-button v-if="canUpdate" :icon="StopCircleOutline" @click="killExecutions()">
                                {{ $t('kill') }}
                            </el-button>
                            <el-button v-if="canDelete" :icon="Delete" type="default" @click="deleteExecutions()">
                                {{ $t('delete') }}
                            </el-button>
                            <el-button v-if="canUpdate" :icon="LabelMultiple" @click="isOpenLabelsModal = !isOpenLabelsModal">
                                {{ $t('Set labels') }}
                            </el-button>
                        </bulk-select>
                        <el-dialog v-if="isOpenLabelsModal" v-model="isOpenLabelsModal" destroy-on-close :append-to-body="true">
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
                                <id :value="scope.row.id" :shrink="true" @click="onRowDoubleClick(scope.row)" />
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
                                <span v-if="isRunning(scope.row)">{{ $filters.humanizeDuration(durationFrom(scope.row)) }}</span>
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
                                <router-link :to="{name: 'flows/update', params: {namespace: scope.row.namespace, id: scope.row.flowId}}">
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
                            prop="triggers"
                            v-if="displayColumn('triggers')"
                            :label="$t('triggers')"
                            class-name="shrink"
                        >
                            <template #default="scope">
                                <trigger-avatar :execution="scope.row" />
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
                                <el-tooltip>
                                    <template #content>
                                        <pre class="mb-0">{{ JSON.stringify(scope.row.inputs, null, '\t') }}</pre>
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
                                <el-tooltip :content="$t('taskid column details')">
                                    {{ scope.column.label }}
                                </el-tooltip>
                            </template>
                            <template #default="scope">
                                <code>
                                    {{ scope.row.taskRunList?.slice(-1)[0].taskId }}
                                    {{ scope.row.taskRunList?.slice(-1)[0].attempts?.length > 1 ? `(${scope.row.taskRunList?.slice(-1)[0].attempts.length})` : '' }}
                                </code>
                            </template>
                        </el-table-column>

                        <el-table-column column-key="action" class-name="row-action">
                            <template #default="scope">
                                <router-link :to="{name: 'executions/update', params: {namespace: scope.row.namespace, flowId: scope.row.flowId, id: scope.row.id}}">
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
    </div>
</template>

<script setup>
    import BulkSelect from "../layout/BulkSelect.vue";
    import SelectTable from "../layout/SelectTable.vue";
    import Restart from "vue-material-design-icons/Restart.vue";
    import Delete from "vue-material-design-icons/Delete.vue";
    import StopCircleOutline from "vue-material-design-icons/StopCircleOutline.vue";
    import Pencil from "vue-material-design-icons/Pencil.vue";
    import Import from "vue-material-design-icons/Import.vue";
    import Utils from "../../utils/utils";
    import LabelMultiple from "vue-material-design-icons/LabelMultiple.vue";
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
    import StatusFilterButtons from "../layout/StatusFilterButtons.vue"
    import StateGlobalChart from "../../components/stats/StateGlobalChart.vue";
    import TriggerAvatar from "../../components/flows/TriggerAvatar.vue";
    import DateAgo from "../layout/DateAgo.vue";
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
            StatusFilterButtons,
            StateGlobalChart,
            TriggerAvatar,
            DateAgo,
            Kicon,
            Labels,
            Id,
            TriggerFlow,
            TopNavBar,
            LabelInput
        },
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
                default: false
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
        },
        data() {
            return {
                isDefaultNamespaceAllow: true,
                dailyReady: false,
                dblClickRouteName: "executions/update",
                flowTriggerDetails: undefined,
                recomputeInterval: false,
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
                if (this.$route.query.startDate) {
                    return this.$route.query.startDate;
                }
                if (this.$route.query.timeRange) {
                    return this.$moment().subtract(this.$moment.duration(this.$route.query.timeRange).as("milliseconds")).toISOString(true);
                }
                return undefined;
            },
            displayButtons() {
                return (this.$route.name === "flows/update");
            },
            canCheck() {
                return this.canDelete || this.canUpdate;
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
            isDisplayedTop() {
                return this.embed === false || this.filter
            }
        },
        methods: {
            onDisplayColumnsChange(event) {
                localStorage.setItem(this.storageKey, event);
                this.displayColumns = event;
            },
            displayColumn(column) {
                return this.hidden ? !this.hidden.includes(column) : this.displayColumns.includes(column);
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
                }

                if (this.namespace) {
                    queryFilter["namespace"] = this.namespace;
                }

                if (this.flowId) {
                    queryFilter["flowId"] = this.flowId;
                }

                return _merge(base, queryFilter)
            },
            loadData(callback) {
                if (this.isDisplayedTop) {
                    this.dailyReady = false;

                    this.$store
                        .dispatch("stat/daily", this.loadQuery({
                            startDate: this.$moment(this.startDate).toISOString(true),
                            endDate: this.$moment(this.endDate).toISOString(true)
                        }, true))
                        .then(() => {
                            this.dailyReady = true;
                        });
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
            restartExecutions() {
                this.$toast().confirm(
                    this.$t("bulk restart", {"executionCount": this.queryBulkAction ? this.total : this.selection.length}),
                    () => {
                        if (this.queryBulkAction) {
                            return this.$store
                                .dispatch("execution/queryRestartExecution", this.loadQuery({
                                    sort: this.$route.query.sort || "state.startDate:desc",
                                    state: this.$route.query.state ? [this.$route.query.state] : this.statuses,
                                }))
                                .then(r => {
                                    this.$toast().success(this.$t("executions restarted", {executionCount: r.data.count}));
                                    this.loadData();
                                })
                        } else {
                            return this.$store
                                .dispatch("execution/bulkRestartExecution", {executionsId: this.selection})
                                .then(r => {
                                    this.$toast().success(this.$t("executions restarted", {executionCount: r.data.count}));
                                    this.loadData();
                                }).catch(e => this.$toast().error(e.invalids.map(exec => {
                                    return {message: this.$t(exec.message, {executionId: exec.invalidValue})}
                                }), this.$t(e.message)))
                        }
                    },
                    () => {
                    }
                )
            },
            deleteExecutions() {
                this.$toast().confirm(
                    this.$t("bulk delete", {"executionCount": this.queryBulkAction ? this.total : this.selection.length}),
                    () => {
                        if (this.queryBulkAction) {
                            return this.$store
                                .dispatch("execution/queryDeleteExecution", this.loadQuery({
                                    sort: this.$route.query.sort || "state.startDate:desc",
                                    state: this.$route.query.state ? [this.$route.query.state] : this.statuses
                                }, false))
                                .then(r => {
                                    this.$toast().success(this.$t("executions deleted", {executionCount: r.data.count}));
                                    this.loadData();
                                })
                        } else {
                            return this.$store
                                .dispatch("execution/bulkDeleteExecution", {executionsId: this.selection})
                                .then(r => {
                                    this.$toast().success(this.$t("executions deleted", {executionCount: r.data.count}));
                                    this.loadData();
                                }).catch(e => this.$toast().error(e.invalids.map(exec => {
                                    return {message: this.$t(exec.message, {executionId: exec.invalidValue})}
                                }), this.$t(e.message)))
                        }
                    },
                    () => {
                    }
                )
            },
            killExecutions() {
                this.$toast().confirm(
                    this.$t("bulk kill", {"executionCount": this.queryBulkAction ? this.total : this.selection.length}),
                    () => {
                        if (this.queryBulkAction) {
                            return this.$store
                                .dispatch("execution/queryKill", this.loadQuery({
                                    sort: this.$route.query.sort || "state.startDate:desc",
                                    state: this.$route.query.state ? [this.$route.query.state] : this.statuses
                                }, false))
                                .then(r => {
                                    this.$toast().success(this.$t("executions killed", {executionCount: r.data.count}));
                                    this.loadData();
                                })
                        } else {
                            return this.$store
                                .dispatch("execution/bulkKill", {executionsId: this.selection})
                                .then(r => {
                                    this.$toast().success(this.$t("executions killed", {executionCount: r.data.count}));
                                    this.loadData();
                                }).catch(e => this.$toast().error(e.invalids.map(exec => {
                                    return {message: this.$t(exec.message, {executionId: exec.invalidValue})}
                                }), this.$t(e.message)))
                        }
                    },
                    () => {
                    }
                )
            },
            setLabels() {
                this.$toast().confirm(
                    this.$t("bulk set labels", {"executionCount": this.queryBulkAction ? this.total : this.selection.length}),
                    () => {
                        if (this.queryBulkAction) {
                            return this.$store
                                .dispatch("execution/querySetLabels",  {
                                    params: this.loadQuery({
                                        sort: this.$route.query.sort || "state.startDate:desc",
                                        state: this.$route.query.state ? [this.$route.query.state] : this.statuses
                                    }, false),
                                    data: this.executionLabels
                                })
                                .then(r => {
                                    this.$toast().success(this.$t("bulk set labels", {executionCount: r.data.count}));
                                    this.loadData();
                                })
                        } else {
                            return this.$store
                                .dispatch("execution/bulkSetLabels", {executionsId: this.selection, executionLabels: this.executionLabels})
                                .then(r => {
                                    this.$toast().success(this.$t("bulk set labels", {executionCount: r.data.count}));
                                    this.loadData();
                                }).catch(e => this.$toast().error(e.invalids.map(exec => {
                                    return {message: this.$t(exec.message, {executionId: exec.invalidValue})}
                                }), this.$t(e.message)))
                        }
                    },
                    () => {
                    }
                )
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
        }
    };
</script>
