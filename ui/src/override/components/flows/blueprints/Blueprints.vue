<template>
    <nav v-if="!embed" class="header">
        <h4 class="text-uppercase welcome">
            {{ $t("blueprints.header.welcome") }}
        </h4>
        <h4 class="catch-phrase">
            {{ $t("blueprints.header.catch phrase.1") }}
        </h4>
        <h4 class="catch-phrase">
            {{ $t("blueprints.header.catch phrase.2") }}
        </h4>
    </nav>
    <div class="main-container" v-bind="$attrs">
        <blueprint-detail :embed="embed" v-if="selectedBlueprintId" :blueprint-id="selectedBlueprintId" @back="selectedBlueprintId = undefined" />
        <data-table v-else class="blueprints" @page-changed="onPageChanged" ref="dataTable" :total="total" divider>
            <template #navbar>
                <div class="d-flex sub-nav">
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

                <el-divider />
            </template>
            <template #table>
                <el-card class="blueprint-card hoverable" v-for="blueprint in blueprints" @click="goToDetail(blueprint.id)">
                    <div class="side">
                        <div class="title">
                            {{ blueprint.title }}
                        </div>
                        <div class="tags text-uppercase">
                            {{ tagsToString(blueprint.tags) }}
                        </div>
                        <div class="tasks-container">
                            <task-icon :cls="task" only-icon v-for="task in [...new Set(blueprint.includedTasks)]" />
                        </div>
                    </div>
                    <div class="side buttons ms-auto">
                        <el-tooltip trigger="click" content="Copied" placement="left" :auto-close="2000">
                            <el-button class="hoverable" @click.stop="copy(blueprint.id)" :icon="icon.ContentCopy" size="large" text bg>
                                {{ $t('copy') }}
                            </el-button>
                        </el-tooltip>
                    </div>
                </el-card>
            </template>
        </data-table>
    </div>
</template>
<script>
    import RouteContext from "../../../../mixins/routeContext";
    import DataTableActions from "../../../../mixins/dataTableActions";
    import SearchField from "../../../../components/layout/SearchField.vue";
    import DataTable from "../../../../components/layout/DataTable.vue";
    import BlueprintDetail from "../../../../components/flows/blueprints/BlueprintDetail.vue";
    import {shallowRef} from "vue";
    import ContentCopy from "vue-material-design-icons/ContentCopy.vue";
    import TaskIcon from "../../../../components/plugins/TaskIcon.vue";

    export default {
        mixins: [DataTableActions, RouteContext],
        inheritAttrs: false,
        components: {
            TaskIcon,
            SearchField,
            DataTable,
            BlueprintDetail
        },
        async created() {
            this.selectedTag = this.$route?.query?.selectedTag ?? 0;
        },
        props: {
            embed: {
                type: Boolean,
                default: false
            }
        },
        methods: {
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
                    this.selectedBlueprintId = blueprintId;
                } else {
                    this.$router.push({name: "blueprints/view", params: {blueprintId}})
                }
            },
            loadTags(beforeLoadBlueprintBaseUri) {
                return this.$http
                    .get(beforeLoadBlueprintBaseUri + "/tags")
                    .then(response => {
                        // Handle switch tab while fetching data
                        if(this.blueprintBaseUri === beforeLoadBlueprintBaseUri) {
                            this.tags = this.tagsResponseMapper(response.data);
                        }
                    })
            },
            tagsResponseMapper(tagsResponse) {
                return Object.fromEntries(tagsResponse.map(tag => [tag.id, tag]))
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


                if (this.$route.query.tags || this.selectedTag) {
                    query.tags = this.$route.query.tags || this.selectedTag;
                }

                return this.$http
                    .get(beforeLoadBlueprintBaseUri, {
                        params: query
                    })
                    .then(response => {
                        // Handle switch tab while fetching data
                        if(this.blueprintBaseUri === beforeLoadBlueprintBaseUri) {
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
                    if(this.blueprintBaseUri === beforeLoadBlueprintBaseUri) {
                        callback();
                    }
                })
            }
        },
        data() {
            return {
                q: undefined,
                selectedTag: 0,
                tags: undefined,
                blueprints: undefined,
                total: 0,
                selectedBlueprintId: undefined,
                icon: {
                    ContentCopy: shallowRef(ContentCopy)
                }
            }
        },
        computed: {
            routeInfo() {
                return {
                    title: this.$t("blueprints.title")
                };
            },
            blueprintBaseUri() {
                return "/api/v1/blueprints/community";
            }
        },
        watch: {
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
            }
        }
    };
</script>
<style scoped lang="scss">
    @import "../../../../styles/components/blueprints/blueprints";
</style>