<template>
    <data-table @page-changed="onPageChanged" :size="pageSize" :page="pageNumber" :total="total">
        <template #table>
            <el-table
                :data="flows"
                ref="table"
                :default-sort="{prop: 'id', order: 'ascending'}"
                table-layout="auto"
                fixed
                @sort-change="onSort"
            >
                <el-table-column
                    prop="id"
                    sortable="custom"
                    :sort-orders="['ascending', 'descending']"
                    :label="$t('id')"
                >
                    <template #default="scope">
                        <router-link
                            :to="{name: 'flows/update', params: {namespace: scope.row.namespace, id: scope.row.id}}"
                        >
                            {{ $filters.invisibleSpace(scope.row.id) }}
                        </router-link>
                        &nbsp;<markdown-tooltip
                            :id="scope.row.namespace + '-' + scope.row.id"
                            :description="scope.row.description"
                            :title="scope.row.namespace + '.' + scope.row.id"
                        />
                    </template>
                </el-table-column>

                <el-table-column :label="$t('labels')">
                    <template #default="scope">
                        <labels :labels="scope.row.labels" />
                    </template>
                </el-table-column>

                <el-table-column
                    prop="state.startDate"
                    :label="$t('last execution date')"
                    v-if="user.hasAny(permission.EXECUTION)"
                >
                    <template #default="scope">
                        <date-ago
                            v-if="lastExecutionByFlowReady"
                            :inverted="true"
                            :date="getLastExecution(scope.row).startDate"
                        />
                    </template>
                </el-table-column>

                <el-table-column
                    prop="state.current"
                    :label="$t('last execution status')"
                    v-if="user.hasAny(permission.EXECUTION)"
                >
                    <template #default="scope">
                        <status
                            v-if="lastExecutionByFlowReady && getLastExecution(scope.row).lastStatus"
                            :status="getLastExecution(scope.row).lastStatus"
                            size="small"
                        />
                    </template>
                </el-table-column>

                <el-table-column
                    prop="state"
                    :label="$t('execution statistics')"
                    v-if="user.hasAny(permission.EXECUTION)"
                    class-name="row-graph"
                >
                    <template #default="scope">
                        <!-- TODO: Replace the usage of StateChart with one of the new chart components -->
                        <state-chart
                            :duration="true"
                            :namespace="scope.row.namespace"
                            :flow-id="scope.row.id"
                            v-if="dailyGroupByFlowReady"
                            :data="chartData(scope.row)"
                        />
                    </template>
                </el-table-column>

                <el-table-column :label="$t('triggers')" class-name="row-action">
                    <template #default="scope">
                        <trigger-avatar :flow="scope.row" />
                    </template>
                </el-table-column>

                <el-table-column column-key="action" class-name="row-action">
                    <template #default="scope">
                        <router-link
                            :to="{name: 'flows/update', params: {namespace: scope.row.namespace, id: scope.row.id}}"
                        >
                            <kicon :tooltip="$t('details')" placement="left">
                                <TextSearch />
                            </kicon>
                        </router-link>
                    </template>
                </el-table-column>
            </el-table>
        </template>
    </data-table>
</template>

<script>
    import {mapState} from "vuex";
    import permission from "../../models/permission";
    import DataTable from "../layout/DataTable.vue";
    import DataTableActions from "../../mixins/dataTableActions";
    import MarkdownTooltip from "../layout/MarkdownTooltip.vue"
    import Labels from "../layout/Labels.vue"
    import DateAgo from "../layout/DateAgo.vue";
    import Status from "../Status.vue";
    import StateChart from "../stats/StateChart.vue";
    import TriggerAvatar from "../flows/TriggerAvatar.vue";
    import Kicon from "../Kicon.vue"
    import _merge from "lodash/merge";

    import TextSearch from "vue-material-design-icons/TextSearch.vue";

    export default {
        components: {DataTable, MarkdownTooltip, Labels, DateAgo, Status, StateChart, TriggerAvatar, Kicon, TextSearch},
        mixins: [DataTableActions],
        computed: {
            ...mapState("auth", ["user"]),
            ...mapState("flow", ["flows", "total"]),
            ...mapState("stat", ["dailyGroupByFlow", "lastExecutions"]),
        },
        data() {
            return {
                permission: permission,
                dailyGroupByFlowReady: false,
                lastExecutionByFlowReady: false
            }
        },
        methods: {
            loadQuery(base) {
                return _merge(base, this.queryWithFilter())
            },
            loadData(callback) {
                const params =  {
                    namespace: this.$route.params.id,
                    page: this.$route.query.page || this.internalPageNumber,
                    size: this.$route.query.size || this.internalPageSize
                }

                this.$store
                    .dispatch("flow/findFlows", this.loadQuery(params))
                    .then((flows) => {
                        this.dailyGroupByFlowReady = false;
                        this.lastExecutionByFlowReady = false;

                        if (flows.results && flows.results.length > 0) {
                            if (this.user && this.user.hasAny(permission.EXECUTION)) {
                                this.$store
                                    .dispatch("stat/dailyGroupByFlow", {
                                        flows: flows.results
                                            .map(flow => {
                                                return {namespace: flow.namespace, id: flow.id}
                                            }),
                                        startDate: this.$moment(this.startDate).add(-1, "day").startOf("day").toISOString(true),
                                        endDate: this.$moment(this.endDate).endOf("day").toISOString(true)
                                    })
                                    .then(() => {
                                        this.dailyGroupByFlowReady = true
                                    })

                                this.$store
                                    .dispatch("stat/lastExecutions", {
                                        flows: flows.results
                                            .map(flow => {
                                                return {namespace: flow.namespace, id: flow.id}
                                            }),
                                    })
                                    .then(() => {
                                        this.lastExecutionByFlowReady = true
                                    })
                            }
                        }
                    })
                    .finally(callback);
            },
            getLastExecution(row) {
                let noState = {state: null, startDate: null}
                if (this.lastExecutions && this.lastExecutions.length > 0) {
                    let filteredFlowExec = this.lastExecutions.filter((executedFlow) => executedFlow.flowId == row.id && executedFlow.namespace == row.namespace)
                    if (filteredFlowExec.length > 0) {
                        return {
                            lastStatus: filteredFlowExec[0].state?.current,
                            startDate: filteredFlowExec[0].state?.startDate
                        }
                    }
                    return noState
                }
                else {
                    return noState
                }
            },
            chartData(row) {
                if (this.dailyGroupByFlow && this.dailyGroupByFlow[row.namespace] && this.dailyGroupByFlow[row.namespace][row.id]) {
                    return this.dailyGroupByFlow[row.namespace][row.id];
                } else {
                    return [];
                }
            },
        }
    };
</script>