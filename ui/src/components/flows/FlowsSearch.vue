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
                        data-type="flow"
                        :value="$route.query.namespace"
                        @input="onDataTableValue('namespace', $event)"
                    />
                </template>

                <template #table>
                    <div v-if="search === undefined">
                        <b-alert variant="light" show>
                            {{ $t('no result') }}
                        </b-alert>
                    </div>

                    <template v-for="(item, i) in search">
                        <b-card :key="`card-${i}`" class="mb-2">
                            <template #header>
                                <router-link :to="{path: `/flows/edit/${item.model.namespace}/${item.model.id}`, query: {'tab': 'data-source'}}">
                                    {{ item.model.namespace }}.{{ item.model.id }}
                                </router-link>
                            </template>
                            <template v-for="(fragment, j) in item.fragments">
                                <small :key="`pre-${i}-${j}`" >
                                    <pre class="mb-1 text-sm-left" v-html="fragment"></pre>
                                </small>
                            </template>
                        </b-card>

                    </template>
                </template>
            </data-table>
        </div>
    </div>
</template>

<script>
    import {mapState} from "vuex";
    import NamespaceSelect from "../namespace/NamespaceSelect";
    import RouteContext from "../../mixins/routeContext";
    import DataTableActions from "../../mixins/dataTableActions";
    import RestoreUrl from "../../mixins/restoreUrl";
    import DataTable from "../layout/DataTable";
    import SearchField from "../layout/SearchField";
    import qb from "../../utils/queryBuilder";

    export default {
        mixins: [RouteContext, RestoreUrl, DataTableActions],
        components: {
            NamespaceSelect,
            DataTable,
            SearchField,
        },
        data() {
            return {
                isDefaultNamespaceAllow: true,
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
                const query = this.loadQuery();
                if (query !== "*") {
                    this.$store
                        .dispatch("flow/searchFlows", {
                            q: query,
                            size: parseInt(this.$route.query.size || 25),
                            page: parseInt(this.$route.query.page || 1),
                            sort: this.$route.query.sort
                        })
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
