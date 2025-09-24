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
                                :placeholder="$t('search')"
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
                    <el-card
                        class="blueprint-card"
                        :class="{'embed': embed}"
                        v-for="blueprint in blueprints"
                        :key="blueprint.id"
                        @click="goToDetail(blueprint.id)"
                    >
                        <component
                            class="blueprint-link"
                            :is="embed ? 'div' : 'router-link'"
                            :to="embed ? undefined : {name: 'blueprints/view', params: {blueprintId: blueprint.id, tab: blueprintType, kind: blueprintKind}}"
                        >
                            <div class="left">
                                <div class="blueprint">
                                    <div
                                        class="ps-0 title"
                                        :class="{'embed-title': embed, 'text-truncate': embed}"
                                    >
                                        {{ blueprint.title ?? blueprint.id }}
                                    </div>
                                    <div v-if="embed" class="tags-w-icons-container">
                                        <div class="tags-w-icons">
                                            <div v-for="(tag, index) in blueprint.tags" :key="index">
                                                <el-tag size="small">
                                                    {{ tag }}
                                                </el-tag>
                                            </div>
                                            <div class="tasks-container">
                                                <TaskIcon
                                                    :icons="pluginsStore.icons"
                                                    :cls="task"
                                                    :key="task"
                                                    v-for="task in [...new Set(blueprint.includedTasks)]"
                                                />
                                            </div>
                                        </div>
                                    </div>
                                    <div v-else-if="!system" class="tags text-uppercase">
                                        <div v-for="(tag, index) in blueprint.tags" :key="index" class="tag-box">
                                            <el-tag size="small">
                                                {{ tag }}
                                            </el-tag>
                                        </div>
                                    </div>
                                </div>
                                <div v-if="!embed" class="tasks-container">
                                    <TaskIcon
                                        :icons="pluginsStore.icons"
                                        :cls="task"
                                        :key="task"
                                        v-for="task in [...new Set(blueprint.includedTasks)]"
                                    />
                                </div>
                            </div>
                            <div class="side buttons ms-auto">
                                <slot name="buttons" :blueprint="blueprint" />
                                <el-tooltip v-if="embed" trigger="click" content="Copied" placement="left" :autoClose="2000" effect="light">
                                    <el-button
                                        type="primary"
                                        size="default"
                                        :icon="icon.ContentCopy"
                                        @click.prevent.stop="copy(blueprint.id)"
                                        class="copy-button p-2"
                                    />
                                </el-tooltip>
                                <el-button v-else-if="userCanCreate" type="primary" size="default" @click.prevent.stop="blueprintToEditor(blueprint.id)">
                                    {{ $t('use') }}
                                </el-button>
                            </div>
                        </component>
                    </el-card>
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

    const {onPageChanged, onDataLoaded, load, ready, internalPageNumber} = useDataTableActions();
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
        if (route.query.page || internalPageNumber) query.page = parseInt(route.query.page as string);
        if (route.query.size || internalPageNumber) query.size = parseInt(route.query.size as string);
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
        display: grid;
        width: 100%;

        .blueprint-card {
            cursor: pointer;
            border-radius: 0;
            border: 0;
            border-bottom: 1px solid var(--ks-border-primary);

            .blueprint {
                display: flex;
                align-items: center;
                flex-wrap: wrap;

                @media (max-width: 1024px) {
                    margin-bottom: 10px;
                }

                .tags-w-icons-container {
                    display: flex;
                    align-items: center;
                    justify-content: space-between;
                    width: 100%;
                    margin-top: 7px;

                    .tags-w-icons {
                        display: flex;
                        align-items: center;
                        gap: .35rem;
                    }
                }
            }

            .el-tag {
                background-color: var(--ks-tag-background);
                padding: 13px 10px;
                color: var(--ks-tag-content);
                text-transform: capitalize;
                font-size: $small-font-size;
                border: 1px solid var(--ks-border-primary);

                html.dark & {
                    background-color: rgba(64, 69, 89, .7);
                }
            }

            &.embed {
                position: relative;
            }

            .blueprint-link {
                display: flex;
                color: inherit;
                text-decoration: inherit;
                align-items: center;
                width: 100%;

                .left {
                    align-items: center;
                    flex: 1;
                    min-width: 0;
                    .title {
                        width: 500px;
                        font-weight: bold;
                        font-size: $small-font-size;
                        padding-left: 0;
                        margin-right: 15px;

                        @media (max-width: 780px) {
                            margin-bottom: 10px;
                        }
                    }

                    .embed-title {
                        width: 100%;
                        white-space: nowrap;
                        overflow: hidden;
                        text-overflow: ellipsis;
                        font-weight: 400;
                    }

                    .tags {
                        margin: 10px 0;
                        display: flex;

                        .tag-box {
                            margin-right: .5rem;
                        }
                    }


                    .tasks-container {
                        $plugin-icon-size: calc(var(--font-size-base) + 0.3rem);
                        display: flex;
                        gap: .25rem;
                        width: fit-content;
                        height: $plugin-icon-size;

                        :deep(> *) {
                            width: $plugin-icon-size;
                        }
                    }
                }


                .side {
                    &.buttons {
                        white-space: nowrap;
                        flex-shrink: 0;
                    }

                    &.copy-button {
                        position: absolute;
                        right: 1rem;
                        transform: translateY(-50%);
                        top: 50%;
                        z-index: 10;
                    }
                }
            }

            @include res(lg) {
                &:not(.embed) .blueprint-link .left {
                    display: flex;
                    width: 100%;

                    > :first-child {
                        flex-grow: 1;
                    }

                    .tags {
                        margin-bottom: 0;
                    }

                    .tasks-container {
                        margin: 0 $spacer;
                        height: 2.0rem;

                        :deep(.wrapper) {
                            width: 2.0rem;
                            height: 2.0rem;
                        }
                    }
                }
            }

            html.dark &.embed {
                background-color: var(--ks-background-card);
            }
        }
    }

    .tags-selection {
        display: flex;
        width: 100%;
        margin-bottom: 1rem;
        gap: .3rem;
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
        }
    }
</style>
