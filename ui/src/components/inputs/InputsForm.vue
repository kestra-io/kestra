<template>
    <template v-if="initialInputs">
        <!-- Active FORM header: its displayName (only when set), with the optional description beneath.
             A FORM without a displayName, or an ungrouped inputs run, shows no header. The step
             progress now lives in the bottom bar (see .wizard-progress below). -->
        <div v-if="isWizard && current?.kind === 'form' && (current.displayName || current.description)" class="wizard-step-header">
            <h5 v-if="current.displayName" class="wizard-step-title">{{ current.displayName }}</h5>
            <KsMarkdown v-if="current.description" :content="current.description" class="text-description" />
        </div>

        <template v-for="input in visibleInputs" :key="input.id">
            <KsFormItem
                :required="input.required !== false"
                :rules="requiredRules(input)"
                :prop="input.id.includes('.') ? [input.id] : input.id"
                :error="inputError(input.id)"
                :inlineMessage="true"
            >
                <template #label>
                    <KsMarkdown :content="inputLabel(input)" class="d-inline-flex md-label" />
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
                    :disabled="isComputingInput(input.id)"
                    :placeholder="isComputingInput(input.id) ? t('loading') : undefined"
                    :loading="isLoadingInput(input.id)"
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
                    :disabled="isComputingInput(input.id)"
                    :placeholder="isComputingInput(input.id) ? t('loading') : undefined"
                    :loading="isLoadingInput(input.id)"
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
                    @update:model-value="onChangeBool(input)"
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
                <div class="kel-input kel-input-file" v-if="input.type === 'FILE'">
                    <div class="kel-input__wrapper">
                        <input
                            :data-testid="`input-form-${input.id}`"
                            :id="input.id+'-file'"
                            class="kel-input__inner custom-file-input"
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
        </template>

        <div v-if="isOnRecap" class="wizard-recap" data-testid="inputs-wizard-recap">
            <h5 class="wizard-step-title">{{ $t('review your inputs') }}</h5>
            <div v-for="section in recapSections" :key="section.index" class="wizard-recap-section">
                <div class="wizard-recap-section-header">
                    <span class="wizard-recap-section-title">{{ section.title }}</span>
                    <KsButton :icon="Pencil" @click="editStep(section.index)" :data-testid="`recap-edit-${section.index}`">
                        {{ $t('edit') }}
                    </KsButton>
                </div>
                <div v-for="field in section.fields" :key="field.id" class="wizard-recap-field">
                    <span class="wizard-recap-field-label">{{ inputLabel(field) }}</span>
                    <span class="wizard-recap-field-value">{{ recapDisplayValue(field) }}</span>
                </div>
            </div>
        </div>

        <div class="d-flex justify-content-end">
            <ValidationError v-if="inputErrors" :errors="inputErrors" />
        </div>

        <div v-if="isWizard" class="wizard-nav">
            <KsButton v-if="currentStep > 0" :icon="ChevronLeft" @click="goBack" data-testid="wizard-back">
                {{ $t('back') }}
            </KsButton>
            <span class="wizard-nav-spacer" />
            <KsButton
                v-if="current?.kind !== 'recap'"
                type="primary"
                :icon="returnToRecap ? Check : ChevronRight"
                :loading="navLoading"
                @click="goNext"
                data-testid="wizard-next"
            >
                {{ showComputingLabel ? $t('loading') : $t(returnToRecap ? 'done' : 'next') }}
            </KsButton>
        </div>

        <!-- Label-less progress bar pinned to the bottom of the pane; hidden on the recap, returns on Edit.
             One segment per fillable step; fills only once the step is passed via Next (stepStatus). -->
        <div v-if="isWizard && !isOnRecap" class="wizard-progress">
            <KsSteps variant="bar" :active="currentStep" data-testid="wizard-progress">
                <KsStep
                    v-for="section in recapSections"
                    :key="section.index"
                    :title="section.title"
                    :status="stepStatus(section.index)"
                />
            </KsSteps>
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
    import {ref, reactive, computed, watch, onMounted, onBeforeUnmount, toRaw, markRaw, type Component, getCurrentInstance, nextTick} from "vue"
    import {type Check, Execution, useExecutionsStore, ValidationEventPayload, ValidationResponse, ValueOptionLike} from "../../stores/executions"
    import {useI18n} from "vue-i18n"
    import debounce from "lodash/debounce"
    import {useEditorBindings} from "../../composables/useEditorBindings"
    import {useInputsWizard} from "../../composables/useInputsWizard"
    import {normalize, flattenInputs, type InputType} from "../../utils/inputs"
    import {inputsToFormData} from "../../utils/submitTask"
    import DeleteOutlineIcon from "vue-material-design-icons/DeleteOutline.vue"
    import PencilIcon from "vue-material-design-icons/Pencil.vue"
    import PlusIcon from "vue-material-design-icons/Plus.vue"
    import ContentSaveIcon from "vue-material-design-icons/ContentSave.vue"
    import ChevronUp from "vue-material-design-icons/ChevronUp.vue"
    import ChevronDown from "vue-material-design-icons/ChevronDown.vue"
    import ChevronLeftIcon from "vue-material-design-icons/ChevronLeft.vue"
    import ChevronRightIcon from "vue-material-design-icons/ChevronRight.vue"
    import CheckIcon from "vue-material-design-icons/Check.vue"
    import {Flow} from "../../stores/flow"
    import {InputMetaData} from "../../stores/executions"

    function toOption(item: ValueOptionLike): {label: string; value: string} {
        return typeof item === "string" ? {label: item, value: item} : item
    }

    interface SelectedTrigger {
        inputs?: Record<string, unknown>;
    }

    const modelValue = defineModel<Record<string, unknown>>()

    // Props
    const props = withDefaults(defineProps<{
        executeClicked?: boolean;
        initialInputs?: InputMetaData[];
        flow?: Flow;
        execution?: Execution;
        selectedTrigger?: SelectedTrigger;
        mode?: "flat" | "wizard";
        formGroups?: Record<string, {displayName?: string; description?: string}>;
    }>(), {
        executeClicked: false,
        initialInputs: () => [],
        flow: undefined,
        execution: undefined,
        selectedTrigger: undefined,
        mode: "flat",
        formGroups: undefined,
    })

    // Emits
    const emit = defineEmits<{
        "update:modelValueNoDefault": [value: Record<string, unknown>];
        "update:checks": [checks: Check[]];
        "confirm": [];
        "validation": [payload: ValidationEventPayload];
        "update:onRecap": [value: boolean];
        "ready": [];
    }>()

    // Stores and composables
    const executionsStore = useExecutionsStore()
    const {t} = useI18n()
    const instance = getCurrentInstance()
    const editorBindings = useEditorBindings()

    // Reactive state
    // Using 'any' type for v-model compatibility with various Element Plus components
    const inputsValues = reactive<Record<string, any>>({...modelValue.value})
    const previousInputsValues = ref<Record<string, any>>({})
    const inputsMetaData = ref<InputMetaData[]>([])
    const multiSelectInputs = reactive<Record<string, any>>({})
    const inputsValidated = ref<Set<string>>(new Set())
    const editingArrayId = ref<string | null>(null)
    const editableItems = reactive<Record<string, string[]>>({})
    // true while an input-rendering call (which may run a subflow() function) is in flight
    const isComputingValues = ref(false)
    // bumped on every user input change; a validate response built before the latest change is stale
    // and must be discarded, otherwise it would reset a value the user just picked (e.g. while a slow
    // subflow() render is still in flight)
    let inputGeneration = 0

    // Icons exposed to template (markRaw to avoid reactivity overhead)
    const DeleteOutline = markRaw(DeleteOutlineIcon) as Component
    const Pencil = markRaw(PencilIcon) as Component
    const Plus = markRaw(PlusIcon) as Component
    const ContentSave = markRaw(ContentSaveIcon) as Component
    const ChevronLeft = markRaw(ChevronLeftIcon) as Component
    const ChevronRight = markRaw(ChevronRightIcon) as Component
    const Check = markRaw(CheckIcon) as Component

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

    // ---- FORM wizard ----
    // A flow whose inputs contain a FORM renders as a multi-step Next/Back wizard; otherwise this
    // degrades to flat mode. The composable owns the nav/recap/persistence as well as
    // visibleInputs/inputLabel (which also drive the flat form). Destructured with identical names so
    // the template needs no changes; the refs/computeds keep their reactivity through the destructure.
    const {
        isWizard,
        current,
        currentStep,
        returnToRecap,
        navLoading,
        showComputingLabel,
        isOnRecap,
        stepStatus,
        visibleInputs,
        inputLabel,
        recapSections,
        recapDisplayValue,
        goNext,
        goBack,
        editStep,
        restorePersistedValues,
        persistValues,
    } = useInputsWizard({
        props,
        inputsMetaData,
        inputsValues,
        multiSelectInputs,
        inputsValidated,
        validateInputs,
        onRecapChange: (val) => emit("update:onRecap", val),
    })

    // Inputs whose `values` are rendered dynamically (e.g. via the subflow() function).
    // Derived from the raw flow inputs because the validate response strips `expression`.
    // FORM groups are expanded to dotted leaves so a dynamic input nested in a FORM (wizard mode)
    // is matched by its dotted id (e.g. `setup.region`), same as inputsMetaData/template ids.
    const dynamicInputIds = computed(() =>
        new Set(flattenInputs(props.initialInputs ?? []).filter(it => it.expression || it.dependsOn).map(it => it.id)),
    )

    // True while a dynamic input's values are being (re)computed. Drives the loading spinner so the
    // user knows the available values may change — on the initial fetch AND on later recomputations.
    function isLoadingInput(id: string): boolean {
        return isComputingValues.value && dynamicInputIds.value.has(id)
    }

    // True while a dynamic input's values are being (re)computed and it still has no value. Drives the
    // disabled state + "computing" placeholder, on the initial fetch and on any later recomputation
    // (e.g. a dependsOn change). Once a value is present the input stays usable and keeps it (spinner
    // only), so a user's pick is never disrupted.
    function isComputingInput(id: string): boolean {
        if (!isLoadingInput(id)) {
            return false
        }
        const value = inputsValues[id] ?? multiSelectInputs[id]
        return value === undefined || value === null || value === ""
            || (Array.isArray(value) && value.length === 0)
    }

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
        // While a dynamic input's values are being (re)computed its metadata is stale — an error from
        // an earlier validate (e.g. "Missing required" computed when it was still empty) would otherwise
        // flash even though the field is now filled, until the in-flight validate's clean response lands.
        // Suppress it until the recompute settles.
        if (isLoadingInput(id)) {
            return undefined
        }
        const meta = inputsMetaData.value.find((it) => it.id === id && it.errors && it.errors.length > 0)
        if (!meta) {
            return undefined
        }
        const message = meta.errors!.map(err => err.message).join("\n")

        // A render/resolution failure (a SELECT whose expression/subflow() can't resolve, or an input
        // whose `defaults` Pebble expression throws) means the field itself is broken — the backend flags
        // these with `renderError`. Surface them as soon as the input is shown (initial load / wizard step
        // arrival), without waiting for an edit or a Next click. Plain value errors (e.g. a required input
        // left empty) are not flagged and stay gated until interaction.
        const isRenderError = meta.errors!.some(err => err.renderError)

        // if this input has not been edited yet showing a value error is annoying
        if (!isRenderError && !inputsValidated.value.has(id)) {
            return undefined
        }

        return message
    }

    function updateDefaults(): void {
        for (const input of inputsMetaData.value) {
            const {type, id, value, defaults} = input
            // An unrendered Pebble-expression default must be rendered server-side, not pre-filled as a
            // raw template string. Until there's a concrete rendered `value`, leave the field empty: a
            // successful render returns a value (filled on the next validate); a failed render then
            // surfaces its renderError instead of being masked by re-submitting the raw `{{ ... }}`.
            if (value == null && typeof defaults === "string" && defaults.includes("{{")) {
                continue
            }
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

    function onChangeBool(input: InputMetaData): void {
        onChange(input)
    }

    function onChange(input: InputMetaData): void {
        // mark inputs as changed so any in-flight (older) validate response is discarded as stale
        inputGeneration++
        // give 2 seconds for the user to finish their edit
        // and for the server to return with validated content
        setTimeout(() => {
            inputsValidated.value.add(input.id)
        }, 2000)
        input.isDefault = false
        modelValue.value = {...inputsValues}
        emit("update:modelValueNoDefault", {...inputsValuesWithNoDefault.value})
    }

    function onSubmit(): void {
        // In the wizard, Enter / Ctrl+Enter advances steps until the recap, then confirms.
        if (isWizard.value && current.value && current.value.kind !== "recap") {
            goNext()
            return
        }
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

    const inputsValuesWithNoDefault = computed<Record<string, unknown>>(() => {
        return inputsMetaData.value.reduce((acc: Record<string, unknown>, input) => {
            acc[input.id] = input.isDefault ? undefined : inputsValues[input.id]
            return acc
        }, {})
    })

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

    // Signature of the last completed validate payload, and the one currently in flight. Used to skip
    // redundant round-trips: clicking Next without editing anything would otherwise re-validate the
    // identical form data on every step.
    let lastValidatedSignature: string | undefined
    let pendingValidation: {signature: string, promise: Promise<void>} | undefined

    // Stable signature of a validate payload. Sorted so FormData iteration order can't change it;
    // files are keyed by name/size/lastModified since their contents aren't cheaply hashable.
    function formDataSignature(formData: FormData): string {
        const parts: string[] = []
        for (const [key, value] of formData.entries()) {
            parts.push(value instanceof File
                ? `${key}=file:${value.name}:${value.size}:${value.lastModified}`
                : `${key}=${value}`)
        }
        return parts.sort().join(" ")
    }

    async function validateInputs(): Promise<void> {
        if (inputsMetaData.value === undefined || inputsMetaData.value.length === 0) {
            return
        }

        const formData = inputsToFormData({$moment: moment}, inputsMetaData.value, inputsValuesWithNoDefault.value)

        // inputsToFormData returns undefined when no value is set; treat that as a stable empty
        // signature so an all-defaults form still dedups (and we never deref undefined).
        const signature = formData ? formDataSignature(formData) : ""

        // Nothing changed since the last validate — the current metadata already reflects this exact
        // payload, so the round-trip would be redundant. Mirrors the change-watcher's same-values skip.
        if (signature === lastValidatedSignature) {
            return
        }
        // An identical validate is already in flight — await it instead of firing a second one.
        if (pendingValidation?.signature === signature) {
            return pendingValidation.promise
        }

        // generation this request was built at; if the user changes an input before the response
        // lands, the response is stale and applying it would clobber the user's new value
        const requestGeneration = inputGeneration

        const metadataCallback = async (response: ValidationResponse): Promise<void> => {
            if (requestGeneration !== inputGeneration) {
                return
            }
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
            await nextTick() // wait for the DOM to update validations before updating defaults
            // NOTE: validations happen mostly using an object updated in the parent form.
            updateDefaults()
        }

        const run = async (): Promise<void> => {
            if (props.flow !== undefined) {
                const options = {namespace: props.flow.namespace, id: props.flow.id}
                const {data} = await executionsStore.validateExecution({...options, formData})

                metadataCallback(data)
            } else if (props.execution !== undefined) {
                const options = {id: props.execution.id}
                const {data} = await executionsStore.validateResume({...options, formData})

                metadataCallback(data)
            } else {
                // Apps-only branch: the validate round-trip is owned by the parent (BlockForm). Await it
                // so the wizard's per-step gating reads fresh metadata — the parent MUST invoke the
                // callback on every path or this never resolves and goNext hangs (see BlockForm.validation).
                await new Promise<void>((resolve) => {
                    emit("validation", {
                        formData: formData,
                        inputsMetaData: inputsMetaData.value,
                        callback: (response: ValidationResponse) => {
                            metadataCallback(response)
                            resolve()
                        },
                    })
                })
            }
        }

        // Dynamic inputs (e.g. values rendered via the subflow() function) are disabled and show a
        // "computing" placeholder while this render call is in flight — regardless of its duration.
        isComputingValues.value = true

        const promise = run()
        pendingValidation = {signature, promise}
        let validated = false
        try {
            await promise
            // record only after success, so a thrown validate retries on the next call
            lastValidatedSignature = signature
            validated = true
        } finally {
            isComputingValues.value = false
            if (pendingValidation?.signature === signature) {
                pendingValidation = undefined
            }
        }

        // A change made while this validate was in flight leaves the response stale. Re-validate so a
        // change during ANY in-flight round-trip is never swallowed — including changes made during the
        // initial validate, before the change-watcher below is attached. This matters now that a
        // subflow()-backed input can make a single validate take seconds. Bounded: it stops as soon as
        // the payload signature stops changing (inputsValuesWithNoDefault excludes defaults, so a
        // settled form re-computes the same signature and this no-ops).
        if (validated) {
            const latest = inputsToFormData({$moment: moment}, inputsMetaData.value, inputsValuesWithNoDefault.value)
            const latestSignature = latest ? formDataSignature(latest) : ""
            if (latestSignature !== lastValidatedSignature) {
                return validateInputs()
            }
        }
    }

    function requiredRules(input: InputMetaData): FormItemRule[] | undefined {
        if (input.required === false) {
            return undefined
        }

        if (["BOOLEAN", "BOOL"].includes(input.type)) {
            return [{
                validator: (_rule, val: unknown, callback: (error?: Error) => void) => {
                    if (typeof val === "undefined") {
                        return callback(new Error(t("is required", {field: inputLabel(input)})))
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
                        return callback(new Error(t("is required", {field: inputLabel(input)})))
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
    inputsMetaData.value = JSON.parse(JSON.stringify(flattenInputs(props.initialInputs)))

    if (props.selectedTrigger?.inputs) {
        Object.assign(inputsValues, toRaw(props.selectedTrigger.inputs))
    }

    // Wizard: restore in-progress values (e.g. after a page reload) before the first validate.
    restorePersistedValues()

    // Apply defaults from the raw inputs immediately so static inputs show their default value
    // without waiting for the initial validate call (which may be slow, e.g. a subflow() render).
    // Mark not-yet-provided inputs as default first so they stay excluded from the validate request,
    // matching the post-validate path (inputsValuesWithNoDefault keys off isDefault).
    inputsMetaData.value.forEach((input) => {
        if (inputsValues[input.id] === undefined) {
            input.isDefault = true
        }
    })
    updateDefaults()

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
                    modelValue.value = {...inputsValues}
                    emit("update:modelValueNoDefault", inputsValuesWithNoDefault.value)
                    persistValues()
                }
                previousInputsValues.value = JSON.parse(JSON.stringify(val))
            },
            {deep: true},
        )

        // on first load default values need to be sent to the parent
        // since they are part of the actual value
        modelValue.value = {...inputsValues}

        emit("ready")
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

    // A different flow/execution invalidates the dedup cache: the same InputsForm instance can be
    // reused for another flow (no :key remount), so force a fresh validate even when the new payload
    // signature collides with the previous one (e.g. both empty).
    function invalidateValidationCache(): void {
        lastValidatedSignature = undefined
        pendingValidation = undefined
    }

    // Watchers
    watch(() => props.flow, () => {
        invalidateValidationCache()
        validateInputs()
    })

    watch(() => props.execution, () => {
        invalidateValidationCache()
        validateInputs()
    })

    // Expose to template (for icons and methods used in template)
    defineExpose({
        validateInputs,
        inputsValues,
        inputsMetaData,
        inputsValidated,
        isComputingValues,
        isComputingInput,
        isLoadingInput,
        inputError,
        onChange,
    })
</script>

<style scoped lang="scss">
.md-label {
    height: var(--ks-font-size-lg);
}

.wizard-progress {
    position: sticky;
    bottom: 0;
    margin-top: 1rem;
    padding: 0.75rem 0 0.25rem;
    background: var(--ks-bg-surface);
    border-top: 1px solid var(--ks-border-default);
}

.wizard-step-header {
    margin-bottom: 1rem;
}

.wizard-step-title {
    font-size: var(--ks-font-size-lg);
    font-weight: 600;
    margin: 0 0 0.25rem;
}

.wizard-recap {
    display: flex;
    flex-direction: column;
    gap: 0.75rem;
}

.wizard-recap-section {
    border: 1px solid var(--ks-border-default);
    border-radius: 8px;
    padding: 0.75rem 1rem;
    background: var(--ks-bg-tag);

    .wizard-recap-section-header {
        display: flex;
        align-items: center;
        justify-content: space-between;
        margin-bottom: 0.5rem;

        .wizard-recap-section-title {
            font-weight: 600;
        }
    }

    .wizard-recap-field {
        display: flex;
        justify-content: space-between;
        gap: 1rem;
        padding: 0.2rem 0;
        font-size: var(--ks-font-size-sm);

        .wizard-recap-field-label {
            color: var(--ks-text-secondary);
        }

        .wizard-recap-field-value {
            text-align: right;
            word-break: break-word;
        }
    }
}

.wizard-nav {
    display: flex;
    align-items: center;
    margin-top: 1rem;

    .wizard-nav-spacer {
        flex: 1;
    }
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
