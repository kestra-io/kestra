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
                <template v-slot:navbar>
                    <search-field @onSearch="onSearch" :fields="searchableFields" />
                    <namespace-select :data-type="dataType" @onNamespaceSelect="onNamespaceSelect" />
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
                        @row-dblclicked="onRowDoubleClick"
                        @sort-changed="onSort"
                        responsive="xl"
                        striped
                        bordered
                        hover
                        :items="flows"
                        :fields="fields"
                        ref="table"
                    >
                        <template v-slot:cell(actions)="row">
                            <router-link :to="{name: 'flowEdit', params : row.item}">
                                <eye id="edit-action" />
                            </router-link>
                        </template>

                        <template v-slot:cell(state)="row">
                            <state-chart
                                v-if="dailyGroupByFlowReady"
                                :data="chartData(row)"
                            />
                        </template>

                        <template v-slot:cell(duration)="row">
                            <duration-chart
                                v-if="dailyGroupByFlowReady"
                                :data="chartData(row)"
                            />
                        </template>

                        <template v-slot:cell(id)="row">
                            <router-link
                                :to="{name: 'flowEdit', params: {namespace: row.item.namespace, id: row.item.id}, query:{tab: 'executions'}}"
                            >{{row.item.id}}</router-link>
                        </template>
                    </b-table>
                </template>
            </data-table>
        </div>
        <bottom-line v-if="user && user.hasAnyAction(permission.FLOW, action.CREATE)">
            <ul class="navbar-nav ml-auto">
                <li class="nav-item">
                    <router-link :to="{name: 'flowsAdd'}">
                        <b-button variant="primary">
                            <plus />
                            {{$t('create')}}
                        </b-button>
                    </router-link>
                </li>
            </ul>
        </bottom-line>
    </div>
</template>

<script>
import { mapState } from "vuex";
import permission from "../../models/permission";
import action from "../../models/action";
import NamespaceSelect from "../namespace/NamespaceSelect";
import Plus from "vue-material-design-icons/Plus";
import Eye from "vue-material-design-icons/Eye";
import BottomLine from "../layout/BottomLine";
import RouteContext from "../../mixins/routeContext";
import DataTableActions from "../../mixins/dataTableActions";
import DataTable from "../layout/DataTable";
import SearchField from "../layout/SearchField";
import StateChart from "../stats/StateChart";
import DurationChart from "../stats/DurationChart";
import StateGlobalChart from "../stats/StateGlobalChart";

export default {
    mixins: [RouteContext, DataTableActions],
    components: {
        NamespaceSelect,
        BottomLine,
        Plus,
        Eye,
        DataTable,
        SearchField,
        StateChart,
        DurationChart,
        StateGlobalChart,
    },
    data() {
        return {
            dataType: "flow",
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
        fields() {
            const title = title => {
                return this.$t(title);
            };
            return [
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
                },
                {
                    key: "actions",
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
        chartData(row) {
            if (this.dailyGroupByFlow && this.dailyGroupByFlow[row.item.namespace] && this.dailyGroupByFlow[row.item.namespace][row.item.id]) {
                return this.dailyGroupByFlow[row.item.namespace][row.item.id];
            } else {
                return [];
            }
        },
        loadData(callback) {
            this.dailyReady = false;
            this.$store
                .dispatch("stat/daily", {
                    q: this.query.replace("id:" , "flowId:"),
                    startDate: this.$moment(this.startDate).format('YYYY-MM-DD'),
                    endDate: this.$moment(this.endDate).format('YYYY-MM-DD')
                })
                .then(() => {
                    this.dailyReady = true;
                });

            this.$store
                .dispatch("flow/findFlows", {
                    q: this.query,
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

                        this.$store
                            .dispatch("stat/dailyGroupByFlow", {
                                q: query,
                                startDate: this.$moment(this.startDate).format('YYYY-MM-DD'),
                                endDate: this.$moment(this.endDate).format('YYYY-MM-DD')
                            })
                            .then(() => {
                                this.dailyGroupByFlowReady = true
                            })
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
