<template>
    <template v-if="initialInputs">
        <KsFormItem
            v-for="input in inputsMetaData"
            :key="input.id"
            :required="input.required !== false"
            :rules="requiredRules(input)"
            :prop="input.id"
            :error="inputError(input.id)"
            :inlineMessage="true"
        >
            <template #label>
                <KsMarkdown :content="input.displayName ? input.displayName : input.id" class="d-inline-flex md-label" />
            </template>
            <KsEditor
                v-bind="editorBindings"
                :options="{fullHeight: false}"
                :inline="true"
                :navbar="false"
                v-if="input.type === 'STRING' || input.type === 'URI' || input.type === 'EMAIL'"
                :data-testid="`input-form-${input.id}`"
                v-model="inputsValues[input.id]"
                @update:model-value="onChange(input)"
                @confirm="onSubmit"
            />
            <KsSelect
                :fullHeight="false"
                :input="true"
                :navbar="false"
                v-if="input.type === 'SELECT' && !input.isRadio"
                :data-testid="`input-form-${input.id}`"
                v-model="inputsValues[input.id]"
                @update:model-value="onChange(input)"
                :allowCreate="input.allowCustomValue"
                filterable
                clearable
            >
                <KsOption
                    v-for="item in (input.values ?? []).map(toOption)"
                    :key="item.value"
                    :label="item.label"
                    :value="item.value"
                >
                    <KsMarkdown :content="item.label" />
                </KsOption>
            </KsSelect>
            <KsRadioGroup
                v-if="input.type === 'SELECT' && input.isRadio"
                :data-testid="`input-form-${input.id}`"
                v-model="inputsValues[input.id]"
                @update:model-value="onChange(input)"
            >
                <KsRadio v-for="item in (input.values ?? []).map(toOption)" :key="item.value" :label="item.label" :value="item.value" />
                <KsInput
                    v-if="input.allowCustomValue"
                    v-model="inputsValues[input.id]"
                    @update:model-value="onChange(input)"
                    :placeholder="$t('custom value')"
                />
            </KsRadioGroup>
            <KsSelect
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
                <KsOption
                    v-for="item in ((input.values ?? input.options) ?? []).map(toOption)"
                    :key="item.value"
                    :label="item.label"
                    :value="item.value"
                >
                    <KsMarkdown :content="item.label" />
                </KsOption>
            </KsSelect>
            <KsInput
                type="password"
                v-if="input.type === 'SECRET'"
                :data-testid="`input-form-${input.id}`"
                v-model="inputsValues[input.id]"
                @update:model-value="onChange(input)"
                showPassword
            />
            <span v-if="input.type === 'INT'">
                <KsInputNumber
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
                <KsInputNumber
                    :data-testid="`input-form-${input.id}`"
                    v-model="inputsValues[input.id]"
                    @update:model-value="onChange(input)"
                    :min="input.min"
                    :max="input.max && input.max >= (input.min || -Infinity) ? input.max : Infinity"
                    :step="0.001"
                />
                <div v-if="input.min || input.max" class="hint">{{ numberHint(input) }}</div>
            </span>
            <KsSwitch
                :data-testid="`input-form-${input.id}`"
                v-if="input.type === 'BOOL'"
                v-model="inputsValues[input.id]"
                @update:model-value="onChange(input)"
                class="w-100 boolean-inputs"
            />
            <KsDatePicker
                :data-testid="`input-form-${input.id}`"
                v-if="input.type === 'DATETIME'"
                v-model="inputsValues[input.id]"
                @update:model-value="onChange(input)"
                type="datetime"
            />
            <KsDatePicker
                :data-testid="`input-form-${input.id}`"
                v-if="input.type === 'DATE'"
                v-model="inputsValues[input.id]"
                @update:model-value="onChange(input)"
                type="date"
            />
            <KsTimePicker
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
                        <KsTag
                            v-for="(item, index) in parseArrayValue(input.id)"
                            :key="index"
                        >
                            {{ item }}
                        </KsTag>
                    </div>
                    <KsButton
                        class="p-3"
                        @click="toggleArrayEdit(input.id)"
                        :icon="Pencil"
                    >
                        {{ $t('edit') }}
                    </KsButton>
                </div>

                <div v-else class="edit_input">
                    <div>
                        <div v-for="(_item, index) in editableItems[input.id]" :key="index" class="list-row">
                            <KsInput
                                v-model="editableItems[input.id][index]"
                                class="array-cell"
                            />
                            <KsButton @click="removeArrayItem(input, index)" :icon="DeleteOutline" class="delete-input" :tooltip="$t('remove this item')" />
                            <div class="d-flex flex-column controls-input">
                                <ChevronUp @click="moveArrayItem(input, 'up', index)" />
                                <ChevronDown @click="moveArrayItem(input, 'down', index)" />
                            </div>
                        </div>
                    </div>
                    <KsButton
                        class="add-new mt-1 border-0"
                        @click="addNewArrayItem(input)"
                        :icon="Plus"
                    >
                        {{ $t('add_new_item') }}
                    </KsButton>
                    <div class="d-flex justify-content-end mt-2">
                        <KsButton
                            @click="toggleArrayEdit(input.id)"
                            type="primary"
                            :icon="ContentSave"
                        >
                            {{ $t('save') }}
                        </KsButton>
                    </div>
                </div>
            </div>
            <KsEditor
                v-bind="editorBindings"
                :options="{fullHeight: false, showScroll: inputsValues[input.id]?.length > 530}"
                :inline="true"
                :navbar="false"
                v-if="input.type === 'JSON'"
                :data-testid="`input-form-${input.id}`"
                lang="json"
                v-model="inputsValues[input.id]"
            />
            <KsEditor
                v-bind="editorBindings"
                :options="{fullHeight: false}"
                :inline="true"
                :navbar="false"
                v-if="input.type === 'YAML'"
                :data-testid="`input-form-${input.id}`"
                lang="yaml"
                :modelValue="inputsValues[input.id]"
                @change="onYamlChange(input, $event)"
            />
            <KsDurationPicker
                v-if="input.type === 'DURATION'"
                v-model="inputsValues[input.id]"
                @update:model-value="onChange(input)"
            />
            <KsMarkdown v-if="input.description" :data-testid="`input-form-${input.id}`" class="markdown-tooltip text-description" :content="input.description" />
        </KsFormItem>
        <div class="d-flex justify-content-end">
            <ValidationError v-if="inputErrors" :errors="inputErrors" />
        </div>
    </template>

    <KsAlert type="info" :closable="false" class="mb-3" v-else>
        {{ $t("no inputs") }}
    </KsAlert>
</template>

<script setup lang="ts">
    import moment from "moment-timezone"
    import {KsMessage, KsEditor} from "@kestra-io/design-system"
    import type {FormItemRule} from "@kestra-io/design-system"
    import ValidationError from "../flows/ValidationError.vue"
    import {ref, reactive, computed, watch, onMounted, onBeforeUnmount, toRaw, markRaw, type Component, getCurrentInstance} from "vue"
    import {Execution, useExecutionsStore} from "../../stores/executions"
    import {useI18n} from "vue-i18n"
    import debounce from "lodash/debounce"
    import {useEditorBindings} from "../../composables/useEditorBindings"
    import {normalize, type InputType} from "../../utils/inputs"
    import {inputsToFormData} from "../../utils/submitTask"
    import DeleteOutlineIcon from "vue-material-design-icons/DeleteOutline.vue"
    import PencilIcon from "vue-material-design-icons/Pencil.vue"
    import PlusIcon from "vue-material-design-icons/Plus.vue"
    import ContentSaveIcon from "vue-material-design-icons/ContentSave.vue"
    import ChevronUp from "vue-material-design-icons/ChevronUp.vue"
    import ChevronDown from "vue-material-design-icons/ChevronDown.vue"
    import {Flow} from "../../stores/flow"

    interface InputError {
        message: string;
    }

    type ValueOptionLike = string | {label: string; value: string};

    interface InputMetaData {
        id: string;
        type: InputType
        displayName?: string;
        description?: string;
        required?: boolean;
        defaults?: unknown;
        value?: unknown;
        values?: ValueOptionLike[];
        options?: ValueOptionLike[];
        errors?: InputError[];
        isDefault?: boolean;
        isRadio?: boolean;
        allowCustomValue?: boolean;
        min?: number;
        max?: number;
        allowedFileExtensions?: string[];
        accept?: string;
        prefill?: unknown;
    }

    function toOption(item: ValueOptionLike): {label: string; value: string} {
        return typeof item === "string" ? {label: item, value: item} : item
    }

    interface SelectedTrigger {
        inputs?: Record<string, unknown>;
    }

    interface ValidationResponse {
        checks?: unknown[];
        inputs: Array<{
            enabled: boolean;
            input: InputMetaData;
            errors?: InputError[];
            value?: unknown;
            isDefault?: boolean;
        }>;
    }

    interface ValidationEventPayload {
        formData: FormData | undefined;
        inputsMetaData: InputMetaData[];
        callback: (response: ValidationResponse) => void;
    }

    // Props
    const props = withDefaults(defineProps<{
        executeClicked?: boolean;
        modelValue?: Record<string, unknown>;
        initialInputs?: InputMetaData[];
        flow?: Flow;
        execution?: Execution;
        selectedTrigger?: SelectedTrigger;
    }>(), {
        executeClicked: false,
        modelValue: () => ({}),
        initialInputs: () => [],
        flow: undefined,
        execution: undefined,
        selectedTrigger: undefined,
    })

    // Emits
    const emit = defineEmits<{
        "update:modelValue": [value: Record<string, unknown>];
        "update:modelValueNoDefault": [value: Record<string, unknown>];
        "update:checks": [checks: unknown[]];
        "confirm": [];
        "validation": [payload: ValidationEventPayload];
    }>()

    // Stores and composables
    const executionsStore = useExecutionsStore()
    const {t} = useI18n()
    const instance = getCurrentInstance()
    const editorBindings = useEditorBindings()

    // Reactive state
    // Using 'any' type for v-model compatibility with various Element Plus components
    const inputsValues = reactive<Record<string, any>>({...props.modelValue})
    const previousInputsValues = ref<Record<string, any>>({})
    const inputsMetaData = ref<InputMetaData[]>([])
    const multiSelectInputs = reactive<Record<string, any>>({})
    const inputsValidated = ref<Set<string>>(new Set())
    const editingArrayId = ref<string | null>(null)
    const editableItems = reactive<Record<string, string[]>>({})

    // Icons exposed to template (markRaw to avoid reactivity overhead)
    const DeleteOutline = markRaw(DeleteOutlineIcon) as Component
    const Pencil = markRaw(PencilIcon) as Component
    const Plus = markRaw(PlusIcon) as Component
    const ContentSave = markRaw(ContentSaveIcon) as Component

    // Computed
    const inputErrors = computed<string[] | null>(() => {
        // we only keep errors that don't target an input directly
        const keepErrors = inputsMetaData.value.filter(it => it.id === undefined)
        const errorsExist = keepErrors.filter(it => it.errors && it.errors.length > 0).length > 0

        return errorsExist
            ? keepErrors
                .filter(it => it.errors && it.errors.length > 0)
                .flatMap(it => it.errors?.flatMap(err => err.message) ?? [])
            : null
    })

    // Methods
    function normalizeJSON(value: string): unknown {
        try {
            // Step 1: Remove trailing commas in objects and arrays
            let cleaned = value.replace(/,\s*([}\]])/g, "$1")

            // Step 2: Quote unquoted keys (simple case: keys with letters, numbers, or _)
            cleaned = cleaned.replace(/([{,]\s*)([a-zA-Z0-9_]+)\s*:/g, "$1\"$2\":")

            // Step 3: Parse into JS object
            return JSON.parse(cleaned)
        } catch (e) {
            console.error("Failed to normalize JSON:", (e as Error).message)
            return null
        }
    }

    function inputError(id: string): string | undefined {
        // if this input has not been edited yet
        // showing any error is annoying
        if (!inputsValidated.value.has(id)) {
            return undefined
        }

        const errors = inputsMetaData.value
            .filter((it) => it.id === id && it.errors && it.errors.length > 0)
            .map(it => it.errors!.map(err => err.message).join("\n"))

        return errors.length > 0 ? errors[0] : undefined
    }

    function updateDefaults(): void {
        for (const input of inputsMetaData.value) {
            const {type, id, value, defaults} = input
            const valueOrDefault = value ?? defaults
            if (inputsValues[id] === undefined || inputsValues[id] === null || input.isDefault) {
                if (type === "MULTISELECT") {
                    multiSelectInputs[id] = valueOrDefault
                } else if (type === "JSON" && value == undefined && input.isDefault) {
                    /*
                    * Handle multiline JSON default values
                    * See https://github.com/kestra-io/kestra/issues/11449
                    */
                    inputsValues[id] = normalize(type as InputType, normalizeJSON(input.defaults as string))
                } else {
                    inputsValues[id] = normalize(type as InputType, valueOrDefault)
                }
            }
        }
    }

    function onChange(input: InputMetaData): void {
        // give 2 seconds for the user to finish their edit
        // and for the server to return with validated content
        setTimeout(() => {
            inputsValidated.value.add(input.id)
        }, 2000)
        input.isDefault = false
        emit("update:modelValue", {...inputsValues})
        emit("update:modelValueNoDefault", inputsValuesWithNoDefault())
    }

    function onSubmit(): void {
        emit("confirm")
    }

    function onMultiSelectChange(input: InputMetaData, e: unknown[]): void {
        inputsValues[input.id] = JSON.stringify(e)
        onChange(input)
    }

    function onFileChange(input: InputMetaData, e: Event): void {
        const target = e.target as HTMLInputElement | null
        if (!target) {
            return
        }

        const files = target.files

        if (!files?.length) {
            return
        }

        const file = files[0]

        // Sanitize the filename: remove spaces and special characters
        const sanitizedName = file.name
            .replace(/[^a-zA-Z0-9.-]/g, "_") // Replace special chars with underscore
            .replace(/\s+/g, "_")           // Replace spaces with underscore

        // Create a new File object with the sanitized name
        const sanitizedFile = new File([file], sanitizedName, {
            type: file.type,
            lastModified: file.lastModified,
        })

        const acceptedTypes = getAcceptedFileTypes(input)
        if (acceptedTypes) {
            const allowedTypes = acceptedTypes.toLowerCase().split(",")
            const fileName = sanitizedName.toLowerCase()
            const fileType = file.type.toLowerCase()

            const isAllowed = allowedTypes.some(type => {
                type = type.trim()
                if (type.startsWith(".")) {
                    return fileName.endsWith(type)
                } else {
                    return fileType === type
                }
            })

            if (!isAllowed) {
                KsMessage.error(t("fileTypeNotAllowed", {types: acceptedTypes}))
                target.value = ""
                return
            }
        }

        inputsValues[input.id] = sanitizedFile
        setTimeout(() => onChange(input), 300)
    }

    function onYamlChange(input: InputMetaData, e: Event): void {
        const target = e.target as HTMLInputElement
        inputsValues[input.id] = target.value
        onChange(input)
    }

    function inputsValuesWithNoDefault(): Record<string, unknown> {
        return inputsMetaData.value.reduce((acc: Record<string, unknown>, input) => {
            acc[input.id] = input.isDefault ? undefined : inputsValues[input.id]
            return acc
        }, {})
    }

    function numberHint(input: InputMetaData): string | false {
        const {min, max} = input

        if (min !== undefined && max !== undefined) {
            if (min > max) return `Minimum value ${min} is larger than maximum value ${max}, so we've removed the upper limit.`
            return `Minimum value is ${min}, maximum value is ${max}.`
        } else if (min !== undefined) {
            return `Minimum value is ${min}.`
        } else if (max !== undefined) {
            return `Maximum value is ${max}.`
        }
        return false
    }

    async function validateInputs(): Promise<void> {
        if (inputsMetaData.value === undefined || inputsMetaData.value.length === 0) {
            return
        }

        const inputsValuesNoDefault = inputsValuesWithNoDefault()

        const formData = inputsToFormData({$moment: moment}, inputsMetaData.value, inputsValuesNoDefault)

        const metadataCallback = (response: ValidationResponse): void => {
            emit("update:checks", response.checks || [])
            inputsMetaData.value = response.inputs.reduce((acc: InputMetaData[], it) => {
                if (it.enabled) {
                    acc.push({
                        ...it.input,
                        errors: it.errors,
                        value: it.value || it.input.prefill,
                        isDefault: it.isDefault,
                    })
                }
                return acc
            }, [])
            updateDefaults()
        }

        if (props.flow !== undefined) {
            const options = {namespace: props.flow.namespace, id: props.flow.id}
            const {data} = await executionsStore.validateExecution({...options, formData})

            metadataCallback(data)
        } else if (props.execution !== undefined) {
            const options = {id: props.execution.id}
            const {data} = await executionsStore.validateResume({...options, formData})

            metadataCallback(data)
        } else {
            emit("validation", {
                formData: formData,
                inputsMetaData: inputsMetaData.value,
                callback: (response: ValidationResponse) => {
                    metadataCallback(response)
                },
            })
        }
    }

    function requiredRules(input: InputMetaData): FormItemRule[] | undefined {
        if (input.required === false) {
            return undefined
        }

        if (input.type === "BOOLEAN") {
            return [{
                validator: (_rule, val: unknown, callback: (error?: Error) => void) => {
                    if (val === "undefined") {
                        return callback(new Error(t("is required", {field: input.displayName || input.id})))
                    }
                    callback()
                },
            }]
        }

        if (["ENUM", "SELECT", "MULTISELECT"].includes(input.type)) {
            return [{
                required: true,
                validator: (_rule, _val: unknown, callback: (error?: Error) => void) => {
                    const val = input.type === "MULTISELECT"
                        ? multiSelectInputs[input.id] as unknown[] | undefined
                        : inputsValues[input.id] as unknown[] | string | undefined
                    if (!val || (Array.isArray(val) ? val.length === 0 : !val)) {
                        return callback(new Error(t("is required", {field: input.displayName || input.id})))
                    }
                    callback()
                },
                trigger: "change",
            }]
        }

        return undefined
    }

    function parseArrayValue(inputId: string): unknown[] {
        const value = inputsValues[inputId]
        if (!value) return []

        if (typeof value === "string") {
            try {
                return JSON.parse(value)
            } catch {
                return []
            }
        }
        return []
    }

    function addNewArrayItem(input: InputMetaData): void {
        if (!editableItems[input.id]) {
            editableItems[input.id] = parseArrayValue(input.id).map(item =>
                item?.toString() || "",
            )
        }
        editableItems[input.id].push("")
    }

    function updateArrayValue(input: InputMetaData): void {
        const validItems = editableItems[input.id]
            .filter(item => item && item.trim() !== "")
            .map(item => item.trim())

        inputsValues[input.id] = JSON.stringify(validItems)
        onChange(input)
    }

    function removeArrayItem(input: InputMetaData, index: number): void {
        editableItems[input.id].splice(index, 1)
        updateArrayValue(input)
    }

    function toggleArrayEdit(inputId: string): void {
        const isEditing = editingArrayId.value === inputId
        if (isEditing && editableItems[inputId]) {
            const input = inputsMetaData.value.find(i => i.id === inputId)
            if (input) {
                updateArrayValue(input)
            }
        }
        editingArrayId.value = isEditing ? null : inputId
        if (!isEditing) {
            editableItems[inputId] = parseArrayValue(inputId).map(v => v?.toString() || "")
        }
    }

    function moveArrayItem(input: InputMetaData, direction: "up" | "down", index: number): void {
        const {id} = input
        const items = editableItems[id]
        const isValidMove = direction === "up" ? index > 0 : index < items.length - 1
        if (!isValidMove) return
        const targetIndex = direction === "up" ? index - 1 : index + 1;
        [items[index], items[targetIndex]] = [items[targetIndex], items[index]]

        updateArrayValue(input)
    }

    function getFilePlaceholder(value: unknown): string {
        if (typeof value === "string" && value.startsWith("nsfile://")) {
            return t("defaultsToNamespaceFile", {name: value.substring(10)})
        }
        if (value && typeof value === "object" && "name" in value && typeof (value as {name: unknown}).name === "string") {
            return (value as {name: string}).name
        }
        return t("no_file_choosen")
    }

    function getAcceptedFileTypes(input: Pick<InputMetaData, "allowedFileExtensions" | "accept">): string {
        if (input.allowedFileExtensions && input.allowedFileExtensions.length > 0) {
            return input.allowedFileExtensions.join(",")
        }
        return input.accept || ""
    }

    // Debounced validation
    const debouncedValidation = debounce(validateInputs, 500)

    // Keyboard event listener
    let keyListener: ((e: KeyboardEvent) => void) | null = null

    // Initialization
    inputsMetaData.value = JSON.parse(JSON.stringify(props.initialInputs))

    if (props.selectedTrigger?.inputs) {
        Object.assign(inputsValues, toRaw(props.selectedTrigger.inputs))
    }

    // Run initial validation and setup watcher
    validateInputs().then(() => {
        watch(
            () => ({...inputsValues}),
            (val) => {
                // only revalidate if values have changed
                if (JSON.stringify(val) !== JSON.stringify(previousInputsValues.value)) {
                    // only revalidate if values are stable for more than 500ms
                    // to avoid too many calls to the server
                    debouncedValidation()
                    emit("update:modelValue", {...inputsValues})
                    emit("update:modelValueNoDefault", inputsValuesWithNoDefault())
                }
                previousInputsValues.value = JSON.parse(JSON.stringify(val))
            },
            {deep: true},
        )

        // on first load default values need to be sent to the parent
        // since they are part of the actual value
        emit("update:modelValue", {...inputsValues})
    })

    // Lifecycle hooks
    onMounted(() => {
        setTimeout(() => {
            const el = instance?.proxy?.$el as HTMLElement | undefined
            const input = el?.querySelector?.("input")
            if (input && !input.className.includes("mx-input")) {
                input.focus()
            }
        }, 500)

        keyListener = (e: KeyboardEvent) => {
            // Ctrl/Control + Enter
            if (e.key === "Enter" && (e.ctrlKey || e.metaKey)) {
                e.preventDefault()
                onSubmit()
            }
        }

        document.addEventListener("keydown", keyListener)
    })

    onBeforeUnmount(() => {
        if (keyListener) {
            document.removeEventListener("keydown", keyListener)
        }
    })

    // Watchers
    watch(() => props.flow, () => {
        validateInputs()
    })

    watch(() => props.execution, () => {
        validateInputs()
    })

    // Expose to template (for icons and methods used in template)
    defineExpose({
        validateInputs,
        inputsValues,
        inputsMetaData,
    })
</script>

<style scoped lang="scss">
.md-label {
    height: var(--ks-font-size-lg);
}

.hint {
    font-size: var(--ks-font-size-xs);
    color: var(--ks-text-secondary);
}

.text-description {
    width: 100%;
    font-size: var(--ks-font-size-xs);
    color: var(--ks-text-secondary);
}

:deep(.boolean-inputs) {
    display: flex;
    align-items: center;

    .kel-radio-button {
        &.is-active {
            .kel-radio-button__original-radio:not(:disabled) + .kel-radio-button__inner {
                color: var(--ks-text-primary);
                background-color: var(--ks-btn-secondary-bg-active);
                box-shadow: 0 0 0 0 var(--ks-border-focus);
            }
        }

        .kel-radio-button__inner {
            border: var(--ks-border-default);
            transition: 0.3s ease-in-out;

            &:hover {
                color: var(--ks-text-secondary);
                border-color: var(--ks-border-focus);
                background-color: var(--ks-bg-surface);
            }

            &:first-child {
                border-left: var(--ks-border-default);
            }
        }
    }
}

.kel-input-file {
    display: flex;
    align-items: center;

    .kel-input__inner {
        cursor: pointer;
    }

    .kel-input__wrapper {
        padding: 0.5rem;
    }

}

.preview {
    display: flex;
    align-items: center;
    gap: 10px;

    .tags {
        flex: 1;
        background: var(--ks-bg-input);
        border: 1px solid var(--ks-border-default);
        border-radius: 4px;
        display: flex;
        flex-wrap: wrap;
        align-items: center;
        padding: 5px;
        gap: 4px;

        :deep(.kel-tag) {
            display: inline-flex;
            align-items: center;
            border-radius: 4px;
            background-color: var(--ks-bg-tag);
            color: var(--ks-text-primary);
        }
    }
}

.edit_input {
    .list-row {
        position: relative;
        margin-bottom: 8px;

        .array-cell {
            :deep(.kel-input__wrapper) {
                box-shadow: none;
                border: 1px solid var(--ks-border-default);
                border-radius: 5px;
            }

            :deep(.kel-input__inner) {
                color: #eeae7e !important;
                font-size: var(--ks-font-size-sm) !important;

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
            color: var(--ks-text-secondary);
            background: transparent;

            &:hover {
                color: var(--ks-status-error);
            }
        }

        .controls-input {
            position: absolute;
            right: 2px;
            top: 50%;
            transform: translateY(-50%);
            padding: 3px;
            border-left: 1px solid var(--ks-border-default);
            color: var(--ks-text-secondary);
            background: transparent;
        }
    }

    .add-new {
        padding: 5px 8px;
        color: var(--ks-text-dim);
        font-size: var(--ks-font-size-sm);
        background: none;

        &:hover {
            color: var(--ks-text-secondary);
        }
    }
}

.kel-form-item {
    &:has(.edit_input) {
        padding: 1rem;
        border-radius: 8px;
        border: 1px solid var(--ks-border-default);
        background-color: var(--ks-bg-active);
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

.kel-input-file {
  .kel-input__wrapper {
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
    color: var(--ks-text-secondary) !important;
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
