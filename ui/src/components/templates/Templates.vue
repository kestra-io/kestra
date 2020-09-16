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
                            <router-link :to="{name: 'templateEdit', params : row.item}">
                                <eye id="edit-action" />
                            </router-link>
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
        BottomLine,
        Plus,
        Eye,
        DataTable,
        SearchField,
    },
    data() {
        return {
            dataType: "template",
            permission: permission,
            action: action,
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
                    label: title("template"),
                    sortable: true
                },
                {
                    key: "actions",
                    label: "",
                    class: "row-action"
                }
            ];
        },
    },
    methods: {
        loadData(callback) {
            this.$store
                .dispatch("templates/findTemplates", {
                    q: "",
                    size: parseInt(this.$route.query.size || 25),
                    page: parseInt(this.$route.query.page || 1),
                    sort: this.$route.query.sort
                })
                .then(() => {
                    callback();
                })
        }
    }
};
</script>