import {computed, onMounted, ref, watch} from "vue"
import {useRoute} from "vue-router"

import {shouldShowWelcome} from "../../../utils/welcomeGuard"
import {useProductTourStore} from "../../../stores/productTour"

function useTourRoute() {
    const route = useRoute()

    return computed(() => ({
        name: "ai",
        query: {tour: "start"},
        params: {tenant: route.params.tenant},
    }))
}

export function useProductTourMenuEntry() {
    const tourStore = useProductTourStore()

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
    watch(() => tourStore.state.status, () => void refresh())

    return {
        visible,
        tourRoute: useTourRoute(),
        dismiss: () => tourStore.dismissMenuEntry(),
    }
}

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
