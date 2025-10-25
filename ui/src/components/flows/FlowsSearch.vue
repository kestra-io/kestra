<template>
    <TopNavBar :title="routeInfo.title" :breadcrumb="routeInfo.breadcrumb" />
    <section class="container" v-if="ready">
        <div>
            <DataTable
                @page-changed="onPageChanged"
                striped
                hover
                ref="dataTable"
                :total="flowStore.total"
            >
                <template #navbar>
                    <el-form-item>
                        <SearchField />
                    </el-form-item>
                    <el-form-item>
                        <NamespaceSelect
                            data-type="flow"
                            v-if="$route.name !== 'flows/update'"
                            :value="$route.query.namespace"
                            @update:model-value="onDataTableValue('namespace', $event)"
                        />
                    </el-form-item>
                </template>

                <template #table>
                    <template v-if="Array.isArray(flowStore.search) && flowStore.search.length > 0">
                        <template v-for="(item, i) in flowStore.search" :key="`card-${i}`">
                            <el-card class="mb-2" shadow="never">
                                <template #header>
                                    <router-link :to="{path: `/flows/edit/${item.model?.namespace || item.namespace}/${item.model?.id || item.id}/source`}">
                                        {{ item.model?.namespace || item.namespace }}.{{ item.model?.id || item.id }}
                                    </router-link>
                                </template>
                                <!-- Search results with fragments -->
                                <template v-if="item.fragments">
                                    <template v-for="(fragment, j) in item.fragments" :key="`pre-${i}-${j}`">
                                        <small>
                                            <pre class="mb-1 text-sm-left" v-html="sanitize(fragment)" />
                                        </small>
                                    </template>
                                </template>
                                <!-- show labels if available -->
                                <div v-else-if="item.labels && Object.keys(item.labels).length > 0">
                                    <el-tag 
                                        v-for="(value, key) in item.labels" 
                                        :key="key" 
                                        size="small" 
                                        class="me-1"
                                    >
                                        {{ key }}:{{ value }}
                                    </el-tag>
                                </div>
                            </el-card>
                        </template>
                    </template>
                    <NoData v-else />
                </template>
            </DataTable>
        </div>
    </section>
</template>

<script setup lang="ts">
    import {computed, watch} from "vue";
    import {useI18n} from "vue-i18n";
    import {useRoute} from "vue-router";
    import {useFlowStore} from "../../stores/flow";
    import NamespaceSelect from "../namespaces/components/NamespaceSelect.vue";
    import DataTable from "../layout/DataTable.vue";
    import SearchField from "../layout/SearchField.vue";
    import NoData from "../layout/NoData.vue";
    import _escape from "lodash/escape";
    import _merge from "lodash/merge";
    import TopNavBar from "../layout/TopNavBar.vue";

    import useRouteContext from "../../composables/useRouteContext";
    import useRestoreUrl from "../../composables/useRestoreUrl";
    import {useDataTableActions} from "../../composables/useDataTableActions";

    interface LoadQueryParams {
        size: number;
        page: number;
        sort?: string;
    }

    const route = useRoute();

    const {t} = useI18n();

    const flowStore = useFlowStore();

    const routeInfo = computed(() => ({
        title: (route.meta?.title as string) || t("source search") || "source search",
        breadcrumb: [
            {
                label: t("flows") || "flows",
                link: {name: "flows/list"}
            }
        ]
    }));

    useRouteContext(routeInfo);
    const {saveRestoreUrl} = useRestoreUrl({restoreUrl: true, isDefaultNamespaceAllow: true});

    // wire data table actions
    const dataTableActions = useDataTableActions({
        loadData: loadData,
        saveRestoreUrl: saveRestoreUrl
    });

    const {
        onPageChanged,
        onDataTableValue,
        queryWithFilter,
        ready
    } = dataTableActions;

    function sanitize(content: string) {
        return _escape(content)
            .replaceAll("[mark]", "<mark>")
            .replaceAll("[/mark]", "</mark>");
    }

    function loadQuery(base: LoadQueryParams): Record<string, string | number | undefined> {
        const queryFilter = queryWithFilter();
        return _merge(base, queryFilter);
    }

    function loadData(callback?: () => void) {
        const query = route.query;

        if (query.q !== undefined && query.q !== "") {
            // search in source code
            const size = typeof query.size === "string" ? parseInt(query.size) : 25;
            const page = typeof query.page === "string" ? parseInt(query.page) : 1;
            const sort = typeof query.sort === "string" ? query.sort : undefined;

            flowStore
                .searchFlows(loadQuery({
                    size,
                    page,
                    sort
                }))
                .finally(() => {
                    saveRestoreUrl();
                    if (callback) callback();
                });
        } else {
            // show all flows without search
            const size = typeof query.size === "string" ? parseInt(query.size) : 25;
            const page = typeof query.page === "string" ? parseInt(query.page) : 1;
            const sort = typeof query.sort === "string" ? query.sort : "id:asc";

            flowStore
                .findFlows(loadQuery({
                    size,
                    page,
                    sort
                }))
                .then(() => {
                    // Map flows to search format for display
                    if (flowStore.flows) {
                        flowStore.search = flowStore.flows.map((flow: any) => ({
                            namespace: flow.namespace,
                            id: flow.id,
                            labels: flow.labels
                        }));
                    }
                })
                .finally(() => {
                    saveRestoreUrl();
                    if (callback) callback();
                });
        }
    }

    // Watch for route query changes to trigger search
    watch(
        () => route.query,
        (newQuery, oldQuery) => {
            // load data if the query parameter changed
            if (oldQuery && newQuery && oldQuery.q !== newQuery.q) {
                loadData();
            }
        },
        {deep: true}
    );

</script>

