<template>
    <TopNavBar :title="routeInfo.title" :breadcrumb="routeInfo.breadcrumb" />
    <section class="full-container">
        <KsDataTable
            ref="dataTable"
            :loadData="loadData"
            :currentPage="urlPage"
            :pageSize="urlSize"
            @ready="ready = true"
            @page-changed="({page, size}: {page: number; size: number}) => router.push({query: {...route.query, page: String(page), size: String(size)}})"
            striped
            hover
            :total="flowStore.total"
            fitHeight
        >
            <template #navbar>
                <KsFormItem>
                    <SearchField />
                </KsFormItem>
                <KsFormItem>
                    <NamespaceSelect
                        v-if="$route.name !== 'flows/update'"
                        data-type="flow"
                        v-model="namespace"
                        @update:model-value="onNamespaceChange"
                    />
                </KsFormItem>
            </template>

            <template #table>
                <KsSplitter class="search-splitter">
                    <KsSplitterPanel min="20%" size="35%" key="results">
                        <SourceSearchResults
                            :results="flowStore.search"
                            :selectedKey="selectedKey"
                            @select="onSelect"
                            data-test="source-search-results-pane"
                        />
                    </KsSplitterPanel>
                    <KsSplitterPanel min="20%" key="preview">
                        <SourceSearchPreview
                            :selected="selected"
                            :query="searchQuery"
                            data-test="source-search-preview-pane"
                        />
                    </KsSplitterPanel>
                </KsSplitter>
            </template>
        </KsDataTable>
    </section>
</template>

<script setup lang="ts">
    import {ref, computed, watch, useTemplateRef} from "vue"
    import {useI18n} from "vue-i18n"
    import {useRoute, useRouter} from "vue-router"
    import TopNavBar from "../layout/TopNavBar.vue"
    import SearchField from "../layout/SearchField.vue"
    import NamespaceSelect from "../namespaces/components/NamespaceSelect.vue"
    import SourceSearchResults from "./SourceSearchResults.vue"
    import SourceSearchPreview from "./SourceSearchPreview.vue"
    import useRouteContext from "../../composables/useRouteContext"
    import useRestoreUrl from "../../composables/useRestoreUrl"

    import {useFlowStore} from "../../stores/flow"

    const {loadInit} = useRestoreUrl()

    const {t} = useI18n()
    const route = useRoute()
    const router = useRouter()
    const flowStore = useFlowStore()
    const dataTable = useTemplateRef("dataTable")
    const ready = ref(false)
    const selected = ref<{namespace: string; id: string; matchIndex: number} | null>(null)

    const selectedKey = computed(() =>
        selected.value ? `${selected.value.namespace}.${selected.value.id}#${selected.value.matchIndex}` : null,
    )

    const routeInfo = computed(() => ({
        title: (route.meta?.title as string) ?? t("source search"),
        breadcrumb: [
            {
                label: t("flows"),
                link: {name: "flows/list"},
            },
        ],
    }))

    useRouteContext(routeInfo)

    const namespace = computed({
        get: () => route.query?.namespace as [],
        set: (val) => onNamespaceChange(val),
    })

    const searchQuery = computed(() => (route.query.q as string) ?? "")

    function onNamespaceChange(val: any) {
        const query = {...route.query}
        if (val === undefined || val === "" || val === null || (Array.isArray(val) && val.length === 0)) {
            delete query["namespace"]
        } else {
            query["namespace"] = val
        }
        delete query["page"]
        router.push({query})
    }

    async function loadData({page, size}: {page: number; size: number; sort?: string}) {
        if (!loadInit.value) return
        const {page: _p, size: _s, sort: _so, ...filters} = route.query
        const params: {page: number; size: number; [key: string]: any} = {page, size, ...filters}
        await flowStore.searchFlows(params).finally(() => {
            if (!params.q) {
                flowStore.total = 0
                flowStore.search = undefined
            }
        })
    }

    const urlPage = computed(() => Number(route.query.page) || 1)
    const urlSize = computed(() => Number(route.query.size) || 25)

    const filterQueryKey = computed(() => {
        const {page: _p, size: _s, sort: _so, ...filters} = route.query
        return JSON.stringify(filters)
    })

    watch(filterQueryKey, () => {
        selected.value = null
        dataTable.value?.resetAndReload()
    })

    watch(urlPage, () => {
        selected.value = null
    })

    function onSelect(item: {namespace: string; id: string; matchIndex: number}) {
        selected.value = item
    }
</script>

<style scoped lang="scss">
.search-splitter {
    flex: 1;
    min-height: 0;
    overflow: hidden;
}
</style>
