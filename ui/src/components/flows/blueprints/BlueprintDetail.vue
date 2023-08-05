<template>
    <div v-loading="!blueprint">
        <template v-if="blueprint">
            <top-nav-bar v-if="!embed" :title="blueprint.title" :breadcrumb="breadcrumb">
                <template #additional-right>
                    <ul v-if="userCanCreateFlow">
                        <router-link :to="{name: 'flows/create'}" @click="asAutoRestoreDraft">
                            <el-button type="primary" v-if="!embed">
                                {{ $t('use') }}
                            </el-button>
                        </router-link>
                    </ul>
                </template>
            </top-nav-bar>
            <div v-else class="header-wrapper">
                <div class="header d-flex">
                    <button class="back-button align-self-center">
                        <el-icon size="medium" @click="goBack">
                            <ArrowLeft />
                        </el-icon>
                    </button>
                    <h2 class="blueprint-title align-self-center">
                        {{ blueprint.title }}
                    </h2>
                </div>
            </div>

            <div :class="{'mt-3': !embed}" class="blueprint-container">
                <el-card>
                    <div class="embedded-topology" v-if="flowGraph">
                        <low-code-editor
                            v-if="flowGraph"
                            :flow-id="parsedFlow.id"
                            :namespace="parsedFlow.namespace"
                            :flow-graph="flowGraph"
                            :source="blueprint.flow"
                            :view-type="embed ? 'source-blueprints' : 'blueprints'"
                            is-read-only
                        />
                    </div>
                </el-card>
                <el-row :gutter="30">
                    <el-col :md="24" :lg="embed ? 24 : 18">
                        <h4>{{ $t("source") }}</h4>
                        <el-card>
                            <editor class="position-relative" :read-only="true" :full-height="false" :minimap="false" :model-value="blueprint.flow" lang="yaml">
                                <template #nav>
                                    <div class="position-absolute copy-wrapper">
                                        <el-tooltip trigger="click" content="Copied" placement="left" :auto-close="2000">
                                            <el-button text round :icon="icon.ContentCopy" @click="Utils.copy(blueprint.flow)" />
                                        </el-tooltip>
                                    </div>
                                </template>
                            </editor>
                        </el-card>
                        <template v-if="blueprint.description">
                            <h4>About this blueprint</h4>
                            <markdown :source="blueprint.description" />
                        </template>
                    </el-col>
                    <el-col :md="24" :lg="embed ? 24 : 6">
                        <h4>Plugins</h4>
                        <div class="plugins-container">
                            <div v-for="task in [...new Set(blueprint.includedTasks)]" :key="task">
                                <task-icon :cls="task" :icons="icons" />
                            </div>
                        </div>
                    </el-col>
                </el-row>
            </div>
        </template>
    </div>
</template>
<script setup>
    import ArrowLeft from "vue-material-design-icons/ArrowLeft.vue";
    import Editor from "../../inputs/Editor.vue";
    import LowCodeEditor from "../../inputs/LowCodeEditor.vue";
    import TaskIcon from  "@kestra-io/ui-libs/src/components/misc/TaskIcon.vue";
    import TopNavBar from "../../layout/TopNavBar.vue";
    import Utils from "../../../utils/utils";
</script>
<script>
    import YamlUtils from "../../../utils/yamlUtils";
    import {shallowRef} from "vue";
    import ContentCopy from "vue-material-design-icons/ContentCopy.vue";
    import Markdown from "../../layout/Markdown.vue";
    import {mapState} from "vuex";
    import permission from "../../../models/permission";
    import action from "../../../models/action";
    import {apiUrl} from "override/utils/route";

    export default {
        components: {Markdown},
        emits: ["back"],
        data() {
            return {
                flowGraph: undefined,
                icon: {
                    ContentCopy: shallowRef(ContentCopy)
                },
                blueprint: undefined,
                breadcrumb: [
                    {
                        label: this.$t("blueprints.title"),
                        link: {
                            name: "blueprints",
                            params: this.$route.params
                        }
                    }
                ]
            }
        },
        props: {
            blueprintId: {
                type: String,
                required: true
            },
            embed: {
                type: Boolean,
                default: false
            },
            tab: {
                type: String,
                default: "community"
            }
        },
        methods: {
            goBack() {
                if (this.embed) {
                    this.$emit("back");
                } else {
                    this.$router.push({
                        name: "blueprints",
                        params: {
                            tenant: this.$route.params.tenant
                        }
                    })
                }
            },
            asAutoRestoreDraft() {
                localStorage.setItem("autoRestore-creation_draft", this.blueprint.flow);
            }
        },
        async created() {
            this.blueprint = (await this.$http.get(`${this.blueprintBaseUri}/${this.blueprintId}`)).data

            try {
                if (this.blueprintBaseUri.endsWith("community")) {
                    this.flowGraph = (await this.$http.get(`${this.blueprintBaseUri}/${this.blueprintId}/graph`, {
                        validateStatus: (status) => {
                            return status === 200;
                        }
                    })).data;
                } else {
                    this.flowGraph = await this.$store.dispatch("flow/getGraphFromSourceResponse", {
                        flow: this.blueprint.flow, config: {
                            validateStatus: (status) => {
                                return status === 200;
                            }
                        }
                    });
                }
            } catch (e) {
                console.error("Unable to create the blueprint's topology : " + e);
            }
        },
        computed: {
            ...mapState("auth", ["user"]),
            ...mapState("plugin", ["icons"]),
            userCanCreateFlow() {
                return this.user.hasAnyAction(permission.FLOW, action.CREATE);
            },
            parsedFlow() {
                return {
                    ...YamlUtils.parse(this.blueprint.flow),
                    source: this.blueprint.flow
                }
            },
            blueprintBaseUri() {
                return `${apiUrl(this.$store)}/blueprints/` + (this.embed ? this.tab : (this.$route?.params?.tab ?? "community"));
            }
        }
    };
</script>
<style scoped lang="scss">
    @import "@kestra-io/ui-libs/src/scss/variables.scss";

    .header-wrapper {
        margin-bottom: calc($spacer * 2);

        .el-card & {
            margin-top: 2.5rem;
        }

        .header {
            margin-bottom: calc(var(--spacer) * 0.5);

            > * {
                margin: 0;
            }

            .back-button {
                padding-left: 0;
                padding-right: calc($spacer * 1.5);
                cursor: pointer;
                border: none;
                background: none;
                display: flex;
                align-items: center;
                :deep(.material-design-icon) {
                    font-size: $h4-font-size;
                }
            }

            .blueprint-title {
                font-weight: bold;
                text-overflow: ellipsis;
                overflow: hidden;
            }
        }
    }

    .copy-wrapper {
        right: $spacer;
        top: $spacer;
        z-index: 1
    }

    .blueprint-container {
        height: 100%;

        :deep(.el-card) {
            .el-card__body {
                padding: 0;
            }
        }

        h4 {
            margin-top: calc($spacer * 2);
            font-weight: bold;
        }

        .embedded-topology {
            max-height: 50%;
            height: 30vh;
            width: 100%;
        }

        .plugins-container {
            display: flex;
            flex-wrap: wrap;
            > div {
                background: var(--card-bg);
                border-radius: var(--bs-border-radius);
                min-width : 100px;
                width: 100px;
                height : 100px;
                padding: $spacer;
                margin-right: $spacer;
                margin-bottom: $spacer;
                display: flex;
                flex-wrap: wrap;
                border: 1px solid var(--bs-border-color);

                :deep(.wrapper) {
                    .icon {
                        height: 100%;
                        margin: 0;
                    }

                    .hover {
                        position: static;
                        background: none;
                        border-top: 0;
                        font-size: var(--font-size-sm);
                    }

                }
            }
        }
    }
</style>