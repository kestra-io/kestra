<template>
    <errors code="404" v-if="error && embed" />
    <div v-else>
        <slot name="nav" />
        <data-table class="blueprints" @page-changed="onPageChanged" ref="dataTable" :total="total" divider>
            <template #navbar>
                <el-radio-group v-if="ready && !system" v-model="selectedTag" class="tags-selection">
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
            <template #search>
                <search-field :router="!embed" placeholder="search blueprint" @search="s => q = s" class="blueprints-search" />
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
                        :to="embed ? undefined : {name: 'blueprints/view', params: {blueprintId: blueprint.id, tab}}"
                    >
                        <div class="left">
                            <div>
                                <div class="title">
                                    {{ blueprint.title }}
                                </div>
                                <div v-if="!system" class="tags text-uppercase">
                                    {{ tagsToString(blueprint.tags) }}
                                </div>
                            </div>
                            <div class="tasks-container">
                                <task-icon
                                    :icons="icons"
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
                                    @click.prevent.stop="copy(blueprint.id)"
                                    :icon="icon.ContentCopy"
                                    size="large"
                                    text
                                    bg
                                >
                                    {{ $t('copy') }}
                                </el-button>
                            </el-tooltip>
                            <el-button v-else size="large" text bg @click.prevent.stop="blueprintToEditor(blueprint.id)">
                                {{ $t('use') }}
                            </el-button>
                        </div>
                    </component>
                </el-card>
            </template>
        </data-table>
        <slot name="bottom-bar" />
    </div>
</template>

<script>
    import SearchField from "../../../../components/layout/SearchField.vue";
    import DataTable from "../../../../components/layout/DataTable.vue";
    import TaskIcon from "@kestra-io/ui-libs/src/components/misc/TaskIcon.vue";
    import DataTableActions from "../../../../mixins/dataTableActions";
    import {shallowRef} from "vue";
    import ContentCopy from "vue-material-design-icons/ContentCopy.vue";
    import RestoreUrl from "../../../../mixins/restoreUrl";
    import permission from "../../../../models/permission";
    import action from "../../../../models/action";
    import {mapState} from "vuex";
    import Utils from "../../../../utils/utils";
    import Errors from "../../../../components/errors/Errors.vue";
    import {editorViewTypes} from "../../../../utils/constants";
    import {apiUrl} from "override/utils/route.js";

    export default {
        mixins: [RestoreUrl, DataTableActions],
        components: {TaskIcon, DataTable, SearchField, Errors},
        emits: ["goToDetail", "loaded"],
        props: {
            blueprintBaseUri: {
                type: String,
                required: true
            },
            tab: {
                type: String,
                default: undefined,
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
        data() {
            return {
                q: undefined,
                selectedTag: this.initSelectedTag(),
                tags: undefined,
                blueprints: undefined,
                total: 0,
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
            async copy(blueprintId) {
                await Utils.copy(
                    (await this.$http.get(`${this.embedFriendlyBlueprintBaseUri}/${blueprintId}/flow`)).data
                );
            },
            async blueprintToEditor(blueprintId) {
                localStorage.setItem(editorViewTypes.STORAGE_KEY, editorViewTypes.SOURCE_TOPOLOGY);
                this.$router.push({
                    name: "flows/create",
                    params: {
                        tenant: this.$route.params.tenant
                    },
                    query: {blueprintId: blueprintId, blueprintSource: this.embedFriendlyBlueprintBaseUri.includes("community") ? "community" : "custom"}
                });
            },
            tagsToString(blueprintTags) {
                return blueprintTags?.map(id => this.tags?.[id]?.name).join(" ")
            },
            goToDetail(blueprintId) {
                if (this.embed) {
                    this.$emit("goToDetail", blueprintId);
                }
            },
            loadTags(beforeLoadBlueprintBaseUri) {
                const query = {}
                if (this.$route.query.q || this.q) {
                    query.q = this.$route.query.q || this.q;
                }

                return this.$http
                    .get(beforeLoadBlueprintBaseUri + "/tags", {
                        params: query
                    })
                    .then(response => {
                        // Handle switch tab while fetching data
                        if (this.embedFriendlyBlueprintBaseUri === beforeLoadBlueprintBaseUri) {
                            this.tags = this.tagsResponseMapper(response.data);
                        }
                    })
            },
            loadBlueprints(beforeLoadBlueprintBaseUri) {
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

                return this.$http
                    .get(beforeLoadBlueprintBaseUri, {
                        params: query
                    })
                    .then(response => {
                        // Handle switch tab while fetching data
                        if (this.embedFriendlyBlueprintBaseUri === beforeLoadBlueprintBaseUri) {
                            const blueprintsResponse = response.data;
                            this.total = blueprintsResponse.total;
                            this.blueprints = blueprintsResponse.results;
                        }
                    });
            },
            loadData(callback) {
                const beforeLoadBlueprintBaseUri = this.embedFriendlyBlueprintBaseUri;

                Promise.all([
                    this.loadTags(beforeLoadBlueprintBaseUri),
                    this.loadBlueprints(beforeLoadBlueprintBaseUri)
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
                    if (this.embedFriendlyBlueprintBaseUri === beforeLoadBlueprintBaseUri && callback) {
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
            ...mapState("plugin", ["icons"]),
            userCanCreateFlow() {
                return this.user.hasAnyAction(permission.FLOW, action.CREATE);
            },
            embedFriendlyBlueprintBaseUri() {
                const tab = this.tab ?? this?.$route?.params?.tab ?? "community";
                let base = this.blueprintBaseUri;

                return base
                    ? (base.endsWith("/undefined") ? base.replace("/undefined", `/${tab}`) : base)
                    : `${apiUrl(this.$store)}/blueprints/${tab}`;
            }
        },
        watch: {
            $route(newValue, oldValue) {
                if (oldValue.name === newValue.name) {
                    this.selectedTag = this.initSelectedTag();
                }
            },
            q() {
                if (this.embed) {
                    this.load(this.onDataLoaded);
                }
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
            blueprintBaseUri() {
                this.loadData();
            },
            tab() {
                this.loadData()
            }
        }
    };
</script>
<style scoped lang="scss">
    @use 'element-plus/theme-chalk/src/mixins/mixins' as *;
    @import "@kestra-io/ui-libs/src/scss/variables.scss";

    .sub-nav {
        margin: 0 0 $spacer;

        > * {
            margin: 0;
        }

        // Two elements => one element on each side
        &:has(> :nth-child(2)) {
            margin: $spacer 0 calc(0.5 * var(--spacer)) 0;

            .el-card & {
                // Enough space not to overlap with switch view when embedded
                margin-top: calc(1.6 * var(--spacer));


                // Embedded tabs looks weird without cancelling the margin (this brings a top-left tabs with bottom-right search)
                > :nth-child(1) {
                    margin-top: calc(-1.5 * var(--spacer));
                }
            }

            > :nth-last-child(1) {
                margin-left: auto;
                padding: calc(0.5 * var(--spacer)) 0;
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
            margin: 0 0 1px 0;
            border-radius: 0;
            border: 0;

            .blueprint-link {
                display: flex;
                color: inherit;
                text-decoration: inherit;
                width: 100%;

                .left {
                    .title {
                        font-weight: bold;
                        font-size: $small-font-size;
                    }

                    .tags {
                        font-family: $font-family-monospace;
                        font-weight: bold;
                        font-size: $sub-sup-font-size;
                        margin-bottom: calc(var(--spacer) / 2);
                        color: $primary;

                        html.dark & {
                            color: $pink;
                        }
                    }


                    .tasks-container {
                        $plugin-icon-size: calc(var(--font-size-base) + 0.4rem);
                        display: flex;
                        gap: calc(var(--spacer) / 4);
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
                    }


                    html.dark & :deep(.el-button) {
                        background-color: var(--bs-gray-300);
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
                background-color: var(--bs-gray-600);
            }
        }
    }

    .tags-selection {
        display: flex;
        width: 100%;
        margin-bottom: var(--spacer);
        gap: calc($spacer / 3);
        flex-wrap: wrap;

        & > * {
            max-width: 50%;

            :deep(span) {
                border-radius: $border-radius !important;
                border: 1px solid var(--bs-border-color);
                background: var(--bs-white);
                width: 100%;
                font-size: var(--el-font-size-extra-small);
                font-weight: bold;
                box-shadow: none;
                text-overflow: ellipsis;
                overflow: hidden;
            }
        }

        html.dark & :deep(:not(.is-active) span) {
            background: var(--bs-gray-100);
        }
    }
</style>
