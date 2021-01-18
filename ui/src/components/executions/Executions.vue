<template>
    <div v-if="ready">
        <data-table @onPageChanged="onPageChangedOverload" ref="dataTable" :total="total" :size="pageSize" :page="pageNumber">
            <template #navbar v-if="embed === false">
                <search-field ref="searchField" @onSearch="onSearch" :fields="searchableFields" />
                <namespace-select data-type="flow" v-if="$route.name !== 'flowEdit'" @onNamespaceSelect="onNamespaceSelect" />
                <status-filter-buttons @onRefresh="onStatusChange" />
                <date-range @onDate="onSearch" />
                <refresh-button class="float-right" @onRefresh="loadData" />
            </template>

            <template #top v-if="embed === false">
                <state-global-chart
                    v-if="daily"
                    :ready="dailyReady"
                    :data="daily"
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
                    :items="executions"
                    :fields="fields"
                    @row-dblclicked="onRowDoubleClick"
                    show-empty
                >
                    <template #empty>
                        <span class="text-black-50">{{ $t('no result') }}</span>
                    </template>

                    <template #cell(details)="row">
                        <router-link :to="{name: 'executionEdit', params: row.item}">
                            <kicon :tooltip="$t('details')" placement="left">
                                <eye />
                            </kicon>
                        </router-link>
                    </template>
                    <template #cell(startDate)="row">
                        <date-ago :date="row.item.state.startDate" />
                    </template>
                    <template #cell(endDate)="row">
                        <span v-if="!isRunning(row.item)">
                            <date-ago :date="row.item.state.endDate" />
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
                        <span v-if="isRunning(row.item)">{{ durationFrom(row.item) | humanizeDuration }}</span>
                        <span v-else>{{ row.item.state.duration | humanizeDuration }}</span>
                    </template>
                    <template #cell(flowId)="row">
                        <router-link
                            :to="{name: 'flowEdit', params: {namespace: row.item.namespace, id: row.item.flowId}}"
                        >
                            {{ row.item.flowId }}
                        </router-link>
                    </template>
                    <template #cell(id)="row">
                        <code>{{ row.item.id | id }}</code>
                    </template>

                    <template #cell(trigger)="row">
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
    import Kicon from "../Kicon"

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
            DateAgo,
            Kicon
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
            pageSize: {
                type: Number,
                default: 25
            },
            pageNumber: {
                type: Number,
                default: 1
            },
        },
        created() {
            this.internalPageSize = this.pageSize;
            this.internalPageNumber = this.pageNumber;
        },
        data() {
            return {
                dataType: "execution",
                dailyReady: false,
                internalPageSize: undefined,
                internalPageNumber: undefined,
                flowTriggerDetails: undefined
            };
        },
        beforeCreate() {
            const q = JSON.parse(localStorage.getItem("executionQueries") || "{}")
            q.sort = q.sort ? q.sort :  "state.startDate:desc"
            q.status = q.status ? q.status : []
            localStorage.setItem("executionQueries", JSON.stringify(q))
        },
        computed: {
            ...mapState("execution", ["executions", "total"]),
            ...mapState("stat", ["daily"]),
            fields() {
                const title = title => {
                    return this.$t(title);
                };
                let fields = [
                    {
                        key: "id",
                        label: title("id")
                    },
                    {
                        key: "startDate",
                        label: title("start date"),
                        sortable: true
                    },
                    {
                        key: "endDate",
                        label: title("end date"),
                        sortable: true
                    },
                    {
                        key: "duration",
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
                        key: "current",
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

                this.hidden.forEach(value => {
                    fields = fields.filter(col => col.key !== value);
                })

                return fields;
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
            onPageChangedOverload(item) {
                this.internalPageSize = item.size;
                this.internalPageNumber = item.page;
                this.onPageChanged(item);
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
                    size: parseInt(this.$route.query.size || this.internalPageSize),
                    page: parseInt(this.$route.query.page || this.internalPageNumber),
                    q: this.executionQuery,
                    sort: this.$route.query.sort || "state.startDate:desc",
                    state: this.$route.query.status ? [this.$route.query.status] : this.statuses
                }).finally(callback);
            },
            durationFrom(item) {
                return (+new Date() - new Date(item.state.startDate).getTime()) / 1000
            },
        }
    };
</script>
