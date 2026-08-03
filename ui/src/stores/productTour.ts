import {computed, ref, watch} from "vue"
import {defineStore} from "pinia"

import {TOUR_FLOW_ID, TOUR_NAMESPACE, TOUR_REPORT_FLOW_ID} from "../components/onboarding/tour/tourFlows"

export type ProductTourStatus = "not_started" | "in_progress" | "completed" | "skipped";
export type ProductTourMode = "guided" | null;

export interface TourProgress {
    namespace: string;
    flowId: string;
    reportFlowId: string;
    introSeen: boolean;
    menuDismissed: boolean;
    blueprintsNudgeDismissed: boolean;
    webhookKey: string | null;
    eventWatchSince: string | null;
    reportFlowCreated: boolean;
    editorPanelsBackup: string | null;
    editorPanelsRemembered: boolean;
    lastExecutionId: string | null;
    failedExecutionId: string | null;
    restartedExecutionId: string | null;
    eventExecutionId: string | null;
}

interface ProductTourState {
    status: ProductTourStatus;
    mode: ProductTourMode;
    instanceUuid: string | null;
    guideId: "product_tour" | null;
    currentStepId: string | null;
    startedAt: string | null;
    completedAt: string | null;
    tour: TourProgress;
}

const STORAGE_KEY = "kestra.productTour.state"

const TOUR_START_SCENE = "copilot"

const defaultTourState = (): TourProgress => ({
    namespace: TOUR_NAMESPACE,
    flowId: TOUR_FLOW_ID,
    reportFlowId: TOUR_REPORT_FLOW_ID,
    introSeen: false,
    menuDismissed: false,
    blueprintsNudgeDismissed: false,
    webhookKey: null,
    eventWatchSince: null,
    reportFlowCreated: false,
    editorPanelsBackup: null,
    editorPanelsRemembered: false,
    lastExecutionId: null,
    failedExecutionId: null,
    restartedExecutionId: null,
    eventExecutionId: null,
})

const defaultState = (): ProductTourState => ({
    status: "not_started",
    mode: null,
    instanceUuid: null,
    guideId: null,
    currentStepId: null,
    startedAt: null,
    completedAt: null,
    tour: defaultTourState(),
})

export const useProductTourStore = defineStore("productTour", () => {
    const state = ref<ProductTourState>(defaultState())

    const isGuidedActive = computed(
        () => state.value.mode === "guided" && state.value.status === "in_progress",
    )

    const isDismissed = computed(
        () => state.value.status === "completed" || state.value.tour.menuDismissed,
    )

    const dismissMenuEntry = () => {
        state.value.tour = {...state.value.tour, menuDismissed: true}
    }

    const dismissBlueprintsNudge = () => {
        state.value.tour = {...state.value.tour, blueprintsNudgeDismissed: true}
    }

    const syncInstance = (uuid?: string | null) => {
        if (!uuid) {
            return
        }
        if (state.value.instanceUuid && state.value.instanceUuid !== uuid) {
            state.value = defaultState()
        }
        state.value.instanceUuid = uuid
    }

    const load = () => {
        const persisted = localStorage.getItem(STORAGE_KEY)
        if (!persisted) {
            return
        }
        try {
            const parsed = JSON.parse(persisted)
            state.value = {
                ...defaultState(),
                ...parsed,
                tour: {...defaultTourState(), ...(parsed?.tour ?? {})},
            }
        } catch {
            state.value = defaultState()
        }
    }

    const persist = () => {
        localStorage.setItem(STORAGE_KEY, JSON.stringify(state.value))
    }

    const reset = () => {
        state.value = defaultState()
    }

    const startGuided = () => {
        const {menuDismissed, blueprintsNudgeDismissed} = state.value.tour
        state.value = {
            ...defaultState(),
            instanceUuid: state.value.instanceUuid,
            status: "in_progress",
            mode: "guided",
            guideId: "product_tour",
            currentStepId: TOUR_START_SCENE,
            startedAt: new Date().toISOString(),
            tour: {...defaultTourState(), menuDismissed, blueprintsNudgeDismissed},
        }
    }

    const skip = () => {
        state.value.status = "skipped"
        state.value.completedAt = new Date().toISOString()
    }

    const complete = () => {
        state.value.status = "completed"
        state.value.completedAt = new Date().toISOString()
    }

    const setStep = (stepId: string) => {
        state.value.currentStepId = stepId
    }

    const setTourState = (patch: Partial<TourProgress>) => {
        state.value.tour = {...state.value.tour, ...patch}
    }

    load()
    watch(state, persist, {deep: true})

    return {
        state,
        isGuidedActive,
        isDismissed,
        reset,
        startGuided,
        skip,
        complete,
        setStep,
        setTourState,
        syncInstance,
        dismissMenuEntry,
        dismissBlueprintsNudge,
    }
})
