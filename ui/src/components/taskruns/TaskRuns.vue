<template>
    <div v-if="ready">
        <data-table @onPageChanged="onPageChanged" ref="dataTable" :total="total" :max="maxTaskRunSetting">
            <template #navbar>
                <search-field />
                <namespace-select
                    data-type="flow"
                    v-if="$route.name !== 'flows/update'"
                    :value="$route.query.namespace"
                    @input="onDataTableValue('namespace', $event)"
                />
                <status-filter-buttons
                    :value="$route.query.status"
                    @input="onDataTableValue('status', $event)"
                />
                <date-range
                    :start="$route.query.start"
                    @start="onDataTableValue('start', $event)"
                    :end="$route.query.end"
                    @end="onDataTableValue('end', $event)"
                />
                <refresh-button class="float-right" @onRefresh="load" />
            </template>

            <template #top>
                <state-global-chart
                    v-if="taskRunDaily"
                    :ready="dailyReady"
                    :data="taskRunDaily"
                />
            </template>

            <template #table>
                <b-table
                    :no-local-sorting="true"
                    @sort-changed="onSort"
                    responsive
                    striped
                    hover
                    bordered
                    :items="taskruns"
                    :fields="fields"
                    @row-dblclicked="onRowDoubleClick"
                    show-empty
                >
                    <template #empty>
                        <span class="text-black-50">{{ $t('no result') }}</span>
                    </template>
                    <template #cell(details)="row">
                        <router-link
                            :to="{name: 'executions/update', params: {namespace: row.item.namespace, flowId: row.item.flowId, id: row.item.executionId},query: {tab:'gantt'}}"
                        >
                            <kicon :tooltip="$t('details')" placement="left">
                                <eye />
                            </kicon>
                        </router-link>
                    </template>
                    <template #cell(startDate)="row">
                        <date-ago :inverted="true" :date="row.item.state.startDate" />
                    </template>
                    <template #cell(endDate)="row">
                        <span v-if="!isRunning(row.item)">
                            <date-ago :inverted="true" :date="row.item.state.endDate" />
                        </span>
                    </template>
                    <template #cell(current)="row">
                        <status
                            class="status"
                            :status="row.item.state.current"
                            size="sm"
                        />
                    </template>
                    <template #cell(duration)="row">
                        <span v-if="isRunning(row.item)">
                            {{ durationFrom(row.item) | humanizeDuration }}
                        </span>
                        <span v-else>
                            {{ row.item.state.duration | humanizeDuration }}
                        </span>
                    </template>
                    <template #cell(flowId)="row">
                        <router-link
                            :to="{name: 'flows/update', params: {namespace: row.item.namespace, id: row.item.flowId}}"
                        >
                            {{ row.item.flowId }}
                        </router-link>
                    </template>
                    <template #cell(id)="row">
                        <code>{{ row.item.id | id }}</code>
                    </template>
                    <template #cell(executionId)="row">
                        <code>{{ row.item.executionId | id }}</code>
                    </template>
                    <template #cell(taskId)="row">
                        <code v-b-tooltip.hover :title="row.item.taskId">{{ row.item.taskId | ellipsis(25) }} </code>
                    </template>
                </b-table>
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
    import qb from "../../utils/queryBuilder";

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
            Kicon
        },
        data() {
            return {
                dailyReady: false,
                isDefaultNamespaceAllow: true,
            };
        },
        beforeMount() {
            if (this.$route.query.sort === undefined) {
                this.$router.push({
                    query: {...this.$route.query, ...{sort: "taskRunList.state.startDate:desc"}}
                });
            }
        },
        computed: {
            ...mapState("taskrun", ["taskruns", "total", "maxTaskRunSetting"]),
            ...mapState("stat", ["taskRunDaily"]),
            routeInfo() {
                return {
                    title: this.$t("taskruns")
                };
            },
            fields() {
                const title = title => {
                    return this.$t(title);
                };
                return [
                    {
                        key: "taskId",
                        label: title("task")
                    },
                    {
                        key: "id",
                        label: title("id")
                    },
                    {
                        key: "executionId",
                        label: title("execution"),
                    },
                    {
                        key: "startDate",
                        label: title("start date"),
                        sortable: true,
                        sortKey: "taskRunList.state.startDate"
                    },
                    {
                        key: "endDate",
                        label: title("end date"),
                        sortable: true,
                        sortKey: "taskRunList.state.endDate"
                    },
                    {
                        key: "duration",
                        label: title("duration"),
                        sortable: true,
                        sortKey: "taskRunList.state.duration"
                    },
                    {
                        key: "namespace",
                        label: title("namespace"),
                        sortable: true,
                        sortKey: "taskRunList.namespace.keyword"
                    },
                    {
                        key: "flowId",
                        label: title("flow"),
                        sortable: true,
                        sortKey: "taskRunList.flowId.keyword"
                    },
                    {
                        key: "current",
                        label: title("state"),
                        class: "text-center",
                        sortable: true,
                        sortKey: "taskRunList.state.current"
                    },
                    {
                        key: "details",
                        label: "",
                        class: "row-action"
                    }
                ];
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
        methods: {
            isRunning(item){
                return State.isRunning(item.state.current);
            },
            onRowDoubleClick(item) {
                this.$router.push({
                    name: "executions/update",
                    params: {namespace: item.namespace, flowId: item.flowId, id: item.executionId},
                    query: {tab: "gantt"}
                });
            },
            loadQuery() {
                let filter = []
                let query = this.queryWithFilter();

                if (query.namespace) {
                    filter.push(`taskRunList.namespace:${query.namespace}*`)
                }

                if (query.q) {
                    filter.push(qb.toLucene(query.q));
                }

                if (query.start) {
                    filter.push(`taskRunList.state.startDate:[${query.start} TO *]`)
                }

                if (query.end) {
                    filter.push(`taskRunList.state.endDate:[* TO ${query.end}]`)
                }

                return filter.join(" AND ") || "*"
            },
            loadData(callback) {
                this.$store
                    .dispatch("stat/taskRunDaily", {
                        q: this.loadQuery(),
                        startDate: this.$moment(this.startDate).startOf("day").toISOString(true),
                        endDate: this.$moment(this.endDate).endOf("day").toISOString(true)
                    })
                    .then(() => {
                        this.dailyReady = true;
                    });

                this.$store.dispatch("taskrun/maxTaskRunSetting");

                this.$store
                    .dispatch("taskrun/findTaskRuns", {
                        size: parseInt(this.$route.query.size || 25),
                        page: parseInt(this.$route.query.page || 1),
                        q: this.loadQuery(),
                        sort: this.$route.query.sort,
                        state: this.$route.query.status
                    })
                    .finally(callback);
            },
            durationFrom(item) {
                return (+new Date() - new Date(item.state.startDate).getTime()) / 1000;
            }
        }
    };
</script>
