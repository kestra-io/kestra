import {useRoute} from "vue-router"
import {useApiStore} from "../stores/api"
import {pageFromRoute} from "../utils/eventsRouter"
import {TOUR_SCENE_IDS} from "../components/onboarding/tour/tourScenes"

/** Bumped from 2.0.0 with the new tour, so its events are distinguishable from the old guide's. */
export const TOUR_ANALYTICS_VERSION = "3.0.0"
export const TOUR_ANALYTICS_EXPERIENCE = "product_tour"

export type OnboardingTourEvent =
    | "tour_offered"
    | "tour_started"
    | "tour_continued"
    | "tour_completed"
    | "tour_closed";

interface TrackOnboardingOptions {
    event: OnboardingTourEvent;
    action?: string | null;
    mode?: "guided" | null;
    additional?: Record<string, unknown>;
}

export function useOnboardingAnalytics() {
    const apiStore = useApiStore()
    const route = useRoute()

    const trackOnboarding = ({
        event,
        action,
        mode,
        additional = {},
    }: TrackOnboardingOptions) => {
        const step = action && TOUR_SCENE_IDS.includes(action)
            ? TOUR_SCENE_IDS.indexOf(action) + 1
            : undefined

        apiStore.events({
            type: "ONBOARDING",
            onboarding: {
                version: TOUR_ANALYTICS_VERSION,
                experience: TOUR_ANALYTICS_EXPERIENCE,
                template: TOUR_ANALYTICS_EXPERIENCE,
                guideId: "product_tour",
                event,
                action,
                step,
                mode,
                ...additional,
            },
            page: pageFromRoute(route),
        })
    }

    return {
        trackOnboarding,
    }
}
