<template>
    <div v-if="ready">
        <data-table @onPageChanged="onPageChanged" ref="dataTable" :total="total" :size="pageSize" :page="pageNumber">
            <template #navbar v-if="embed === false">
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
                    :end="$route.query.end"
                    @input="onDataTableValue($event)"
                />
                <refresh-button class="float-right" @onRefresh="load" />
            </template>

            <template #top v-if="embed === false">
                <state-global-chart
                    v-if="daily"
                    class="mb-4"
                    :ready="dailyReady"
                    :data="daily"
                />
            </template>

            <template #table>
                <b-table
                    :no-local-sorting="true"
                    @sort-changed="onSort"
                    :responsive="true"
                    striped
                    hover
                    sort-by="state.startDate"
                    sort-desc
                    :items="executions"
                    :fields="fields"
                    @row-dblclicked="onRowDoubleClick"
                    ref="table"
                    show-empty
                >
                    <template #empty>
                        <span class="text-muted">{{ $t('no result') }}</span>
                    </template>

                    <template #cell(details)="row">
                        <router-link :to="{name: 'executions/update', params: row.item}">
                            <kicon :tooltip="$t('details')" placement="left">
                                <eye />
                            </kicon>
                        </router-link>
                    </template>
                    <!-- eslint-disable-next-line -->
                    <template #cell(state.startDate)="row">
                        <date-ago :inverted="true" :date="row.item.state.startDate" />
                    </template>
                    <!-- eslint-disable-next-line -->
                    <template #cell(state.endDate)="row">
                        <span v-if="!isRunning(row.item)">
                            <date-ago :inverted="true" :date="row.item.state.endDate" />
                        </span>
                    </template>
                    <!-- eslint-disable-next-line -->
                    <template #cell(state.current)="row">
                        <status
                            class="status"
                            :status="row.item.state.current"
                            size="sm"
                        />
                    </template>
                    <!-- eslint-disable-next-line -->
                    <template #cell(state.duration)="row">
                        <span v-if="isRunning(row.item)">{{ durationFrom(row.item) | humanizeDuration }}</span>
                        <span v-else>{{ row.item.state.duration | humanizeDuration }}</span>
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

                    <template #cell(trigger)="row">
                        <trigger-avatar :execution="row.item" />
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
    import RefreshButton from "../layout/RefreshButton"
    import StatusFilterButtons from "../layout/StatusFilterButtons"
    import StateGlobalChart from "../../components/stats/StateGlobalChart";
    import TriggerAvatar from "../../components/flows/TriggerAvatar";
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
        },
        data() {
            return {
                isDefaultNamespaceAllow: true,
                dailyReady: false,
                dblClickRouteName: "executions/update",
                flowTriggerDetails: undefined
            };
        },
        computed: {
            ...mapState("execution", ["executions", "total"]),
            ...mapState("stat", ["daily"]),
            routeInfo() {
                return {
                    title: this.$t("executions")
                };
            },
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
                        key: "state.startDate",
                        label: title("start date"),
                        sortable: true,
                    },
                    {
                        key: "state.endDate",
                        label: title("end date"),
                        sortable: true,
                    },
                    {
                        key: "state.duration",
                        label: title("duration"),
                        sortable: true,
                    },
                ]

                if (this.$route.name !== "flows/update") {
                    fields.push(
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
                    )
                }

                fields.push(
                    {
                        key: "state.current",
                        label: title("state"),
                        class: "text-center",
                        sortable: true,
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
                );

                this.hidden.forEach(value => {
                    fields = fields.filter(col => col.key !== value);
                })

                return fields;
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
            onStatusChange() {

                this.load(this.onDataLoaded);
            },
            loadQuery(stats) {
                let filter = [];
                let query = this.queryWithFilter();

                if (query.namespace) {
                    filter.push(`namespace:${query.namespace}*`)
                }

                if (query.q) {
                    filter.push(qb.toLucene(query.q));
                }

                if (query.start && !stats) {
                    filter.push(`state.startDate:[${query.start} TO *]`)
                }

                if (query.end && !stats) {
                    filter.push(`state.endDate:[* TO ${query.end}]`)
                }

                if (this.$route.name === "flows/update") {
                    filter.push(`namespace:${this.$route.params.namespace}`);
                    filter.push(`flowId:${this.$route.params.id}`);
                }

                return filter.join(" AND ") || "*"
            },
            loadData(callback) {
                if (this.embed === false) {
                    this.dailyReady = false;

                    this.$store
                        .dispatch("stat/daily", {
                            q: this.loadQuery(true),
                            startDate: this.$moment(this.startDate).add(-1, "day").startOf("day").toISOString(true),
                            endDate: this.$moment(this.endDate).endOf("day").toISOString(true)
                        })
                        .then(() => {
                            this.dailyReady = true;
                        });
                }

                this.$store.dispatch("execution/findExecutions", {
                    size: parseInt(this.$route.query.size || this.internalPageSize),
                    page: parseInt(this.$route.query.page || this.internalPageNumber),
                    q: this.loadQuery(false),
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
