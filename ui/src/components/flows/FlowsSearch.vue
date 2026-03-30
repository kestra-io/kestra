<template>
    <TopNavBar :title="routeInfo.title" :breadcrumb="routeInfo.breadcrumb" />
    <section class="container">
        <div>
            <ks-data-table
                ref="dataTable"
                :load-data="loadData"
                @ready="ready = true"
                @page-changed="({page, size}) => router.push({query: {...route.query, page: String(page), size: String(size)}})"
                striped
                hover
                :total="flowStore.total"
            >
                <template #navbar>
                    <ks-form-item>
                        <SearchField />
                    </ks-form-item>
                    <ks-form-item>
                        <NamespaceSelect
                            v-if="$route.name !== 'flows/update'"
                            data-type="flow"
                            v-model="namespace"
                            @update:model-value="onNamespaceChange"
                        />
                    </ks-form-item>
                </template>

                <template #table>
                    <template v-for="(item, _i) in flowStore.search" :key="`card-${_i}`">
                        <ks-card class="mb-2" shadow="never">
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
                        </ks-card>
                    </template>

                    <NoData v-if="flowStore.search === undefined || flowStore.search.length === 0" />
                </template>
            </ks-data-table>
        </div>
    </section>
</template>

<script setup lang="ts">
    import {ref, computed, watch, useTemplateRef} from "vue";
    import {useI18n} from "vue-i18n";
    import {useRoute, useRouter} from "vue-router";
    import _escape from "lodash/escape";
    import NoData from "../layout/NoData.vue";
    import TopNavBar from "../layout/TopNavBar.vue";
import SearchField from "../layout/SearchField.vue";
    import NamespaceSelect from "../namespaces/components/NamespaceSelect.vue";
    import useRouteContext from "../../composables/useRouteContext";

    import {useFlowStore} from "../../stores/flow";

    const {t} = useI18n();
    const route = useRoute();
    const router = useRouter();
    const flowStore = useFlowStore();
    const dataTable = useTemplateRef("dataTable");
    const ready = ref(false);

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

    const namespace = computed({
        get: () => route.query?.namespace as [],
        set: (val) => onNamespaceChange(val)
    });

    function onNamespaceChange(val: any) {
        const query = {...route.query};
        if (val === undefined || val === "" || val === null || (Array.isArray(val) && val.length === 0)) {
            delete query["namespace"];
        } else {
            query["namespace"] = val;
        }
        delete query["page"];
        router.push({query});
    }

    async function loadData({page, size}: {page: number; size: number; sort?: string}) {
        const {page: _p, size: _s, sort: _so, ...filters} = route.query;
        const params = {page, size, ...filters};
        await flowStore.searchFlows(params).finally(() => {
            if (!params.q) {
                flowStore.total = 0;
                flowStore.search = undefined;
            }
        });
    }

    const filterQuery = computed(() => {
        const {page: _p, size: _s, sort: _so, ...filters} = route.query
        return filters
    })
    watch(filterQuery, () => {
        dataTable.value?.resetAndReload()
    }, {deep: true})

    function sanitize(content: string) {
        return _escape(content)
            .replaceAll("[mark]", "<mark>")
            .replaceAll("[/mark]", "</mark>");
    }
</script>
