<template>
    <top-nav-bar :title="routeInfo.title" :breadcrumb="routeInfo.breadcrumb" />
    <section class="container" v-if="ready">
        <div>
            <data-table
                @page-changed="onPageChanged"
                striped
                hover
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
                            v-if="$route.name !== 'flows/update'"
                            :value="$route.query.namespace"
                            @update:model-value="onDataTableValue('namespace', $event)"
                        />
                    </el-form-item>
                </template>

                <template #table>
                    <div v-if="search === undefined || search.length === 0">
                        <el-alert type="info" class="mb-3" :closable="false">
                            {{ $t('no result') }}
                        </el-alert>
                    </div>

                    <template v-for="(item, i) in search" :key="`card-${i}`">
                        <el-card class="mb-2" shadow="never">
                            <template #header>
                                <router-link :to="{path: `/flows/edit/${item.model.namespace}/${item.model.id}/source`}">
                                    {{ item.model.namespace }}.{{ item.model.id }}
                                </router-link>
                            </template>
                            <template v-for="(fragment, j) in item.fragments" :key="`pre-${i}-${j}`">
                                <small>
                                    <pre class="mb-1 text-sm-left" v-html="sanitize(fragment)" />
                                </small>
                            </template>
                        </el-card>
                    </template>
                </template>
            </data-table>
        </div>
    </section>
</template>

<script>
    import {mapState} from "vuex";
    import NamespaceSelect from "../namespace/NamespaceSelect.vue";
    import RouteContext from "../../mixins/routeContext";
    import DataTableActions from "../../mixins/dataTableActions";
    import RestoreUrl from "../../mixins/restoreUrl";
    import DataTable from "../layout/DataTable.vue";
    import SearchField from "../layout/SearchField.vue";
    import _escape from "lodash/escape"
    import _merge from "lodash/merge";
    import TopNavBar from "../layout/TopNavBar.vue";

    export default {
        mixins: [RouteContext, RestoreUrl, DataTableActions],
        components: {
            NamespaceSelect,
            DataTable,
            SearchField,
            TopNavBar
        },
        data() {
            return {
                isDefaultNamespaceAllow: true
            };
        },
        computed: {
            ...mapState("flow", ["search", "total"]),
            routeInfo() {
                return {
                    title: this.$t("source search"),
                    breadcrumb: [
                        {
                            label: this.$t("flows"),
                            link: {
                                name: "flows/list",
                            }
                        },
                    ]
                };
            }
        },
        methods: {
            sanitize(content) {
                return _escape(content)
                    .replaceAll("[mark]", "<mark>")
                    .replaceAll("[/mark]", "</mark>")
            },
            loadQuery(base) {
                let queryFilter = this.queryWithFilter();

                return _merge(base, queryFilter)
            },
            loadData(callback) {
                if (this.$route.query["q"] !== undefined) {
                    this.$store
                        .dispatch("flow/searchFlows", this.loadQuery({
                            size: parseInt(this.$route.query.size || 25),
                            page: parseInt(this.$route.query.page || 1),
                            sort: this.$route.query.sort
                        }))
                        .finally(() => {
                            this.saveRestoreUrl();
                        })
                        .finally(callback)
                } else {
                    this.$store.commit("flow/setSearch", undefined);
                    callback();
                }

            }
        }
    };
</script>
