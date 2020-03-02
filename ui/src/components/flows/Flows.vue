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
                    >
                        <template v-slot:cell(actions)="row">
                            <router-link :to="{name: 'flow', params : row.item}">
                                <eye id="edit-action" />
                            </router-link>
                        </template>
                        <template v-slot:cell(id)="row">
                            <router-link :to="{name: 'flow', params : row.item}">
                                {{row.item.id}}
                            </router-link>
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

export default {
    mixins: [RouteContext, DataTableActions],
    components: {
        NamespaceSelector,
        BottomLine,
        Plus,
        Eye,
        DataTable,
        SearchField
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
                    label: title("id"),
                    sortable: true
                },
                {
                    key: "namespace",
                    label: title("namespace"),
                    sortable: true
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
        loadData(callback) {
            this.$store.dispatch("flow/findFlows", {
                q: this.query,
                size: parseInt(this.$route.query.size || 25),
                page: parseInt(this.$route.query.page || 1),
                sort: this.$route.query.sort
            }).finally(callback);
        }
    }
};
</script>