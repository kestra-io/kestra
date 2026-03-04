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
                            v-if="$route.name !== 'flows/update'"
                            data-type="flow"
                            v-model="namespace"
                            @update:model-value="onDataTableValue('namespace', $event)"
                        />
                    </el-form-item>
                </template>

                <template #table>
                    <template v-for="(item, _i) in flowStore.search" :key="`card-${_i}`">
                        <el-card class="mb-2" shadow="never">
                            <template #header>
                                <router-link :to="{path: `/flows/edit/${item.model.namespace}/${item.model.id}/source`}">
                                    {{ item.model.namespace }}.{{ item.model.id }}
                                </router-link>
                            </template>
                            <template v-for="(fragment, _j) in item.fragments" :key="`pre-${_i}-${_j}`">
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
    import {computed} from "vue";
    import {useI18n} from "vue-i18n";
    import {useRoute} from "vue-router";
    import _escape from "lodash/escape";
    import NoData from "../layout/NoData.vue";
    import TopNavBar from "../layout/TopNavBar.vue";
    import DataTable from "../layout/DataTable.vue";
    import SearchField from "../layout/SearchField.vue";
    import NamespaceSelect from "../namespaces/components/NamespaceSelect.vue";
    import useRouteContext from "../../composables/useRouteContext";
    import {useDataTableActions} from "../../composables/useDataTableActions";

    import {useFlowStore} from "../../stores/flow";

    const {t} = useI18n();
    const route = useRoute();
    const flowStore = useFlowStore();

    const routeInfo = computed(() => ({
        title: (route.meta?.title as string) ?? t("source search"),
        breadcrumb: [
            {
                label: t("flows"),
                link: {name: "flows/list"}
            }
        ]
    }));

    useRouteContext(routeInfo);

    const {onPageChanged, onDataTableValue, queryWithFilter, ready} = useDataTableActions({
        loadData
    });

    const namespace = computed({
        get: () => route.query?.namespace as [],
        set: (val) => onDataTableValue("namespace", val)
    });

    function loadData(callback?: () => void) {
        const params = queryWithFilter();
        flowStore.searchFlows(params).finally(() => {
            if (!params.q) {
                flowStore.total = 0;
                flowStore.search = undefined;
            }
            callback?.();
        });
    }

    function sanitize(content: string) {
        return _escape(content)
            .replaceAll("[mark]", "<mark>")
            .replaceAll("[/mark]", "</mark>");
    }
</script>
