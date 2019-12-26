<template>
    <div>
        <div>
            <data-table @onPageChanged="loadFlows" striped hover bordered ref="dataTable" :total="total">
                <template v-slot:navbar>
                    <namespace-selector @onNamespaceSelect="onNamespaceSelect" />
                </template>
                <template v-slot:table>
                    <b-table responsive="xl" striped bordered hover :items="flows" :fields="fields">
                        <template v-slot:cell(actions)="row">
                            <router-link :to="{name: 'flow', params : row.item}">
                                <eye id="edit-action" />
                            </router-link>
                        </template>
                        <template v-slot:cell(namespace)="row">
                            <a href @click.prevent="onNamespaceSelect(row.item.namespace)">{{row.item.namespace}}</a>
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

export default {
    mixins: [RouteContext],
    components: {
        NamespaceSelector,
        BottomLine,
        Plus,
        Eye,
        DataTable
    },

    mounted() {
        this.onNamespaceSelect(this.$route.query.namespace);
    },
    watch: {
        $route() {
            this.onNamespaceSelect(this.$route.query.namespace);
        }
    },
    computed: {
        ...mapState("flow", ["flows", "total"]),
        ...mapState("namespace", ["namespace", "namespace"]),
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
                },
                {
                    key: "namespace",
                    label: title("namespace"),
                },
                {
                    key: "revision",
                    label: title("revision"),
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
        loadFlows(pagination) {
            if (this.namespace) {
                this.$store.dispatch("flow/loadFlows", {
                    namespace: this.namespace,
                    size: pagination.size,
                    page: pagination.page
                });
            } else {
                this.$store.dispatch("flow/findFlows", {
                    q: "*",
                    size: pagination.size,
                    page: pagination.page
                });
            }
        },
        onNamespaceSelect(namespace) {
            if (this.$route.query.namespace !== namespace) {
                this.$router.push({ query: { namespace } });
                this.page = 1;
            }
            this.$store.commit("namespace/setNamespace", namespace);
            this.loadFlows(this.$refs.dataTable.pagination);
        }
    }
};
</script>