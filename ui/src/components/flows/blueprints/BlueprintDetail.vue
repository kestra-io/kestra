<template>
    <top-nav-bar v-if="!embed && blueprint" :title="blueprint.title" :breadcrumb="breadcrumb" v-loading="!blueprint">
        <template #additional-right>
            <ul v-if="userCanCreateFlow">
                <router-link :to="toEditor()">
                    <el-button type="primary" v-if="!embed">
                        {{ $t('use') }}
                    </el-button>
                </router-link>
            </ul>
        </template>
    </top-nav-bar>
    <div v-else-if="blueprint" class="header-wrapper">
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

    <section v-bind="$attrs" :class="{'container': !embed}" class="blueprint-container" v-loading="!blueprint">
        <el-card v-if="blueprint && kind === 'flow'">
            <div class="embedded-topology" v-if="flowGraph">
                <low-code-editor
                    v-if="flowGraph"
                    :flow-id="parsedFlow.id"
                    :namespace="parsedFlow.namespace"
                    :flow-graph="flowGraph"
                    :source="blueprint.source"
                    :view-type="embed ? 'source-blueprints' : 'blueprints'"
                    is-read-only
                />
            </div>
        </el-card>
        <el-row :gutter="30" v-if="blueprint">
            <el-col :md="24" :lg="embed ? 24 : 18">
                <h4>{{ $t("source") }}</h4>
                <el-card>
                    <editor
                        class="position-relative"
                        :read-only="true"
                        :input="true"
                        :full-height="false"
                        :minimap="false"
                        :model-value="blueprint.source"
                        lang="yaml"
                        :navbar="false"
                    >
                        <template #absolute>
                            <copy-to-clipboard class="position-absolute" :text="blueprint.source" />
                        </template>
                    </editor>
                </el-card>
                <template v-if="blueprint.description">
                    <h4>About this blueprint</h4>
                    <div v-if="!system" class="tags text-uppercase">
                        <div v-for="(tag, index) in blueprint.tags" :key="index" class="tag-box">
                            <el-tag type="info" size="small">
                                {{ tag }}
                            </el-tag>
                        </div>
                    </div>
                    <markdown :source="blueprint.description" />
                </template>
            </el-col>
            <el-col :md="24" :lg="embed ? 24 : 6" v-if="blueprint?.includedTasks?.length > 0">
                <h4>Plugins</h4>
                <div class="plugins-container">
                    <div v-for="task in [...new Set(blueprint.includedTasks)]" :key="task">
                        <task-icon :cls="task" :icons="icons" />
                    </div>
                </div>
            </el-col>
        </el-row>
    </section>
</template>
<script setup>
    import ArrowLeft from "vue-material-design-icons/ArrowLeft.vue";
    import Editor from "../../inputs/Editor.vue";
    import LowCodeEditor from "../../inputs/LowCodeEditor.vue";
    import TaskIcon from  "@kestra-io/ui-libs/src/components/misc/TaskIcon.vue";
    import TopNavBar from "../../layout/TopNavBar.vue";
</script>
<script>
    import YamlUtils from "../../../utils/yamlUtils";
    import Markdown from "../../layout/Markdown.vue";
    import CopyToClipboard from "../../layout/CopyToClipboard.vue";
    import {mapState} from "vuex";
    import permission from "../../../models/permission";
    import action from "../../../models/action";

    export default {
        components: {Markdown, CopyToClipboard},
        emits: ["back"],
        data() {
            return {
                flowGraph: undefined,
                blueprint: undefined,
                tab: "",
                breadcrumb: [
                    {
                        label: this.$t("blueprints.title"),
                        link: {
                            name: "blueprints",
                            params: this.$route.params.tab ? this.$route.params.tab : {...this.$route.params, tab: this.tab},
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
            blueprintType: {
                type: String,
                default: "community"
            },
            kind: {
                type: String,
                default: "flow",
            },
        },
        methods: {
            goBack() {
                if (this.embed) {
                    this.$emit("back");
                } else {
                    this.$router.push({
                        name: "blueprints",
                        params: {
                            tenant: this.$route.params.tenant,
                            tab: this.tab
                        }
                    })
                }
            },
            toEditor() {
                const query = this.blueprintKind === "flow" ?
                    {blueprintId: this.blueprintId, blueprintSource: this.blueprintType} :
                    {blueprintId: this.blueprintId};
                return {name: `${this.blueprintKind}s/create`, query};
            }
        },
        async created() {
            this.$store.dispatch("blueprints/getBlueprint", {type: this.blueprintType, kind: this.blueprintKind, id: this.blueprintId})
                .then(data => {
                    this.blueprint = data;
                    if (this.kind === "flow") {
                        try {
                            if (this.blueprintType === "community") {
                                this.$store.dispatch(
                                    "blueprints/getBlueprintGraph",
                                    {
                                        type: this.blueprintType,
                                        kind: this.blueprintKind,
                                        id: this.blueprintId,
                                        validateStatus: (status) => {
                                            return status === 200;
                                        }
                                    })
                                    .then(data => {
                                        this.flowGraph  = data;
                                    });
                            } else {
                                this.$store.dispatch("flow/getGraphFromSourceResponse", {
                                    flow: this.blueprint.source, config: {
                                        validateStatus: (status) => {
                                            return status === 200;
                                        }
                                    }
                                }).then(data => {
                                    this.flowGraph = data ;
                                });
                            }
                        } catch (e) {
                            console.error("Unable to create the blueprint's topology : " + e);
                        }
                    }
                });
        },
        computed: {
            ...mapState("auth", ["user"]),
            ...mapState("plugin", ["icons"]),
            userCanCreateFlow() {
                return this.user.hasAnyAction(permission.FLOW, action.CREATE);
            },
            parsedFlow() {
                return {
                    ...YamlUtils.parse(this.blueprint.source),
                    source: this.blueprint.source
                }
            },
            blueprintKind() {
                return this.blueprintType === "community" ? this.kind : undefined;
            },
        }
    };
</script>
<style scoped lang="scss">
    @import "@kestra-io/ui-libs/src/scss/variables";

    .header-wrapper {
        margin-bottom: calc($spacer * 2);

        .el-card & {
            margin-top: 2.5rem;
        }

        .header {
            margin-bottom: .5rem;

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

    .blueprint-container {
        height: 100%;

        :deep(.el-card) {
            .el-card__body {
                padding: 0;
            }
        }

        h4 {
            margin-top: calc($spacer * 2);
            margin-bottom: 0;
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
                background: var(--ks-background-card);
                border-radius: var(--bs-border-radius);
                min-width : 100px;
                width: 100px;
                height : 100px;
                padding: $spacer;
                margin-right: $spacer;
                margin-bottom: $spacer;
                display: flex;
                flex-wrap: wrap;
                border: 1px solid var(--ks-border-primary);

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

    .tags {
        margin: 10px 0;
        display: flex;

        .el-tag.el-tag--info {
            background-color: var(--ks-background-card);
            padding: 15px 10px;
            color: var(--ks-content-primary);
            text-transform: capitalize;
            font-size: var(--el-font-size-small);
            border: 1px solid var(--ks-border-primary);
        }

        .tag-box {
            margin-right: calc($spacer / 3);
        }
    }
</style>