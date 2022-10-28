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
                    :value="$route.query.state"
                    @input="onDataTableValue('state', $event)"
                />
                <date-range
                    :start-date="$route.query.startDate"
                    :end-date="$route.query.endDate"
                    @input="onDataTableValue($event)"
                />
                <refresh-button class="float-right" @onRefresh="load" />
            </template>

            <template #top>
                <state-global-chart
                    v-if="taskRunDaily"
                    :ready="dailyReady"
                    :data="taskRunDaily"
                    class="mb-4"
                />
            </template>

            <template #table>
                <b-table
                    :no-local-sorting="true"
                    @sort-changed="onSort"
                    :responsive="true"
                    striped
                    hover
                    sort-by="taskRunList.state.startDate"
                    sort-desc
                    :items="taskruns"
                    :fields="fields"
                    @row-dblclicked="onRowDoubleClick"
                    show-empty
                >
                    <template #empty>
                        <span class="text-muted">{{ $t('no result') }}</span>
                    </template>
                    <template #cell(details)="row">
                        <router-link
                            :to="{name: 'executions/update', params: {namespace: row.item.namespace, flowId: row.item.flowId, id: row.item.executionId, tab:'gantt'}}"
                        >
                            <kicon :tooltip="$t('details')" placement="left">
                                <eye />
                            </kicon>
                        </router-link>
                    </template>
                    <!-- eslint-disable-next-line -->
                    <template #cell(taskRunList.state.startDate)="row">
                        <date-ago :inverted="true" :date="row.item.state.startDate" />
                    </template>
                    <!-- eslint-disable-next-line -->
                    <template #cell(taskRunList.state.endDate)="row">
                        <span v-if="!isRunning(row.item)">
                            <date-ago :inverted="true" :date="row.item.state.endDate" />
                        </span>
                    </template>
                    <!-- eslint-disable-next-line -->
                    <template #cell(taskRunList.state.current)="row">
                        <status
                            class="status"
                            :status="row.item.state.current"
                            size="sm"
                        />
                    </template>
                    <!-- eslint-disable-next-line -->
                    <template #cell(taskRunList.state.duration)="row">
                        <span v-if="isRunning(row.item)">
                            {{ durationFrom(row.item) | humanizeDuration }}
                        </span>
                        <span v-else>
                            {{ row.item.state.duration | humanizeDuration }}
                        </span>
                    </template>
                    <!-- eslint-disable-next-line -->
                    <template #cell(taskRunList.flowId.keyword)="row">
                        <router-link
                            :to="{name: 'flows/update', params: {namespace: row.item.namespace, id: row.item.flowId}}"
                        >
                            {{ row.item.flowId }}
                        </router-link>
                    </template>
                    <!-- eslint-disable-next-line -->
                    <template #cell(taskRunList.namespace.keyword)="row">
                        <router-link
                            :to="{name: 'taskruns/list', query: {namespace: row.item.namespace}}"
                        >
                            {{ row.item.namespace }}
                        </router-link>
                    </template>
                    <template #cell(id)="row">
                        <id :value="row.item.id" :shrink="true" />
                    </template>
                    <template #cell(executionId)="row">
                        <id :value="row.item.executionId" :shrink="true" />
                    </template>
                    <template #cell(taskId)="row">
                        <id :value="row.item.taskId + row.item.taskId + row.item.taskId + row.item.taskId" :shrink="true" :size="25" />
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
            fields() {
                const title = title => {
                    return this.$t(title);
                };
                return [
                    {
                        key: "executionId",
                        label: title("execution"),
                    },
                    {
                        key: "taskId",
                        label: title("task")
                    },
                    {
                        key: "id",
                        label: title("id")
                    },
                    {
                        key: "taskRunList.state.startDate",
                        label: title("start date"),
                        sortable: true,
                    },
                    {
                        key: "taskRunList.state.endDate",
                        label: title("end date"),
                        sortable: true,
                    },
                    {
                        key: "taskRunList.state.duration",
                        label: title("duration"),
                        sortable: true,
                    },
                    {
                        key: "taskRunList.namespace.keyword",
                        label: title("namespace"),
                        sortable: true,
                    },
                    {
                        key: "taskRunList.flowId.keyword",
                        label: title("flow"),
                        sortable: true,
                    },
                    {
                        key: "taskRunList.state.current",
                        label: title("state"),
                        class: "text-center",
                        sortable: true,
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
