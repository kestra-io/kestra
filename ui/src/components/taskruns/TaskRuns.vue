<template>
    <div v-if="ready">
        <data-table @page-changed="onPageChanged" ref="dataTable" :total="total" :max="maxTaskRunSetting">
            <template #navbar>
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

            <template #top>
                <state-global-chart
                    v-if="taskRunDaily"
                    class="mb-4"
                    :ready="dailyReady"
                    :data="taskRunDaily"
                />
            </template>

            <template #table>
                <el-table
                    :data="taskruns"
                    ref="table"
                    :default-sort="{prop: 'state.startDate', order: 'descending'}"
                    stripe
                    table-layout="auto"
                    fixed
                    @row-dblclick="onRowDoubleClick"
                    @sort-change="onSort"
                >
                    <el-table-column prop="executionId" :label="$t('execution')">
                        <template #default="scope">
                            <id :value="scope.row.executionId" :shrink="true" />
                        </template>
                    </el-table-column>

                    <el-table-column prop="taskId" :label="$t('task')">
                        <template #default="scope">
                            <id :value="scope.row.taskId" :shrink="true" />
                        </template>
                    </el-table-column>

                    <el-table-column prop="id" sortable="custom" :sort-orders="['ascending', 'descending']" :label="$t('id')">
                        <template #default="scope">
                            <id :value="scope.row.id" :shrink="true" />
                        </template>
                    </el-table-column>

                    <el-table-column prop="taskRunList.state.startDate" sortable="custom" :sort-orders="['ascending', 'descending']" :label="$t('start date')">
                        <template #default="scope">
                            <date-ago :inverted="true" :date="scope.row.state.startDate" />
                        </template>
                    </el-table-column>

                    <el-table-column prop="state.endDate" sortable="custom" :sort-orders="['ascending', 'descending']" :label="$t('end date')">
                        <template #default="scope">
                            <date-ago :inverted="true" :date="scope.row.state.endDate" />
                        </template>
                    </el-table-column>

                    <el-table-column prop="state.duration" sortable="custom" :sort-orders="['ascending', 'descending']" :label="$t('duration')">
                        <template #default="scope">
                            <span v-if="isRunning(scope.row)">{{ $filters.humanizeDuration(durationFrom(scope.row)) }}</span>
                            <span v-else>{{ $filters.humanizeDuration(scope.row.state.duration) }}</span>
                        </template>
                    </el-table-column>

                    <el-table-column v-if="$route.name !== 'flows/update'" prop="namespace" sortable="custom" :sort-orders="['ascending', 'descending']" :label="$t('namespace')" />

                    <el-table-column v-if="$route.name !== 'flows/update'" prop="flowId" sortable="custom" :sort-orders="['ascending', 'descending']" :label="$t('flow')">
                        <template #default="scope">
                            <router-link :to="{name: 'flows/update', params: {namespace: scope.row.namespace, id: scope.row.flowId}}">
                                {{ scope.row.flowId }}
                            </router-link>
                        </template>
                    </el-table-column>

                    <el-table-column prop="state.current" sortable="custom" :sort-orders="['ascending', 'descending']" :label="$t('state')">
                        <template #default="scope">
                            <status :status="scope.row.state.current" size="small" />
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
    </div>
</template>

<script>
    import {mapState} from "vuex";
    import DataTable from "../layout/DataTable";
    import Eye from "vue-material-design-icons/Eye";
    import Status from "../Status";
    import RouteContext from "../../mixins/routeContext";
    import DataTableActions from "../../mixins/dataTableActions";
    import SearchField from "../layout/SearchField";
    import NamespaceSelect from "../namespace/NamespaceSelect";
    import DateRange from "../layout/DateRange";
    import RefreshButton from "../layout/RefreshButton";
    import StatusFilterButtons from "../layout/StatusFilterButtons";
    import StateGlobalChart from "../../components/stats/StateGlobalChart";
    import DateAgo from "../layout/DateAgo";
    import Kicon from "../Kicon"
    import RestoreUrl from "../../mixins/restoreUrl";
    import State from "../../utils/state";
    import Id from "../Id";
    import _merge from "lodash/merge";

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
            DateAgo,
            Kicon,
            Id
        },
        data() {
            return {
                dailyReady: false,
                isDefaultNamespaceAllow: true,
            };
        },
        computed: {
            ...mapState("taskrun", ["taskruns", "total", "maxTaskRunSetting"]),
            ...mapState("stat", ["taskRunDaily"]),
            routeInfo() {
                return {
                    title: this.$t("taskruns")
                };
            },
            endDate() {
                return new Date();
            },
            startDate() {
                return this.$moment(this.endDate)
                    .add(-30, "days")
                    .toDate();
            }
        },
        created() {
            this.$store.dispatch("taskrun/maxTaskRunSetting");
        },
        methods: {
            isRunning(item){
                return State.isRunning(item.state.current);
            },
            onRowDoubleClick(item) {
                this.$router.push({
                    name: "executions/update",
                    params: {namespace: item.namespace, flowId: item.flowId, id: item.executionId, tab: "gantt"},
                });
            },
            loadQuery(base, stats) {
                let queryFilter = this.queryWithFilter();

                if (stats) {
                    delete queryFilter["startDate"];
                    delete queryFilter["endDate"];
                }

                return _merge(base, queryFilter)
            },
            loadData(callback) {
                this.$store
                    .dispatch("stat/taskRunDaily", this.loadQuery({
                        startDate: this.$moment(this.startDate).startOf("day").add(-1, "day").toISOString(true),
                        endDate: this.$moment(this.endDate).endOf("day").toISOString(true)
                    }, true))
                    .then(() => {
                        this.dailyReady = true;
                    });

                this.$store
                    .dispatch("taskrun/findTaskRuns", this.loadQuery({
                        size: parseInt(this.$route.query.size || 25),
                        page: parseInt(this.$route.query.page || 1),
                        state: this.$route.query.state ? [this.$route.query.state] : this.statuses
                    }, false))
                    .finally(callback);
            },
            durationFrom(item) {
                return (+new Date() - new Date(item.state.startDate).getTime()) / 1000;
            }
        }
    };
</script>
