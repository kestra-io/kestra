import {computed, ref, watch} from "vue"
import {defineStore} from "pinia"

import {TOUR_FLOW_ID, TOUR_NAMESPACE, TOUR_REPORT_FLOW_ID} from "../components/onboarding/tour/tourFlows"

export type ProductTourStatus = "not_started" | "in_progress" | "completed" | "skipped";
export type ProductTourMode = "guided" | null;

/**
 * What the tour created on the instance so far. Scenes read it to navigate to the right flow or
 * execution, and it survives a page reload in the middle of the tour.
 */
export interface TourProgress {
    namespace: string;
    flowId: string;
    reportFlowId: string;
    /** The intro card is only shown once per tour run. */
    introSeen: boolean;
    /** Set when the left menu entry is hidden by hand, without taking the tour. */
    menuDismissed: boolean;
    /** Set when the nudge on the Blueprints page is closed. Kept apart from the menu entry: the
     * nudge stays until it is closed there, whatever the menu entry does. */
    blueprintsNudgeDismissed: boolean;
    webhookKey: string | null;
    /** When the tour started watching for an execution created by the webhook trigger. */
    eventWatchSince: string | null;
    reportFlowCreated: boolean;
    /**
     * The editor's panel layout as it was before the tour opened the Docs panel in it.
     *
     * `null` means there was no stored layout, so restoring means removing the entry again. Kept
     * apart from `editorPanelsRemembered`, which says whether there is anything to restore at all.
     */
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
    /** Instance the rest of this state belongs to, from `/configs`. */
    instanceUuid: string | null;
    guideId: "product_tour" | null;
    currentStepId: string | null;
    startedAt: string | null;
    completedAt: string | null;
    tour: TourProgress;
}

// Its own key, not the one the previous guided onboarding wrote: that state describes a different
// tour, and reading it here would leave people who finished the old one without this one.
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

    /** The tour is running: the guide card is on screen and driving the app. */
    const isGuidedActive = computed(
        () => state.value.mode === "guided" && state.value.status === "in_progress",
    )

    /**
     * True once the left menu entry should be gone: the tour was completed, or the entry was hidden
     * by hand. Skipping alone only means "not now", so the entry stays.
     */
    const isDismissed = computed(
        () => state.value.status === "completed" || state.value.tour.menuDismissed,
    )

    const dismissMenuEntry = () => {
        state.value.tour = {...state.value.tour, menuDismissed: true}
    }

    const dismissBlueprintsNudge = () => {
        state.value.tour = {...state.value.tour, blueprintsNudgeDismissed: true}
    }

    /**
     * Forget everything that belongs to another instance.
     *
     * This state lives in the browser, so a fresh instance on the same address would otherwise
     * inherit the previous one's flow and execution ids, and whoever skipped the tour there would
     * never be offered it again here.
     */
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
        // Both dismissals are preferences rather than progress, so a new run keeps them.
        const {menuDismissed, blueprintsNudgeDismissed} = state.value.tour
        state.value = {
            ...defaultState(),
            instanceUuid: state.value.instanceUuid,
            status: "in_progress",
            mode: "guided",
            guideId: "product_tour",
            currentStepId: TOUR_START_SCENE,
            // The tour runs against the regular editor, so the editor keeps all of its panels.
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
