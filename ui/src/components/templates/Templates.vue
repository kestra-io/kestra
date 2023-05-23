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
                        @selection-change="handleSelectionChange"
                    >
                        <el-table-column type="selection" v-if="(canRead)" />

                        <el-table-column prop="id" sortable="custom" :sort-orders="['ascending', 'descending']" :label="$t('id')">
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

                        <el-table-column prop="namespace" sortable="custom" :sort-orders="['ascending', 'descending']" :label="$t('namespace')" :formatter="(_, __, cellValue) => $filters.invisibleSpace(cellValue)" />

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
                <ul v-if="templatesSelection.length !== 0 && canRead">
                    <bottom-line-counter v-model="queryBulkAction" :selections="templatesSelection" :total="total" @update:model-value="selectAll()">
                        <el-button v-if="canRead" :icon="Download" size="large" @click="exportTemplates()">
                            {{ $t('export') }}
                        </el-button>
                        <el-button v-if="canDelete" @click="deleteTemplates" size="large" :icon="TrashCan">
                            {{ $t('delete') }}
                        </el-button>
                    </bottom-line-counter>
                </ul>

                <li class="spacer" />
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
        </bottom-line>
    </div>
</template>

<script setup>
    import Plus from "vue-material-design-icons/Plus.vue";
    import Download from "vue-material-design-icons/Download.vue";
    import TrashCan from "vue-material-design-icons/TrashCan.vue";
</script>

<script>
    import {mapState} from "vuex";
    import permission from "../../models/permission";
    import action from "../../models/action";
    import NamespaceSelect from "../namespace/NamespaceSelect.vue";
    import Eye from "vue-material-design-icons/Eye.vue";
    import BottomLine from "../layout/BottomLine.vue";
    import RouteContext from "../../mixins/routeContext";
    import DataTableActions from "../../mixins/dataTableActions";
    import DataTable from "../layout/DataTable.vue";
    import SearchField from "../layout/SearchField.vue";
    import Kicon from "../Kicon.vue"
    import RestoreUrl from "../../mixins/restoreUrl";
    import _merge from "lodash/merge";
    import MarkdownTooltip from "../../components/layout/MarkdownTooltip.vue";
    import BottomLineCounter from "../layout/BottomLineCounter.vue";
    import Upload from "vue-material-design-icons/Upload.vue";

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
            BottomLineCounter,
            Upload
        },
        data() {
            return {
                isDefaultNamespaceAllow: true,
                permission: permission,
                action: action,
                templatesSelection: [],
                queryBulkAction: false
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
            handleSelectionChange(val) {
                if (val.length === 0) {
                    this.queryBulkAction = false
                }
                this.templatesSelection = val.map(x => {
                    return {
                        id: x.id,
                        namespace: x.namespace
                    }
                });
            },
            selectAll() {
                if (this.$refs.table.getSelectionRows().length !== this.$refs.table.data.length) {
                    this.$refs.table.toggleAllSelection();
                }
            },
            exportTemplates() {
                this.$toast().confirm(
                    this.$t("template export", {"templateCount": this.queryBulkAction ? this.total : this.templatesSelection.length}),
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
                                .dispatch("template/exportTemplateByIds", {ids: this.templatesSelection})
                                .then(_ => {
                                    this.$toast().success(this.$t("templates exported"));
                                })
                        }
                    },
                    () => {}
                )
            },
            importTemplates() {
                const formData = new FormData();
                formData.append("fileUpload", this.$refs.file.files[0]);
                this.$store
                    .dispatch("template/importTemplates", formData)
                    .then(_ => {
                        this.$toast().success(this.$t("templates imported"));
                        this.loadData(() => {})
                    })
            },
            deleteTemplates(){
                this.$toast().confirm(
                    this.$t("template delete", {"templateCount": this.queryBulkAction ? this.total : this.templatesSelection.length}),
                    () => {
                        if (this.queryBulkAction) {
                            return this.$store
                                .dispatch("template/deleteTemplateByQuery", this.loadQuery({
                                    namespace: this.$route.query.namespace ? [this.$route.query.namespace] : undefined,
                                    q: this.$route.query.q ? [this.$route.query.q] : undefined,
                                }, false))
                                .then(r => {
                                    this.$toast().success(this.$t("templates deleted", {count: r.data.count}));
                                    this.loadData(() => {})
                                })
                        } else {
                            return this.$store
                                .dispatch("template/deleteTemplateByIds", {ids: this.templatesSelection})
                                .then(r => {
                                    this.$toast().success(this.$t("templates deleted", {count: r.data.count}));
                                    this.loadData(() => {})
                                })
                        }
                    },
                    () => {}
                )
            },
        },
    };
</script>

