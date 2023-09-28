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
                        :value="Utils.asArray($route.query.state)"
                        @update:model-value="onDataTableValue('state', $event)"
                    />
                </el-form-item>
                <el-form-item>
                    <date-range
                        :start-date="startDate"
                        :end-date="endDate"
                        @update:model-value="onDataTableValue($event)"
                    />
                </el-form-item>
                <el-form-item>
                    <label-filter
                        :model-value="$route.query.labels"
                        @update:model-value="onDataTableValue('labels', $event)"
                    />
                </el-form-item>
                <el-form-item>
                    <refresh-button class="float-right" @refresh="refresh" />
                </el-form-item>
            </template>

            <template #top v-if="embed === false">
                <state-global-chart
                    v-if="daily"
                    class="mb-4"
                    :ready="dailyReady"
                    :data="daily"
                    :start-date="startDate"
                    :end-date="endDate"
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
                    :selectable="!hidden.includes('selection') && canCheck"
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
                        </bulk-select>
                    </template>
                    <template #default>
                        <el-table-column prop="id" v-if="!hidden.includes('id')" sortable="custom"
                                         :sort-orders="['ascending', 'descending']" :label="$t('id')">
                            <template #default="scope">
                                <id :value="scope.row.id" :shrink="true" />
                            </template>
                        </el-table-column>

                        <el-table-column prop="state.startDate" v-if="!hidden.includes('state.startDate')"
                                         sortable="custom"
                                         :sort-orders="['ascending', 'descending']" :label="$t('start date')">
                            <template #default="scope">
                                <date-ago :inverted="true" :date="scope.row.state.startDate" />
                            </template>
                        </el-table-column>

                        <el-table-column prop="state.endDate" v-if="!hidden.includes('state.endDate')" sortable="custom"
                                         :sort-orders="['ascending', 'descending']" :label="$t('end date')">
                            <template #default="scope">
                                <date-ago :inverted="true" :date="scope.row.state.endDate" />
                            </template>
                        </el-table-column>

                        <el-table-column prop="state.duration" v-if="!hidden.includes('state.duration')"
                                         sortable="custom"
                                         :sort-orders="['ascending', 'descending']" :label="$t('duration')">
                            <template #default="scope">
                                <span v-if="isRunning(scope.row)">{{
                                        $filters.humanizeDuration(durationFrom(scope.row))
                                    }}</span>
                                <span v-else>{{ $filters.humanizeDuration(scope.row.state.duration) }}</span>
                            </template>
                        </el-table-column>

                        <el-table-column v-if="$route.name !== 'flows/update' && !hidden.includes('namespace')"
                                         prop="namespace"
                                         sortable="custom" :sort-orders="['ascending', 'descending']"
                                         :label="$t('namespace')"
                                         :formatter="(_, __, cellValue) => $filters.invisibleSpace(cellValue)" />

                        <el-table-column v-if="$route.name !== 'flows/update' && !hidden.includes('flowId')"
                                         prop="flowId"
                                         sortable="custom" :sort-orders="['ascending', 'descending']"
                                         :label="$t('flow')">
                            <template #default="scope">
                                <router-link
                                    :to="{name: 'flows/update', params: {namespace: scope.row.namespace, id: scope.row.flowId}}">
                                    {{ $filters.invisibleSpace(scope.row.flowId) }}
                                </router-link>
                            </template>
                        </el-table-column>

                        <el-table-column v-if="!hidden.includes('labels')" :label="$t('labels')">
                            <template #default="scope">
                                <labels :labels="scope.row.labels" />
                            </template>
                        </el-table-column>

                        <el-table-column prop="state.current" v-if="!hidden.includes('state.current')" sortable="custom"
                                         :sort-orders="['ascending', 'descending']" :label="$t('state')">
                            <template #default="scope">
                                <status :status="scope.row.state.current" size="small" />
                            </template>
                        </el-table-column>

                        <el-table-column prop="triggers" v-if="!hidden.includes('triggers')" :label="$t('triggers')"
                                         class-name="shrink">
                            <template #default="scope">
                                <trigger-avatar :execution="scope.row" />
                            </template>
                        </el-table-column>

                        <el-table-column column-key="action" class-name="row-action">
                            <template #default="scope">
                                <router-link
                                    :to="{name: 'executions/update', params: {namespace: scope.row.namespace, flowId: scope.row.flowId, id: scope.row.id}}">
                                    <kicon :tooltip="$t('details')" placement="left">
                                        <eye />
                                    </kicon>
                                </router-link>
                            </template>
                        </el-table-column>
                    </template>
                </select-table>
            </template>
        </data-table>

        <bottom-line v-if="displayBottomBar">
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
                        <trigger-flow v-if="flow" :disabled="flow.disabled || isReadOnly" :flow-id="flow.id"
                                      :namespace="flow.namespace" />
                    </li>
                </template>
            </ul>
        </bottom-line>
    </div>
</template>

<script setup>
    import BulkSelect from "../layout/BulkSelect.vue";
    import SelectTable from "../layout/SelectTable.vue";
    import Restart from "vue-material-design-icons/Restart.vue";
    import Delete from "vue-material-design-icons/Delete.vue";
    import StopCircleOutline from "vue-material-design-icons/StopCircleOutline.vue";
    import Pencil from "vue-material-design-icons/Pencil.vue";
    import Utils from "../../utils/utils";
</script>

<script>
    import {mapState} from "vuex";
    import DataTable from "../layout/DataTable.vue";
    import Eye from "vue-material-design-icons/Eye.vue";
    import Status from "../Status.vue";
    import RouteContext from "../../mixins/routeContext";
    import DataTableActions from "../../mixins/dataTableActions";
    import SelectTableActions from "../../mixins/selectTableActions";
    import SearchField from "../layout/SearchField.vue";
    import NamespaceSelect from "../namespace/NamespaceSelect.vue";
    import LabelFilter from "../labels/LabelFilter.vue";
    import DateRange from "../layout/DateRange.vue";
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
    import BottomLine from "../layout/BottomLine.vue";
    import permission from "../../models/permission";
    import action from "../../models/action";
    import TriggerFlow from "../../components/flows/TriggerFlow.vue";

    export default {
        mixins: [RouteContext, RestoreUrl, DataTableActions, SelectTableActions],
        components: {
            Status,
            Eye,
            DataTable,
            SearchField,
            NamespaceSelect,
            LabelFilter,
            DateRange,
            RefreshButton,
            StatusFilterButtons,
            StateGlobalChart,
            TriggerAvatar,
            DateAgo,
            Kicon,
            Labels,
            Id,
            BottomLine,
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
            isReadOnly: {
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
                recomputeInterval: false
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
                // used to be able to force refresh the base interval when auto-reloading
                this.recomputeInterval;
                return this.$route.query.endDate ? this.$route.query.endDate : this.$moment().toISOString(true);
            },
            startDate() {
                // used to be able to force refresh the base interval when auto-reloading
                this.recomputeInterval;
                return this.$route.query.startDate ? this.$route.query.startDate : this.$moment(this.endDate)
                    .add(-30, "days").toISOString(true);
            },
            displayBottomBar() {
                return (this.$route.name === "flows/update");
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
            }
        },
        methods: {
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

                if ((!queryFilter["startDate"] || !queryFilter["endDate"]) && !stats) {
                    queryFilter["startDate"] = this.startDate;
                    queryFilter["endDate"] = this.endDate;
                }

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
            editFlow() {
                this.$router.push({
                    name: "flows/update", params: {
                        namespace: this.flow.namespace,
                        id: this.flow.id,
                        tab: "editor"
                    }
                })
            },
        }
    };
</script>