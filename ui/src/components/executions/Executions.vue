<template>
    <div v-if="ready">
        <data-table @onPageChanged="onPageChanged" ref="dataTable" :total="total">
            <template v-slot:navbar>
                <search-field ref="searchField" @onSearch="onSearch" :fields="searchableFields" />
                <namespace-select v-if="$route.name !== 'flowEdit'"  @onNamespaceSelect="onNamespaceSelect" />
                <status-filter-buttons @onRefresh="loadData"/>
                <date-range @onDate="onSearch" />
                <refresh-button class="float-right" @onRefresh="loadData"/>
            </template>

            <template v-slot:top>
                <state-global-chart
                    :ready="dailyReady"
                    :endDate="endDate"
                    :startDate="startDate"
                    :data="daily"
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
                    :items="executions"
                    :fields="fields"
                    @row-dblclicked="onRowDoubleClick"
                >
                    <template v-slot:cell(details)="row">
                        <router-link :to="{name: 'executionEdit', params: row.item}">
                            <eye id="edit-action" />
                        </router-link>
                    </template>
                    <template
                        v-slot:cell(state.startDate)="row"
                    >{{row.item.state.startDate | date('YYYY/MM/DD HH:mm:ss')}}</template>
                    <template
                        v-slot:cell(state.endDate)="row"
                    >
                    <span v-if="!['RUNNING', 'CREATED'].includes(row.item.state.current)">
                        {{row.item.state.endDate | date('YYYY/MM/DD HH:mm:ss')}}
                    </span>
                    </template>
                    <template v-slot:cell(state.current)="row">
                        <status
                            @click.native="addStatusToQuery(row.item.state.current)"
                            class="status"
                            :status="row.item.state.current"
                            size="sm"
                        />
                    </template>
                     <template v-slot:cell(state.duration)="row">
                        <span v-if="['RUNNING', 'CREATED'].includes(row.item.state.current)">{{durationFrom(row.item) | humanizeDuration}}</span>
                        <span v-else>{{row.item.state.duration | humanizeDuration}}</span>
                    </template>
                    <template v-slot:cell(flowId)="row">
                        <router-link
                            :to="{name: 'flowEdit', params: {namespace: row.item.namespace, id: row.item.flowId}}"
                        >{{row.item.flowId}}</router-link>
                    </template>
                    <template v-slot:cell(id)="row">
                        <code>{{row.item.id | id}}</code>
                    </template>
                </b-table>
            </template>
        </data-table>
    </div>
</template>

<script>
import { mapState } from "vuex";
import DataTable from "../layout/DataTable";
import Eye from "vue-material-design-icons/Eye";
import Status from "../Status";
import RouteContext from "../../mixins/routeContext";
import DataTableActions from "../../mixins/dataTableActions";
import SearchField from "../layout/SearchField";
import NamespaceSelect from "../namespace/NamespaceSelect";
import DateRange from "../layout/DateRange";
import RefreshButton from '../layout/RefreshButton'
import StatusFilterButtons from '../layout/StatusFilterButtons'
import StateGlobalChart from "@/components/stats/StateGlobalChart";

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
        StateGlobalChart
    },
    data() {
        return {
            dataType: "execution",
            dailyReady: false,
        };
    },
    beforeCreate () {
        const params = JSON.parse(localStorage.getItem('executionQueries') || '{}')
        params.sort = 'state.startDate:desc'
        params.status = this.$route.query.status || params.status || 'ALL'
        localStorage.setItem('executionQueries', JSON.stringify(params))
    },
    computed: {
        ...mapState("execution", ["executions", "total"]),
        ...mapState("stat", ["daily"]),
        fields() {
            const title = title => {
                return this.$t(title);
            };
            return [
                {
                    key: "id",
                    label: title("id")
                },
                {
                    key: "state.startDate",
                    label: title("start date"),
                    sortable: true
                },
                {
                    key: "state.endDate",
                    label: title("end date"),
                    sortable: true
                },
                {
                    key: "state.duration",
                    label: title("duration"),
                    sortable: true
                },
                {
                    key: "namespace",
                    label: title("namespace"),
                    sortable: true
                },
                {
                    key: "flowId",
                    label: title("flow"),
                    sortable: true
                },
                {
                    key: "state.current",
                    label: title("state"),
                    class: "text-center",
                    sortable: true
                },
                {
                    key: "details",
                    label: "",
                    class: "row-action"
                }
            ];
        },
        executionQuery() {
            let filter;

            if (this.$route.name === "flowEdit") {
                filter = `namespace:${this.$route.params.namespace} AND flowId:${this.$route.params.id}`;
            }

            return this.query + (filter ? " " + filter : "");
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
        addStatusToQuery(status) {
            const token = status.toUpperCase()
            this.$refs.searchField.search = token;
            this.$refs.searchField.onSearch();
        },
        triggerExecution() {
            this.$store
                .dispatch("execution/triggerExecution", this.$route.params)
                .then(response => {
                    this.$router.push({
                        name: "execution",
                        params: response.data
                    });

                    return response.data
                })
                .then((execution) => {
                    this.$toast().success({type: 'triggered', name: execution.id});
                })
        },
        loadData(callback) {
            this.dailyReady = false;
            this.$store
                .dispatch("stat/daily", {
                    q: this.executionQuery,
                    startDate: this.$moment(this.startDate).format('YYYY-MM-DD'),
                    endDate: this.$moment(this.endDate).format('YYYY-MM-DD')
                })
                .then(() => {
                    this.dailyReady = true;
                });


            this.$store.dispatch("execution/findExecutions", {
                size: parseInt(this.$route.query.size || 25),
                page: parseInt(this.$route.query.page || 1),
                q: this.executionQuery,
                sort: this.$route.query.sort,
                state: this.$route.query.status
            }).finally(callback);
        },
        durationFrom(item) {
            return (+new Date() - new Date(item.state.startDate).getTime()) / 1000
        },
    }
};
</script>
