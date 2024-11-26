<template>
    <template v-if="initialInputs">
        <el-form-item
            v-for="input in filteredInputs || []"
            :key="input.id"
            :label="input.displayName ? input.displayName : input.id"
            :required="input.required !== false"
            :rules="input.type === 'BOOLEAN' ? [requiredBooleanRule(input)] : undefined"
            :prop="input.id"
            :error="inputError(input.id)"
            :inline-message="true"
        >
            <editor
                :full-height="false"
                :input="true"
                :navbar="false"
                v-if="input.type === 'STRING' || input.type === 'URI' || input.type === 'EMAIL'"
                :data-test-id="`input-form-${input.id}`"
                v-model="inputsValues[input.id]"
                @update:model-value="onChange(input)"
                @confirm="onSubmit"
            />
            <el-select
                :full-height="false"
                :input="true"
                :navbar="false"
                v-if="input.type === 'ENUM' || input.type === 'SELECT'"
                :data-test-id="`input-form-${input.id}`"
                v-model="inputsValues[input.id]"
                @update:model-value="onChange(input)"
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
                @update:model-value="onMultiSelectChange(input, $event)"
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
                v-model="inputsValues[input.id]"
                @update:model-value="onChange(input)"
                show-password
            />
            <span v-if="input.type === 'INT'">
                <el-input-number
                    :data-test-id="`input-form-${input.id}`"
                    v-model="inputsValues[input.id]"
                    @update:model-value="onChange(input)"
                    :min="input.min"
                    :max="input.max && input.max >= (input.min || -Infinity) ? input.max : Infinity"
                    :step="1"
                />
                <div v-if="input.min || input.max" class="hint">{{ numberHint(input) }}</div>
            </span>
            <span v-if="input.type === 'FLOAT'">
                <el-input-number
                    :data-test-id="`input-form-${input.id}`"
                    v-model="inputsValues[input.id]"
                    @update:model-value="onChange(input)"
                    :min="input.min"
                    :max="input.max && input.max >= (input.min || -Infinity) ? input.max : Infinity"
                    :step="0.001"
                />
                <div v-if="input.min || input.max" class="hint">{{ numberHint(input) }}</div>
            </span>
            <el-radio-group
                :data-test-id="`input-form-${input.id}`"
                v-if="input.type === 'BOOLEAN'"
                v-model="inputsValues[input.id]"
                @update:model-value="onChange(input)"
                class="w-100"
            >
                <el-radio-button :label="$t('true')" :value="true" />
                <el-radio-button :label="$t('false')" :value="false" />
                <el-radio-button :label="$t('undefined')" value="undefined" />
            </el-radio-group>
            <el-date-picker
                :data-test-id="`input-form-${input.id}`"
                v-if="input.type === 'DATETIME'"
                v-model="inputsValues[input.id]"
                @update:model-value="onChange(input)"
                type="datetime"
            />
            <el-date-picker
                :data-test-id="`input-form-${input.id}`"
                v-if="input.type === 'DATE'"
                v-model="inputsValues[input.id]"
                @update:model-value="onChange(input)"
                type="date"
            />
            <el-time-picker
                :data-test-id="`input-form-${input.id}`"
                v-if="input.type === 'TIME'"
                v-model="inputsValues[input.id]"
                @update:model-value="onChange(input)"
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
                        :style="{display: typeof(inputsValues[input.id]) === 'string' && inputsValues[input.id].startsWith('kestra:///') ? 'none': ''}"
                    >
                    <label
                        v-if="typeof(inputsValues[input.id]) === 'string' && inputsValues[input.id].startsWith('kestra:///')"
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
                v-model="inputsValues[input.id]"
            />
            <editor
                :full-height="false"
                :input="true"
                :navbar="false"
                v-if="input.type === 'YAML'"
                :data-test-id="`input-form-${input.id}`"
                lang="yaml"
                :model-value="inputsValues[input.id]"
                @change="onYamlChange(input, $event)"
            />
            <duration-picker
                v-if="input.type === 'DURATION'"
                :data-test-id="`input-form-${input.id}`"
                v-model="inputsValues[input.id]"
                @update:model-value="onChange(input)"
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
        <div class="d-flex justify-content-end">
            <ValidationError v-if="inputErrors" :errors="inputErrors" />
        </div>
    </template>

    <el-alert type="info" :show-icon="true" :closable="false" v-else>
        {{ $t("no inputs") }}
    </el-alert>
</template>
<script setup>
    import ValidationError from "../flows/ValidationError.vue";
</script>
<script>
    import {mapState} from "vuex";
    import debounce from "lodash/debounce";
    import Editor from "../../components/inputs/Editor.vue";
    import Markdown from "../layout/Markdown.vue";
    import Inputs from "../../utils/inputs";
    import YamlUtils from "../../utils/yamlUtils.js";
    import DurationPicker from "./DurationPicker.vue";
    import {inputsToFormDate} from "../../utils/submitTask"

    export default {
        computed: {
            ...mapState("auth", ["user"]),
            YamlUtils() {
                return YamlUtils
            },
            inputErrors() {
                // we only keep errors that don't target an input directly
                const keepErrors = this.inputsMetaData.filter(it => it.inputId === undefined);

                return keepErrors.filter(it => it.errors && it.errors.length > 0).length > 0 ?
                    keepErrors.filter(it => it.errors && it.errors.length > 0).flatMap(it => it.errors?.flatMap(err => err.message)) :
                    null
            },
            filteredInputs(){
                const inputVisibility = this.inputsMetaData.reduce((acc, it) => {
                    acc[it.inputId] = it.enabled;
                    return acc;
                }, {});
                return this.initialInputs.filter(it => inputVisibility[it.id]);
            },
            /**
             * To be able to compare values in a watcher, we need to return a new object
             * We cannot compare proxied objects
             * https://stackoverflow.com/questions/62729380/vue-watch-outputs-same-oldvalue-and-newvalue
             */
        },
        components: {Editor, Markdown, DurationPicker},
        props: {
            executeClicked: {
                type: Boolean,
                default: false
            },
            modelValue: {
                default: () => ({}),
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
                inputsValues: this.modelValue,
                previousInputsValues: {},
                inputsMetaData: [],
                inputsValidation: [],
                multiSelectInputs: {},
                inputsValidated: new Set(),
            };
        },
        emits: ["update:modelValue", "confirm", "validation"],
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
            inputError(id) {
                // if this input has not been edited yet
                // showing any error is annoying
                if(!this.inputsValidated.has(id)){
                    return null;
                }

                const errors = this.inputsMetaData
                    .filter(({inputId, errors}) => {
                        return inputId === id && errors && errors.length > 0;
                    })
                    .map(it => it.errors.map(err => err.message).join("\n"))

                return errors.length > 0 ? errors[0] : null;
            },
            updateDefaults() {
                for (const input of this.inputsMetaData || []) {
                    if (this.inputsValues[input.inputId] === undefined || this.inputsValues[input.inputId] === null) {
                        const {type, defaults} = this.initialInputs.find(it => it.id === input.inputId);
                        if (type === "MULTISELECT") {
                            this.multiSelectInputs[input.id] = input.defaults;
                        }
                        this.inputsValues[input.inputId] = Inputs.normalize(type, defaults);
                    }
                }
            },
            onChange(input) {
                // give a second for the user to finish their edit
                // and for the server to return with validated content
                setTimeout(() => {
                    this.inputsValidated.add(input.id);
                }, 300);
                this.$emit("update:modelValue", this.inputsValues);
            },
            onSubmit() {
                this.$emit("confirm");
            },
            onMultiSelectChange(input, e) {
                this.inputsValues[input.id] = JSON.stringify(e).toString();
                this.onChange(input);
            },
            onFileChange(input, e) {
                if (!e.target) {
                    return;
                }

                const files = e.target.files || e.dataTransfer.files;
                if (!files.length) {
                    return;
                }
                this.inputsValues[input.id] = e.target.files[0];
                this.onChange(input);
            },
            onYamlChange(input, e) {
                this.inputsValues[input.id] = e.target.value;
                this.onChange(input);
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
                if (this.initialInputs === undefined || this.initialInputs.length === 0) {
                    return;
                }

                const formData = inputsToFormDate(this, this.initialInputs, this.inputsValues);

                if (this.flow !== undefined) {
                    const options = {namespace: this.flow.namespace, id: this.flow.id};
                    this.$store.dispatch("execution/validateExecution", {...options, formData})
                        .then(response => {
                            this.inputsMetaData = response.data.inputs.map(it => {
                                return {
                                    inputId: it.input?.id,
                                    enabled: it.enabled,
                                    errors: it.errors
                                }
                            })
                            this.updateDefaults();
                        });
                } else if (this.execution !== undefined) {
                    const options = {id: this.execution.id};
                    this.$store.dispatch("execution/validateResume", {...options, formData})
                        .then(response => {
                            this.inputsMetaData = response.data.inputs.map(it => {
                                return {
                                    inputId: it.input?.id,
                                    enabled: it.enabled,
                                    errors: it.errors
                                }
                            })
                            this.updateDefaults();
                        });
                } else {
                    this.$emit("validation", {
                        formData: formData,
                        callback: (response) => {
                            this.inputsMetaData = response.inputs.map(it => {
                                return {
                                    inputId: it.input?.id,
                                    enabled: it.enabled,
                                    errors: it.errors
                                }
                            })
                            this.updateDefaults();
                        }
                    });
                }
            },
            requiredBooleanRule(input) {
                return input.required !== false ? {
                    validator: (_, val, callback) => {
                        if(val === "undefined"){
                            return callback(new Error(this.$t("is required", {field: input.displayName || input.id})));
                        }
                        callback()
                    },
                } : undefined
            }
        },
        watch: {
            inputsValues: {
                handler(val) {
                    // only revalidate if values have changed
                    if(JSON.stringify(val) !== JSON.stringify(this.previousInputsValues)){
                        // only revalidate if values are stable for more than 200ms
                        // to avoid too many useless calls to the server
                        debounce(this.validateInputs, 200)();
                        this.$emit("update:modelValue", this.inputsValues);
                    }
                    this.previousInputsValues = JSON.parse(JSON.stringify(val))
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
    width: 100%;
    font-size: var(--font-size-xs);
    color: var(--bs-gray-700);
}
</style>
