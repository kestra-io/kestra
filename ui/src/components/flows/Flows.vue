<template>
    <div v-if="ready">
        <div>
            <data-table
                @onPageChanged="loadData"
                striped
                hover
                bordered
                ref="dataTable"
                :total="total"
            >
                <template v-slot:navbar>
                    <namespace-selector @onNamespaceSelect="onNamespaceSelect" />
                    <v-s />
                    <search-field @onSearch="onSearch" :fields="searchableFields" />
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
                            <router-link :to="{name: 'flow', params : row.item}">
                                <eye id="edit-action" />
                            </router-link>
                        </template>

                        <template v-slot:cell(state)="row">
                            <chart
                                v-if="row.item.metrics"
                                dateFormat="YYYY-MM-DD"
                                :dateInterval="dateInterval"
                                :endDate="endDate"
                                :startDate="startDate"
                                :data="chartData(row)"
                            />
                        </template>

                        <template v-slot:cell(duration)="row">
                            <trend v-if="row.item.trend" :trend="row.item.trend" />

                            <div class="stats">
                                <span
                                    v-if="row.item.lastDayDurationStats"
                                    class="value"
                                >{{row.item.lastDayDurationStats.avg | humanizeDuration }}</span>
                                <span v-if="row.item.lastDayDurationStats" class="title">(24h)</span>
                            </div>

                            <div class="stats">
                                <span
                                    v-if="row.item.periodDurationStats"
                                    class="value"
                                >{{row.item.periodDurationStats.avg | humanizeDuration }}</span>
                                <span v-if="row.item.periodDurationStats" class="title">(30d)</span>
                            </div>
                        </template>
                    </b-table>
                </template>
            </data-table>
        </div>
        <bottom-line>
            <ul class="navbar-nav ml-auto">
                <li class="nav-item">
                    <router-link :to="{name: 'flowsAdd'}">
                        <b-button variant="primary">
                            <plus />
                            {{$t('add flow') | cap }}
                        </b-button>
                    </router-link>
                </li>
            </ul>
        </bottom-line>
    </div>
</template>

<script>
import { mapState } from "vuex";
import NamespaceSelector from "../namespace/Selector";
import Plus from "vue-material-design-icons/Plus";
import Eye from "vue-material-design-icons/Eye";
import BottomLine from "../layout/BottomLine";
import RouteContext from "../../mixins/routeContext";
import DataTableActions from "../../mixins/dataTableActions";
import DataTable from "../layout/DataTable";
import SearchField from "../layout/SearchField";
import Chart from "./Chart";
import Trend from "../Trend";

export default {
    mixins: [RouteContext, DataTableActions],
    components: {
        NamespaceSelector,
        BottomLine,
        Plus,
        Eye,
        DataTable,
        SearchField,
        Chart,
        Trend
    },
    props: {
        endDate: {
            type: Date,
            default: () => {
                return new Date();
            }
        },
        dateInterval: {
            type: Number,
            default: () => {
                return -30;
            }
        }
    },
    data() {
        return {
            dataType: "flow"
        };
    },
    computed: {
        ...mapState("flow", ["flows", "total"]),
        fields() {
            const title = title => {
                return this.$t(title).capitalize();
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
                    class: "row-state"
                },
                {
                    key: "duration",
                    label: title("duration"),
                    sortable: false,
                    class: "row-duration"
                },
                {
                    key: "actions",
                    label: "",
                    class: "row-action"
                }
            ];
        },
        startDate() {
            return this.$moment(this.endDate)
                .add(this.dateInterval, "days")
                .toDate();
        }
    },
    methods: {
        chartData(row) {
            const statuses = ["success", "failed", "created", "running"];
            return {
                json: row.item.metrics,
                keys: { x: "startDate", value: statuses },
                groups: [statuses]
            };
        },
        loadData(callback) {
            this.$store.dispatch("flow/searchAndAggregate", {
                q: this.query,
                startDate: this.startDate.toISOString(),
                size: parseInt(this.$route.query.size || 25),
                page: parseInt(this.$route.query.page || 1),
                sort: this.$route.query.sort
            });
            callback();
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
