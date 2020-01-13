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
            this.onNamespaceSelect(this.$route.query.namespace);
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
        onSearch(query) {
            this.query = query;
            this.loadFlows();
        },
        onSort(sort) {
            this.sort = [`${sort.sortBy}:${sort.sortDesc ? "desc" : "asc"}`];
            this.loadFlows();
        },
        onRowDoubleClick(item) {
            this.$router.push({name: 'flow', params: item})
        },
        loadFlows() {
            const pagination = this.$refs.dataTable.nextPagination;
            if (this.namespace) {
                this.$store.dispatch("flow/loadFlows", {
                    namespace: this.namespace,
                    size: pagination.size,
                    page: pagination.page,
                    sort: this.sort,
                    q: this.query
                });
            } else {
                this.$store.dispatch("flow/findFlows", {
                    q: this.query,
                    size: pagination.size,
                    page: pagination.page,
                    sort: this.sort
                });
            }
        },
        onNamespaceSelect(namespace) {
            if (this.$route.query.namespace !== namespace) {
                this.$router.push({ query: { namespace } });
                this.page = 1;
            }
            this.$store.commit("namespace/setNamespace", namespace);
            this.loadFlows();
        }
    }
};
</script>