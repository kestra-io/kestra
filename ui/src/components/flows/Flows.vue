<template>
    <div v-if="ready">
        <div>
            <data-table
                @onPageChanged="onPageChanged"
                striped
                hover
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
                        class="mb-4"
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
                        :responsive="true"
                        striped
                        hover
                        sort-by="id"
                        :items="flows"
                        :fields="fields"
                        :tbody-tr-class="rowClasses"
                        ref="table"
                        show-empty
                    >
                        <template #empty>
                            <span class="text-muted">{{ $t('no result') }}</span>
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
                                :duration="true"
                                :namespace="row.item.namespace"
                                :flow-id="row.item.id"
                                v-if="dailyGroupByFlowReady"
                                :data="chartData(row)"
                            />
                        </template>

                        <template #cell(id)="row">
                            <router-link
                                :to="{name: 'flows/update', params: {namespace: row.item.namespace, id: row.item.id}}"
                            >
                                {{ row.item.id }}
                            </router-link>
                            &nbsp;<markdown-tooltip
                                :id="row.item.namespace + '-' + row.item.id"
                                :description="row.item.description"
                                :title="row.item.namespace + '.' + row.item.id"
                                :modal="true"
                            />
                        </template>

                        <template #cell(triggers)="row">
                            <trigger-avatar :flow="row.item" />
                        </template>
                    </b-table>
                </template>
            </data-table>
        </div>


        <bottom-line v-if="user && user.hasAnyAction(permission.FLOW, action.CREATE)">
            <ul class="navbar-nav ml-auto">
                <li class="nav-item">
                    <router-link :to="{name: 'flows/search'}">
                        <b-button variant="secondary">
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
    import StateGlobalChart from "../stats/StateGlobalChart";
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
            StateGlobalChart,
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
                            startDate: this.$moment(this.startDate).add(-1, "day").startOf("day").toISOString(true),
                            endDate: this.$moment(this.endDate).endOf("day").toISOString(true)
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
                        sort: this.$route.query.sort || "id:asc"
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
                                        startDate: this.$moment(this.startDate).add(-1, "day").startOf("day").toISOString(true),
                                        endDate: this.$moment(this.endDate).endOf("day").toISOString(true)
                                    })
                                    .then(() => {
                                        this.dailyGroupByFlowReady = true
                                    })
                            }
                        }
                    })
            },
            rowClasses(flow) {
                return flow.disabled ? ["disabled"] : [];
            }
        }
    };
</script>

