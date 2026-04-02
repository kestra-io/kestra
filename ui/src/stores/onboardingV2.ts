import {computed, ref, watch} from "vue";
import {defineStore} from "pinia";

export type OnboardingStatus = "not_started" | "in_progress" | "paused" | "completed" | "skipped";
export type OnboardingMode = "guided" | "self_serve" | null;
export type OnboardingEditorMode = "normal" | "code_only";

interface OnboardingV2State {
    status: OnboardingStatus;
    mode: OnboardingMode;
    guideId: "first_flow" | null;
    currentStepId: string | null;
    editorMode: OnboardingEditorMode;
    startedAt: string | null;
    completedAt: string | null;
    pausedAt: string | null;
    saveCount: number;
    executionCount: number;
}

const STORAGE_KEY = "onboarding.v2.state";
const LEGACY_STORAGE_KEY = "tourDoneOrSkip";

const FIRST_FLOW_START_STEP = "flow_basics";

const defaultState = (): OnboardingV2State => ({
    status: "not_started",
    mode: null,
    guideId: null,
    currentStepId: null,
    editorMode: "normal",
    startedAt: null,
    completedAt: null,
    pausedAt: null,
    saveCount: 0,
    executionCount: 0,
});

export const useOnboardingV2Store = defineStore("onboardingV2", () => {
    const state = ref<OnboardingV2State>(defaultState());

    const isGuidedActive = computed(
        () =>
            state.value.mode === "guided" &&
            ["in_progress", "paused"].includes(state.value.status),
    );

    const isInProgress = computed(() => state.value.status === "in_progress");

    const load = () => {
        const persisted = localStorage.getItem(STORAGE_KEY);
        if (persisted) {
            try {
                state.value = {...defaultState(), ...JSON.parse(persisted)};
                return;
            } catch {
                state.value = defaultState();
            }
        }

        // one-time legacy migration
        const legacy = localStorage.getItem(LEGACY_STORAGE_KEY);
        if (legacy === "true") {
            state.value = {
                ...defaultState(),
                status: "completed",
                mode: "guided",
                guideId: "first_flow",
                completedAt: new Date().toISOString(),
            };
            localStorage.removeItem(LEGACY_STORAGE_KEY);
        }
    };

    const persist = () => {
        localStorage.setItem(STORAGE_KEY, JSON.stringify(state.value));
    };

    const reset = () => {
        state.value = defaultState();
    };

    const startGuided = () => {
        state.value = {
            ...defaultState(),
            status: "in_progress",
            mode: "guided",
            guideId: "first_flow",
            currentStepId: FIRST_FLOW_START_STEP,
            editorMode: "code_only",
            startedAt: new Date().toISOString(),
        };
    };

    const startSelfServe = () => {
        state.value = {
            ...defaultState(),
            status: "completed",
            mode: "self_serve",
            completedAt: new Date().toISOString(),
        };
    };

    const pause = () => {
        if (state.value.status !== "in_progress") {
            return;
        }
        state.value.status = "paused";
        state.value.pausedAt = new Date().toISOString();
    };

    const resume = () => {
        if (state.value.status !== "paused") {
            return;
        }
        state.value.status = "in_progress";
        state.value.pausedAt = null;
    };

    const skip = () => {
        state.value.status = "skipped";
        state.value.editorMode = "normal";
        state.value.completedAt = new Date().toISOString();
    };

    const complete = () => {
        state.value.status = "completed";
        state.value.editorMode = "normal";
        state.value.completedAt = new Date().toISOString();
    };

    const setStep = (stepId: string) => {
        state.value.currentStepId = stepId;
    };

    const setEditorMode = (editorMode: OnboardingEditorMode) => {
        state.value.editorMode = editorMode;
    };

    const recordSave = () => {
        if (!isGuidedActive.value) {
            return;
        }
        state.value.saveCount += 1;
    };

    const recordExecution = () => {
        if (!isGuidedActive.value) {
            return;
        }
        state.value.executionCount += 1;
    };

    load();
    watch(state, persist, {deep: true});

    return {
        state,
        isGuidedActive,
        isInProgress,
        reset,
        startGuided,
        startSelfServe,
        pause,
        resume,
        skip,
        complete,
        setStep,
        setEditorMode,
        recordSave,
        recordExecution,
    };
});
