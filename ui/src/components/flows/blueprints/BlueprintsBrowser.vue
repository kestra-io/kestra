<template>
    <Errors v-if="error && embed" code="404" />
    <div v-else>
        <slot name="nav" />
        <slot name="content">
            <KsDataTable
                ref="dataTable"
                class="blueprints"
                :loadData="loadData"
                :total="total"
                :currentPage="urlPage"
                :pageSize="urlSize"
                :noGutter="!embed && !system"
                @ready="ready = true"
                @page-changed="onPageChanged"
            >
                <template #navbar>
                    <nav v-if="system" class="system-header">
                        <p>{{ $t("system_namespace") }}</p>
                        <p>{{ $t("system_namespace_description") }}</p>
                    </nav>
                </template>

                <template #top>
                    <div class="toolbar" :class="{plain: embed || system}">
                        <BlueprintsFilterBar
                            v-model="selectedTags"
                            :class="{search: !embed && !system}"
                            :embed
                            :system
                            :tags
                            :inline="!embed && !system"
                            @search="handleSearch"
                        />
                        <div v-if="ready && !system && !embed" class="tags">
                            <KsCheckTag
                                v-for="tag in tagList"
                                :key="tag.id"
                                pill
                                :checked="selectedTags.includes(tag.id)"
                                @change="toggleTag(tag.id)"
                            >
                                {{ tag.name }}
                            </KsCheckTag>
                        </div>
                    </div>
                </template>

                <template #table>
                    <KsNoData
                        v-if="isEmpty"
                        :title="$t('blueprints.empty')"
                    />
                    <div v-else-if="embed && !system" class="blueprint-list">
                        <BlueprintListRow
                            v-for="blueprint in blueprints"
                            :key="blueprint.id"
                            :blueprint
                            :tags
                            @click="goToDetail(blueprint.id)"
                            @copy="copy(blueprint.id)"
                        />
                    </div>
                    <div v-else class="card-grid" :class="{system}">
                        <BlueprintCard
                            v-for="blueprint in blueprints"
                            :key="blueprint.id"
                            :blueprint
                            :embed
                            :system
                            :tags
                            :blueprintKind
                            :blueprintType
                            :icons="pluginsStore.icons"
                            @click="goToDetail(blueprint.id)"
                            @use="blueprintToEditor(blueprint.id)"
                        >
                            <template v-if="$slots.buttons" #buttons="slotProps">
                                <slot name="buttons" :blueprint="slotProps.blueprint" />
                            </template>
                        </BlueprintCard>
                    </div>
                </template>
            </KsDataTable>

            <slot name="bottom-bar" />
        </slot>
    </div>
</template>

<script setup lang="ts">
    import {computed, onActivated, onMounted, ref, useTemplateRef, watch} from "vue"
    import {useRoute, useRouter} from "vue-router"

    import Errors from "../../../components/errors/Errors.vue"
    import BlueprintCard from "./BlueprintCard.vue"
    import BlueprintListRow from "./BlueprintListRow.vue"
    import BlueprintsFilterBar from "./BlueprintsFilterBar.vue"

    import {useApiStore} from "../../../stores/api"
    import {useBlueprintsStore} from "../../../stores/blueprints"
    import {useCoreStore} from "../../../stores/core"
    import {useDocStore} from "../../../stores/doc"
    import {usePluginsStore} from "../../../stores/plugins"

    import useRestoreUrl from "../../../composables/useRestoreUrl"
    import {editorViewTypes} from "../../../utils/constants"
    import * as Utils from "../../../utils/utils"

    import type {BlueprintTag, FlowBlueprint} from "../../../stores/blueprints"

    const SELECTED_TAG_QUERY_KEY = "filters[tags][IN]"
    const SEARCH_QUERY_KEY = "filters[q][EQUALS]"

    const props = withDefaults(defineProps<{
        blueprintType?: "community" | "custom";
        blueprintKind?: "flow" | "dashboard" | "app";
        embed?: boolean;
        system?: boolean;
        tagsResponseMapper?: (tagsResponse: any[]) => Record<string, any>;
    }>(), {
        blueprintType: "community",
        blueprintKind: "flow",
        embed: false,
        system: false,
        tagsResponseMapper: (tagsResponse: any[]) =>
            Object.fromEntries(tagsResponse.map((tag: any) => [tag.id, tag])),
    })

    const emit = defineEmits<{
        goToDetail: [blueprintId: string];
        loaded: [];
    }>()

    const route = useRoute()
    const router = useRouter()

    const apiStore = useApiStore()
    const blueprintsStore = useBlueprintsStore()
    const coreStore = useCoreStore()
    const docStore = useDocStore()
    const pluginsStore = usePluginsStore()

    const {loadInit} = useRestoreUrl()
    const dataTable = useTemplateRef("dataTable")

    const initSelectedTags = (): string[] => {
        const raw = route.query[SELECTED_TAG_QUERY_KEY]
        return ([raw].flat().filter(Boolean) as string[]).flatMap(t => t.split(","))
    }

    const ready = ref(false)
    const total = ref(0)
    const error = ref(false)
    const blueprints = ref<FlowBlueprint[]>()
    const tags = ref<Record<string, BlueprintTag>>()
    const searchText = ref(route.query[SEARCH_QUERY_KEY] ?? "")
    const selectedTags = ref<string[]>(initSelectedTags())

    const urlPage = computed(() => Number(route.query.page) || 1)
    const urlSize = computed(() => Number(route.query.size) || 25)
    const tagList = computed(() => Object.values(tags.value ?? {}))
    const isEmpty = computed(() => ready.value && !blueprints.value?.length)

    const handleSearch = (query: string) => {
        searchText.value = query
    }

    const toggleTag = (tagId: string) => {
        selectedTags.value = selectedTags.value.includes(tagId)
            ? selectedTags.value.filter(id => id !== tagId)
            : [...selectedTags.value, tagId]
    }

    const onPageChanged = ({page, size}: {page: number; size: number}) => {
        router.push({query: {...route.query, page: String(page), size: String(size)}})
    }

    async function copy(id: string) {
        await Utils.copy(
            await blueprintsStore.getBlueprintSource({
                type: props.blueprintType,
                kind: props.blueprintKind,
                id,
            }),
        )
    }

    function editorRoute(blueprintId: string) {
        const additionalQuery: Record<string, any> = {}
        if (props.blueprintKind === "flow") {
            additionalQuery.blueprintSource = props.blueprintType
        }
        return {
            name: `${props.blueprintKind}s/create`,
            params: {tenant: route.params.tenant},
            query: {blueprintId, ...additionalQuery},
        }
    }

    function blueprintToEditor(blueprintId: string) {
        apiStore.posthogEvents({
            type: "BLUEPRINT",
            action: "use_click",
            blueprint: {
                id: blueprintId,
                kind: props.blueprintKind,
                type: props.blueprintType,
                source: "browser",
            },
        })
        localStorage.setItem(editorViewTypes.STORAGE_KEY, editorViewTypes.SOURCE_TOPOLOGY)
        router.push(editorRoute(blueprintId))
    }

    function goToDetail(blueprintId: string) {
        if (props.embed) {
            emit("goToDetail", blueprintId)
        } else {
            router.push({
                name: "blueprints/view",
                params: {
                    tenant: route.params.tenant,
                    kind: props.blueprintKind,
                    tab: props.blueprintType,
                    blueprintId,
                },
            })
        }
    }

    async function loadTags(beforeLoadBlueprintType: string) {
        const query: Record<string, any> = {}
        if (route.query[SEARCH_QUERY_KEY] ?? searchText.value) {
            query.q = route.query[SEARCH_QUERY_KEY] ?? searchText.value
        }
        const data = await blueprintsStore.getBlueprintTags({
            type: props.blueprintType,
            kind: props.blueprintKind,
            ...query,
        })
        if (props.blueprintType === beforeLoadBlueprintType) {
            tags.value = props.tagsResponseMapper(data)
        }
    }

    async function loadBlueprints(beforeLoadBlueprintType: string, page: number, size: number) {
        const query: Record<string, any> = {}
        if (page) query.page = page
        if (size) query.size = size
        if (route.query[SEARCH_QUERY_KEY] || searchText.value) {
            query.q = route.query[SEARCH_QUERY_KEY] || searchText.value
        }
        if (props.system) {
            query.tags = "system"
        } else {
            const tagsFromRoute = initSelectedTags()
            const tagsToUse = tagsFromRoute.length > 0 ? tagsFromRoute : selectedTags.value
            if (tagsToUse.length > 0) {
                query.tags = tagsToUse
            }
        }

        const data = await blueprintsStore.getBlueprints({
            type: props.blueprintType,
            kind: props.blueprintKind,
            params: query,
        })
        if (props.blueprintType === beforeLoadBlueprintType) {
            total.value = data.total
            blueprints.value = data.results
        }
    }

    async function loadData({page, size}: {page: number; size: number; sort?: string}) {
        // Skip while useRestoreUrl is restoring the URL — the route.query
        // watcher will trigger resetAndReload once the restore lands.
        if (!loadInit.value) return
        const beforeLoadBlueprintType = props.blueprintType
        try {
            await Promise.all([
                loadTags(beforeLoadBlueprintType),
                loadBlueprints(beforeLoadBlueprintType, page, size),
            ])
            emit("loaded")
        } catch {
            if (props.embed) error.value = true
            else coreStore.error = 404
        }
    }

    const syncFromRoute = () => {
        searchText.value = route.query?.[SEARCH_QUERY_KEY] ?? ""
        const newTags = initSelectedTags()
        const same = newTags.length === selectedTags.value.length
            && newTags.every((t, i) => t === selectedTags.value[i])
        if (!same) {
            selectedTags.value = newTags
        }
    }

    onMounted(() => {
        syncFromRoute()
        docStore.docId = `blueprints.${props.blueprintType}`
    })

    onActivated(() => {
        syncFromRoute()
        dataTable.value?.resetAndReload()
    })

    watch(
        () => [route.query[SELECTED_TAG_QUERY_KEY], route.query[SEARCH_QUERY_KEY]],
        () => {
            syncFromRoute()
            dataTable.value?.resetAndReload()
        },
    )

    watch(searchText, () => {
        dataTable.value?.resetAndReload()
    })

    watch(selectedTags, newTags => {
        if (props.embed) {
            dataTable.value?.resetAndReload()
            return
        }
        const query = {...route.query}
        if (newTags.length > 0) {
            query[SELECTED_TAG_QUERY_KEY] = newTags.join(",")
        } else {
            delete query[SELECTED_TAG_QUERY_KEY]
        }
        router.push({query})
    })

    watch(tags, val => {
        const validTags = selectedTags.value.filter(tagId =>
            Object.prototype.hasOwnProperty.call(val, tagId),
        )
        if (validTags.length !== selectedTags.value.length) {
            selectedTags.value = validTags
        }
    })

    watch([() => props.blueprintType, () => props.blueprintKind], () => {
        dataTable.value?.resetAndReload()
    })

    defineExpose({
        reload: () => dataTable.value?.reload(),
    })
</script>

<style scoped lang="scss">
    .blueprints {
        width: 100%;
    }

    .system-header {
        padding-bottom: var(--ks-spacing-4);

        p:first-child {
            margin-bottom: 0;
            font-weight: lighter;
        }

        p:last-child {
            font-size: var(--ks-font-size-xl);
            font-weight: var(--ks-font-weight-semibold);
        }
    }

    .toolbar {
        display: flex;
        flex-wrap: wrap;
        align-items: center;
        gap: var(--ks-spacing-3);
        margin-bottom: var(--ks-spacing-4);

        &.plain {
            display: contents;
        }

        .search {
            flex: 1 1 17rem;
            max-width: 17rem;
            margin-bottom: calc(-1 * var(--ks-spacing-4));
        }

        .tags {
            display: flex;
            flex: 1 1 auto;
            flex-wrap: wrap;
            gap: var(--ks-spacing-2);
        }
    }

    .card-grid {
        display: grid;
        grid-template-columns: repeat(auto-fill, minmax(297px, 1fr));
        gap: var(--ks-spacing-4);

        &.system {
            padding-inline: var(--ks-data-table-gutter);
        }
    }

    .blueprint-list {
        display: flex;
        flex-direction: column;
    }
</style>
