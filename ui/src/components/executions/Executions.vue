<template>
    <div v-if="ready">
        <data-table @onPageChanged="loadData" ref="dataTable" :total="total">
            <template v-slot:navbar>
                <namespace-selector @onNamespaceSelect="onNamespaceSelect" />
                <v-s />
                <search-field ref="searchField" @onSearch="onSearch" :fields="searchableFields" />
                <date-range @onDate="onSearch" />
                <refresh-button class="float-right" @onRefresh="loadData"/>
                <v-s />
                <status-filter-buttons @onRefresh="addStatusToQuery"/>
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
                        <router-link :to="{name: 'execution', params: row.item}">
                            <eye id="edit-action" />
                        </router-link>
                    </template>
                    <template
                        v-slot:cell(state.startDate)="row"
                    >{{row.item.state.startDate | date('YYYY/MM/DD HH:mm:ss')}}</template>
                    <template
                        v-slot:cell(state.endDate)="row"
                    >{{row.item.state.endDate | date('YYYY/MM/DD HH:mm:ss')}}</template>
                    <template v-slot:cell(state.current)="row">
                        <status
                            @click.native="addStatusToQuery(row.item.state.current)"
                            class="status sm"
                            :status="row.item.state.current"
                        />
                    </template>
                     <template v-slot:cell(state.duration)="row">
                        <p>{{row.item.state.duration | humanizeDuration}}</p>
                    </template>
                    <template v-slot:cell(flowId)="row">
                        <router-link
                            :to="{name: 'flow', params: {namespace: row.item.namespace, id: row.item.flowId}}"
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
import NamespaceSelector from "../namespace/Selector";
import DateRange from "../layout/DateRange";
import RefreshButton from '../layout/RefreshButton'
import StatusFilterButtons from '../layout/StatusFilterButtons'

export default {
    mixins: [RouteContext, DataTableActions],
    components: {
        Status,
        Eye,
        DataTable,
        SearchField,
        NamespaceSelector,
        DateRange,
        RefreshButton,
        StatusFilterButtons
    },
    data() {
        return {
            dataType: "execution",
        };
    },
    beforeCreate () {
        const params = JSON.parse(localStorage.getItem('executionQueries') || '{}')
        params.sort = 'state.startDate:desc'
        localStorage.setItem('executionQueries', JSON.stringify(params))
    },
    computed: {
        ...mapState("execution", ["executions", "total"]),
        fields() {
            const title = title => {
                return this.$t(title).capitalize();
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
            if (this.$route.name === "flow") {
                const filter = `flowId:${this.$route.params.id}`;
                return this.query === "*"
                    ? filter
                    : `${this.query} AND ${filter}`;
            } else {
                return this.query;
            }
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
                    this.$bvToast.toast(this.$t("triggered").capitalize(), {
                        title: this.$t("execution").capitalize(),
                        autoHideDelay: 5000,
                        toaster: "b-toaster-top-right",
                        variant: "success"
                    });
                });
        },
        loadData(callback) {
            this.$store.dispatch("execution/findExecutions", {
                size: parseInt(this.$route.query.size || 25),
                page: parseInt(this.$route.query.page || 1),
                q: this.executionQuery,
                sort: this.$route.query.sort
            }).finally(callback);
        }
    }
};
</script>
