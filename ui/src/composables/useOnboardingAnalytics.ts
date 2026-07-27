import {useRoute} from "vue-router"
import {useApiStore} from "../stores/api"
import {pageFromRoute} from "../utils/eventsRouter"
import {TOUR_SCENE_IDS} from "../components/onboarding/tour/tourScenes"

/** Bumped from 2.0.0 with the new tour, so its events are distinguishable from the old guide's. */
export const TOUR_ANALYTICS_VERSION = "3.0.0"
export const TOUR_ANALYTICS_EXPERIENCE = "product_tour"

/**
 * Events the product tour reports, resolved to `app.onboarding-tour.*` in PostHog.
 *
 * `offered` fires when the invitation appears and `started` when it is accepted, so the start rate
 * is one over the other. `continued` fires on every step the user moves through, so the funnel can be
 * read step by step from the `action` property. `completed` covers the early exit offered on a
 * milestone as well as the last step.
 */
export type OnboardingTourEvent =
    | "tour_offered"
    | "tour_started"
    | "tour_continued"
    | "tour_completed"
    | "tour_closed";

interface TrackOnboardingOptions {
    /** Names the event. */
    event: OnboardingTourEvent;
    /** The step it happened on, reported as the `action` property. */
    action?: string | null;
    mode?: "guided" | "self_serve" | null;
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
                // The step name, per the tracking plan: one event name, broken down by step.
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
