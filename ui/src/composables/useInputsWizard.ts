import {ref, computed, watch, onBeforeUnmount, type Ref} from "vue"
import {useI18n} from "vue-i18n"
import {buildWizardSteps, formChildName, executeFormValuesStorageKey, type WizardStep} from "../utils/inputs"
import type {InputMetaData} from "../stores/executions"
import type {Flow} from "../stores/flow"

/**
 * Shared form state the wizard reads/mutates. Passed in (not owned) because InputsForm.vue keeps the
 * canonical reactive state — the wizard only adds step navigation, recap and per-flow value persistence
 * on top of it. Pass the reactive objects/refs by reference so reactivity is preserved.
 */
interface UseInputsWizardDeps {
    props: {
        initialInputs?: InputMetaData[];
        flow?: Flow;
        mode?: "flat" | "wizard";
        formGroups?: Record<string, {displayName?: string; description?: string}>;
    };
    inputsMetaData: Ref<InputMetaData[]>;
    inputsValues: Record<string, any>;
    multiSelectInputs: Record<string, any>;
    inputsValidated: Ref<Set<string>>;
    validateInputs: () => Promise<void>;
    onRecapChange: (value: boolean) => void;
}

/**
 * FORM wizard for InputsForm.vue. A flow whose inputs contain at least one FORM is rendered as a
 * multi-step Next/Back wizard (one step per top-level FORM titled by its displayName, one step per
 * contiguous run of ungrouped top-level inputs, then a recap) shared by the Flow Execute modal and EE
 * Apps; otherwise it degrades to flat mode (single step, no nav) — which is why {@link visibleInputs},
 * {@link inputLabel} and {@link formIds} are provided here even though they also drive the flat form.
 */
export function useInputsWizard(deps: UseInputsWizardDeps) {
    const {props, inputsMetaData, inputsValues, multiSelectInputs, inputsValidated, validateInputs, onRecapChange} = deps
    const {t} = useI18n()

    // One step per top-level FORM (titled by its displayName); a contiguous run of ungrouped
    // top-level inputs forms its own step (document order preserved); a final recap step.
    const hasForms = computed(() => (props.initialInputs ?? []).some(i => i.type === "FORM"))
    const isWizard = computed(() => props.mode === "wizard" && hasForms.value)
    // Per-flow localStorage key: persists in-progress values across Back/Next and page reload,
    // cleared on unmount (dialog discard or execution creation) so a fresh open starts blank.
    const formValuesStorageKey = computed(() => isWizard.value ? executeFormValuesStorageKey(props.flow) : undefined)

    const steps = computed<WizardStep[]>(() => isWizard.value ? buildWizardSteps(props.initialInputs as any) : [])

    const currentStep = ref(0)
    const current = computed<WizardStep | undefined>(() => steps.value[currentStep.value])
    const recapIndex = computed(() => steps.value.length - 1)
    const returnToRecap = ref(false)
    const navLoading = ref(false)
    // Only swap the Next label to the loading message once the validate round-trip is slow
    // enough to notice (>500ms). A fast validate shows just the brief spinner — no jarring label flash.
    const showComputingLabel = ref(false)

    const isOnRecap = computed(() => isWizard.value && current.value?.kind === "recap")

    const visibleInputs = computed<InputMetaData[]>(() => {
        if (!isWizard.value) return inputsMetaData.value
        const step = current.value
        if (!step || step.kind === "recap") return []
        return (step.leafIds ?? [])
            .map(id => inputsMetaData.value.find(m => m.id === id))
            .filter((m): m is InputMetaData => m !== undefined)
    })

    // Owning FORM ids: from the FORM tree (wizard) and/or formGroups keys (EE Apps reconstruction).
    // Used to strip the form prefix off a child's dotted id when it has no explicit displayName.
    const formIds = computed<string[]>(() => {
        const fromTree = (props.initialInputs ?? []).filter(i => i.type === "FORM").map(i => i.id)
        return props.formGroups ? [...fromTree, ...Object.keys(props.formGroups)] : fromTree
    })

    // Label for a leaf: its displayName, else the bare child name (form prefix stripped) so a
    // FORM child without a displayName shows `a` rather than the dotted `form.a`.
    function inputLabel(input: InputMetaData): string {
        return input.displayName || formChildName(input.id, formIds.value)
    }

    // Recap: every non-recap step paired with its index (for the Edit button to jump back).
    const recapSections = computed(() =>
        steps.value
            .map((step, index) => ({step, index}))
            .filter(({step}) => step.kind !== "recap")
            .map(({step, index}) => ({
                index,
                title: step.title || t("inputs"),
                fields: (step.leafIds ?? [])
                    .map(id => inputsMetaData.value.find(m => m.id === id))
                    .filter((m): m is InputMetaData => m !== undefined),
            })),
    )

    function recapDisplayValue(input: InputMetaData): string {
        if (input.type === "SECRET") return "••••••••"
        const raw = input.type === "MULTISELECT" ? multiSelectInputs[input.id] : inputsValues[input.id]
        if (raw === undefined || raw === null || raw === "") return "—"
        if (raw instanceof File) return raw.name
        if (typeof raw === "object") return JSON.stringify(raw)
        return String(raw)
    }

    function stepIsValid(step: WizardStep | undefined): boolean {
        if (!step || step.kind === "recap") return true
        return (step.leafIds ?? []).every(id => {
            const meta = inputsMetaData.value.find(m => m.id === id)
            if (!meta) return true // not in the validate response -> disabled via dependsOn, skip
            if (meta.errors && meta.errors.length > 0) return false
            if (meta.required === false) return true
            const val = meta.type === "MULTISELECT" ? multiSelectInputs[id] : inputsValues[id]
            return !(val === undefined || val === null || val === "" || (Array.isArray(val) && val.length === 0))
        })
    }

    async function goNext(): Promise<void> {
        const step = current.value
        if (!step || step.kind === "recap") return
        navLoading.value = true
        // Defer the label swap so a fast round-trip never flashes it (see showComputingLabel).
        const labelTimer = setTimeout(() => { showComputingLabel.value = true }, 500)
        try {
            await validateInputs()
        } finally {
            // always clear the loading state, even if the validate round-trip throws, so the
            // Next button never gets stuck spinning on the loading message.
            clearTimeout(labelTimer)
            showComputingLabel.value = false
            navLoading.value = false
        }
        // reveal any per-field errors for this step now (bypass the 2s onChange delay)
        ;(step.leafIds ?? []).forEach(id => inputsValidated.value.add(id))
        if (!stepIsValid(step)) return
        if (returnToRecap.value) {
            currentStep.value = recapIndex.value
            returnToRecap.value = false
        } else {
            currentStep.value++
        }
    }

    function goBack(): void {
        if (currentStep.value > 0) {
            currentStep.value--
            returnToRecap.value = false
        }
    }

    function editStep(index: number): void {
        returnToRecap.value = true
        currentStep.value = index
    }

    watch(isOnRecap, (val) => onRecapChange(val), {immediate: true})

    // ---- per-flow value persistence (no-op outside the wizard) ----
    // Restore in-progress values (e.g. after a page reload) before the first validate.
    function restorePersistedValues(): void {
        if (!isWizard.value || !formValuesStorageKey.value) return
        try {
            const stored = localStorage.getItem(formValuesStorageKey.value)
            if (stored) {
                Object.assign(inputsValues, JSON.parse(stored))
            }
        } catch { /* ignore corrupt storage */ }
    }

    function persistValues(): void {
        if (!isWizard.value || !formValuesStorageKey.value) return
        localStorage.setItem(formValuesStorageKey.value, JSON.stringify({...inputsValues}))
    }

    onBeforeUnmount(() => {
        // Clear persisted wizard values on dialog discard / after execution creation.
        // A hard page reload does not run this hook, so reload-then-restore still works.
        if (isWizard.value && formValuesStorageKey.value) {
            localStorage.removeItem(formValuesStorageKey.value)
        }
    })

    return {
        isWizard,
        formValuesStorageKey,
        steps,
        currentStep,
        current,
        recapIndex,
        returnToRecap,
        navLoading,
        showComputingLabel,
        isOnRecap,
        visibleInputs,
        formIds,
        inputLabel,
        recapSections,
        recapDisplayValue,
        stepIsValid,
        goNext,
        goBack,
        editStep,
        restorePersistedValues,
        persistValues,
    }
}
