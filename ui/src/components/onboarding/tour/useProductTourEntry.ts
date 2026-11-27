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

function useEligibility() {
    const variant = useTourVariant()
    const eligible = ref(false)
    let latestCheck = 0

    // Re-checks can overlap (add then remove), so only the latest one's answer counts.
    const refresh = async () => {
        const check = ++latestCheck
        let answer: boolean
        try {
            answer = await variant.eligible()
        } catch {
            answer = false
        }
        if (check === latestCheck) eligible.value = answer
    }

    onMounted(refresh)
    if (variant.entryGate) watch(variant.entryGate.recheckOn, () => void refresh())

    return {eligible, refresh}
}

/** Whether the variant's in-app tour entries may show: always without an `entryGate`, else only while `eligible()` holds. */
export function useProductTourEntryGate() {
    if (!useTourVariant().entryGate) return {open: computed(() => true)}

    const {eligible} = useEligibility()
    return {open: computed(() => eligible.value)}
}

export function useProductTourMenuEntry() {
    const tourStore = useProductTourStore()
    const variant = useTourVariant()

    const {eligible: isNewInstance, refresh} = useEligibility()

    const wasSkipped = computed(() => tourStore.state.status === "skipped")

    const visible = computed(
        () => (isNewInstance.value || (wasSkipped.value && !variant.entryGate))
            && !tourStore.isDismissed
            && !tourStore.isGuidedActive,
    )

    watch(() => tourStore.state.status, () => void refresh())

    return {
        visible,
        menuKey: computed(() => (wasSkipped.value ? "menu_resume" : "menu")),
        tourRoute: useTourRoute(),
        translationKey: useTourKey(),
        dismiss: () => tourStore.dismissMenuEntry(),
    }
}

export function useProductTourNudge() {
    const tourStore = useProductTourStore()
    const gate = useProductTourEntryGate()

    const visible = computed(
        () => gate.open.value
            && !tourStore.state.tour.blueprintsNudgeDismissed
            && !tourStore.isGuidedActive,
    )

    return {
        visible,
        tourRoute: useTourRoute(),
        translationKey: useTourKey(),
        dismiss: () => tourStore.dismissBlueprintsNudge(),
    }
}
