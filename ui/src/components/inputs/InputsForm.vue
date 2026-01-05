<template>
    <template v-if="initialInputs">
        <el-form-item
            v-for="input in inputsMetaData || []"
            :key="input.id"
            :required="input.required !== false"
            :rules="requiredRules(input)"
            :prop="input.id"
            :error="inputError(input.id)"
            :inlineMessage="true"
        >
            <template #label>
                <Markdown :source="input.displayName ? input.displayName : input.id" class="d-inline-flex md-label" />
            </template>
            <Editor
                :fullHeight="false"
                :input="true"
                :navbar="false"
                v-if="input.type === 'STRING' || input.type === 'URI' || input.type === 'EMAIL'"
                :data-testid="`input-form-${input.id}`"
                v-model="inputsValues[input.id]"
                @update:model-value="onChange(input)"
                @confirm="onSubmit"
            />
            <el-select
                :fullHeight="false"
                :input="true"
                :navbar="false"
                v-if="(input.type === 'ENUM' || input.type === 'SELECT') && !input.isRadio"
                :data-testid="`input-form-${input.id}`"
                v-model="inputsValues[input.id]"
                @update:model-value="onChange(input)"
                :allowCreate="input.allowCustomValue"
                filterable
                clearable
            >
                <el-option
                    v-for="item in input.values"
                    :key="item"
                    :label="item"
                    :value="item"
                >
                    <Markdown :source="item" />
                </el-option>
            </el-select>
            <el-radio-group
                v-if="(input.type === 'ENUM' || input.type === 'SELECT') && input.isRadio"
                :data-testid="`input-form-${input.id}`"
                v-model="inputsValues[input.id]"
                @update:model-value="onChange(input)"
            >
                <el-radio v-for="item in input.values" :key="item" :label="item" :value="item" />
                <el-input
                    v-if="input.allowCustomValue"
                    v-model="inputsValues[input.id]"
                    @update:model-value="onChange(input)"
                    :placeholder="$t('custom value')"
                />
            </el-radio-group>
            <el-select
                :fullHeight="false"
                :input="true"
                :navbar="false"
                v-if="input.type === 'MULTISELECT'"
                :data-testid="`input-form-${input.id}`"
                v-model="multiSelectInputs[input.id]"
                @update:model-value="onMultiSelectChange(input, $event)"
                multiple
                filterable
                clearable
                :allowCreate="input.allowCustomValue"
            >
                <el-option
                    v-for="item in (input.values ?? input.options)"
                    :key="item"
                    :label="item"
                    :value="item"
                >
                    <Markdown :source="item" />
                </el-option>
            </el-select>
            <el-input
                type="password"
                v-if="input.type === 'SECRET'"
                :data-testid="`input-form-${input.id}`"
                v-model="inputsValues[input.id]"
                @update:model-value="onChange(input)"
                showPassword
            />
            <span v-if="input.type === 'INT'">
                <el-input-number
                    :data-testid="`input-form-${input.id}`"
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
                    :data-testid="`input-form-${input.id}`"
                    v-model="inputsValues[input.id]"
                    @update:model-value="onChange(input)"
                    :min="input.min"
                    :max="input.max && input.max >= (input.min || -Infinity) ? input.max : Infinity"
                    :step="0.001"
                />
                <div v-if="input.min || input.max" class="hint">{{ numberHint(input) }}</div>
            </span>
            <el-radio-group
                :data-testid="`input-form-${input.id}`"
                v-if="input.type === 'BOOLEAN'"
                v-model="inputsValues[input.id]"
                @update:model-value="onChange(input)"
                class="w-100 boolean-inputs"
            >
                <el-radio-button :label="$t('true')" :value="true" />
                <el-radio-button :label="$t('false')" :value="false" />
                <el-radio-button :label="$t('undefined')" value="undefined" />
            </el-radio-group>
            <el-switch
                :data-testid="`input-form-${input.id}`"
                v-if="input.type === 'BOOL'"
                v-model="inputsValues[input.id]"
                @update:model-value="onChange(input)"
                class="w-100 boolean-inputs"
            />
            <el-date-picker
                :data-testid="`input-form-${input.id}`"
                v-if="input.type === 'DATETIME'"
                v-model="inputsValues[input.id]"
                @update:model-value="onChange(input)"
                type="datetime"
            />
            <el-date-picker
                :data-testid="`input-form-${input.id}`"
                v-if="input.type === 'DATE'"
                v-model="inputsValues[input.id]"
                @update:model-value="onChange(input)"
                type="date"
            />
            <el-time-picker
                :data-testid="`input-form-${input.id}`"
                v-if="input.type === 'TIME'"
                v-model="inputsValues[input.id]"
                @update:model-value="onChange(input)"
                type="time"
            />
            <div class="el-input el-input-file" v-if="input.type === 'FILE'">
                <div class="el-input__wrapper">
                    <input
                        :data-testid="`input-form-${input.id}`"
                        :id="input.id+'-file'"
                        class="el-input__inner custom-file-input"
                        type="file"
                        :accept="getAcceptedFileTypes(input)"
                        @change="onFileChange(input, $event)"
                        autocomplete="off"
                    >
                    <span class="file-placeholder" v-html="getFilePlaceholder(inputsValues[input.id])" />
                </div>
            </div>
            <div
                v-if="input.type === 'ARRAY'"
                :data-testid="`input-form-${input.id}`"
                class="w-100"
            >
                <div v-if="editingArrayId !== input.id" class="preview">
                    <div class="tags">
                        <el-tag
                            v-for="(item, index) in parseArrayValue(input.id)"
                            :key="index"
                        >
                            {{ item }}
                        </el-tag>
                    </div>
                    <el-button
                        class="p-3"
                        @click="toggleArrayEdit(input.id)"
                        :icon="Pencil"
                    >
                        {{ $t('edit') }}
                    </el-button>
                </div>

                <div v-else class="edit_input">
                    <div>
                        <div v-for="(item, index) in editableItems[input.id]" :key="index" class="list-row">
                            <el-input
                                v-model="editableItems[input.id][index]"
                                class="array-cell"
                            />
                            <el-button @click="removeArrayItem(input, index)" :icon="DeleteOutline" class="delete-input" />
                            <div class="d-flex flex-column controls-input">
                                <ChevronUp @click="moveArrayItem(input, 'up', index)" />
                                <ChevronDown @click="moveArrayItem(input, 'down', index)" />
                            </div>
                        </div>
                    </div>
                    <el-button
                        class="add-new mt-1 border-0"
                        @click="addNewArrayItem(input)"
                        :icon="Plus"
                    >
                        {{ $t('add_new_item') }}
                    </el-button>
                    <div class="d-flex justify-content-end mt-2">
                        <el-button
                            @click="toggleArrayEdit(input.id)"
                            type="primary"
                            :icon="ContentSave"
                        >
                            {{ $t('save') }}
                        </el-button>
                    </div>
                </div>
            </div>
            <Editor
                :fullHeight="false"
                :input="true"
                :navbar="false"
                v-if="input.type === 'JSON'"
                :showScroll="inputsValues[input.id]?.length > 530 ? true : false"
                :data-testid="`input-form-${input.id}`"
                lang="json"
                v-model="inputsValues[input.id]"
            />
            <Editor
                :fullHeight="false"
                :input="true"
                :navbar="false"
                v-if="input.type === 'YAML'"
                :data-testid="`input-form-${input.id}`"
                lang="yaml"
                :modelValue="inputsValues[input.id]"
                @change="onYamlChange(input, $event)"
            />
            <DurationPicker
                v-if="input.type === 'DURATION'"
                v-model="inputsValues[input.id]"
                @update:model-value="onChange(input)"
            />
            <Markdown v-if="input.description" :data-testid="`input-form-${input.id}`" class="markdown-tooltip text-description" :source="input.description" font-size-var="font-size-xs" />
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

    <el-alert type="info" :showIcon="true" :closable="false" class="mb-3" v-else>
        {{ $t("no inputs") }}
    </el-alert>
</template>
<script lang="ts">
    import {ElMessage} from "element-plus";
    import ValidationError from "../flows/ValidationError.vue";
    import {markRaw, toRaw} from "vue";
    import {mapStores} from "pinia";
    import {useExecutionsStore} from "../../stores/executions";
    import debounce from "lodash/debounce";
    import Editor from "../../components/inputs/Editor.vue";
    import Markdown from "../layout/Markdown.vue";
    import Inputs from "../../utils/inputs";
    import DurationPicker from "./DurationPicker.vue";
    import {inputsToFormData} from "../../utils/submitTask";
    import DeleteOutline from "vue-material-design-icons/DeleteOutline.vue";
    import Pencil from "vue-material-design-icons/Pencil.vue";
    import Plus from "vue-material-design-icons/Plus.vue";
    import ContentSave from "vue-material-design-icons/ContentSave.vue";
    import ChevronUp from "vue-material-design-icons/ChevronUp.vue";
    import ChevronDown from "vue-material-design-icons/ChevronDown.vue";


    export default {
        computed: {
            ...mapStores(useExecutionsStore),
            inputErrors() {
                // we only keep errors that don't target an input directly
                const keepErrors = this.inputsMetaData.filter(it => it.id === undefined);

                return keepErrors.filter(it => it.errors && it.errors.length > 0).length > 0 ?
                    keepErrors.filter(it => it.errors && it.errors.length > 0).flatMap(it => it.errors?.flatMap(err => err.message)) :
                    null
            }
        },
        components: {Editor, Markdown, DurationPicker, ValidationError, ChevronUp, ChevronDown},
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
                default: () => []
            },
            flow: {
                type: Object,
                default: undefined,
            },
            execution: {
                type: Object,
                default: undefined,
            },
            selectedTrigger: {
                type: Object,
                default: undefined,
            }
        },
        data() {
            return {
                inputsValues: this.modelValue,
                /**
                 * To be able to compare values in a watcher, we need to return a new object
                 * We cannot compare proxied objects, that is the sole purpose of this variable.
                 * @see https://stackoverflow.com/questions/62729380/vue-watch-outputs-same-oldvalue-and-newvalue
                 */
                previousInputsValues: {},
                inputsMetaData: [],
                inputsValidation: [],
                multiSelectInputs: {},
                inputsValidated: new Set(),
                debouncedValidation: () => {},
                editingArrayId: null,
                editableItems: {},
                // expose icon components to the template so linters and the template can resolve them
                DeleteOutline: markRaw(DeleteOutline),
                Pencil:markRaw(Pencil),
                Plus:markRaw(Plus),
                ContentSave:markRaw(ContentSave)
            };
        },
        emits: ["update:modelValue", "update:modelValueNoDefault", "update:checks", "confirm", "validation"],
        created() {
            this.inputsMetaData = JSON.parse(JSON.stringify(this.initialInputs));
            this.debouncedValidation = debounce(this.validateInputs, 500)

            if(this.selectedTrigger?.inputs){
                this.inputsValues = toRaw(this.selectedTrigger.inputs);
            }

            this.validateInputs().then(() => {
                this.$watch("inputsValues", {
                    handler(val) {
                        // only revalidate if values have changed
                        if(JSON.stringify(val) !== JSON.stringify(this.previousInputsValues)){
                            // only revalidate if values are stable for more than 500ms
                            // to avoid too many calls to the server
                            this.debouncedValidation();
                            this.$emit("update:modelValue", this.inputsValues);
                            this.$emit("update:modelValueNoDefault", this.inputsValuesWithNoDefault());
                        }
                        this.previousInputsValues = JSON.parse(JSON.stringify(val))
                    },
                    deep: true
                });

                // on first load default values need to be sent to the parent
                // since they are part of the actual value
                this.$emit("update:modelValue", this.inputsValues)
            });
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

            normalizeJSON(value) {
                try {
                    // Step 1: Remove trailing commas in objects and arrays
                    let cleaned = value.replace(/,\s*([}\]])/g, "$1");

                    // Step 2: Quote unquoted keys (simple case: keys with letters, numbers, or _)
                    cleaned = cleaned.replace(/([{,]\s*)([a-zA-Z0-9_]+)\s*:/g, "$1\"$2\":");

                    // Step 3: Parse into JS object
                    return JSON.parse(cleaned);
                } catch (e) {
                    console.error("Failed to normalize JSON:", e.message);
                    return null;
                }
            },
            inputError(id) {
                // if this input has not been edited yet
                // showing any error is annoying
                if(!this.inputsValidated.has(id)){
                    return null;
                }

                const errors = this.inputsMetaData
                    .filter((it) => {
                        return it.id === id && it.errors && it.errors.length > 0;
                    })
                    .map(it => it.errors.map(err => err.message).join("\n"))

                return errors.length > 0 ? errors[0] : null;
            },
            updateDefaults() {
                for (const input of this.inputsMetaData || []) {
                    const {type, id, value, defaults} = input;
                    const valueOrDefault = value ?? defaults;
                    if (this.inputsValues[id] === undefined || this.inputsValues[id] === null || input.isDefault) {
                        if (type === "MULTISELECT") {
                            this.multiSelectInputs[id] = valueOrDefault;
                        } else if(type === "JSON" && value == undefined && input.isDefault) {
                            /*
                            * Handle multiline JSON default values
                            * See https://github.com/kestra-io/kestra/issues/11449
                            */
                            this.inputsValues[id] = Inputs.normalize(type, this.normalizeJSON(input.defaults));
                        } else {
                            this.inputsValues[id] = Inputs.normalize(type, valueOrDefault);
                        }
                    }
                }
            },
            onChange(input) {
                // give 2 seconds for the user to finish their edit
                // and for the server to return with validated content
                setTimeout(() => {
                    this.inputsValidated.add(input.id);
                }, 2000);
                input.isDefault = false;
                this.$emit("update:modelValue", this.inputsValues);
                this.$emit("update:modelValueNoDefault", this.inputsValuesWithNoDefault());
            },
            onSubmit() {
                this.$emit("confirm");
            },
            onMultiSelectChange(input, e) {
                this.inputsValues[input.id] = JSON.stringify(e);
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

                const file = files[0];

                // Sanitize the filename: remove spaces and special characters
                const sanitizedName = file.name
                    .replace(/[^a-zA-Z0-9.-]/g, "_") // Replace special chars with underscore
                    .replace(/\s+/g, "_");           // Replace spaces with underscore

                // Create a new File object with the sanitized name
                const sanitizedFile = new File([file], sanitizedName, {
                    type: file.type,
                    lastModified: file.lastModified,
                });

                const acceptedTypes = this.getAcceptedFileTypes(input);
                if (acceptedTypes) {
                    const allowedTypes = acceptedTypes.toLowerCase().split(",");
                    const fileName = sanitizedName.toLowerCase();
                    const fileType = file.type.toLowerCase();

                    const isAllowed = allowedTypes.some(type => {
                        type = type.trim();
                        if (type.startsWith(".")) {
                            return fileName.endsWith(type);
                        } else {
                            return fileType === type;
                        }
                    });

                    if (!isAllowed) {
                        ElMessage.error(this.$t("fileTypeNotAllowed", {types: acceptedTypes}));
                        e.target.value = "";
                        return;
                    }
                }

                this.inputsValues[input.id] = sanitizedFile;
                setTimeout(() => this.onChange(input), 300);
            },
            onYamlChange(input, e) {
                this.inputsValues[input.id] = e.target.value;
                this.onChange(input);
            },
            inputsValuesWithNoDefault() {
                return this.inputsMetaData.reduce((acc, input) => {
                    acc[input.id] = input.isDefault ? undefined : this.inputsValues[input.id];
                    return acc;
                }, {});
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
            async validateInputs() {
                if (this.inputsMetaData === undefined || this.inputsMetaData.length === 0) {
                    return;
                }

                const inputsValuesWithNoDefault = this.inputsValuesWithNoDefault();

                const formData = inputsToFormData(this, this.inputsMetaData, inputsValuesWithNoDefault);

                const metadataCallback = (response) => {
                    this.$emit("update:checks", response.checks || []);
                    this.inputsMetaData = response.inputs.reduce((acc,it) => {
                        if(it.enabled){
                            acc.push({...it.input, errors: it.errors, value: it.value || it.input.prefill, isDefault: it.isDefault});
                        }
                        return acc;
                    }, [])
                    this.updateDefaults();
                }

                if (this.flow !== undefined) {
                    const options = {namespace: this.flow.namespace, id: this.flow.id};
                    const {data} = await this.executionsStore.validateExecution({...options, formData})

                    metadataCallback(data);

                } else if (this.execution !== undefined) {
                    const options = {id: this.execution.id};
                    const {data} = await this.executionsStore.validateResume({...options, formData})

                    metadataCallback(data);
                } else {
                    this.$emit("validation", {
                        formData: formData,
                        inputsMetaData: this.inputsMetaData,
                        callback: (response) => {
                            metadataCallback(response);
                        }
                    });
                }
            },
            requiredRules(input) {
                if(input.required === false)
                    return undefined

                if(input.type === "BOOLEAN"){
                    return [{
                        validator: (_, val, callback) => {
                            if(val === "undefined"){
                                return callback(new Error(this.$t("is required", {field: input.displayName || input.id})));
                            }
                            callback()
                        },
                    }]
                }

                if(["ENUM", "SELECT", "MULTISELECT"].includes(input.type)){
                    return [
                        {
                            required: true,
                            validator: (_, __, callback) => {
                                const val = input.type === "MULTISELECT" ? this.multiSelectInputs[input.id] : this.inputsValues[input.id]
                                if(!val?.length){
                                    return callback(new Error(this.$t("is required", {field: input.displayName || input.id})));
                                }
                                callback()
                            },
                            trigger: "change",
                        }
                    ]
                }

                return undefined
            },
            parseArrayValue(inputId) {
                const value = this.inputsValues[inputId];
                if (!value) return [];

                if (typeof value === "string") {
                    return JSON.parse(value);
                }
            },
            addNewArrayItem(input) {
                if (!this.editableItems[input.id]) {
                    this.editableItems[input.id] = this.parseArrayValue(input.id).map(item => item?.toString() || "");
                }
                this.editableItems[input.id].push("");
            },
            updateArrayValue(input) {
                const validItems = this.editableItems[input.id]
                    .filter(item => item && item.trim() !== "")
                    .map(item => item.trim());

                this.inputsValues[input.id] = JSON.stringify(validItems);
                this.onChange(input);
            },
            removeArrayItem(input, index) {
                this.editableItems[input.id].splice(index, 1);
                this.updateArrayValue(input);
            },
            toggleArrayEdit(inputId) {
                const isEditing = this.editingArrayId === inputId;
                if (isEditing && this.editableItems[inputId]) {
                    this.updateArrayValue(this.inputsMetaData.find(i => i.id === inputId));
                }
                this.editingArrayId = isEditing ? null : inputId;
                if (!isEditing) {
                    this.editableItems[inputId] = this.parseArrayValue(inputId).map(v => v?.toString() || "");
                }
            },
            moveArrayItem(input, direction, index) {
                const {id} = input;
                const items = this.editableItems[id];
                const isValidMove = {
                    up: () => index > 0,
                    down: () => index < items.length - 1
                }[direction]?.();
                if (!isValidMove) return;
                const targetIndex = direction === "up" ? index - 1 : index + 1;
                [items[index], items[targetIndex]] = [items[targetIndex], items[index]];

                this.updateArrayValue(input);
            },
            getFilePlaceholder(value) {
                if (typeof value === "string" && value.startsWith("nsfile://")) {
                    return this.$t("defaultsToNamespaceFile", {name: value.substring(10)});
                }
                if (value && typeof value.name === "string") {
                    return value.name;
                }
                return this.$t("no_file_choosen");
            },
            getAcceptedFileTypes(input: { allowedFileExtensions?: string[]; accept?: string; }) {
                if (input.allowedFileExtensions && input.allowedFileExtensions.length > 0) {
                    return input.allowedFileExtensions.join(",");
                }
                return input.accept || "";
            },
        },
        watch: {
            flow () {
                this.validateInputs();
            },
            execution () {
                this.validateInputs();
            }
        }
    };
</script>

<style scoped lang="scss">
.md-label {
    height: 20px;
}

.hint {
    font-size: var(--font-size-xs);
    color: var(--bs-gray-700);
}

.text-description {
    width: 100%;
    font-size: var(--font-size-xs);
    color: var(--bs-gray-700);
}

:deep(.boolean-inputs) {
    display: flex;
    align-items: center;

    .el-radio-button {
        &.is-active {
            .el-radio-button__original-radio:not(:disabled) + .el-radio-button__inner {
                color: var(--ks-content-primary);
                background-color: var(--bs-gray-100);
                box-shadow: 0 0 0 0 var(--ks-border-active);
            }
        }

        .el-radio-button__inner {
            border: var(--ks-border-primary);
            transition: 0.3s ease-in-out;

            &:hover {
                color: var(--ks-content-secondary);
                border-color: var(--ks-border-active);
                background-color: var(--ks-background-card);
            }

            &:first-child {
                border-left: var(--ks-border-primary);
            }
        }
    }
}

.el-input-file {
    display: flex;
    align-items: center;

    .el-input__inner {
        cursor: pointer;
    }

    .el-input__wrapper {
        padding: 0.5rem;
    }

}

.preview {
    display: flex;
    align-items: center;
    gap: 10px;

    .tags {
        flex: 1;
        background: var(--ks-background-input);
        border: 1px solid var(--ks-border-primary);
        border-radius: 4px;
        display: flex;
        flex-wrap: wrap;
        align-items: center;
        padding: 5px;
        gap: 4px;

        :deep(.el-tag) {
            display: inline-flex;
            align-items: center;
            border-radius: 4px;
            background-color: var(--ks-tag-background);
            color: var(--ks-content-tag);
        }
    }
}

.edit_input {
    .list-row {
        position: relative;
        margin-bottom: 8px;

        .array-cell {
            :deep(.el-input__wrapper) {
                box-shadow: none;
                border: 1px solid var(--ks-border-primary);
                border-radius: 5px;
            }

            :deep(.el-input__inner) {
                color: #eeae7e !important;
                font-size: var(--font-size-sm) !important;

                html.light & {
                    color: #dd5f00 !important;
                }
            }
        }

        .delete-input {
            position: absolute;
            right: 28px;
            top: 50%;
            transform: translateY(-50%);
            padding: 4px;
            border: none;
            color: var(--ks-content-secondary);
            background: transparent;

            &:hover {
                color: var(--ks-content-error);
            }
        }

        .controls-input {
            position: absolute;
            right: 2px;
            top: 50%;
            transform: translateY(-50%);
            padding: 3px;
            border-left: 1px solid var(--ks-border-primary);
            color: var(--ks-content-secondary);
            background: transparent;
        }
    }

    .add-new {
        padding: 5px 8px;
        color: var(--ks-content-tertiary);
        font-size: var(--font-size-sm);
        background: none;

        &:hover {
            color: var(--ks-content-secondary);
        }
    }
}

.el-form-item {
    &:has(.edit_input) {
        padding: 1rem;
        border-radius: 8px;
        border: 1px solid var(--ks-border-primary);
        background-color: var(--ks-dropdown-background-active);
    }
}

:deep(.editor-container){
        max-height: 200px;

        & .ks-monaco-editor {
            overflow-x: hidden;
        }
    }

.custom-file-input {
  color: transparent;
  width: 120px;
}

.custom-file-input::-webkit-file-upload-text {
  visibility: hidden;
}

.el-input-file {
  .el-input__wrapper {
    display: flex;
    align-items: center;
    padding: 4px 0 4px 0;
    position: relative;
    max-width: 100%;
  }

  .custom-file-input {
    max-width: 110px;
    min-width: 110px;
    position: relative;
    z-index: 1;
  }

  .file-placeholder {
    margin-left: 8px;
    color: var(--ks-content-secondary) !important;
    font-size: 0.9em;
    flex: 1;
    max-width: calc(100% - 140px); /* 110px for button + 30px for margins/padding */
    min-width: 0;
    display: block;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    padding-right: 16px;
  }
}
</style>
