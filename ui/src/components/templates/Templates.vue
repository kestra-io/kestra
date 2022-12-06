<template>
    <div v-if="ready">
        <div>
            <data-table
                @page-changed="onPageChanged"
                ref="dataTable"
                :total="total"
            >
                <template #navbar>
                    <el-form-item>
                        <search-field />
                    </el-form-item>
                    <el-form-item>
                        <namespace-select
                            data-type="flow"
                            :value="$route.query.namespace"
                            @update:model-value="onDataTableValue('namespace', $event)"
                        />
                    </el-form-item>
                </template>

                <template #table>
                    <el-table
                        :data="templates"
                        ref="table"
                        :default-sort="{prop: 'id', order: 'ascending'}"
                        stripe
                        table-layout="auto"
                        fixed
                        @row-dblclick="onRowDoubleClick"
                        @sort-change="onSort"
                    >
                        <el-table-column prop="id" sortable="custom" :sort-orders="['ascending', 'descending']" :label="$t('id')">
                            <template #default="scope">
                                <router-link
                                    :to="{name: 'flows/update', params: {namespace: scope.row.namespace, id: scope.row.id}}"
                                >
                                    {{ scope.row.id }}
                                </router-link>
                                &nbsp;<markdown-tooltip
                                :id="scope.row.namespace + '-' + scope.row.id"
                                :description="scope.row.description"
                                :title="scope.row.namespace + '.' + scope.row.id"
                            />
                            </template>
                        </el-table-column>

                        <el-table-column column-key="action" class-name="row-action">
                            <template #default="scope">
                                <router-link :to="{name: 'templates/update', params : {namespace: scope.row.namespace, id: scope.row.id}}">
                                    <kicon :tooltip="$t('details')" placement="left">
                                        <eye />
                                    </kicon>
                                </router-link>
                            </template>
                        </el-table-column>
                    </el-table>
                </template>
            </data-table>
        </div>


        <bottom-line v-if="user && user.hasAnyAction(permission.TEMPLATE, action.CREATE)">
            <ul>
                <li>
                    <router-link :to="{name: 'templates/create'}">
                        <el-button :icon="Plus" type="primary">
                            {{ $t('create') }}
                        </el-button>
                    </router-link>
                </li>
            </ul>
        </bottom-line>
    </div>
</template>

<script setup>
    import Plus from "vue-material-design-icons/Plus";
</script>

<script>
    import {mapState} from "vuex";
    import permission from "../../models/permission";
    import action from "../../models/action";
    import NamespaceSelect from "../namespace/NamespaceSelect";
    import Eye from "vue-material-design-icons/Eye";
    import BottomLine from "../layout/BottomLine";
    import RouteContext from "../../mixins/routeContext";
    import DataTableActions from "../../mixins/dataTableActions";
    import DataTable from "../layout/DataTable";
    import SearchField from "../layout/SearchField";
    import Kicon from "../Kicon"
    import RestoreUrl from "../../mixins/restoreUrl";
    import _merge from "lodash/merge";
    import MarkdownTooltip from "../../components/layout/MarkdownTooltip";

    export default {
        mixins: [RouteContext, RestoreUrl, DataTableActions],
        components: {
            BottomLine,
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
