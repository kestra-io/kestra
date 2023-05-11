<template>
    <div v-if="ready">
        <data-table @page-changed="onPageChanged" ref="dataTable" :total="total" :size="pageSize" :page="pageNumber">
            <template #navbar v-if="embed === false">
                <el-form-item>
                    <search-field />
                </el-form-item>
                <el-form-item>
                    <namespace-select
                        data-type="flow"
                        v-if="$route.name !== 'flows/update'"
                        :value="$route.query.namespace"
                        @update:model-value="onDataTableValue('namespace', $event)"
                    />
                </el-form-item>
                <el-form-item>
                    <status-filter-buttons
                        :value="$route.query.state"
                        @update:model-value="onDataTableValue('state', $event)"
                    />
                </el-form-item>
                <el-form-item>
                    <date-range
                        :start-date="$route.query.startDate"
                        :end-date="$route.query.endDate"
                        @update:model-value="onDataTableValue($event)"
                    />
                </el-form-item>
                <el-form-item>
                    <refresh-button class="float-right" @refresh="load" />
                </el-form-item>
            </template>

            <template #top v-if="embed === false">
                <state-global-chart
                    v-if="daily"
                    class="mb-4"
                    :ready="dailyReady"
                    :data="daily"
                />
            </template>

            <template #table>
                <el-table
                    :data="executions"
                    ref="table"
                    :default-sort="{prop: 'state.startDate', order: 'descending'}"
                    stripe
                    table-layout="auto"
                    fixed
                    @row-dblclick="onRowDoubleClick"
                    @sort-change="onSort"
                    @selection-change="handleSelectionChange"
                >
                    <el-table-column type="selection" v-if="!hidden.includes('selection') && (canCheck)" />

                    <el-table-column prop="id" v-if="!hidden.includes('id')" sortable="custom" :sort-orders="['ascending', 'descending']" :label="$t('id')">
                        <template #default="scope">
                            <id :value="scope.row.id" :shrink="true" />
                        </template>
                    </el-table-column>

                    <el-table-column prop="state.startDate" v-if="!hidden.includes('state.startDate')" sortable="custom" :sort-orders="['ascending', 'descending']" :label="$t('start date')">
                        <template #default="scope">
                            <date-ago :inverted="true" :date="scope.row.state.startDate" />
                        </template>
                    </el-table-column>

                    <el-table-column prop="state.endDate" v-if="!hidden.includes('state.endDate')" sortable="custom" :sort-orders="['ascending', 'descending']" :label="$t('end date')">
                        <template #default="scope">
                            <date-ago :inverted="true" :date="scope.row.state.endDate" />
                        </template>
                    </el-table-column>

                    <el-table-column prop="state.duration" v-if="!hidden.includes('state.duration')" sortable="custom" :sort-orders="['ascending', 'descending']" :label="$t('duration')">
                        <template #default="scope">
                            <span v-if="isRunning(scope.row)">{{ $filters.humanizeDuration(durationFrom(scope.row)) }}</span>
                            <span v-else>{{ $filters.humanizeDuration(scope.row.state.duration) }}</span>
                        </template>
                    </el-table-column>

                    <el-table-column v-if="$route.name !== 'flows/update' && !hidden.includes('namespace')" prop="namespace" sortable="custom" :sort-orders="['ascending', 'descending']" :label="$t('namespace')" />

                    <el-table-column v-if="$route.name !== 'flows/update' && !hidden.includes('flowId')" prop="flowId" sortable="custom" :sort-orders="['ascending', 'descending']" :label="$t('flow')">
                        <template #default="scope">
                            <router-link :to="{name: 'flows/update', params: {namespace: scope.row.namespace, id: scope.row.flowId}}">
                                {{ scope.row.flowId }}
                            </router-link>
                        </template>
                    </el-table-column>

                    <el-table-column prop="state.current" v-if="!hidden.includes('state.current')" sortable="custom" :sort-orders="['ascending', 'descending']" :label="$t('state')">
                        <template #default="scope">
                            <status :status="scope.row.state.current" size="small" />
                        </template>
                    </el-table-column>

                    <el-table-column prop="triggers" v-if="!hidden.includes('triggers')" :label="$t('triggers')" class-name="shrink">
                        <template #default="scope">
                            <trigger-avatar :execution="scope.row" />
                        </template>
                    </el-table-column>

                    <el-table-column column-key="action" class-name="row-action">
                        <template #default="scope">
                            <router-link :to="{name: 'executions/update', params: {namespace: scope.row.namespace, flowId: scope.row.flowId, id: scope.row.id}}">
                                <kicon :tooltip="$t('details')" placement="left">
                                    <eye />
                                </kicon>
                            </router-link>
                        </template>
                    </el-table-column>
                </el-table>
            </template>
        </data-table>

        <bottom-line v-if="displayBottomBar">
            <ul>
                <li v-if="executionsSelection.length !== 0 && (canUpdate || canDelete)">
                    <bottom-line-counter v-model="queryBulkAction" :selections="executionsSelection" :total="total" @update:model-value="selectAll()">
                        <el-button v-if="canUpdate" :icon="Restart" size="large" @click="restartExecutions()">
                            {{ $t('restart') }}
                        </el-button>
                        <el-button v-if="canUpdate" :icon="StopCircleOutline" size="large" @click="killExecutions()">
                            {{ $t('kill') }}
                        </el-button>
                        <el-button v-if="canDelete" :icon="Delete" size="large" type="default" @click="deleteExecutions()">
                            {{ $t('delete') }}
                        </el-button>
                    </bottom-line-counter>
                </li>

                <li class="spacer" />

                <template v-if="$route.name === 'flows/update'">
                    <li>
                        <template v-if="isAllowedEdit">
                            <el-button :icon="Pencil" size="large" @click="editFlow">
                                {{ $t('edit flow') }}
                            </el-button>
                        </template>
                    </li>
                    <li>
                        <trigger-flow v-if="flow" :disabled="flow.disabled" :flow-id="flow.id" :namespace="flow.namespace" />
                    </li>
                </template>
            </ul>
        </bottom-line>
    </div>
</template>

<script setup>
    import Restart from "vue-material-design-icons/Restart.vue";
    import Delete from "vue-material-design-icons/Delete.vue";
    import StopCircleOutline from "vue-material-design-icons/StopCircleOutline.vue";
    import Pencil from "vue-material-design-icons/Pencil.vue";
</script>

<script>
    import {mapState} from "vuex";
    import DataTable from "../layout/DataTable.vue";
    import Eye from "vue-material-design-icons/Eye.vue";
    import Status from "../Status.vue";
    import RouteContext from "../../mixins/routeContext";
    import DataTableActions from "../../mixins/dataTableActions";
    import SearchField from "../layout/SearchField.vue";
    import NamespaceSelect from "../namespace/NamespaceSelect.vue";
    import DateRange from "../layout/DateRange.vue";
    import RefreshButton from "../layout/RefreshButton.vue"
    import StatusFilterButtons from "../layout/StatusFilterButtons.vue"
    import StateGlobalChart from "../../components/stats/StateGlobalChart.vue";
    import TriggerAvatar from "../../components/flows/TriggerAvatar.vue";
    import DateAgo from "../layout/DateAgo.vue";
    import Kicon from "../Kicon.vue"
    import RestoreUrl from "../../mixins/restoreUrl";
    import State from "../../utils/state";
    import Id from "../Id.vue";
    import _merge from "lodash/merge";
    import BottomLine from "../layout/BottomLine.vue";
    import BottomLineCounter from "../layout/BottomLineCounter.vue";
    import permission from "../../models/permission";
    import action from "../../models/action";
    import TriggerFlow from "../../components/flows/TriggerFlow.vue";

    export default {
        mixins: [RouteContext, RestoreUrl, DataTableActions],
        components: {
            Status,
            Eye,
            DataTable,
            SearchField,
            NamespaceSelect,
            DateRange,
            RefreshButton,
            StatusFilterButtons,
            StateGlobalChart,
            TriggerAvatar,
            DateAgo,
            Kicon,
            Id,
            BottomLine,
            BottomLineCounter,
            TriggerFlow
        },
        props: {
            embed: {
                type: Boolean,
                default: false
            },
            hidden: {
                type: Array,
                default: () => []
            },
            statuses: {
                type: Array,
                default: () => []
            },
        },
        data() {
            return {
                isDefaultNamespaceAllow: true,
                dailyReady: false,
                dblClickRouteName: "executions/update",
                flowTriggerDetails: undefined,
                executionsSelection: [],
                queryBulkAction: false
            };
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
                return new Date();
            },
            startDate() {
                return this.$moment(this.endDate)
                    .add(-30, "days")
                    .toDate();
            },
            displayBottomBar() {
                return (this.executionsSelection.length !== 0 && (this.canUpdate || this.canDelete)) ||
                    (this.$route.name === "flows/update");
            },
            canCheck() {
                return this.canDelete || this.canUpdate;
            },
            canUpdate() {
                return this.user && this.user.isAllowed(permission.EXECUTION, action.UPDATE, this.$route.query.namespace);
            },
            canDelete() {
                return this.user && this.user.isAllowed(permission.EXECUTION, action.DELETE, this.$route.query.namespace);
            },
            isAllowedEdit() {
                return this.user.isAllowed(permission.FLOW, action.UPDATE, this.flow.namespace);
            },
        },
        methods: {
            handleSelectionChange(val) {
                if (val.length === 0) {
                    this.queryBulkAction = false
                }
                this.executionsSelection = val.map(x => x.id);
            },
            selectAll() {
                if (this.$refs.table.getSelectionRows().length !== this.$refs.table.data.length) {
                    this.$refs.table.toggleAllSelection();
                }
            },
            isRunning(item){
                return State.isRunning(item.state.current);
            },
            onStatusChange() {
                this.load(this.onDataLoaded);
            },
            loadQuery(base, stats) {
                let queryFilter = this.queryWithFilter();

                if (stats) {
                    delete queryFilter["startDate"];
                    delete queryFilter["endDate"];
                }

                if (this.$route.name === "flows/update") {
                    queryFilter["namespace"] = this.$route.params.namespace;
                    queryFilter["flowId"] = this.$route.params.id;
                }

                return _merge(base, queryFilter)
            },
            loadData(callback) {
                if (this.embed === false) {
                    this.dailyReady = false;

                    this.$store
                        .dispatch("stat/daily", this.loadQuery({
                            startDate: this.$moment(this.startDate).add(-1, "day").startOf("day").toISOString(true),
                            endDate: this.$moment(this.endDate).endOf("day").toISOString(true)
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
            durationFrom(item) {
                return (+new Date() - new Date(item.state.startDate).getTime()) / 1000
            },
            restartExecutions() {
                this.$toast().confirm(
                    this.$t("bulk restart", {"executionCount": this.queryBulkAction ? this.total : this.executionsSelection.length}),
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
                                .dispatch("execution/bulkRestartExecution", {executionsId: this.executionsSelection})
                                .then(r => {
                                    this.$toast().success(this.$t("executions restarted", {executionCount: r.data.count}));
                                    this.loadData();
                                }).catch(e => this.$toast().error(e.invalids.map(exec => {
                                    return {message: this.$t(exec.message, {executionId: exec.invalidValue})}
                                }), this.$t(e.message)))
                        }
                    },
                    () => {}
                )
            },
            deleteExecutions() {
                this.$toast().confirm(
                    this.$t("bulk delete", {"executionCount": this.queryBulkAction ? this.total : this.executionsSelection.length}),
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
                                .dispatch("execution/bulkDeleteExecution", {executionsId: this.executionsSelection})
                                .then(r => {
                                    this.$toast().success(this.$t("executions deleted", {executionCount: r.data.count}));
                                    this.loadData();
                                }).catch(e => this.$toast().error(e.invalids.map(exec => {
                                    return {message: this.$t(exec.message, {executionId: exec.invalidValue})}
                                }), this.$t(e.message)))
                        }
                    },
                    () => {}
                )
            },
            killExecutions() {
                this.$toast().confirm(
                    this.$t("bulk kill", {"executionCount": this.queryBulkAction ? this.total : this.executionsSelection.length}),
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
                                .dispatch("execution/bulkKill", {executionsId: this.executionsSelection})
                                .then(r => {
                                    this.$toast().success(this.$t("executions killed", {executionCount: r.data.count}));
                                    this.loadData();
                                }).catch(e => this.$toast().error(e.invalids.map(exec => {
                                    return {message: this.$t(exec.message, {executionId: exec.invalidValue})}
                                }), this.$t(e.message)))
                        }
                    },
                    () => {}
                )
            },
            editFlow() {
                this.$router.push({name:"flows/update", params: {
                    namespace: this.flow.namespace,
                    id: this.flow.id,
                    tab: "editor"
                }})
            },
        }
    };
</script>