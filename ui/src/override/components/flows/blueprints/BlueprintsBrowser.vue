<template>
    <errors code="404" v-if="error && embed" />
    <div v-else>
        <slot name="nav" />
        <slot name="content">
            <data-table class="blueprints" @page-changed="onPageChanged" ref="dataTable" :total="total" hide-top-pagination divider>
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
                    <KestraFilter
                        :prefix="`blueprintsBrowser${blueprintType}`"
                        :placeholder="$t('search')"
                        legacy-query
                    />
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
                                                <task-icon
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
                                    <task-icon
                                        :icons="pluginsStore.icons"
                                        :cls="task"
                                        :key="task"
                                        v-for="task in [...new Set(blueprint.includedTasks)]"
                                    />
                                </div>
                            </div>
                            <div class="side buttons ms-auto">
                                <slot name="buttons" :blueprint="blueprint" />
                                <el-tooltip v-if="embed" trigger="click" content="Copied" placement="left" :auto-close="2000" effect="light">
                                    <el-button
                                        type="primary"
                                        size="default"
                                        :icon="icon.ContentCopy"
                                        @click.prevent.stop="copy(blueprint.id)"
                                        class="copy-button p-2"
                                    />
                                </el-tooltip>
                                <el-button v-else type="primary" size="default" @click.prevent.stop="blueprintToEditor(blueprint.id)">
                                    {{ $t('use') }}
                                </el-button>
                            </div>
                        </component>
                    </el-card>
                </template>
            </data-table>
            <slot name="bottom-bar" />
        </slot>
    </div>
</template>

<script>
    import {shallowRef} from "vue";
    import {mapState} from "vuex";
    import {mapStores} from "pinia";
    import {TaskIcon} from "@kestra-io/ui-libs";
    import ContentCopy from "vue-material-design-icons/ContentCopy.vue";
    import DataTableActions from "../../../../mixins/dataTableActions";
    import DataTable from "../../../../components/layout/DataTable.vue";
    import RestoreUrl from "../../../../mixins/restoreUrl";
    import permission from "../../../../models/permission";
    import action from "../../../../models/action";
    import Utils from "../../../../utils/utils";
    import Errors from "../../../../components/errors/Errors.vue";
    import {editorViewTypes} from "../../../../utils/constants";
    import KestraFilter from "../../../../components/filter/KestraFilter.vue";
    import {usePluginsStore} from "../../../../stores/plugins";

    export default {
        mixins: [RestoreUrl, DataTableActions],
        components: {TaskIcon, DataTable, Errors, KestraFilter},
        emits: ["goToDetail", "loaded"],
        props: {
            blueprintType: {
                type: String,
                default: "community"
            },
            blueprintKind: {
                type: String,
                default: "flow",
            },
            embed: {
                type: Boolean,
                default: false
            },
            system: {
                type: Boolean,
                default: false
            },
            tagsResponseMapper: {
                type: Function,
                default: tagsResponse => Object.fromEntries(tagsResponse.map(tag => [tag.id, tag]))
            }
        },
        mounted() {
            this.$store.commit("doc/setDocId", `blueprints.${this.blueprintType}`);
        },
        data() {
            return {
                q: undefined,
                selectedTag: this.initSelectedTag(),
                tags: undefined,
                total: 0,
                blueprints: undefined,
                icon: {
                    ContentCopy: shallowRef(ContentCopy)
                },
                error: false
            }
        },
        methods: {
            initSelectedTag() {
                return this.$route?.query?.selectedTag ?? 0
            },
            async copy(id) {
                await Utils.copy(
                    (await this.$store.dispatch("blueprints/getBlueprintSource", {type: this.blueprintType, kind: this.blueprintKind, id: id}))
                );
            },
            async blueprintToEditor(blueprintId) {
                localStorage.setItem(editorViewTypes.STORAGE_KEY, editorViewTypes.SOURCE_TOPOLOGY);
                const query = this.blueprintKind === "flow" ?
                    {blueprintId: blueprintId, blueprintSource: this.blueprintType} :
                    {blueprintId: blueprintId};
                this.$router.push({
                    name: `${this.blueprintKind}s/create`,
                    params: {
                        tenant: this.$route.params.tenant
                    },
                    query: query
                });
            },
            goToDetail(blueprintId) {
                if (this.embed) {
                    this.$emit("goToDetail", blueprintId);
                }
            },
            loadTags(beforeLoadBlueprintType) {
                const query = {}
                if (this.$route.query.q || this.q) {
                    query.q = this.$route.query.q || this.q;
                }
                return this.$store.dispatch("blueprints/getBlueprintTagsForQuery", {type: this.blueprintType, kind: this.blueprintKind, ...query})
                    .then(data => {
                        // Handle switch tab while fetching data
                        if (this.blueprintType === beforeLoadBlueprintType) {
                            this.tags = this.tagsResponseMapper(data);
                        }
                    });
            },
            loadBlueprints(beforeLoadBlueprintType) {
                const query = {}

                if (this.$route.query.page || this.internalPageNumber) {
                    query.page = parseInt(this.$route.query.page || this.internalPageNumber);
                }

                if (this.$route.query.size || this.internalPageSize) {
                    query.size = parseInt(this.$route.query.size || this.internalPageSize);
                }

                if (this.$route.query.q || this.q) {
                    query.q = this.$route.query.q || this.q;
                }

                if (this.system) {
                    query.tags = "system";
                } else if (this.$route.query.selectedTag || this.selectedTag) {
                    query.tags = this.$route.query.selectedTag || this.selectedTag;
                }

                return this.$store
                    .dispatch("blueprints/getBlueprintsForQuery", {type: this.blueprintType, kind: this.blueprintKind, params: query})
                    .then(data => {
                        // Handle switch tab while fetching data
                        if (this.blueprintType === beforeLoadBlueprintType) {
                            this.total = data.total;
                            this.blueprints = data.results;
                        }
                    });
            },
            loadData(callback) {
                const beforeLoadBlueprintType = this.blueprintType;

                Promise.all([
                    this.loadTags(beforeLoadBlueprintType),
                    this.loadBlueprints(beforeLoadBlueprintType)
                ]).then(() => {
                    this.$emit("loaded");
                }).catch(() => {
                    if(this.embed) {
                        this.error = true;
                    } else {
                        this.$store.dispatch("core/showError", 404);
                    }
                }).finally(() => {
                    // Handle switch tab while fetching data
                    if (this.blueprintType === beforeLoadBlueprintType && callback) {
                        callback();
                    }
                })
            },
            hardReload() {
                this.ready = false;
                this.selectedTag = 0;
                this.load(this.onDataLoaded);
            }
        },
        computed: {
            ...mapState("auth", ["user"]),
            ...mapStores(usePluginsStore),
            userCanCreateFlow() {
                return this.user.hasAnyAction(permission.FLOW, action.CREATE);
            },
        },
        watch: {
            $route(newValue, oldValue) {
                if (oldValue.name === newValue.name) {
                    this.selectedTag = this.initSelectedTag();
                }
            },
            q() {
                this.load(this.onDataLoaded);
            },
            selectedTag(newSelectedTag) {
                if (!this.embed) {
                    if (newSelectedTag === 0) {
                        newSelectedTag = undefined;
                        this.$router.push({
                            query: {
                                ...this.$route.query,
                            }
                        });
                    }
                    this.$router.push({
                        query: {
                            ...this.$route.query,
                            selectedTag: newSelectedTag
                        }
                    });
                } else {
                    this.load(this.onDataLoaded);
                }
            },
            tags() {
                if(!Object.prototype.hasOwnProperty.call(this.tags, this.selectedTag)) {
                    this.selectedTag = 0;
                }
            },
            blueprintType() {
                this.loadData();
            },
            blueprintKind() {
                this.loadData();
            }
        }
    };
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

    .blueprints-search {
        width: 300px;
        height: 24px;
        font-size: 12px;
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