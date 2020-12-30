<template>
    <div v-if="ready">
        <data-table @onPageChanged="onPageChanged" ref="dataTable" :total="total">
            <template v-slot:navbar>
                <search-field ref="searchField" @onSearch="onSearch" :fields="searchableFields" />
                <namespace-select data-type="flow" v-if="$route.name !== 'flowEdit'" @onNamespaceSelect="onNamespaceSelect" />
                <status-filter-buttons @onRefresh="onStatusChange" />
                <date-range @onDate="onSearch" />
                <refresh-button class="float-right" @onRefresh="loadData" />
            </template>

            <template v-slot:top>
                <state-global-chart
                    v-if="daily"
                    :ready="dailyReady"
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
                    <template #empty>
                        <span class="text-black-50">{{ $t('no result') }}</span>
                    </template>

                    <template v-slot:cell(details)="row">
                        <router-link :to="{name: 'executionEdit', params: row.item}">
                            <eye id="edit-action" />
                        </router-link>
                    </template>
                    <template
                        v-slot:cell(state.startDate)="row"
                    >
                        <date-ago :date="row.item.state.startDate" />
                    </template>
                    <template
                        v-slot:cell(state.endDate)="row"
                    >
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
                        <span v-if="isRunning(row.item)">{{ durationFrom(row.item) | humanizeDuration }}</span>
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

                    <template v-slot:cell(trigger)="row">
                        <trigger-avatar @showTriggerDetails="showTriggerDetails" :execution="row.item" />
                    </template>
                </b-table>
            </template>
        </data-table>

        <flow-trigger-details-modal v-if="flowTriggerDetails" :trigger="flowTriggerDetails" />
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
    import RefreshButton from "../layout/RefreshButton"
    import StatusFilterButtons from "../layout/StatusFilterButtons"
    import StateGlobalChart from "../../components/stats/StateGlobalChart";
    import FlowTriggerDetailsModal from "../../components/flows/TriggerDetailsModal";
    import TriggerAvatar from "../../components/flows/TriggerAvatar";
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
            FlowTriggerDetailsModal,
            TriggerAvatar,
            DateAgo
        },
        data() {
            return {
                dataType: "execution",
                dailyReady: false,
                flowTriggerDetails: undefined
            };
        },
        beforeCreate() {
            const q = JSON.parse(localStorage.getItem("executionQueries") || "{}")
            q.sort = q.sort ? q.sort :  "state.startDate:desc"
            q.status = q.status ? q.status : "ALL"
            localStorage.setItem("executionQueries", JSON.stringify(q))
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
                        key: "trigger",
                        label: title("trigger"),
                        class: "shrink"
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

                return this.query + (filter ? " AND " + filter : "");
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
            onStatusChange() {
                this.saveFilters()
                this.loadData()
            },
            showTriggerDetails(trigger) {
                this.flowTriggerDetails = trigger
                this.$bvModal.show("modal-triggers-details")
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
                        this.$toast().success(this.$t("triggered done", {name: execution.id}));
                    })
            },
            loadData(callback) {
                this.dailyReady = false;
                this.$store
                    .dispatch("stat/daily", {
                        q: this.executionQuery,
                        startDate: this.$moment(this.startDate).format("YYYY-MM-DD"),
                        endDate: this.$moment(this.endDate).format("YYYY-MM-DD")
                    })
                    .then(() => {
                        this.dailyReady = true;
                    });


                this.$store.dispatch("execution/findExecutions", {
                    size: parseInt(this.$route.query.size || 25),
                    page: parseInt(this.$route.query.page || 1),
                    q: this.executionQuery,
                    sort: this.$route.query.sort || "state.startDate:desc",
                    state: this.$route.query.status
                }).finally(callback);
            },
            durationFrom(item) {
                return (+new Date() - new Date(item.state.startDate).getTime()) / 1000
            },
        }
    };
</script>
