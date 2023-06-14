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
        <el-form-item class="search-wrapper">
            <search-field placeholder="search blueprint" @search="s => q = s" />
        </el-form-item>
    </nav>
    <div class="main-container" v-bind="$attrs" v-if="tags && ready">
        <blueprint-detail :embed="embed" v-if="selectedBlueprintId" :blueprint-id="selectedBlueprintId" @back="selectedBlueprintId = undefined" />
        <data-table v-else class="blueprints" @page-changed="onPageChanged" ref="dataTable" :total="total" divider>
            <template #navbar>
                <el-form-item v-if="embed">
                    <search-field embed placeholder="search blueprint" @search="s => q = s" />
                </el-form-item>
                <br>
                <el-radio-group v-model="selectedTags" class="tags-selection">
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
                            {{ dotSeparatedTags(blueprint.tags) }}
                        </div>
                        <div class="tasks-container">
                            <task-icon :cls="task" only-icon v-for="task in [...new Set(blueprint.includedTasks)]" />
                        </div>
                    </div>
                    <div class="side ms-auto hoverable">
                        <el-tooltip trigger="click" content="Copied" placement="left" :auto-close="2000">
                            <el-button @click.stop="copy(blueprint.id)" :icon="icon.ContentCopy" size="large" text bg>
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
    import RouteContext from "../../../mixins/routeContext";
    import DataTableActions from "../../../mixins/dataTableActions";
    import SearchField from "../../layout/SearchField.vue";
    import DataTable from "../../layout/DataTable.vue";
    import BlueprintDetail from "./BlueprintDetail.vue";
    import {shallowRef} from "vue";
    import ContentCopy from "vue-material-design-icons/ContentCopy.vue";
    import TaskIcon from "../../plugins/TaskIcon.vue";

    export default {
        mixins: [DataTableActions, RouteContext],
        components: {
            TaskIcon,
            SearchField,
            DataTable,
            BlueprintDetail
        },
        async created() {
            await this.loadTags();
            this.selectedTags = this.$route?.query?.selectedTags ?? 0;
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
                    (await this.$http.get(`/api/v1/blueprints/${blueprintId}/flow`)).data
                );
            },
            dotSeparatedTags(tagIds) {
                return tagIds.map(id => this.tags[id].name).join(".")
            },
            goToDetail(blueprintId) {
                if (this.embed) {
                    this.selectedBlueprintId = blueprintId;
                } else {
                    this.$router.push({name: "blueprints/view", params: {blueprintId}})
                }
            },
            async loadTags(){
                return this.$http
                    .get("/api/v1/blueprints/tags")
                    .then(response => {
                        this.tags = Object.fromEntries(response.data.map(tag => [tag.id, tag]));
                    })
            },
            loadData(callback){
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


                if (this.$route.query.tagIds || this.selectedTags) {
                    query.tagIds = this.$route.query.tagIds || this.selectedTags;
                }

                this.$http
                    .get("/api/v1/blueprints", {
                        params: query
                    })
                    .then(response => {
                        const blueprintsResponse = response.data;
                        this.total = blueprintsResponse.total;
                        this.blueprints = blueprintsResponse.results;
                    })
                    .finally(callback);
            }
        },
        data() {
            return {
                q: undefined,
                selectedTags: 0,
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
            }
        },
        watch: {
            q(){
                if(this.embed) {
                    this.load(this.onDataLoaded);
                }
            },
            selectedTags(newSelectedTags){
                if (!this.embed) {
                    if(newSelectedTags === 0) {
                        newSelectedTags = undefined;
                        this.$router.push({query: {
                            ...this.$route.query,
                        }});
                    }
                    this.$router.push({query: {
                        ...this.$route.query,
                        selectedTags: newSelectedTags
                    }});
                } else {
                    this.load(this.onDataLoaded);
                }
            }
        }
    };
</script>
<style scoped lang="scss">
    @import "../../../styles/variable";

    .header {
        $neg-offset-from-menu: calc(-1 * var(--offset-from-menu));
        // left and right margins override the default main-content margin
        // top margin overrides the default main-content margin (1 spacer) + the existing top navbar (2 + 2 + 2 spacers)
        margin: calc(-7 * var(--spacer)) $neg-offset-from-menu 0 $neg-offset-from-menu;
        background: linear-gradient(140deg, #9535D0 3.03%, #6A22BB 5.45%, #461A97 11.72%, #36188D 21.59%, #321974 35.13%, #25185C 46.92%, #24155B 63.48%, #25155B 75.13%, #450F95 90.68%, #4F39B3 94.9%, #893FE5 97.9%);
        text-align: center;
        padding-top: calc(7 * var(--spacer));
        padding-bottom: $spacer;

        .welcome {
            color: $pink;
            font-family: $font-family-monospace;
            font-weight: bold;
        }

        .catch-phrase {
            color: $white;
        }

        .search-wrapper {
            margin: calc(2 * var(--spacer)) auto calc(2 * var(--spacer)) auto;
            max-width: 40%;
            & :deep(input) {
                height: 2rem;
            }
        }
    }


    .main-container {
        // To match the SwitchView margin
        padding-top: 10px !important;

        :deep(.hoverable) {
            &.el-checkbox-button:not(.is-checked) span, &.el-card, & .hoverable button{
                color: $black !important;
                background-color: $white;

                &:hover {
                    background-color: var(--bs-gray-300);
                }

                html.dark & {
                    color: $white !important;
                    background-color: rgba(255, 255, 255, 0.1);

                    &:hover {
                        background-color: rgba(255, 255, 255, 0.15)
                    }
                }
            }

            & .hoverable button:hover {
                color: $white !important;
                background-color: $tertiary
            }


            &.el-checkbox-button.is-checked span {
                color: $white !important;
                background-color: $primary;

                html.dark & {
                    background-color: $primary;
                }
            }
        }

        .blueprints {
            width: 100%;

            .blueprint-card {
                cursor: pointer;
                margin: calc(var(--spacer) / 16) 0;
                border-radius: 0;

                &:first-child{
                    border-top-left-radius: $border-radius;
                    border-top-right-radius: $border-radius;
                }

                &:nth-last-child(1 of .blueprint-card){
                    border-bottom-left-radius: $border-radius;
                    border-bottom-right-radius: $border-radius;
                }

                > :deep(.el-card__body) {
                    display: flex;
                }

                .side {
                    margin: auto 0;

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

                    .tasks-container{
                        $plugin-icon-size: calc(var(--font-size-base) + 0.4rem);

                        display: flex;
                        flex-wrap: wrap;
                        flex-direction: column;
                        gap: calc(var(--spacer) / 4);
                        width: fit-content;
                        height: $plugin-icon-size;

                        :deep(> *) {
                            width: $plugin-icon-size;
                            padding: 0.2rem;
                            border-radius: $border-radius;

                            html.dark & {
                                background-color: var(--bs-gray-900);
                            }

                            & * {
                                margin-top: 0;
                            }
                        }
                    }
                }
            }
        }

        .tags-selection{
            display: flex;
            width: 100%;
            margin-bottom: calc(2 * var(--spacer));
            gap: $spacer;
            flex-wrap: wrap;

            & > * {
                flex: 1;

                :deep(span){
                    border: 1px solid var(--bs--gray-300);
                    border-radius: $border-radius;
                    width: 100%;
                    font-weight: bold;
                    box-shadow: none;
                }
            }
        }
    }
</style>