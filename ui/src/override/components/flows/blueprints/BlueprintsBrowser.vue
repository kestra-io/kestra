<template>
    <Errors code="404" v-if="error && embed" />
    <div v-else>
        <slot name="nav" />
        <slot name="content">
            <DataTable class="blueprints" @page-changed="onPageChanged" ref="dataTable" :total="total" divider>
                <template #navbar>
                    <div v-if="ready && !system && !embed">
                        <div class="tags-selection">
                            <el-checkbox-group v-model="selectedTags" class="tags-checkbox-group">
                                <el-checkbox-button
                                    v-for="tag in Object.values(tags || {})"
                                    :key="tag.id"
                                    :label="tag.id"
                                    class="hoverable"
                                >
                                    {{ tag.name }}
                                </el-checkbox-button>
                            </el-checkbox-group>
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
                    <el-row class="mb-3" justify="center">
                        <KSFilter
                            :configuration="blueprintFilter"
                            :buttons="{
                                savedFilters: {shown: false},
                                tableOptions: {shown: false}
                            }"
                            :searchInputFullWidth="true"
                            @search="handleSearch"
                        />
                    </el-row>
                </template>
                <template #table>
                    <el-alert type="info" v-if="ready && (!blueprints || blueprints.length === 0)" :closable="false">
                        {{ $t('blueprints.empty') }}
                    </el-alert>
                    <div class="card-grid">
                        <el-card
                            class="blueprint-card"
                            v-for="blueprint in blueprints"
                            :key="blueprint.id"
                            @click="goToDetail(blueprint.id)"
                        >
                            <div class="card-content-wrapper">
                                <div v-if="!system && blueprint.tags?.length > 0" class="tags-section">
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
                                        <TaskIcon v-for="task in [...new Set(blueprint.includedTasks)]" :key="task" :cls="task" :icons="pluginsStore.icons" />
                                    </div>

                                    <div class="d-flex align-items-center gap-2">
                                        <el-tooltip v-if="embed && !system" trigger="click" content="Copied" placement="left" :autoClose="2000" effect="light">
                                            <el-button
                                                type="primary"
                                                size="default"
                                                :icon="icon.ContentCopy"
                                                @click.prevent.stop="copy(blueprint.id)"
                                                class="p-2"
                                            />
                                        </el-tooltip>
                                        <slot name="buttons" :blueprint="{...blueprint, kind: props.blueprintKind, type: props.blueprintType}">
                                            <el-button v-if="!embed && userCanCreate" type="primary" size="default" @click.prevent.stop="blueprintToEditor(blueprint.id)">
                                                {{ $t('use') }}
                                            </el-button>
                                        </slot>
                                    </div>
                                </div>
                            </div>
                        </el-card>
                    </div>
                </template>
            </DataTable>
            <slot name="bottom-bar" />
        </slot>
    </div>
</template>

<script setup lang="ts">
    import {ref, computed, onMounted, watch} from "vue";
    import {useRoute, useRouter} from "vue-router";
    import {TaskIcon} from "@kestra-io/ui-libs";
    import ContentCopy from "vue-material-design-icons/ContentCopy.vue";
    import DataTable from "../../../../components/layout/DataTable.vue";
    import Errors from "../../../../components/errors/Errors.vue";
    import KSFilter from "../../../../components/filter/components/KSFilter.vue";
    import {editorViewTypes} from "../../../../utils/constants";
    import Utils from "../../../../utils/utils";
    import {usePluginsStore} from "../../../../stores/plugins";
    import {useBlueprintsStore} from "../../../../stores/blueprints";
    import {useCoreStore} from "../../../../stores/core";
    import {useDocStore} from "../../../../stores/doc";
    import {canCreate} from "override/composables/blueprintsPermissions";
    import {useDataTableActions} from "../../../../composables/useDataTableActions";
    import {useBlueprintFilter} from "../../../../components/filter/configurations";

    const blueprintFilter = useBlueprintFilter();

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
        tagsResponseMapper: (tagsResponse: any[]) =>  Object.fromEntries(tagsResponse.map(tag => [tag.id, tag]))
    });

    const {onPageChanged, onDataLoaded, load, ready, internalPageNumber, internalPageSize} = useDataTableActions({loadData});

    const emit = defineEmits(["goToDetail", "loaded"]);

    const route = useRoute();
    const router = useRouter();

    const initSelectedTags = (): string[] => {
        if (!route.query.selectedTag) return [];
        if (Array.isArray(route.query.selectedTag)) {
            return route.query.selectedTag.filter((tag): tag is string => tag !== null);
        }
        return route.query.selectedTag ? [route.query.selectedTag] : [];
    };

    const searchText = ref(route.query.q || "");
    const selectedTags = ref<string[]>(initSelectedTags());
    const tags = ref<Record<string, any> | undefined>(undefined);
    const total = ref(0);
    const blueprints = ref<{
        includedTasks: string[];
        id: string;
        tags: string[];
        title?: string;
        template?: Record<string, any>;
    }[] | undefined>(undefined);
    const error = ref(false);
    const icon = {ContentCopy};

    const handleSearch = (query: string) => {
        searchText.value = query;
        router.push({query: {...route.query, q: query}});
    };

    const pluginsStore = usePluginsStore();
    const blueprintsStore = useBlueprintsStore();
    const coreStore = useCoreStore();
    const docStore = useDocStore();

    const userCanCreate = computed(() => canCreate(props.blueprintKind));

    const processedTags = (blueprintTags: string[]) => {
        return blueprintTags.map(tag => ({
            original: tag,
            display: tags.value?.[tag]?.name ?? tag
        }));
    };

    async function copy(id: string) {
        await Utils.copy(
            await blueprintsStore.getBlueprintSource({
                type: props.blueprintType,
                kind: props.blueprintKind,
                id,
            })
        );
    };

    async function blueprintToEditor (blueprintId: string) {
        localStorage.setItem(editorViewTypes.STORAGE_KEY, editorViewTypes.SOURCE_TOPOLOGY);
        router.push(editorRoute(blueprintId));
    };

    function goToDetail(blueprintId: string) {
        if (props.embed) {
            emit("goToDetail", blueprintId);
        } else {
            router.push({
                name: "blueprints/view",
                params: {
                    tenant: route.params.tenant,
                    kind: props.blueprintKind,
                    tab: route.params.tab,
                    blueprintId: blueprintId
                }
            });
        }
    };

    async function loadTags(beforeLoadBlueprintType: string) {
        const query: Record<string, any> = {};
        if (route.query.q || searchText.value) {
            query.q = route.query.q || searchText.value;
        }
        const data = await blueprintsStore.getBlueprintTags({
            type: props.blueprintType,
            kind: props.blueprintKind,
            ...query,
        });
        if(props.blueprintType === beforeLoadBlueprintType){
            tags.value = props.tagsResponseMapper(data);
        }
    };

    async function loadBlueprints (beforeLoadBlueprintType: string) {
        const query: Record<string, any> = {};
        if (route.query.page || internalPageNumber.value) query.page = parseInt((route.query.page || internalPageNumber.value) as string);
        if (route.query.size || internalPageSize.value) query.size = parseInt((route.query.size || internalPageSize.value) as string);
        if (route.query.q || searchText.value) query.q = route.query.q || searchText.value;
        if (props.system) query.tags = "system";
        else if (selectedTags.value.length > 0) query.tags = selectedTags.value;

        const data = await blueprintsStore.getBlueprints({
            type: props.blueprintType,
            kind: props.blueprintKind,
            params: query,
        });
        if(props.blueprintType === beforeLoadBlueprintType){
            total.value = data.total;
            blueprints.value = data.results;
        }
    };

    async function loadData() {
        const beforeLoadBlueprintType = props.blueprintType;
        try {
            await Promise.all([
                loadTags(beforeLoadBlueprintType),
                loadBlueprints(beforeLoadBlueprintType)
            ]);
            emit("loaded");
            onDataLoaded();
        } catch {
            if (props.embed) error.value = true;
            else coreStore.error = 404;
            onDataLoaded();
        }
    };

    function editorRoute(blueprintId: string) {
        const additionalQuery: Record<string, any> = {};
        if (props.blueprintKind === "flow") {
            additionalQuery.blueprintSource = props.blueprintType;
        }
        return {
            name: `${props.blueprintKind}s/create`,
            params: {tenant: route.params.tenant},
            query: {blueprintId, ...additionalQuery},
        };
    };

    onMounted(() => {
        searchText.value = route.query?.q || "";
        docStore.docId = `blueprints.${props.blueprintType}`;
    });

    watch(route, (newRoute, oldRoute) => {
        if (newRoute.name === oldRoute.name) {
            selectedTags.value = initSelectedTags();
            searchText.value = newRoute.query.q || "";
            load(onDataLoaded);
        }
    });

    watch(searchText, () => {
        load(onDataLoaded);
    });

    watch(selectedTags, (newTags) => {
        if (!props.embed) {
            router.push({
                query: {
                    ...route.query,
                    selectedTag: newTags.length > 0 ? newTags : undefined
                }
            });
        } else {
            load(onDataLoaded);
        }
    });

    watch(tags, (val) => {
        const validTags = selectedTags.value.filter(tagId =>
            Object.prototype.hasOwnProperty.call(val, tagId)
        );
        if (validTags.length !== selectedTags.value.length) {
            selectedTags.value = validTags;
        }
    })

    watch([() => props.blueprintType, () => props.blueprintKind], () => {
        loadData();
    });
</script>

<style scoped lang="scss">
    @use 'element-plus/theme-chalk/src/mixins/mixins' as *;
    @import "@kestra-io/ui-libs/src/scss/variables";

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
            --el-button-bg-color: var(--ks-background-card);

            & > * {
                max-width: 50%;

                :deep(span) {
                    border-radius: $border-radius !important;
                    border: 1px solid var(--ks-border-primary);
                    width: 100%;
                    font-size: var(--el-font-size-extra-small);
                    box-shadow: none;
                    text-overflow: ellipsis;
                    overflow: hidden;
                }

                &:hover :deep(span) {
                    color: var(--ks-content-link-hover);
                    background-color: var(--ks-button-background-secondary-hover);
                }
            }
        }
    }

    .search-bar-row {
        max-width: 800px;
        margin: 0 auto 1.5rem auto;
    }

    .card-grid {
        display: grid;
        grid-template-columns: repeat(auto-fill, minmax(297px, 1fr));
        gap: 1rem;
    }

    .blueprint-card {
        cursor: pointer;
        border: 1px solid var(--ks-border-primary);
        border-radius: 0.25rem;
        background-color: var(--ks-background-card);
        transition: all 0.2s ease;
        display: flex;
        box-shadow: 0px 2px 4px 0px var(--ks-card-shadow);
        min-height: 200px;

        &:hover {
            transform: scale(1.02);
            box-shadow: 0 0.5rem 1rem 0 var(--ks-card-shadow);
        }

        :deep(.icon) {
            width: 24px;
            height: 24px;
        }

        :deep(.el-card__body) {
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
            border: 1px solid var(--ks-border-primary);
            color: var(--ks-content-primary);
            border-radius: 0.25rem;
            padding: 0.25rem 0.5rem;
            font-size: 12px;
            background: var(--ks-tag-background-active);
        }
    }

    .text-section {
        flex-grow: 1;
        margin-top: 0.75rem;

        .title {
            font-size: 1rem;
            font-weight: 600;
            color: var(--ks-content-primary);
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

            :deep(.wrapper) {
                height: 1.5rem;
                width: 1.5rem;
            }
        }
    }
</style>
