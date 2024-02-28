<template>
    <top-nav-bar :title="routeInfo.title">
        <template #additional-right v-if="user && user.hasAnyAction(permission.TEMPLATE, action.CREATE)">
            <ul>
                <li>
                    <div class="el-input el-input-file el-input--large custom-upload">
                        <div class="el-input__wrapper">
                            <label for="importTemplates">
                                <Upload />
                                {{ $t('import') }}
                            </label>
                            <input
                                id="importTemplates"
                                class="el-input__inner"
                                type="file"
                                @change="importTemplates()"
                                ref="file"
                            >
                        </div>
                    </div>
                </li>
                <li>
                    <router-link :to="{name: 'templates/create'}">
                        <el-button :icon="Plus" type="primary" size="large">
                            {{ $t('create') }}
                        </el-button>
                    </router-link>
                </li>
            </ul>
        </template>
    </top-nav-bar>
    <templates-deprecated />
    <section class="container" v-if="ready">
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
                    <select-table
                        ref="selectTable"
                        :data="templates"
                        :default-sort="{prop: 'id', order: 'ascending'}"
                        stripe
                        table-layout="auto"
                        fixed
                        @row-dblclick="onRowDoubleClick"
                        @sort-change="onSort"
                        @selection-change="handleSelectionChange"
                        :selectable="canRead || canDelete"
                    >
                        <template #select-actions>
                            <bulk-select
                                :select-all="queryBulkAction"
                                :selections="selection"
                                :total="total"
                                @update:select-all="toggleAllSelection"
                                @unselect="toggleAllUnselected"
                            >
                                <el-button v-if="canRead" :icon="Download" @click="exportTemplates()">
                                    {{ $t('export') }}
                                </el-button>
                                <el-button v-if="canDelete" @click="deleteTemplates" :icon="TrashCan">
                                    {{ $t('delete') }}
                                </el-button>
                            </bulk-select>
                        </template>
                        <template #default>
                            <el-table-column
                                prop="id"
                                sortable="custom"
                                :sort-orders="['ascending', 'descending']"
                                :label="$t('id')"
                            >
                                <template #default="scope">
                                    <router-link
                                        :to="{name: 'templates/update', params: {namespace: scope.row.namespace, id: scope.row.id}}"
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

                            <el-table-column
                                prop="namespace"
                                sortable="custom"
                                :sort-orders="['ascending', 'descending']"
                                :label="$t('namespace')"
                                :formatter="(_, __, cellValue) => $filters.invisibleSpace(cellValue)"
                            />

                            <el-table-column column-key="action" class-name="row-action">
                                <template #default="scope">
                                    <router-link
                                        :to="{name: 'templates/update', params : {namespace: scope.row.namespace, id: scope.row.id}}"
                                    >
                                        <kicon :tooltip="$t('details')" placement="left">
                                            <TextSearch />
                                        </kicon>
                                    </router-link>
                                </template>
                            </el-table-column>
                        </template>
                    </select-table>
                </template>
            </data-table>
        </div>
    </section>
</template>

<script setup>
    import BulkSelect from "../layout/BulkSelect.vue";
    import SelectTable from "../layout/SelectTable.vue";
    import Plus from "vue-material-design-icons/Plus.vue";
    import Download from "vue-material-design-icons/Download.vue";
    import TrashCan from "vue-material-design-icons/TrashCan.vue";
    import TemplatesDeprecated from "./TemplatesDeprecated.vue";
</script>

<script>
    import {mapState} from "vuex";
    import permission from "../../models/permission";
    import action from "../../models/action";
    import NamespaceSelect from "../namespace/NamespaceSelect.vue";
    import TextSearch from "vue-material-design-icons/TextSearch.vue";
    import RouteContext from "../../mixins/routeContext";
    import TopNavBar from "../../components/layout/TopNavBar.vue";
    import DataTableActions from "../../mixins/dataTableActions";
    import DataTable from "../layout/DataTable.vue";
    import SearchField from "../layout/SearchField.vue";
    import Kicon from "../Kicon.vue"
    import RestoreUrl from "../../mixins/restoreUrl";
    import _merge from "lodash/merge";
    import MarkdownTooltip from "../../components/layout/MarkdownTooltip.vue";
    import Upload from "vue-material-design-icons/Upload.vue";
    import SelectTableActions from "../../mixins/selectTableActions";

    export default {
        mixins: [RouteContext, RestoreUrl, DataTableActions, SelectTableActions],
        components: {
            TextSearch,
            DataTable,
            SearchField,
            NamespaceSelect,
            Kicon,
            MarkdownTooltip,
            Upload,
            TopNavBar
        },
        data() {
            return {
                isDefaultNamespaceAllow: true,
                permission: permission,
                action: action
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
            canRead() {
                return this.user && this.user.isAllowed(permission.FLOW, action.READ);
            },
            canDelete() {
                return this.user && this.user.isAllowed(permission.FLOW, action.DELETE);
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
            selectionMapper(element) {
                return {
                    id: element.id,
                    namespace: element.namespace
                };
            },
            exportTemplates() {
                this.$toast().confirm(
                    this.$t("template export", {"templateCount": this.queryBulkAction ? this.total : this.selection.length}),
                    () => {
                        if (this.queryBulkAction) {
                            return this.$store
                                .dispatch("template/exportTemplateByQuery", this.loadQuery({
                                    namespace: this.$route.query.namespace ? [this.$route.query.namespace] : undefined,
                                    q: this.$route.query.q ? [this.$route.query.q] : undefined,
                                }, false))
                                .then(_ => {
                                    this.$toast().success(this.$t("templates exported"));
                                })
                        } else {
                            return this.$store
                                .dispatch("template/exportTemplateByIds", {ids: this.selection})
                                .then(_ => {
                                    this.$toast().success(this.$t("templates exported"));
                                })
                        }
                    },
                    () => {
                    }
                )
            },
            importTemplates() {
                const formData = new FormData();
                formData.append("fileUpload", this.$refs.file.files[0]);
                this.$store
                    .dispatch("template/importTemplates", formData)
                    .then(_ => {
                        this.$toast().success(this.$t("templates imported"));
                        this.loadData(() => {
                        })
                    })
            },
            deleteTemplates() {
                this.$toast().confirm(
                    this.$t("template delete", {"templateCount": this.queryBulkAction ? this.total : this.selection.length}),
                    () => {
                        if (this.queryBulkAction) {
                            return this.$store
                                .dispatch("template/deleteTemplateByQuery", this.loadQuery({
                                    namespace: this.$route.query.namespace ? [this.$route.query.namespace] : undefined,
                                    q: this.$route.query.q ? [this.$route.query.q] : undefined,
                                }, false))
                                .then(r => {
                                    this.$toast().success(this.$t("templates deleted", {count: r.data.count}));
                                    this.loadData(() => {
                                    })
                                })
                        } else {
                            return this.$store
                                .dispatch("template/deleteTemplateByIds", {ids: this.selection})
                                .then(r => {
                                    this.$toast().success(this.$t("templates deleted", {count: r.data.count}));
                                    this.loadData(() => {
                                    })
                                })
                        }
                    },
                    () => {
                    }
                )
            },
        },
    };
</script>

