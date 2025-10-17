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
                    <template v-for="(item, i) in flowStore.search" :key="`card-${i}`">
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

                    <NoData v-if="flowStore.search === undefined || flowStore.search.length === 0" />
                </template>
            </DataTable>
        </div>
    </section>
</template>

<script setup lang="ts">
import {computed, type Ref} from "vue";
import {useRoute, useRouter, type LocationQuery} from "vue-router";
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

// Type definitions
interface FlowModel {
    id: string;
    namespace: string;
}

interface SearchResult {
    model: FlowModel;
    fragments: string[];
}

interface RouteInfo {
    title: string;
    breadcrumb: Array<{
        label: string;
        link: { name: string };
    }>;
}

interface LoadQueryParams {
    size: number;
    page: number;
    sort?: string;
}

const route = useRoute();
const router = useRouter();

const flowStore = useFlowStore();

// Provide route info for TopNavBar and document title handling
const routeInfo = computed<RouteInfo>(() => ({
    title: (route.meta?.title as string) || (window as Window & { $t?: (key: string) => string }).$t?.("source search") || "source search",
    breadcrumb: [
        {
            label: (window as Window & { $t?: (key: string) => string }).$t?.("flows") || "flows",
            link: { name: "flows/list" }
        }
    ]
}));

// use composables
useRouteContext(routeInfo as Ref<{title: string}>);
const { loadInit, localStorageName, localStorageValue, saveRestoreUrl, goToRestoreUrl } = useRestoreUrl({ restoreUrl: true, isDefaultNamespaceAllow: true });

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
    const query = route.query as LocationQuery;
    
    if (query.q !== undefined) {
        const size = typeof query.size === 'string' ? parseInt(query.size) : 25;
        const page = typeof query.page === 'string' ? parseInt(query.page) : 1;
        const sort = typeof query.sort === 'string' ? query.sort : undefined;
        
        flowStore
            .searchFlows(loadQuery({
                size,
                page,
                sort
            }))
            .finally(() => {
                saveRestoreUrl();
            })
            .finally(() => {
                if (callback) callback();
            });
    } else {
        flowStore.total = 0;
        // @ts-ignore - allow undefined
        flowStore.search = undefined;
        if (callback) callback();
    }
}

</script>
