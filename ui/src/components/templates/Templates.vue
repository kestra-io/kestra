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
                <template #navbar>
                    <search-field />
                    <namespace-select
                        data-type="template"
                        :value="$route.query.namespace"
                        @input="onDataTableValue('namespace', $event)"
                    />
                </template>

                <template #table>
                    <b-table
                        :no-local-sorting="true"
                        @row-dblclicked="onRowDoubleClick"
                        @sort-changed="onSort"
                        responsive
                        striped
                        bordered
                        hover
                        :items="templates"
                        :fields="fields"
                        ref="table"
                        show-empty
                    >
                        <template #empty>
                            <span class="text-black-50">{{ $t('no result') }}</span>
                        </template>

                        <template #cell(actions)="row">
                            <router-link :to="{name: 'templates/update', params : row.item}">
                                <kicon :tooltip="$t('details')" placement="left">
                                    <eye />
                                </kicon>
                            </router-link>
                        </template>

                        <template #cell(id)="row">
                            <router-link
                                :to="{name: `templates/update`, params: {namespace: row.item.namespace, id: row.item.id}}"
                            >
                                {{ row.item.id }}
                            </router-link>
                        </template>
                    </b-table>
                </template>
            </data-table>
        </div>
        <bottom-line v-if="user && user.hasAnyAction(permission.TEMPLATE, action.CREATE)">
            <ul class="navbar-nav ml-auto">
                <li class="nav-item">
                    <router-link :to="{name: 'templates/create'}">
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
    import Eye from "vue-material-design-icons/Eye";
    import BottomLine from "../layout/BottomLine";
    import RouteContext from "../../mixins/routeContext";
    import DataTableActions from "../../mixins/dataTableActions";
    import DataTable from "../layout/DataTable";
    import SearchField from "../layout/SearchField";
    import Kicon from "../Kicon"
    import qb from "../../utils/queryBuilder";
    import RestoreUrl from "../../mixins/restoreUrl";

    export default {
        mixins: [RouteContext, RestoreUrl, DataTableActions],
        components: {
            BottomLine,
            Plus,
            Eye,
            DataTable,
            SearchField,
            NamespaceSelect,
            Kicon
        },
        data() {
            return {
                isDefaultNamespaceAllow: true,
                permission: permission,
                action: action,
            };
        },
        computed: {
            ...mapState("template", ["templates", "total"]),
            ...mapState("stat", ["dailyGroupByFlow", "daily"]),
            ...mapState("auth", ["user"]),
            routeInfo() {
                return {
                    title: this.$t("templates")
                };
            },
            fields() {
                const title = (title) => {
                    return this.$t(title);
                };
                return [
                    {
                        key: "id",
                        label: title("template"),
                        sortable: true,
                    },
                    {
                        key: "namespace",
                        label: title("namespace"),
                        sortable: true
                    },
                    {
                        key: "actions",
                        label: "",
                        class: "row-action",
                    },
                ];
            },
        },
        methods: {
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
                this.$store
                    .dispatch("template/findTemplates", {
                        q: this.loadQuery(),
                        size: parseInt(this.$route.query.size || 25),
                        page: parseInt(this.$route.query.page || 1),
                        sort: this.$route.query.sort,
                    })
                    .then(() => {
                        callback();
                    });
            },
        },
    };
</script>
