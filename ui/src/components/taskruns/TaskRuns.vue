<template>
    <top-nav-bar :title="routeInfo.title" />
    <div class="mt-3" v-if="ready">
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
                        :value="Utils.asArray($route.query.state)"
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
                        :model-value="$route.query.childFilter"
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
                    <refresh-button class="float-right" @refresh="load" />
                </el-form-item>
            </template>

            <template #top>
                <state-global-chart
                    v-if="taskRunDaily"
                    class="mb-4"
                    :ready="dailyReady"
                    :data="taskRunDaily"
                    :start-date="startDate"
                    :end-date="endDate"
                    :type="stateGlobalChartTypes.TASKRUNS"
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
                            <id :value="scope.row.executionId" :shrink="true" @click="onRowDoubleClick(scope.row)" />
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

                    <el-table-column prop="attempts" :label="$t('attempts')">
                        <template #default="scope">
                            {{ scope.row.attempts ? scope.row.attempts.length : 0 }}
                        </template>
                    </el-table-column>

                    <el-table-column prop="state.current" sortable="custom" :sort-orders="['ascending', 'descending']" :label="$t('state')">
                        <template #default="scope">
                            <status :status="scope.row.state.current" size="small" />
                        </template>
                    </el-table-column>

                    <el-table-column column-key="action" class-name="row-action">
                        <template #default="scope">
                            <router-link :to="{name: 'executions/update', params: {namespace: scope.row.namespace, flowId: scope.row.flowId, id: scope.row.executionId}}">
                                <kicon :tooltip="$t('details')" placement="left">
                                    <TextSearch />
                                </kicon>
                            </router-link>
                        </template>
                    </el-table-column>
                </el-table>
            </template>
        </data-table>
    </div>
</template>
<script setup>
    import Utils from "../../utils/utils";
</script>
<script>
    import {mapState} from "vuex";
    import DataTable from "../layout/DataTable.vue";
    import TextSearch from "vue-material-design-icons/TextSearch.vue";
    import Status from "../Status.vue";
    import RouteContext from "../../mixins/routeContext";
    import TopNavBar from "../../components/layout/TopNavBar.vue";
    import DataTableActions from "../../mixins/dataTableActions";
    import SearchField from "../layout/SearchField.vue";
    import NamespaceSelect from "../namespace/NamespaceSelect.vue";
    import DateRange from "../layout/DateRange.vue";
    import RefreshButton from "../layout/RefreshButton.vue";
    import StatusFilterButtons from "../layout/StatusFilterButtons.vue";
    import StateGlobalChart from "../../components/stats/StateGlobalChart.vue";
    import DateAgo from "../layout/DateAgo.vue";
    import Kicon from "../Kicon.vue"
    import RestoreUrl from "../../mixins/restoreUrl";
    import State from "../../utils/state";
    import Id from "../Id.vue";
    import _merge from "lodash/merge";
    import {stateGlobalChartTypes} from "../../utils/constants";
    import LabelFilter from "../labels/LabelFilter.vue";

    export default {
        mixins: [RouteContext, RestoreUrl, DataTableActions],
        components: {
            Status,
            TextSearch,
            DataTable,
            SearchField,
            NamespaceSelect,
            DateRange,
            RefreshButton,
            StatusFilterButtons,
            StateGlobalChart,
            DateAgo,
            Kicon,
            Id,
            LabelFilter,
            TopNavBar
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
            stateGlobalChartTypes() {
                return stateGlobalChartTypes;
            },
            endDate() {
                return this.$route.query.endDate ? this.$route.query.endDate : this.$moment(this.endDate).toISOString(true);
            },
            startDate() {
                return  this.$route.query.startDate ?  this.$route.query.startDate : this.$moment(this.endDate)
                    .add(-30, "days").toISOString(true);
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
                    params: {
                        namespace: item.namespace,
                        flowId: item.flowId,
                        id: item.executionId,
                        tab: "gantt",
                        tenant: this.$route.params.tenant
                    },
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
                        startDate: this.startDate,
                        endDate: this.endDate
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
