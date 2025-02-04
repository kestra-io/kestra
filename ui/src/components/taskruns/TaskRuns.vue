<template>
    <top-nav-bar :title="routeInfo.title" />
    <section class="container" v-if="ready">
        <data-table @page-changed="onPageChanged" ref="dataTable" :total="total">
            <template #navbar>
                <KestraFilter
                    prefix="taskruns"
                    :include="['namespace', 'state', 'scope', 'labels', 'child', 'relative_date', 'absolute_date']"
                    :buttons="{
                        refresh: {shown: true, callback: load},
                        settings: {shown: true, charts: {shown: true, value: showChart, callback: onShowChartChange}}
                    }"
                />
            </template>

            <template #top>
                <el-card v-if="showStatChart()" shadow="never" class="mb-4">
                    <ExecutionsBar v-if="taskRunDaily" :data="taskRunDaily" :total="executionsCount" />
                </el-card>
            </template>

            <template #table>
                <el-table
                    :data="taskruns"
                    ref="table"
                    :default-sort="{prop: 'state.startDate', order: 'descending'}"
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
    </section>
</template>
<script setup>
    import KestraFilter from "../filter/KestraFilter.vue";
</script>
<script>
    import {mapState} from "vuex";
    import DataTable from "../layout/DataTable.vue";
    import TextSearch from "vue-material-design-icons/TextSearch.vue";
    import Status from "../Status.vue";
    import RouteContext from "../../mixins/routeContext";
    import TopNavBar from "../../components/layout/TopNavBar.vue";
    import DataTableActions from "../../mixins/dataTableActions";
    import DateAgo from "../layout/DateAgo.vue";
    import Kicon from "../Kicon.vue"
    import RestoreUrl from "../../mixins/restoreUrl";
    import {State} from "@kestra-io/ui-libs"
    import Id from "../Id.vue";
    import _merge from "lodash/merge";
    import {stateGlobalChartTypes, storageKeys} from "../../utils/constants";
    import ExecutionsBar from "../../components/dashboard/components/charts/executions/Bar.vue"

    export default {
        mixins: [RouteContext, RestoreUrl, DataTableActions],
        components: {
            Status,
            TextSearch,
            DataTable,
            DateAgo,
            Kicon,
            Id,
            TopNavBar,
            ExecutionsBar
        },
        data() {
            return {
                dailyReady: false,
                isDefaultNamespaceAllow: true,
                canAutoRefresh: false,
                lastRefreshDate: new Date(),
                showChart: ["true", null].includes(localStorage.getItem(storageKeys.SHOW_CHART)),
            };
        },
        computed: {
            ...mapState("taskrun", ["taskruns", "total"]),
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
                if (this.$route.query.endDate) {
                    return this.$route.query.endDate;
                }
                return undefined;
            },
            startDate() {
                // probable hack to trigger cache invalidation without date change
                if (this.$route.query.startDate && this.lastRefreshDate) {
                    return this.$route.query.startDate;
                }
                if (this.$route.query.timeRange) {
                    return this.$moment().subtract(this.$moment.duration(this.$route.query.timeRange).as("milliseconds")).toISOString(true);
                }

                // the default is PT30D
                return this.$moment().subtract(30, "days").toISOString(true);
            },
            executionsCount() {
                return [...this.taskRunDaily].reduce((a, b) => {
                    return a + Object.values(b.executionCounts).reduce((a, b) => a + b, 0);
                }, 0);
            },
        },
        methods: {
            onDateFilterTypeChange(event) {
                this.canAutoRefresh = event;
            },
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
            onShowChartChange(value) {
                this.showChart = value;
                localStorage.setItem(storageKeys.SHOW_CHART, value);

                if (this.showChart) {
                    this.loadData();
                }
            },
            showStatChart() {
                return this.showChart;
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
                this.lastRefreshDate = new Date();
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
