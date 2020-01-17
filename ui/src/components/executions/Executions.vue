<template>
    <div>
        <data-table @onPageChanged="loadExecutions" ref="dataTable" :total="total">
            <template v-slot:navbar>
                <namespace-selector @onNamespaceSelect="onNamespaceSelect" />
                    <v-s />
                <search-field @onSearch="onSearch" :fields="searchableFields" />
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
                        <status class="status" :status="row.item.state.current" />
                    </template>
                    <template v-slot:cell(flowId)="row">
                        <router-link
                            :to="{name: 'flow', params: {namespace: row.item.namespace, id: row.item.flowId}}"
                        >{{row.item.flowId}}</router-link>
                    </template>
                    <template v-slot:cell(namespace)="row">
                        <router-link
                            :to="{name: 'flowsList', query: {namespace: row.item.namespace}}"
                        >{{row.item.namespace}}</router-link>
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
import SearchField from "../layout/SearchField";
import queryBuilder from "../../utils/queryBuilder";
import NamespaceSelector from "../namespace/Selector";

export default {
    mixins: [RouteContext],
    components: { Status, Eye, DataTable, SearchField, NamespaceSelector },
    created() {
        if (localStorage.getItem("executionQueries")) {
            this.$router.push({
                query: JSON.parse(localStorage.getItem("executionQueries"))
            });
        }
        this.query = queryBuilder(this.$route, this.fields);
        this.loadExecutions();
    },
    data() {
        return {
            query: "*"
        };
    },
    watch: {
        $route() {
            localStorage.setItem(
                "executionQueries",
                JSON.stringify(this.$route.query)
            );
        }
    },
    computed: {
        ...mapState("execution", ["executions", "total"]),
        searchableFields() {
            return this.fields.filter(f => !["actions"].includes(f.key));
        },
        routeInfo() {
            return {
                title: this.$t("executions")
          };
        },
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
        }
    },
    methods: {
        onSearch() {
            this.query = queryBuilder(this.$route, this.fields);
            this.loadExecutions();
        },
        onRowDoubleClick(item) {
            this.$router.push({ name: "execution", params: item });
        },
        onSort(sortItem) {
            const sort = [
                `${sortItem.sortBy}:${sortItem.sortDesc ? "desc" : "asc"}`
            ];
            this.$router.push({
                query: { ...this.$route.query, sort }
            });
            this.loadExecutions();
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
        loadExecutions() {
            this.$store.dispatch("execution/findExecutions", {
                size: parseInt(this.$route.query.size || 25),
                page: parseInt(this.$route.query.page || 1),
                q: this.query,
                sort: this.$route.query.sort
            });
        }
    }
};
</script>
