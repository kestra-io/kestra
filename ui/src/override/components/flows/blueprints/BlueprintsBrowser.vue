<template>
    <Errors code="404" v-if="error && embed" />
    <div v-else>
        <slot name="nav" />
        <slot name="content">
            <DataTable class="blueprints" @page-changed="onPageChanged" ref="dataTable" :total="total" hideTopPagination divider>
                <template #navbar>
                    <el-radio-group v-if="ready && !system && !embed" v-model="selectedTag" class="tags-selection">
                        <el-radio-button
                            :key="0"
                            :value="0"
                            class="hoverable"
                        >
                            {{ $t("all tags") }}
                        </el-radio-button>
                        <el-radio-button
                            v-for="tag in Object.values(tags || {})"
                            :key="tag.id"
                            :value="tag.id"
                            class="hoverable"
                            @dblclick.stop="selectedTag = 0"
                        >
                            {{ tag.name }}
                        </el-radio-button>
                    </el-radio-group>
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
                    <el-row class="mb-3 px-3" justify="center">
                        <el-col :xs="24" :sm="18" :md="12" :lg="10" :xl="8">
                            <el-input
                                v-model="searchText"
                                :placeholder="$t('Search or choose filters...')"
                                clearable
                                @input="updateSearch"
                            />
                        </el-col>
                    </el-row>
                </template>
                <template #table>
                    <el-alert type="info" v-if="ready && (!blueprints || blueprints.length === 0)" :closable="false">
                        {{ $t('blueprints.empty') }}
                    </el-alert>
                    <div class="card-grid">
                        <el-card
                            class="blueprint-card"
                            :class="{'embed': embed}"
                            v-for="blueprint in blueprints"
                            :key="blueprint.id"
                            @click="goToDetail(blueprint.id)"
                        >
                            <div class="card-content-wrapper">
                                <div v-if="!system && blueprint.tags?.length > 0" class="tags-section text-uppercase">
                                    <span v-for="tag in blueprint.tags" :key="tag" class="tag-item">{{ tag }}</span>
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

                                    <div>
                                        <el-tooltip v-if="embed" trigger="click" content="Copied" placement="left" :autoClose="2000" effect="light">
                                            <el-button
                                                type="primary"
                                                size="default"
                                                :icon="icon.ContentCopy"
                                                @click.prevent.stop="copy(blueprint.id)"
                                                class="p-2"
                                            />
                                        </el-tooltip>

                                        <el-button v-else-if="userCanCreate" type="primary" size="default" @click.prevent.stop="blueprintToEditor(blueprint.id)">
                                            {{ $t('use') }}
                                        </el-button>
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
    // @ts-expect-error data-table does not have types yet
    import DataTable from "../../../../components/layout/DataTable.vue";
    import Errors from "../../../../components/errors/Errors.vue";
    import {editorViewTypes} from "../../../../utils/constants";
    import Utils from "../../../../utils/utils";
    import {usePluginsStore} from "../../../../stores/plugins";
    import {useBlueprintsStore} from "../../../../stores/blueprints";
    import {useCoreStore} from "../../../../stores/core";
    import {useDocStore} from "../../../../stores/doc";
    import {canCreate} from "override/composables/blueprintsPermissions";
    import {useDataTableActions} from "../../../../composables/useDataTableActions";
    import useRestoreUrl from "../../../../composables/useRestoreUrl";

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
    useRestoreUrl();

    const emit = defineEmits(["goToDetail", "loaded"]);

    const route = useRoute();
    const router = useRouter();

    const initSelectedTag = () => route.query.selectedTag && typeof route.query.selectedTag === "string" ? route.query.selectedTag : 0;

    const searchText = ref(route.query.q || "");
    const selectedTag = ref<number | string>(initSelectedTag());
    const tags = ref<Record<string, any> | undefined>(undefined);
    const total = ref(0);
    const blueprints = ref<{
        includedTasks: string[];
        id: string;
        tags: string[];
        title?: string;
    }[] | undefined>(undefined);
    const error = ref(false);
    const icon = {ContentCopy};

    const pluginsStore = usePluginsStore();
    const blueprintsStore = useBlueprintsStore();
    const coreStore = useCoreStore();
    const docStore = useDocStore();

    const userCanCreate = computed(() => canCreate(props.blueprintKind));

    const updateSearch = (value: string) => {
        router.push({query: {...route.query, q: value || undefined}});
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
        }
    };

    async function loadTags(beforeLoadBlueprintType: string) {
        const query: Record<string, any> = {};
        if (route.query.q || searchText.value) {
            query.q = route.query.q || searchText.value;
        }
        const data = await blueprintsStore.getBlueprintTagsForQuery({
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
        else if (route.query.selectedTag || selectedTag.value) query.tags = route.query.selectedTag || selectedTag.value;

        const data = await blueprintsStore.getBlueprintsForQuery({
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
        } catch {
            if (props.embed) error.value = true;
            else coreStore.error = 404;
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

    watch(route,
          (newValue, oldValue) =>{
              if (oldValue.name === newValue.name) {
                  selectedTag.value = initSelectedTag();
                  searchText.value = route.query.q || "";
              }
          }
    );

    watch(searchText, () => {
        load(onDataLoaded);
    });

    watch(selectedTag, (newSelectedTag) => {
        if (!props.embed) {
            if (newSelectedTag === 0) {
                router.push({
                    query: {
                        ...route.query,
                    }
                });
            }
            router.push({
                query: {
                    ...route.query,
                    selectedTag: newSelectedTag
                }
            });
        } else {
            load(onDataLoaded);
        }
    });

    watch(tags, (val) => {
        if(!Object.prototype.hasOwnProperty.call(val, selectedTag.value)) {
            selectedTag.value = 0;
        }
    })

    watch([() => props.blueprintType, () => props.blueprintKind], () => {
        loadData();
    });
</script>

<style scoped lang="scss">
    @use 'element-plus/theme-chalk/src/mixins/mixins' as *;
    @import "@kestra-io/ui-libs/src/scss/variables";

    .sub-nav {
        margin: 0 0 $spacer;

        > * {
            margin: 0;
        }

        // Two elements => one element on each side
        &:has(> :nth-child(2)) {
            margin: $spacer 0 .5rem 0;

            .el-card & {
                // Enough space not to overlap with switch view when embedded
                margin-top: 1.6rem;


                // Embedded tabs looks weird without cancelling the margin (this brings a top-left tabs with bottom-right search)
                > :nth-child(1) {
                    margin-top: -1.5rem;
                }
            }

            > :nth-last-child(1) {
                margin-left: auto;
                padding: .5rem 0;
            }
        }
    }

    .blueprints {
        width: 100%;
    }

    .card-grid {
        display: grid;
        grid-template-columns: repeat(auto-fill, minmax(350px, 1fr));
        gap: 1.5rem;
    }

    .blueprint-card {
        cursor: pointer;
        border: 1px solid var(--ks-border-primary);
        border-radius: 8px;
        background-color: var(--ks-background-card);
        transition: all 0.2s ease;
        display: flex;

        &:hover {
            border-color: var(--bs-primary);
            box-shadow: 0 4px 16px rgba(var(--bs-primary-rgb), 0.1);
        }

        :deep(.el-card__body) {
            padding: 0;
            height: 100%;
            width: 100%;
        }
    }

    .card-content-wrapper {
        padding: 1.5rem;
        display: flex;
        flex-direction: column;
        height: 100%;
        width: 100%;
    }

    .tags-section {
        display: flex;
        flex-wrap: wrap;
        gap: 0.75rem;
        
        .tag-item {
            background-color: rgba(39, 38, 38, 0.144);
            backdrop-filter: blur(10px);
            border-radius: 10px;
            border: 1px solid rgba(255, 255, 255, 0.2);
            padding: 0.25rem 0.6rem;
            font-size: 0.7rem;
            font-weight: 700;
            color: white;
        }
    }

    .text-section {
        flex-grow: 1;
        margin-top: 0.75rem;
        
        .title {
            font-size: 1rem;
            font-weight: 600;
            color: var(--bs-body-color);
            line-height: 1.4;
            margin: 0;
        }
    }

    .bottom-section {
        margin-top: 1.5rem;
        display: flex;
        justify-content: space-between;
        align-items: center;

        .task-icons {
            display: flex;
            gap: 0.25rem;
            align-items: center;

            :deep(.wrapper) {
                height: 1.5rem;
                width: 1.5rem;
            }
        }
    }
</style>
