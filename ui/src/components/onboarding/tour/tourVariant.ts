import type {Component, WatchSource} from "vue"
import type {RouteLocationRaw} from "vue-router"

import TourFinale from "./TourFinale.vue"
import {TOUR_SCENES, type TourScene} from "./tourScenes"
import {useTourActions} from "./useTourActions"
import {shouldShowWelcome} from "../../../utils/welcomeGuard"

/** Values of the `?tour=` query: `start` always starts the tour, `auto` only if it never ran in this browser. */
export enum TourQueryMode {
    START = "start",
    AUTO = "auto",
}

/**
 * One complete guided experience. `TourOverlay` renders whichever variant
 * `override/components/onboarding/tour/useTourVariant` resolves, so a distribution can serve a
 * different tour without forking the overlay.
 */
export interface TourVariant<A = unknown> {
    id: string;
    /** Scene copy is read from `<i18nPrefix>.scenes.<sceneId>.*`. */
    i18nPrefix: string;
    scenes: TourScene<A>[];
    finale: Component;
    entryRoute: (tenant?: string) => RouteLocationRaw;
    autoStartRoute: string;
    eligible: () => Promise<boolean>;
    /** When set, every in-app tour entry shows only while `eligible()` holds, even for a skipped tour, and is re-checked whenever `recheckOn` changes. */
    entryGate?: {recheckOn: WatchSource};
    useActions: () => A;
    // Method syntax keeps `A` bivariant, so any `TourVariant<X>` still widens to the bare `TourVariant`.
    cleanup?(actions: A): void;
}

export const DEFAULT_TOUR_VARIANT: TourVariant<ReturnType<typeof useTourActions>> = {
    id: "product_tour",
    i18nPrefix: "onboarding.tour",
    scenes: TOUR_SCENES,
    finale: TourFinale,
    entryRoute: (tenant) => ({name: "ai", query: {tour: TourQueryMode.START}, params: {tenant}}),
    autoStartRoute: "ai",
    eligible: shouldShowWelcome,
    useActions: useTourActions,
    cleanup: (actions) => actions.restoreEditorPanels(),
}
