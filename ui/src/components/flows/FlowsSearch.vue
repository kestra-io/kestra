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
                        data-type="flow"
                        :value="$route.query.namespace"
                        @input="onDataTableValue('namespace', $event)"
                    />
                </template>

                <template #table>
                    <div v-if="search === undefined || search.length === 0">
                        <b-alert variant="light" class="text-muted" show>
                            {{ $t('no result') }}
                        </b-alert>
                    </div>

                    <template v-for="(item, i) in search" :key="`card-${i}`">
                        <b-card class="mb-2">
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
    import _escape from "lodash/escape"
    import _merge from "lodash/merge";

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
