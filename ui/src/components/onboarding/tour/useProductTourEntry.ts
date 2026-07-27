import {computed, onMounted, ref, watch} from "vue"
import {useRoute} from "vue-router"

import {shouldShowWelcome} from "../../../utils/welcomeGuard"
import {useProductTourStore} from "../../../stores/productTour"

/** Where every entry point sends the user: the welcome page, with the tour starting on arrival. */
function useTourRoute() {
    const route = useRoute()

    return computed(() => ({
        name: "ai",
        query: {tour: "start"},
        params: {tenant: route.params.tenant},
    }))
}

/**
 * The left menu entry: while the instance still has nothing of its own, and after a skip.
 *
 * An instance with flows is no longer new, and the entry would then be in the way of the menu it
 * sits in. Whoever skipped the tour keeps it either way, since leaving it says that it stays here.
 */
export function useProductTourMenuEntry() {
    const tourStore = useProductTourStore()

    /** No flow outside the tutorial namespace yet, so the instance is still untouched. */
    const isNewInstance = ref(false)

    const wasSkipped = computed(() => tourStore.state.status === "skipped")

    const visible = computed(
        () => (isNewInstance.value || wasSkipped.value)
            && !tourStore.isDismissed
            && !tourStore.isGuidedActive,
    )

    const refresh = async () => {
        try {
            isNewInstance.value = await shouldShowWelcome()
        } catch {
            isNewInstance.value = false
        }
    }

    onMounted(refresh)
    // Re-check when the tour ends, so the offer goes away as soon as the user has a flow.
    watch(() => tourStore.state.status, () => void refresh())

    return {
        visible,
        tourRoute: useTourRoute(),
        dismiss: () => tourStore.dismissMenuEntry(),
    }
}

/**
 * The nudge on the Blueprints page: shown until it is closed there.
 *
 * Blueprints are read by people who have not built anything yet, whatever the age of the instance,
 * so this one is not tied to the menu entry. It only steps aside while the tour is running, where
 * the guide card is already on screen.
 */
export function useProductTourNudge() {
    const tourStore = useProductTourStore()

    const visible = computed(
        () => !tourStore.state.tour.blueprintsNudgeDismissed
            && !tourStore.isGuidedActive,
    )

    return {
        visible,
        tourRoute: useTourRoute(),
        dismiss: () => tourStore.dismissBlueprintsNudge(),
    }
}
