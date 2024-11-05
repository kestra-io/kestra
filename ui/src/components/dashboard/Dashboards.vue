<template>
    <top-nav-bar :title="routeInfo.title">
        <template #additional-right>
            <ul>
                <li>
                    <router-link :to="{name: 'dashboards/create'}">
                        <el-button :icon="Plus" type="primary">
                            {{ $t("create") }}
                        </el-button>
                    </router-link>
                </li>
            </ul>
        </template>
    </top-nav-bar>

    <section data-component="FILENAME_PLACEHOLDER" class="container" v-if="ready">
        <div>
            <data-table
                @page-changed="onPageChanged"
                ref="dataTable"
                :total="total"
            >
                <template #navbar />

                <template #table v-if="dashboards.length">
                    <select-table
                        ref="selectTable"
                        :data="dashboards"
                        :default-sort="{prop: 'id', order: 'ascending'}"
                        stripe
                        table-layout="auto"
                        fixed
                        @row-dblclick="onRowDoubleClick"
                        @sort-change="onSort"
                        :row-class-name="rowClasses"
                        @selection-change="handleSelectionChange"
                        :selectable="false"
                    >
                        <template #default>
                            <el-table-column
                                prop="id"
                                sortable="custom"
                                :sort-orders="['ascending', 'descending']"
                                :label="$t('id')"
                            >
                                <template #default="scope">
                                    <div class="dashboard-id">
                                        <router-link
                                            :to="{name: 'dashboards/update', params: {id: scope.row.id}}"
                                            class="me-1"
                                        >
                                            {{ $filters.invisibleSpace(scope.row.id) }}
                                        </router-link>
                                        <markdown-tooltip
                                            :id="scope.row.id"
                                            :description="scope.row.description"
                                            :title="scope.row.id"
                                        />
                                    </div>
                                </template>
                            </el-table-column>

                            <el-table-column
                                prop="title"
                                sortable="custom"
                                :sort-orders="['ascending', 'descending']"
                                :label="$t('title')"
                                :formatter="(_, __, cellValue) => $filters.invisibleSpace(cellValue)"
                            />

                            <el-table-column
                                prop="description"
                                sortable="custom"
                                :sort-orders="['ascending', 'descending']"
                                :label="$t('description')"
                                :formatter="(_, __, cellValue) => $filters.invisibleSpace(cellValue)"
                            />

                            <el-table-column
                                prop="created"
                                :label="$t('created date')"
                            >
                                <template #default="scope">
                                    <date-ago :inverted="true" :date="scope.row.created" />
                                </template>
                            </el-table-column>

                            <el-table-column
                                prop="updated"
                                :label="$t('updated date')"
                            >
                                <template #default="scope">
                                    <date-ago :inverted="true" :date="scope.row.updated" />
                                </template>
                            </el-table-column>

                            <el-table-column column-key="action" class-name="row-action">
                                <template #default="scope">
                                    <router-link
                                        :to="{name: 'dashboards/update', params : {id: scope.row.id}}"
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
    import Plus from "vue-material-design-icons/Plus.vue";
    import TextSearch from "vue-material-design-icons/TextSearch.vue";
    import DataTable from "../layout/DataTable.vue";
    import Kicon from "../Kicon.vue";
    import TopNavBar from "../layout/TopNavBar.vue";
    import SelectTable from "../layout/SelectTable.vue";
    import MarkdownTooltip from "../layout/MarkdownTooltip.vue";
    import DateAgo from "../layout/DateAgo.vue";
</script>

<script>
    import _merge from "lodash/merge.js";
    import RouteContext from "../../mixins/routeContext.js";
    import RestoreUrl from "../../mixins/restoreUrl.js";
    import DataTableActions from "../../mixins/dataTableActions.js";

    export default {
        mixins: [RouteContext, RestoreUrl, DataTableActions],
        methods: {
            loadQuery(base) {
                let queryFilter = this.queryWithFilter();

                return _merge(base, queryFilter)
            },
            async loadData(callback) {
                const {results, total} = await this.$store.dispatch("dashboard/list", this.loadQuery({
                    size: parseInt(this.$route.query.size || 25),
                    page: parseInt(this.$route.query.page || 1),
                    sort: this.$route.query.sort || "title:asc"
                }));

                this.dashboards = results;
                this.total = total;

                callback();
            }
        },
        data() {
            return {
                dashboards: undefined,
                total: 0
            }
        },
        computed: {
            routeInfo() {
                return {
                    title: this.$t("dashboards")
                };
            },
        }
    }
</script>

<style lang="scss" scoped>
    :deep(nav .dropdown-menu) {
        display: flex;
        width: 20rem;
    }

    .dashboard-id {
        min-width: 200px;
    }

    :deep(.el-select),
    :deep(.el-select-dropdown),
    :deep(.label-filter),
    :deep(.namespace-select),
    :deep(.search-field) {
        .el-input__inner,
        .el-input__wrapper,
        .el-select-dropdown__item,
        .el-tag,
        input {
            font-size: 16px;
        }
    }

</style>