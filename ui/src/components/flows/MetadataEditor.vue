<template>
    <el-form label-position="top">
        <el-form-item :required="true">
            <template #label>
                <code>{{ $t("id") }}</code>
            </template>
            <el-input :disabled="editing" v-model="newMetadata.id" />
        </el-form-item>
        <el-form-item :required="true">
            <template #label>
                <code>{{ $t("namespace") }}</code>
            </template>
            <el-input :disabled="editing" v-model="newMetadata.namespace" />
        </el-form-item>
        <el-form-item>
            <template #label>
                <div class="d-flex flex-column gap-2">
                    <code class="flex-grow-1 text-lowercase">{{ $t("description") }}</code>
                    <el-button-group size="small" class="my-1">
                        <el-button @click="preview = false">
                            <Pencil class="me-1" /> {{ $t("edit") }}
                        </el-button>
                        <el-button @click="preview = true">
                            <Eye class="me-1" /> {{ $t("preview") }}
                        </el-button>
                    </el-button-group>
                </div>
            </template>
            <editor
                v-if="!preview"
                v-model="newMetadata.description"
                :navbar="false"
                :full-height="false"
                :input="true"
                lang="text"
                @update:model-value="(value) => newMetadata.description = value"
            />
            <markdown v-else :source="newMetadata.description" />
        </el-form-item>
        <el-form-item>
            <template #label>
                <code>retry</code>
            </template>
            <editor
                :model-value="newMetadata.retry"
                :navbar="false"
                :full-height="false"
                :input="true"
                lang="yaml"
                @update:model-value="(value) => newMetadata.retry = value"
            />
        </el-form-item>
        <el-form-item>
            <template #label>
                <code>{{ $t("labels") }}</code>
            </template>
            <div class="d-flex w-100" v-for="(item, index) in newMetadata.labels" :key="index">
                <div class="flex-fill flex-grow-1 w-100 me-2">
                    <el-input
                        :model-value="item[0]"
                        @update:model-value="onKey(index, $event)"
                    />
                </div>
                <div class="flex-fill flex-grow-1 w-100 me-2">
                    <el-input
                        :model-value="item[1]"
                        @update:model-value="onValue(index, $event)"
                    />
                </div>
                <div class="flex-shrink-1">
                    <el-button-group class="d-flex flex-nowrap">
                        <el-button :icon="Plus" @click="addItem" />
                        <el-button
                            :icon="Minus"
                            @click="removeItem(index)"
                            :disabled="index === 0 && newMetadata.labels.length === 1"
                        />
                    </el-button-group>
                </div>
            </div>
        </el-form-item>
        <el-form-item>
            <template #label>
                <code>{{ $t("inputs") }}</code>
            </template>
            <metadata-inputs v-model="newMetadata.inputs" :inputs="newMetadata.inputs" />
        </el-form-item>
        <el-form-item>
            <template #label>
                <code>{{ $t("outputs") }}</code>
            </template>
            <editor
                :model-value="newMetadata.outputs"
                :navbar="false"
                :full-height="false"
                :input="true"
                lang="yaml"
                @update:model-value="(value) => newMetadata.outputs = value"
            />
        </el-form-item>
        <el-form-item>
            <template #label>
                <code>{{ $t("variables") }}</code>
            </template>
            <metadata-variables v-model="newMetadata.variables" :variables="newMetadata.variables" />
        </el-form-item>
        <el-switch
            :model-value="showConcurrency"
            @update:model-value="updateConcurrency"
            :active-text="$t('enable concurrency')"
        />
        <el-form-item v-if="concurrencySchema">
            <template #label>
                <code>{{ $t("concurrency") }}</code>
                <br>
                <task-basic
                    :schema="concurrencySchema"
                    v-model="newMetadata.concurrency"
                    root="concurrency"
                    v-if="showConcurrency"
                />
            </template>
        </el-form-item>
        <el-form-item>
            <template #label>
                <code>{{ $t("plugin defaults") }}</code>
            </template>
            <editor
                :model-value="newMetadata.pluginDefaults"
                :navbar="false"
                :full-height="false"
                :input="true"
                lang="yaml"
                @update:model-value="(value) => newMetadata.pluginDefaults = value"
            />
        </el-form-item>
        <el-form-item>
            <template #label>
                <code>{{ $t("disabled") }}</code>
            </template>
            <div>
                <el-switch active-color="green" v-model="newMetadata.disabled" @update:model-value="(value) => newMetadata.disabled = value" />
            </div>
        </el-form-item>
    </el-form>
</template>
<script setup>
    import TaskBasic from "./tasks/TaskBasic.vue";

    import Pencil from "vue-material-design-icons/Pencil.vue";
    import Eye from "vue-material-design-icons/Eye.vue";
    import Plus from "vue-material-design-icons/Plus.vue";
    import Minus from "vue-material-design-icons/Minus.vue";
</script>
<script>
    import {toRaw} from "vue";
    import markdown from "../layout/Markdown.vue";
    import MetadataInputs from "./MetadataInputs.vue";
    import MetadataVariables from "./MetadataVariables.vue";
    import yamlUtils from "../../utils/yamlUtils";
    import Editor from "../inputs/Editor.vue";
    import {mapState} from "vuex";

    export default {
        emits: ["update:modelValue"],
        created() {
            this.setup();
        },
        mounted() {
            this.$store
                .dispatch("plugin/loadSchemaType", {
                    type: "flow",
                })
                .then((response) => {
                    this.concurrencySchema = response.definitions["io.kestra.core.models.flows.Concurrency"]
                    this.schemas = response
                })
        },
        components: {
            markdown,
            Editor,
            MetadataInputs,
            MetadataVariables,
        },
        props: {
            metadata: {
                type: Object,
                required: true
            },
            editing: {
                type: Boolean,
                default: true
            }
        },
        data() {
            return {
                newMetadata: {
                    id: "",
                    namespace: "",
                    description: "",
                    retry: "",
                    labels: [["", undefined]],
                    inputs: [],
                    variables: [["", undefined]],
                    concurrency: {},
                    pluginDefaults: "",
                    outputs: "",
                    disabled: false
                },
                concurrencySchema: null,
                schemas: {},
                preview: false,
                showConcurrency: false
            };
        },
        watch: {
            newMetadata: {
                handler() {
                    this.update();
                },
                deep: true
            }
        },
        methods: {
            setup() {
                this.newMetadata.id = this.metadata.id
                this.newMetadata.namespace = this.metadata.namespace
                this.newMetadata.description = this.metadata?.description || ""
                this.newMetadata.labels = this.metadata.labels ? Object.entries(toRaw(this.metadata.labels)) : [["", undefined]]
                this.newMetadata.inputs = this.metadata.inputs || []
                this.newMetadata.variables = this.metadata.variables ? Object.entries(toRaw(this.metadata.variables)) : [["", undefined]]
                this.newMetadata.concurrency = this.metadata.concurrency || {}
                this.newMetadata.pluginDefaults = yamlUtils.stringify(this.metadata.pluginDefaults) || ""
                this.newMetadata.outputs = yamlUtils.stringify(this.metadata.outputs) || ""
                this.newMetadata.disabled = this.metadata.disabled || false
                this.newMetadata.retry = yamlUtils.stringify(this.metadata.retry) || ""
                this.showConcurrency = !!this.metadata.concurrency
            },
            addItem() {
                const local = this.newMetadata.labels || [];
                local.push(["", undefined]);

                this.newMetadata.labels = local;
            },
            removeItem(x) {
                const local = this.newMetadata.labels || [];
                local.splice(x, 1);

                this.newMetadata.labels = local;
            },
            onValue(key, value) {
                const local = this.newMetadata.labels || [];
                local[key][1] = value;
                this.newMetadata.labels = local;

            },
            onKey(key, value) {
                const local = this.newMetadata.labels || [];
                local[key][0] = value;
                this.newMetadata.labels = local;
            },
            arrayToObject(array) {
                return array.reduce((obj, [key, value]) => {
                    if (key) {
                        obj[key] = value;
                    }
                    return obj;
                }, {});
            },
            update() {
                this.$emit("update:modelValue", this.cleanMetadata);
            },
            cleanConcurrency(concurrency) {
                if (concurrency?.limit === 0) {
                    return null
                }
                return concurrency
            },
            updateConcurrency(value) {
                if (value) {
                    this.newMetadata.concurrency = this.newMetadata.concurrency || {}
                } else {
                    this.newMetadata.concurrency = null
                }
                this.showConcurrency = value;
            }
        },
        computed: {
            ...mapState("plugin", ["inputSchema", "inputsType"]),
            cleanMetadata() {
                const pluginDefaults = yamlUtils.parse(this.newMetadata.pluginDefaults);
                const outputs = yamlUtils.parse(this.newMetadata.outputs);
                const retry = yamlUtils.parse(this.newMetadata.retry);
                const metadata = {
                    id: this.newMetadata.id,
                    namespace: this.newMetadata.namespace,
                    description: this.newMetadata.description,
                    retry: retry,
                    labels: this.arrayToObject(this.newMetadata.labels),
                    inputs: this.newMetadata.inputs.filter(e => e.id && e.type),
                    variables: this.arrayToObject(this.newMetadata.variables),
                    concurrency: this.cleanConcurrency(this.newMetadata.concurrency),
                    pluginDefaults: pluginDefaults,
                    outputs: outputs,
                    disabled: this.newMetadata.disabled
                }

                return metadata;
            }
        }
    };
</script>

<style lang="scss" scoped>
    :deep(label) {
        padding-right: 0;
    }
</style>