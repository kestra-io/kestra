<template>
    <div v-loading="!blueprint">
        <template v-if="blueprint">
            <div class="header">
                <button class="back-button">
                    <el-icon size="medium" @click="goBack">
                        <KeyboardBackspace />
                    </el-icon>
                </button>
                <h4 class="blueprint-title">
                    {{ blueprint.title }}
                </h4>
                <div class="ms-auto">
                    <router-link :to="{name: 'flows/create'}" @click="asAutoRestoreDraft">
                        <el-button size="large" v-if="!embed">
                            {{ $t('use') }}
                        </el-button>
                    </router-link>
                </div>
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
                            is-read-only
                            graph-only
                        />
                    </div>
                </el-card>
                <h5>Source code</h5>
                <editor :read-only="true" :full-height="false" :minimap="false" :model-value="blueprint.flow" lang="yaml">
                    <template #nav>
                        <div style="text-align: right">
                            <el-tooltip trigger="click" content="Copied" placement="left" :auto-close="2000">
                                <el-button text round :icon="icon.ContentCopy" @click="copy(blueprint.flow)" />
                            </el-tooltip>
                        </div>
                    </template>
                </editor>
                <template v-if="blueprint.description">
                    <h5>About this blueprint</h5>
                    <p>{{ blueprint.description }}</p>
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
</script>
<script>
    import YamlUtils from "../../../utils/yamlUtils";
    import {shallowRef} from "vue";
    import ContentCopy from "vue-material-design-icons/ContentCopy.vue";

    export default {
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
            }
        },
        methods: {
            goBack() {
                if(this.embed) {
                    this.$emit("back");
                }else {
                    this.$router.push({name: "flow-gallery"})
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
            this.blueprint = (await this.$http.get(`/api/v1/blueprints/${this.blueprintId}`)).data

            try {
                this.flowGraph = (await this.$http.get(`/api/v1/blueprints/${this.blueprintId}/graph`, {
                  validateStatus: (status) => {
                    return status === 200;
                  }
                })).data;
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
            }
        }
    };
</script>
<style scoped lang="scss">
    @import "../../../styles/variable";

    .header {
        display: flex;

        > * {
            margin-top: auto;
            margin-bottom: auto;
        }

        .back-button {
            cursor: pointer;
            padding: $spacer;
            height: calc(1em + (var(--spacer) * 2));
            width: calc(1em + (var(--spacer) * 2));
            border: none;
            background: none;
        }

        .blueprint-title {
            font-weight: bold;
        }
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