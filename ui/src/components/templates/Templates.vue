<template>
    <div v-if="ready">
        <div>
            <data-table
                @onPageChanged="onPageChanged"
                striped
                hover
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
                        :responsive="true"
                        striped
                        hover
                        sort-by="id"
                        :items="templates"
                        :fields="fields"
                        ref="table"
                        show-empty
                    >
                        <template #empty>
                            <span class="text-muted">{{ $t('no result') }}</span>
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
                            &nbsp;<markdown-tooltip
                                :id="row.item.namespace + '-' + row.item.id"
                                :description="row.item.description"
                                :title="row.item.namespace + '.' + row.item.id"
                                :modal="true"
                            />
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
    import RestoreUrl from "../../mixins/restoreUrl";
    import _merge from "lodash/merge";
    import MarkdownTooltip from "@/components/layout/MarkdownTooltip";

    export default {
        mixins: [RouteContext, RestoreUrl, DataTableActions],
        components: {
            BottomLine,
            Plus,
            Eye,
            DataTable,
            SearchField,
            NamespaceSelect,
            Kicon,
            MarkdownTooltip,
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
            loadQuery(base) {
                let queryFilter = this.queryWithFilter();

                return _merge(base, queryFilter)
            },
            loadData(callback) {
                this.$store
                    .dispatch("template/findTemplates", this.loadQuery({
                        size: parseInt(this.$route.query.size || 25),
                        page: parseInt(this.$route.query.page || 1),
                        sort: this.$route.query.sort || "id:asc",
                    }))
                    .then(() => {
                        callback();
                    });
            },
        },
    };
</script>
