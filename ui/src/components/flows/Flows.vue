<template>
    <div v-if="ready">
        <div>
            <data-table
                @onPageChanged="onPageChanged"
                striped
                hover
                bordered
                ref="dataTable"
                :total="total"
            >
                <template #navbar>
                    <search-field />
                    <namespace-select
                        data-type="flow"
                        :value="$route.query.namespace"
                        @input="onDataTableValue('namespace', $event)"
                    />
                </template>

                <template #top>
                    <state-global-chart
                        v-if="daily"
                        :ready="dailyReady"
                        :data="daily"
                    />
                </template>

                <template #table>
                    <b-table
                        :no-local-sorting="true"
                        @row-dblclicked="onRowDoubleClick"
                        @sort-changed="onSort"
                        responsive
                        striped
                        bordered
                        hover
                        :items="flows"
                        :fields="fields"
                        ref="table"
                        show-empty
                    >
                        <template #empty>
                            <span class="text-black-50">{{ $t('no result') }}</span>
                        </template>

                        <template #cell(actions)="row">
                            <router-link :to="{name: 'flows/update', params : row.item}">
                                <kicon :tooltip="$t('details')" placement="left">
                                    <eye />
                                </kicon>
                            </router-link>
                        </template>

                        <template #cell(state)="row">
                            <state-chart
                                v-if="dailyGroupByFlowReady"
                                :data="chartData(row)"
                            />
                        </template>

                        <template #cell(duration)="row">
                            <duration-chart
                                v-if="dailyGroupByFlowReady"
                                :data="chartData(row)"
                            />
                        </template>

                        <template #cell(id)="row">
                            <router-link
                                :to="{name: 'flows/update', params: {namespace: row.item.namespace, id: row.item.id}, query:{tab: 'executions'}}"
                            >
                                {{ row.item.id }}
                            </router-link>
                            &nbsp;<markdown-tooltip :id="row.item.namespace + '-' + row.item.id" :description="row.item.description" />
                        </template>

                        <template #cell(triggers)="row">
                            <trigger-avatar @showTriggerDetails="showTriggerDetails" :flow="row.item" />
                        </template>
                    </b-table>
                </template>
            </data-table>
        </div>

        <trigger-details-modal :trigger="flowTriggerDetails" />

        <bottom-line v-if="user && user.hasAnyAction(permission.FLOW, action.CREATE)">
            <ul class="navbar-nav ml-auto">
                <li class="nav-item">
                    <router-link :to="{name: 'flows/search'}">
                        <b-button variant="light">
                            <kicon>
                                <text-box-search />
                                {{ $t('source search') }}
                            </kicon>
                        </b-button>
                    </router-link>
                </li>

                <li class="nav-item">
                    <router-link :to="{name: 'flows/create'}">
                        <b-button variant="primary">
                            <kicon>
                                <plus />
                                {{ $t('create') }}
                            </kicon>
                        </b-button>
                    </router-link>
                </li>
            </ul>
        </bottom-line>
    </div>
</template>

<script>
    import {mapState} from "vuex";
    import permission from "../../models/permission";
    import action from "../../models/action";
    import NamespaceSelect from "../namespace/NamespaceSelect";
    import Plus from "vue-material-design-icons/Plus";
    import TextBoxSearch from "vue-material-design-icons/TextBoxSearch";
    import Eye from "vue-material-design-icons/Eye";
    import BottomLine from "../layout/BottomLine";
    import RouteContext from "../../mixins/routeContext";
    import DataTableActions from "../../mixins/dataTableActions";
    import RestoreUrl from "../../mixins/restoreUrl";
    import DataTable from "../layout/DataTable";
    import SearchField from "../layout/SearchField";
    import StateChart from "../stats/StateChart";
    import DurationChart from "../stats/DurationChart";
    import StateGlobalChart from "../stats/StateGlobalChart";
    import TriggerDetailsModal from "./TriggerDetailsModal";
    import TriggerAvatar from "./TriggerAvatar";
    import MarkdownTooltip from "../layout/MarkdownTooltip"
    import Kicon from "../Kicon"
    import qb from "../../utils/queryBuilder";

    export default {
        mixins: [RouteContext, RestoreUrl, DataTableActions],
        components: {
            NamespaceSelect,
            BottomLine,
            Plus,
            TextBoxSearch,
            Eye,
            DataTable,
            SearchField,
            StateChart,
            DurationChart,
            StateGlobalChart,
            TriggerDetailsModal,
            TriggerAvatar,
            MarkdownTooltip,
            Kicon
        },
        data() {
            return {
                isDefaultNamespaceAllow: true,
                permission: permission,
                action: action,
                dailyGroupByFlowReady: false,
                dailyReady: false,
                flowTriggerDetails: undefined
            };
        },
        computed: {
            ...mapState("flow", ["flows", "total"]),
            ...mapState("stat", ["dailyGroupByFlow", "daily"]),
            ...mapState("auth", ["user"]),
            routeInfo() {
                return {
                    title: this.$t("flows")
                };
            },
            fields() {
                const title = title => {
                    return this.$t(title);
                };

                let fields = [
                    {
                        key: "id",
                        label: title("flow"),
                        sortable: true
                    },
                    {
                        key: "namespace",
                        label: title("namespace"),
                        sortable: true
                    },
                ]

                if (this.user.hasAny(permission.EXECUTION)) {
                    fields.push(
                        {
                            key: "state",
                            label: title("execution statistics"),
                            sortable: false,
                            class: "row-graph"
                        },
                        {
                            key: "duration",
                            label: title("duration"),
                            sortable: false,
                            class: "row-graph"
                        }
                    );
                }


                fields.push(
                    {
                        key: "triggers",
                        label: title("triggers"),
                        class: "shrink"
                    },
                    {
                        key: "actions",
                        label: "",
                        class: "row-action"
                    }
                )

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
            showTriggerDetails(trigger) {
                this.flowTriggerDetails = trigger
                this.$bvModal.show("modal-triggers-details")
            },
            chartData(row) {
                if (this.dailyGroupByFlow && this.dailyGroupByFlow[row.item.namespace] && this.dailyGroupByFlow[row.item.namespace][row.item.id]) {
                    return this.dailyGroupByFlow[row.item.namespace][row.item.id];
                } else {
                    return [];
                }
            },
            loadQuery() {
                let filter = []
                let query = this.queryWithFilter();

                if (query.namespace) {
                    filter.push(`namespace:${query.namespace}*`)
                }

                if (query.q) {
                    filter.push(qb.toLucene(query.q));
                }

                return filter.join(" AND ") || "*"
            },
            loadData(callback) {
                this.dailyReady = false;

                if (this.user.hasAny(permission.EXECUTION)) {
                    this.$store
                        .dispatch("stat/daily", {
                            q: this.loadQuery(),
                            startDate: this.$moment(this.startDate).format("YYYY-MM-DD"),
                            endDate: this.$moment(this.endDate).format("YYYY-MM-DD")
                        })
                        .then(() => {
                            this.dailyReady = true;
                        });
                }

                this.$store
                    .dispatch("flow/findFlows", {
                        q: this.loadQuery(),
                        size: parseInt(this.$route.query.size || 25),
                        page: parseInt(this.$route.query.page || 1),
                        sort: this.$route.query.sort
                    })
                    .then(flows => {
                        this.dailyGroupByFlowReady = false;
                        callback();

                        if (flows.results && flows.results.length > 0) {
                            let query = "((" + flows.results
                                .map(flow => "flowId:" + flow.id + " AND namespace:" + flow.namespace)
                                .join(") OR (") + "))"

                            if (this.user && this.user.hasAny(permission.EXECUTION)) {
                                this.$store
                                    .dispatch("stat/dailyGroupByFlow", {
                                        q: query,
                                        startDate: this.$moment(this.startDate).format("YYYY-MM-DD"),
                                        endDate: this.$moment(this.endDate).format("YYYY-MM-DD")
                                    })
                                    .then(() => {
                                        this.dailyGroupByFlowReady = true
                                    })
                            }
                        }
                    })
            }
        }
    };
</script>
<style lang="scss" scoped>
@import "../../styles/_variable.scss";

.stats {
    display: block;
    font-size: $font-size-xs;
}
.stats span.title {
    padding-left: 10px;
    color: $gray-600;
}
.stats span.value {
    color: $gray-900;
}
</style>
