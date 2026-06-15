<template>
    <Errors code="404" v-if="error && embed" />
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
                divider
                noPaginationGutter
                :noGutter="!embed && !system"
                @ready="ready = true"
                @page-changed="onPageChanged"
            >
                <template #navbar>
                    <div v-if="ready && !system && !embed">
                        <div class="tags-selection">
                            <KsCheckboxGroup v-model="selectedTags" class="tags-checkbox-group">
                                <KsCheckboxButton
                                    v-for="tag in Object.values(tags || {})"
                                    :key="tag.id"
                                    :value="tag.id"
                                    class="hoverable"
                                >
                                    {{ tag.name }}
                                </KsCheckboxButton>
                            </KsCheckboxGroup>
                        </div>
                    </div>
                    <nav v-else-if="system" class="header pb-3">
                        <p class="mb-0 fw-lighter">
                            {{ $t("system_namespace") }}
                        </p>
                        <p class="fs-5 fw-semibold">
                            {{ $t("system_namespace_description") }}
                        </p>
                    </nav>
                </template>
                <template #top>
                    <BlueprintsFilterBar
                        v-model="selectedTags"
                        :embed
                        :system
                        :tags
                        @search="handleSearch"
                    />
                </template>
                <template #table>
                    <KsAlert type="info" v-if="ready && (!blueprints || blueprints.length === 0)" :closable="false">
                        {{ $t('blueprints.empty') }}
                    </KsAlert>
                    <div v-if="embed && !system" class="blueprint-list">
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
                        <KsCard
                            class="blueprint-card"
                            v-for="blueprint in blueprints"
                            :key="blueprint.id"
                            @click="goToDetail(blueprint.id)"
                        >
                            <div class="card-content-wrapper">
                                <div v-if="!system && blueprint.tags?.length" class="tags-section">
                                    <span v-for="tag in processedTags(blueprint.tags)" :key="tag.original" class="tag-item">{{ tag.display }}</span>
                                </div>
                                <div v-if="blueprint.template" class="tags-section">
                                    <span class="tag-item">{{ $t('template') }}</span>
                                </div>
                                <div class="text-section">
                                    <h3 class="title">
                                        {{ blueprint.title ?? blueprint.id }}
                                    </h3>
                                </div>
                                <div class="bottom-section">
                                    <div class="task-icons">
                                        <KsTaskIcon v-for="task in [...new Set(blueprint.includedTasks)]" :key="task" :cls="task" :icons="pluginsStore.icons" />
                                    </div>

                                    <div class="d-flex align-items-center gap-2">
                                        <slot name="buttons" :blueprint="{...blueprint, kind: props.blueprintKind, type: props.blueprintType}">
                                            <KsButton v-if="(!embed || system) && userCanCreate" type="primary" size="default" @click.prevent.stop="blueprintToEditor(blueprint.id)">
                                                {{ $t('use') }}
                                            </KsButton>
                                        </slot>
                                    </div>
                                </div>
                            </div>
                        </KsCard>
                    </div>
                </template>
            </KsDataTable>
            <slot name="bottom-bar" />
        </slot>
    </div>
</template>

<script setup lang="ts">
    import {ref, computed, onMounted, onActivated, useTemplateRef, watch} from "vue"
    import {useRoute, useRouter} from "vue-router"
    import {KsTaskIcon} from "@kestra-io/design-system"
    import Errors from "../../../components/errors/Errors.vue"
    import BlueprintListRow from "./BlueprintListRow.vue"
    import BlueprintsFilterBar from "./BlueprintsFilterBar.vue"
    import {editorViewTypes} from "../../../utils/constants"
    import * as Utils from "../../../utils/utils"
    import {usePluginsStore} from "../../../stores/plugins"
    import {useBlueprintsStore} from "../../../stores/blueprints"
    import type {BlueprintTag, FlowBlueprint} from "../../../stores/blueprints"
    import {useApiStore} from "../../../stores/api"
    import {useCoreStore} from "../../../stores/core"
    import {useDocStore} from "../../../stores/doc"
    import {canCreate} from "override/composables/blueprintsPermissions"
    import useRestoreUrl from "../../../composables/useRestoreUrl"

    const {loadInit} = useRestoreUrl()

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
        tagsResponseMapper: (tagsResponse: any[]) =>  Object.fromEntries(tagsResponse.map(tag => [tag.id, tag])),
    })

    const emit = defineEmits(["goToDetail", "loaded"])

    const route = useRoute()
    const router = useRouter()

    const dataTable = useTemplateRef("dataTable")
    const ready = ref(false)

    const SELECTED_TAG_QUERY_KEY = "filters[tags][IN]"

    const initSelectedTags = (): string[] => {
        const raw = route.query[SELECTED_TAG_QUERY_KEY]
        return ([raw].flat().filter(Boolean) as string[]).flatMap(t => t.split(","))
    }

    const searchText = ref(route.query["filters[q][EQUALS]"] ?? "")
    const selectedTags = ref<string[]>(initSelectedTags())
    const tags = ref<Record<string, BlueprintTag> | undefined>(undefined)
    const total = ref(0)
    const blueprints = ref<FlowBlueprint[] | undefined>(undefined)
    const error = ref(false)

    const handleSearch = (query: string) => {
        searchText.value = query
    }

    const urlPage = computed(() => Number(route.query.page) || 1)
    const urlSize = computed(() => Number(route.query.size) || 25)

    const onPageChanged = ({page, size}: {page: number; size: number}) => {
        router.push({query: {...route.query, page: String(page), size: String(size)}})
    }

    const pluginsStore = usePluginsStore()
    const blueprintsStore = useBlueprintsStore()
    const apiStore = useApiStore()
    const coreStore = useCoreStore()
    const docStore = useDocStore()

    const userCanCreate = computed(() => canCreate(props.blueprintKind))

    const processedTags = (blueprintTags?: string[]) => {
        return (blueprintTags ?? []).map(tag => ({
            original: tag,
            display: tags.value?.[tag]?.name ?? tag,
        }))
    }

    async function copy(id: string) {
        await Utils.copy(
            await blueprintsStore.getBlueprintSource({
                type: props.blueprintType,
                kind: props.blueprintKind,
                id,
            }),
        )
    };

    async function blueprintToEditor (blueprintId: string) {
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
    };

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
                    blueprintId: blueprintId,
                },
            })
        }
    };

    async function loadTags(beforeLoadBlueprintType: string) {
        const query: Record<string, any> = {}
        if (route.query["filters[q][EQUALS]"] ?? searchText.value) {
            query.q = route.query["filters[q][EQUALS]"] ?? searchText.value
        }
        const data = await blueprintsStore.getBlueprintTags({
            type: props.blueprintType,
            kind: props.blueprintKind,
            ...query,
        })
        if(props.blueprintType === beforeLoadBlueprintType){
            tags.value = props.tagsResponseMapper(data)
        }
    };

    async function loadBlueprints (beforeLoadBlueprintType: string, page: number, size: number) {
        const query: Record<string, any> = {}
        if (page) query.page = page
        if (size) query.size = size
        if (route.query["filters[q][EQUALS]"] || searchText.value) query.q = route.query["filters[q][EQUALS]"] || searchText.value
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
        if(props.blueprintType === beforeLoadBlueprintType){
            total.value = data.total
            blueprints.value = data.results
        }
    };

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
    };

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
    };

    const syncFromRoute = () => {
        searchText.value = route.query?.["filters[q][EQUALS]"] ?? ""
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
        () => [route.query[SELECTED_TAG_QUERY_KEY], route.query["filters[q][EQUALS]"]],
        () => {
            syncFromRoute()
            dataTable.value?.resetAndReload()
        },
    )

    watch(searchText, () => {
        dataTable.value?.resetAndReload()
    })

    watch(selectedTags, (newTags) => {
        if (!props.embed) {
            const query = {...route.query}
            if (newTags.length > 0) {
                query[SELECTED_TAG_QUERY_KEY] = newTags.join(",")
            } else {
                delete query[SELECTED_TAG_QUERY_KEY]
            }
            router.push({query})
        } else {
            dataTable.value?.resetAndReload()
        }
    })

    watch(tags, (val) => {
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
    .tags-selection {
        display: flex;
        width: 100%;
        margin-bottom: 1rem;
        gap: .3rem;
        flex-wrap: wrap;

        .tags-checkbox-group {
            display: flex;
            width: 100%;
            gap: .5rem;
            flex-wrap: wrap;
            --kel-button-bg-color: var(--ks-bg-surface);

            & > * {
                max-width: 50%;

                :deep(span) {
                    border-radius: 0.25rem !important;
                    border: 1px solid var(--ks-border-default);
                    width: 100%;
                    font-size: var(--ks-font-size-xs);
                    box-shadow: none;
                    text-overflow: ellipsis;
                    overflow: hidden;
                }

                &:hover :deep(span) {
                    color: var(--ks-text-link);
                    background-color: var(--ks-btn-secondary-bg-hover);
                }
            }
        }
    }

    .card-grid {
        display: grid;
        grid-template-columns: repeat(auto-fill, minmax(297px, 1fr));
        gap: 1rem;

        &.system {
            padding-inline: var(--ks-data-table-gutter);
        }
    }

    .blueprint-list {
        display: flex;
        flex-direction: column;
    }

    .blueprint-card {
        cursor: pointer;
        border: 1px solid var(--ks-border-default);
        border-radius: var(--ks-radius-base);
        background-color: var(--ks-bg-surface);
        transition: all 0.2s ease;
        display: flex;
        box-shadow: 0px 2px 4px 0px var(--ks-shadow-element);
        min-height: 200px;

        &:hover {
            transform: scale(1.02);
            box-shadow: 0 0.5rem 1rem 0 var(--ks-shadow-element);
        }

        :deep(.icon) {
            width: 24px;
            height: 24px;
        }

        :deep(.kel-card__body) {
            height: 100%;
            width: 100%;
        }
    }

    .card-content-wrapper {
        display: flex;
        flex-direction: column;
        height: 100%;
        width: 100%;
    }

    .tags-section {
        display: flex;
        flex-wrap: wrap;
        gap: 0.25rem;

        .tag-item {
            border: 1px solid var(--ks-border-default);
            color: var(--ks-text-primary);
            border-radius: var(--ks-radius-base);
            padding: 0.25rem 0.5rem;
            font-size: var(--ks-font-size-xs);
            background: var(--ks-bg-tag-active);
        }
    }

    .text-section {
        flex-grow: 1;
        margin-top: 0.75rem;

        .title {
            font-size: var(--ks-font-size-base);
            font-weight: 600;
            color: var(--ks-text-primary);
            line-height: 22px;
            overflow-wrap: break-word;
        }
    }

    .bottom-section {
        margin-top: 1.5rem;
        display: flex;
        justify-content: space-between;
        align-items: center;
        gap: 1rem;

        .task-icons {
            display: flex;
            gap: 0.5rem;
            align-items: center;
            flex: 1;
            flex-wrap: wrap;

            :deep(.ks-task-icon) {
                height: 1.5rem;
                width: 1.5rem;
            }
        }
    }
</style>
