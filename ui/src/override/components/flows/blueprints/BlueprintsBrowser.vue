<template>
    <div>
        <data-table class="blueprints" @page-changed="onPageChanged" ref="dataTable" :total="total" divider>
            <template #navbar>
                <div class="d-flex sub-nav">
                    <slot name="nav" />
                    <el-form-item>
                        <search-field :embed="embed" placeholder="search blueprint" @search="s => q = s" />
                    </el-form-item>
                </div>
                <el-radio-group v-if="ready" v-model="selectedTag" class="tags-selection">
                    <el-radio-button
                        :key="0"
                        :label="0"
                        class="hoverable"
                    >
                        {{ $t("all tags") }}
                    </el-radio-button>
                    <el-radio-button
                        v-for="tag in Object.values(tags)"
                        :key="tag.id"
                        :label="tag.id"
                        class="hoverable"
                    >
                        {{ tag.name }}
                    </el-radio-button>
                </el-radio-group>
            </template>
            <template #table>
                <el-alert type="info" v-if="!blueprints || blueprints.length  === 0" :closable="false">
                    {{ $t('no result') }}
                </el-alert>
                <el-card class="blueprint-card" :class="{'embed': embed}" v-for="blueprint in blueprints"
                         @click="goToDetail(blueprint.id)">
                    <component class="blueprint-link" :is="embed ? 'div' : 'router-link'"
                               :to="embed ? undefined : {name: 'blueprints/view', params: {blueprintId: blueprint.id}}">
                        <div class="left">
                            <div>
                                <div class="title">
                                    {{ blueprint.title }}
                                </div>
                                <div class="tags text-uppercase">
                                    {{ tagsToString(blueprint.tags) }}
                                </div>
                            </div>
                            <div class="tasks-container">
                                <task-icon :cls="task" only-icon
                                           v-for="task in [...new Set(blueprint.includedTasks)]" />
                            </div>
                        </div>
                        <div class="side buttons ms-auto">
                            <slot name="buttons" :blueprint="blueprint" />
                            <el-tooltip trigger="click" content="Copied" placement="left" :auto-close="2000">
                                <el-button @click.prevent.stop="copy(blueprint.id)" :icon="icon.ContentCopy"
                                           size="large" text bg>
                                    {{ $t('copy') }}
                                </el-button>
                            </el-tooltip>
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
    import TaskIcon from "../../../../components/plugins/TaskIcon.vue";
    import DataTableActions from "../../../../mixins/dataTableActions";
    import {shallowRef} from "vue";
    import ContentCopy from "vue-material-design-icons/ContentCopy.vue";
    import RestoreUrl from "../../../../mixins/restoreUrl";

    export default {
        mixins: [RestoreUrl, DataTableActions],
        components: {TaskIcon, DataTable, SearchField},
        emits: ["goToDetail"],
        props: {
            blueprintBaseUri: {
                type: String,
                default: "/api/v1/blueprints/community"
            },
            embed: {
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
                }
            }
        },
        methods: {
            initSelectedTag() {
                return this.$route?.query?.selectedTag ?? 0
            },
            async copy(blueprintId) {
                await navigator.clipboard.writeText(
                    (await this.$http.get(`${this.blueprintBaseUri}/${blueprintId}/flow`)).data
                );
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
                return this.$http
                    .get(beforeLoadBlueprintBaseUri + "/tags")
                    .then(response => {
                        // Handle switch tab while fetching data
                        if (this.blueprintBaseUri === beforeLoadBlueprintBaseUri) {
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


                if (this.$route.query.selectedTag || this.selectedTag) {
                    query.tags = this.$route.query.selectedTag || this.selectedTag;
                }

                return this.$http
                    .get(beforeLoadBlueprintBaseUri, {
                        params: query
                    })
                    .then(response => {
                        // Handle switch tab while fetching data
                        if (this.blueprintBaseUri === beforeLoadBlueprintBaseUri) {
                            const blueprintsResponse = response.data;
                            this.total = blueprintsResponse.total;
                            this.blueprints = blueprintsResponse.results;
                        }
                    });
            },
            loadData(callback) {
                const beforeLoadBlueprintBaseUri = this.blueprintBaseUri;

                Promise.all([
                    new Promise(async (resolve) => {
                        if (this.tags === undefined) {
                            await this.loadTags(beforeLoadBlueprintBaseUri);
                        }
                        resolve();
                    }),
                    this.loadBlueprints(beforeLoadBlueprintBaseUri)
                ]).finally(() => {
                    // Handle switch tab while fetching data
                    if (this.blueprintBaseUri === beforeLoadBlueprintBaseUri) {
                        callback();
                    }
                })
            },
            hardReload() {
                this.ready = false;
                this.selectedTag = 0;
                this.tags = undefined;
                this.load(this.onDataLoaded);
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
            blueprintBaseUri() {
                this.hardReload();
            }
        }
    };
</script>
<style scoped lang="scss">
    @use 'element-plus/theme-chalk/src/mixins/mixins' as *;
    @import "../../../../styles/variable";

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

    .blueprints {
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
                            padding: 0.2rem;
                            border-radius: $border-radius;

                            html.dark & {
                                background-color: var(--bs-white);
                            }

                            & * {
                                margin-top: 0;
                            }
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
                        height: 2.5rem;

                        :deep(.wrapper) {
                            width: 2.5rem;
                            padding: 8px;
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
        margin-bottom: calc(2 * var(--spacer));
        gap: $spacer;
        flex-wrap: wrap;

        & > * {
            max-width: 50%;

            :deep(span) {
                border-radius: $border-radius !important;
                border: 1px solid var(--bs-border-color);
                background: var(--bs-white);
                width: 100%;
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