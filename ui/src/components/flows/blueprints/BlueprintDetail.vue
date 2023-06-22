<template>
    <div v-loading="!blueprint">
        <template v-if="blueprint">
            <div class="header-wrapper">
                <div class="header d-flex">
                    <button class="back-button align-self-center">
                        <el-icon size="medium" @click="goBack">
                            <KeyboardBackspace />
                        </el-icon>
                    </button>
                    <h4 class="blueprint-title align-self-center">
                        {{ blueprint.title }}
                    </h4>
                    <div class="ms-auto align-self-center">
                        <router-link :to="{name: 'flows/create'}" @click="asAutoRestoreDraft">
                            <el-button size="large" type="primary" v-if="!embed">
                                {{ $t('use') }}
                            </el-button>
                        </router-link>
                    </div>
                </div>
                <el-breadcrumb v-if="!embed">
                    <el-breadcrumb-item>
                        <router-link :to="{name: 'home'}">
                            <home-outline /> {{ $t('home') }}
                        </router-link>
                    </el-breadcrumb-item>
                    <el-breadcrumb-item>
                        <router-link :to="{name: 'blueprints', params: $route.params}">
                            {{ $t('blueprints.title') }}
                        </router-link>
                    </el-breadcrumb-item>
                </el-breadcrumb>
            </div>

            <div class="blueprint-container">
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
                <h5>{{ $t("source") }}</h5>
                <editor class="position-relative" :read-only="true" :full-height="false" :minimap="false" :model-value="blueprint.flow" lang="yaml">
                    <template #nav>
                        <div class="position-absolute copy-wrapper">
                            <el-tooltip trigger="click" content="Copied" placement="left" :auto-close="2000">
                                <el-button text round :icon="icon.ContentCopy" @click="copy(blueprint.flow)" />
                            </el-tooltip>
                        </div>
                    </template>
                </editor>
                <template v-if="blueprint.description">
                    <h5>About this blueprint</h5>
                    <markdown :source="blueprint.description" />
                </template>
                <h5>Plugins</h5>
                <div class="plugins-container">
                    <task-icon :cls="task" v-for="task in [...new Set(blueprint.includedTasks)]" />
                </div>
            </div>
        </template>
    </div>
</template>
<script setup>
    import KeyboardBackspace from "vue-material-design-icons/KeyboardBackspace.vue";
    import Editor from "../../inputs/Editor.vue";
    import LowCodeEditor from "../../inputs/LowCodeEditor.vue";
    import TaskIcon from "../../plugins/TaskIcon.vue";
    import HomeOutline from "vue-material-design-icons/HomeOutline.vue";
</script>
<script>
    import YamlUtils from "../../../utils/yamlUtils";
    import {shallowRef} from "vue";
    import ContentCopy from "vue-material-design-icons/ContentCopy.vue";
    import Markdown from "../../layout/Markdown.vue";


    export default {
        components: {Markdown},
        data() {
            return {
                flowGraph: undefined,
                icon: {
                    ContentCopy: shallowRef(ContentCopy)
                },
                blueprint: undefined
            }
        },
        props: {
            blueprintId: {
                type: String
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
                    this.$router.push({name: "blueprints"})
                }
            },
            copy(text) {
                navigator.clipboard.writeText(text);
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
            parsedFlow() {
                return {
                    ...YamlUtils.parse(this.blueprint.flow),
                    source: this.blueprint.flow
                }
            },
            blueprintBaseUri() {
                return "/api/v1/blueprints/" + (this.embed ? this.tab : (this.$route?.params?.tab ?? "community"));
            }
        }
    };
</script>
<style scoped lang="scss">
    @import "../../../styles/variable";

    .header-wrapper {
        margin-bottom: $spacer;

        .el-card & {
            margin-top: 2.5rem;
        }

        .header {
            margin-bottom: calc(var(--spacer) * 0.5);

            > * {
                margin: 0;
            }

            .back-button {
                cursor: pointer;
                height: calc(1em + (var(--spacer) * 2));
                width: calc(1em + (var(--spacer) * 2));
                border: none;
                background: none;
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

        h5 {
            font-weight: bold;
            margin: calc(2 * var(--spacer)) 0 $spacer 0;
        }

        .embedded-topology {
            max-height: 50%;
            height: 30vh;
            width: 100%;
            margin: $spacer 0;

        }

        .plugins-container {
            display: flex;
            flex-wrap: wrap;
            flex-direction: row;
            justify-content: left;
            gap: $spacer;
            height: 5rem;

            > :deep(*) {
                position: relative;
                background-color: $white;
                width: 5rem;
            }
        }
    }
</style>