import {computed, onMounted, ref, watch} from "vue"
import {useRoute} from "vue-router"

import {useProductTourStore} from "../../../stores/productTour"
import {useTourVariant} from "override/components/onboarding/tour/useTourVariant"

function useTourRoute() {
    const route = useRoute()
    const variant = useTourVariant()

    return computed(() => variant.entryRoute(route.params.tenant as string | undefined))
}

function useTourKey() {
    const variant = useTourVariant()

    return (suffix: string) => `${variant.i18nPrefix}.${suffix}`
}

export function useProductTourMenuEntry() {
    const tourStore = useProductTourStore()
    const variant = useTourVariant()

    const isNewInstance = ref(false)

    const wasSkipped = computed(() => tourStore.state.status === "skipped")

    const visible = computed(
        () => (isNewInstance.value || wasSkipped.value)
            && !tourStore.isDismissed
            && !tourStore.isGuidedActive,
    )

    const refresh = async () => {
        try {
            isNewInstance.value = await variant.eligible()
        } catch {
            isNewInstance.value = false
        }
    }

    onMounted(refresh)
    watch(() => tourStore.state.status, () => void refresh())

    return {
        visible,
        tourRoute: useTourRoute(),
        tk: useTourKey(),
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
        tk: useTourKey(),
        dismiss: () => tourStore.dismissBlueprintsNudge(),
    }
}
