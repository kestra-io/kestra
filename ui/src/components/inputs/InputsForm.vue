<template>
    <template v-if="inputsList">
        <el-form-item
            v-for="input in inputsList || []"
            :key="input.id"
            :label="input.displayName ? input.displayName : input.id"
            :required="input.required !== false"
            :prop="input.id"
        >
            <editor
                :full-height="false"
                :input="true"
                :navbar="false"
                v-if="input.type === 'STRING' || input.type === 'URI' || input.type === 'EMAIL'"
                :data-test-id="`input-form-${input.id}`"
                v-model="inputs[input.id]"
                @update:model-value="onChange"
                @confirm="onSubmit"
            />
            <el-select
                :full-height="false"
                :input="true"
                :navbar="false"
                v-if="input.type === 'ENUM' || input.type === 'SELECT'"
                :data-test-id="`input-form-${input.id}`"
                v-model="inputs[input.id]"
                @update:model-value="onChange"
                :allow-create="input.allowCustomValue"
                filterable
            >
                <el-option
                    v-for="item in input.values"
                    :key="item"
                    :label="item"
                    :value="item"
                >
                    {{ item }}
                </el-option>
            </el-select>
            <el-select
                :full-height="false"
                :input="true"
                :navbar="false"
                v-if="input.type === 'MULTISELECT'"
                :data-test-id="`input-form-${input.id}`"
                v-model="multiSelectInputs[input.id]"
                @update:model-value="onMultiSelectChange(input.id, $event)"
                multiple
                filterable
                :allow-create="input.allowCustomValue"
            >
                <el-option
                    v-for="item in (input.values ?? input.options)"
                    :key="item"
                    :label="item"
                    :value="item"
                >
                    {{ item }}
                </el-option>
            </el-select>
            <el-input
                type="password"
                v-if="input.type === 'SECRET'"
                :data-test-id="`input-form-${input.id}`"
                v-model="inputs[input.id]"
                @update:model-value="onChange"
                show-password
            />
            <span v-if="input.type === 'INT'">
                <el-input-number
                    :data-test-id="`input-form-${input.id}`"
                    v-model="inputs[input.id]"
                    @update:model-value="onChange"
                    :min="input.min"
                    :max="input.max && input.max >= (input.min || -Infinity) ? input.max : Infinity"
                    :step="1"
                />
                <div v-if="input.min || input.max" class="hint">{{ numberHint(input) }}</div>
            </span>
            <span v-if="input.type === 'FLOAT'">
                <el-input-number
                    :data-test-id="`input-form-${input.id}`"
                    v-model="inputs[input.id]"
                    @update:model-value="onChange"
                    :min="input.min"
                    :max="input.max && input.max >= (input.min || -Infinity) ? input.max : Infinity"
                    :step="0.001"
                />
                <div v-if="input.min || input.max" class="hint">{{ numberHint(input) }}</div>
            </span>
            <el-radio-group
                :data-test-id="`input-form-${input.id}`"
                v-if="input.type === 'BOOLEAN'"
                v-model="inputs[input.id]"
                @update:model-value="onChange"
                class="w-100"
            >
                <el-radio-button :label="$t('true')" :value="true" />
                <el-radio-button :label="$t('false')" :value="false" />
                <el-radio-button :label="$t('undefined')" :value="undefined" />
            </el-radio-group>
            <el-date-picker
                :data-test-id="`input-form-${input.id}`"
                v-if="input.type === 'DATETIME'"
                v-model="inputs[input.id]"
                @update:model-value="onChange"
                type="datetime"
            />
            <el-date-picker
                :data-test-id="`input-form-${input.id}`"
                v-if="input.type === 'DATE'"
                v-model="inputs[input.id]"
                @update:model-value="onChange"
                type="date"
            />
            <el-time-picker
                :data-test-id="`input-form-${input.id}`"
                v-if="input.type === 'TIME'"
                v-model="inputs[input.id]"
                @update:model-value="onChange"
                type="time"
            />
            <div class="el-input el-input-file" v-if="input.type === 'FILE'">
                <div class="el-input__wrapper">
                    <input
                        :data-test-id="`input-form-${input.id}`"
                        :id="input.id+'-file'"
                        class="el-input__inner"
                        type="file"
                        @change="onFileChange(input, $event)"
                        autocomplete="off"
                        :style="{display: typeof(inputs[input.id]) === 'string' && inputs[input.id].startsWith('kestra:///') ? 'none': ''}"
                    >
                    <label
                        v-if="typeof(inputs[input.id]) === 'string' && inputs[input.id].startsWith('kestra:///')"
                        :for="input.id+'-file'"
                    >Kestra Internal Storage File</label>
                </div>
            </div>
            <editor
                :full-height="false"
                :input="true"
                :navbar="false"
                v-if="input.type === 'JSON' || input.type === 'ARRAY'"
                :data-test-id="`input-form-${input.id}`"
                lang="json"
                v-model="inputs[input.id]"
            />
            <editor
                :full-height="false"
                :input="true"
                :navbar="false"
                v-if="input.type === 'YAML'"
                :data-test-id="`input-form-${input.id}`"
                lang="yaml"
                :model-value="inputs[input.id]"
                @change="onYamlChange(input, $event)"
            />
            <duration-picker
                v-if="input.type === 'DURATION'"
                :data-test-id="`input-form-${input.id}`"
                v-model="inputs[input.id]"
                @update:model-value="onChange"
            />
            <markdown v-if="input.description" :data-test-id="`input-form-${input.id}`" class="markdown-tooltip text-description" :source="input.description" font-size-var="font-size-xs" />
            <template v-if="executeClicked">
                <template v-for="err in input.errors ?? []" :key="err">
                    <el-text type="warning">
                        {{ err.message }}
                    </el-text>
                </template>
            </template>
        </el-form-item>
    </template>
    <el-alert type="info" :show-icon="true" :closable="false" v-else>
        {{ $t("no inputs") }}
    </el-alert>
</template>

<script>
    import Editor from "../../components/inputs/Editor.vue";
    import Markdown from "../layout/Markdown.vue";
    import Inputs from "../../utils/inputs";
    import YamlUtils from "../../utils/yamlUtils.js";
    import DurationPicker from "./DurationPicker.vue";
    import {inputsToFormDate} from "../../utils/submitTask"
    import {mapState} from "vuex";

    export default {
        computed: {
            ...mapState("auth", ["user"]),
            YamlUtils() {
                return YamlUtils
            },
        },
        components: {Editor, Markdown, DurationPicker},
        props: {
            executeClicked: {
                type: Boolean,
                default: false
            },
            modelValue: {
                default: undefined,
                type: Object
            },
            initialInputs: {
                type: Array,
                default: undefined
            },
            flow: {
                type: Object,
                default: undefined,
            },
            execution: {
                type: Object,
                default: undefined,
            },
        },
        data() {
            return {
                inputs: {},
                inputsList: [],
                inputsValidation: [],
                multiSelectInputs: {},
            };
        },
        emits: ["update:modelValue", "confirm"],
        created() {
            this.inputsList.push(...(this.initialInputs ?? []));
            this.validateInputs();
        },
        mounted() {
            setTimeout(() => {
                const input = this.$el && this.$el.querySelector && this.$el.querySelector("input")
                if (input && !input.className.includes("mx-input")) {
                    input.focus()
                }
            }, 500)

            this._keyListener = function(e) {
                // Ctrl/Control + Enter
                if (e.key === "Enter" && (e.ctrlKey || e.metaKey))  {
                    e.preventDefault();
                    this.onSubmit();
                }
            };

            document.addEventListener("keydown", this._keyListener.bind(this));
        },
        beforeUnmount() {
            document.removeEventListener("keydown", this._keyListener);
        },
        methods: {
            updateDefaults() {
                for (const input of this.inputsList || []) {
                    if (this.inputs[input.id] === undefined || this.inputs[input.id] === null) {
                        if (input.type === "MULTISELECT") {
                            this.multiSelectInputs[input.id] = input.defaults;
                        }
                        this.inputs[input.id] = Inputs.normalize(input.type, input.defaults);
                    }
                }
            },
            onChange() {
                this.$emit("update:modelValue", this.inputs);
            },
            onSubmit() {
                this.$emit("confirm");
            },
            onMultiSelectChange(input, e) {
                this.inputs[input] = JSON.stringify(e).toString();
                this.onChange();
            },
            onFileChange(input, e) {
                if (!e.target) {
                    return;
                }

                const files = e.target.files || e.dataTransfer.files;
                if (!files.length) {
                    return;
                }
                this.inputs[input.id] = e.target.files[0];
                this.onChange();
            },
            onYamlChange(input, e) {
                this.inputs[input.id] = e.target.value;
                this.onChange();
            },
            numberHint(input){
                const {min, max} = input;

                if (min !== undefined && max !== undefined) {
                    if(min > max) return `Minimum value ${min} is larger than maximum value ${max}, so we've removed the upper limit.`;
                    return `Minimum value is ${min}, maximum value is ${max}.`;
                } else if (min !== undefined) {
                    return `Minimum value is ${min}.`;
                } else if (max !== undefined) {
                    return `Maximum value is ${max}.`;
                } else return false;
            },
            validateInputs() {
                if (this.inputsList === undefined || this.inputsList.length === 0) {
                    return;
                }

                const formData = inputsToFormDate(this, this.inputsList, this.inputs);
                if (this.flow !== undefined) {
                    const options = {namespace: this.flow.namespace, id: this.flow.id};
                    this.$store.dispatch("execution/validateExecution", {...options, formData})
                        .then(response => {
                            this.inputsList = response.data.inputs.filter(it => it.enabled).map(it => {
                                return {...it.input, errors: it.errors}
                            });
                            this.updateDefaults();
                        });

                    return;
                }

                if (this.execution !== undefined) {
                    const options = {id: this.execution.id};
                    this.$store.dispatch("execution/validateResume", {...options, formData})
                        .then(response => {
                            this.inputsList = response.data.inputs.filter(it => it.enabled).map(it => it.input);
                            this.updateDefaults();
                        });
                }
            }
        },
        watch: {
            inputs: {
                handler() {
                    this.validateInputs();
                    this.$emit("update:modelValue", this.inputs);
                },
                deep: true
            },
            flow: {
                handler() {
                    this.validateInputs()
                }
            },
            execution: {
                handler() {
                    this.validateInputs()
                }
            }
        }
    };
</script>

<style scoped lang="scss">
.hint {
    font-size: var(--font-size-xs);
    color: var(--bs-gray-700);
}

.text-description {
    font-size: var(--font-size-xs);
    color: var(--bs-gray-700);
}
</style>
