<template>
    <div v-if="ready">
        <data-table @onPageChanged="onPageChanged" ref="dataTable" :total="total" :max="maxTaskRunSetting">
            <template v-slot:navbar>
                <search-field ref="searchField" @onSearch="onSearch" :fields="searchableFields" />
                <namespace-select
                    data-type="flow"
                    v-if="$route.name !== 'flowEdit'"
                    @onNamespaceSelect="onNamespaceSelect"
                />
                <status-filter-buttons @onRefresh="loadData" />
                <date-range @onDate="onSearch" />
                <refresh-button class="float-right" @onRefresh="loadData" />
            </template>

            <template v-slot:top>
                <state-global-chart
                    v-if="taskRunDaily"
                    :ready="dailyReady"
                    :data="taskRunDaily"
                />
            </template>

            <template v-slot:table>
                <b-table
                    :no-local-sorting="true"
                    @sort-changed="onSort"
                    responsive="xl"
                    striped
                    hover
                    bordered
                    :items="taskruns"
                    :fields="fields"
                    @row-dblclicked="onRowDoubleClick"
                >
                    <template #empty>
                        <span class="text-black-50">{{ $t('no result') }}</span>
                    </template>
                    <template v-slot:cell(details)="row">
                        <router-link
                            :to="{name: 'executionEdit', params: {namespace: row.item.namespace, flowId: row.item.flowId, id: row.item.executionId},query: {tab:'gantt'}}"
                        >
                            <eye id="edit-action" />
                        </router-link>
                    </template>
                    <template v-slot:cell(state.startDate)="row">
                        <date-ago :date="row.item.state.startDate" />
                    </template>
                    <template v-slot:cell(state.endDate)="row">
                        <span v-if="!isRunning(row.item)">
                            <date-ago :date="row.item.state.endDate" />
                        </span>
                    </template>
                    <template v-slot:cell(state.current)="row">
                        <status
                            class="status"
                            :status="row.item.state.current"
                            size="sm"
                        />
                    </template>
                    <template v-slot:cell(state.duration)="row">
                        <span
                            v-if="isRunning(row.item)"
                        >{{ durationFrom(row.item) | humanizeDuration }}</span>
                        <span v-else>{{ row.item.state.duration | humanizeDuration }}</span>
                    </template>
                    <template v-slot:cell(flowId)="row">
                        <router-link
                            :to="{name: 'flowEdit', params: {namespace: row.item.namespace, id: row.item.flowId}}"
                        >
                            {{ row.item.flowId }}
                        </router-link>
                    </template>
                    <template v-slot:cell(id)="row">
                        <code>{{ row.item.id | id }}</code>
                    </template>
                    <template v-slot:cell(executionId)="row">
                        <code>{{ row.item.executionId | id }}</code>
                    </template>
                    <template v-slot:cell(taskId)="row">
                        <code v-b-tooltip.hover :title="row.item.taskId">{{ row.item.taskId | ellipsis(25) }} </code>
                    </template>
                    <template v-slot:cell(executionId)="row">
                        <code>{{ row.item.executionId | id }}</code>
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

    export default {
        mixins: [RouteContext, DataTableActions],
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
            DateAgo
        },
        data() {
            return {
                dataType: "taskrun",
                dailyReady: false
            };
        },
        beforeCreate() {
            const queries = JSON.parse(
                localStorage.getItem("taskrunQueries") || "{}"
            );
            queries.sort = queries.sort ? queries.sort : "taskRunList.state.startDate:desc";
            queries.status = this.$route.query.status || queries.status || "ALL";
            if (!this.$route.query.sort) {
                this.$router.push({
                    name: this.$route.name,
                    query: {...this.$route.query, ...queries}
                });
            }
            localStorage.setItem("taskrunQueries", JSON.stringify(queries));
        },
        computed: {
            ...mapState("taskrun", ["taskruns", "total", "maxTaskRunSetting"]),
            ...mapState("stat", ["taskRunDaily"]),
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
                        label: title("execution")
                    },
                    {
                        key: "state.startDate",
                        label: title("start date"),
                        sortable: true,
                        sortKey: "taskRunList.state.startDate"
                    },
                    {
                        key: "state.endDate",
                        label: title("end date"),
                        sortable: true,
                        sortKey: "taskRunList.state.endDate"
                    },
                    {
                        key: "state.duration",
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
                        key: "state.current",
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
            executionQuery() {
                let query = this.query.replace("namespace:", "taskRunList.namespace:");
                query = query.replace("state.startDate:", "taskRunList.state.startDate:");
                query = query.replace("state.endDate:", "taskRunList.state.endDate:");
                return query;
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
            onRowDoubleClick(item) {
                this.$router.push({
                    name: "executionEdit",
                    params: {namespace: item.namespace, flowId: item.flowId, id: item.executionId},
                    query: {tab: "gantt"}
                });
            },
            loadData(callback) {
                this.$store
                    .dispatch("stat/taskRunDaily", {
                        q: this.executionQuery,
                        startDate: this.$moment(this.startDate).format("YYYY-MM-DD"),
                        endDate: this.$moment(this.endDate).format("YYYY-MM-DD")
                    })
                    .then(() => {
                        this.dailyReady = true;
                    });

                this.$store.dispatch("taskrun/maxTaskRunSetting");

                this.$store
                    .dispatch("taskrun/findTaskRuns", {
                        size: parseInt(this.$route.query.size || 25),
                        page: parseInt(this.$route.query.page || 1),
                        q: this.executionQuery,
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
