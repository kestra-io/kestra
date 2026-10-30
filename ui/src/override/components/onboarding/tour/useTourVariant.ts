import {DEFAULT_TOUR_VARIANT, type TourVariant} from "../../../../components/onboarding/tour/tourVariant"

// Which guided tour this distribution serves; EE replaces this file to pick one per tenant focus.
export function useTourVariant(): TourVariant {
    return DEFAULT_TOUR_VARIANT
}
