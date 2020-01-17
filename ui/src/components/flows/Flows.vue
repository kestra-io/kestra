<template>
    <div>
        <div>
            <data-table
                @onPageChanged="loadFlows"
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
                    >
                        <template v-slot:cell(actions)="row">
                            <router-link :to="{name: 'flow', params : row.item}">
                                <eye id="edit-action" />
                            </router-link>
                        </template>
                        <template v-slot:cell(namespace)="row">
                            <a
                                href
                                @click.prevent="onNamespaceSelect(row.item.namespace)"
                            >{{row.item.namespace}}</a>
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
import DataTable from "../layout/DataTable";
import SearchField from "../layout/SearchField";
import queryBuilder from "../../utils/queryBuilder";
export default {
    mixins: [RouteContext],
    components: {
        NamespaceSelector,
        BottomLine,
        Plus,
        Eye,
        DataTable,
        SearchField
    },
    created() {
        if (localStorage.getItem("flowQueries")) {
            this.$router.push({
                query: JSON.parse(localStorage.getItem("flowQueries"))
            });
        }
        this.query = queryBuilder(this.$route, this.fields);
    },
    mounted() {
        this.onNamespaceSelect(this.$route.query.namespace);
    },
    data() {
        return {
            query: "*",
            sort: ""
        };
    },
    watch: {
        $route() {
            localStorage.setItem(
                "flowQueries",
                JSON.stringify(this.$route.query)
            );
        }
    },
    computed: {
        ...mapState("flow", ["flows", "total"]),
        ...mapState("namespace", ["namespace", "namespace"]),
        searchableFields() {
            return this.fields.filter(f => !["actions"].includes(f.key));
        },
        routeInfo() {
            return {
                title: this.$t("flows")
            };
        },
        fields() {
            const title = title => {
                return this.$t(title).capitalize();
            };
            return [
                {
                    key: "id",
                    label: title("id"),
                    sortable: true
                },
                {
                    key: "namespace",
                    label: title("namespace")
                },
                {
                    key: "revision",
                    label: title("revision"),
                    sortable: true
                },
                {
                    key: "actions",
                    label: "",
                    class: "row-action"
                }
            ];
        }
    },
    methods: {
        onSearch() {
            this.query = queryBuilder(this.$route, this.fields);
            this.loadFlows();
        },
        onSort(sortItem) {
            const sort = [
                `${sortItem.sortBy}:${sortItem.sortDesc ? "desc" : "asc"}`
            ];
            this.$router.push({
                query: { ...this.$route.query, sort }
            });
            this.loadFlows();
        },
        onRowDoubleClick(item) {
            this.$router.push({ name: "flow", params: item });
        },
        loadFlows() {
            this.$store.dispatch("flow/findFlows", {
                q: this.query,
                size: parseInt(this.$route.query.size || 25),
                page: parseInt(this.$route.query.page || 1),
                sort: this.$route.query.sort
            });
        },
        onNamespaceSelect() {
            if (this.$route.query.page !== "1") {
                this.$router.push({ query: { ...this.$route.query, page: 1 } });
            }
            this.query = queryBuilder(this.$route, this.fields);
            this.loadFlows();
        }
    }
};
</script>