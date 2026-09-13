import {defineStore} from "pinia"
import {computed, ref} from "vue"

export type FeatureSpotlight = {
    navItemId: string;
};

export const FEATURE_SPOTLIGHTS: FeatureSpotlight[] = [
    {navItemId: "mcp-servers"},
]

const SEEN_STORAGE_KEY = "featureSpotlightsSeen"

function loadSeenIds(): string[] {
    try {
        const stored = JSON.parse(localStorage.getItem(SEEN_STORAGE_KEY) ?? "[]")
        return Array.isArray(stored) ? stored.filter((id) => typeof id === "string") : []
    } catch {
        return []
    }
}

export const useFeatureSpotlightStore = defineStore("featureSpotlight", () => {
    const seenIds = ref<string[]>(loadSeenIds())

    const unseenSpotlights = computed((): FeatureSpotlight[] => FEATURE_SPOTLIGHTS.filter((spotlight) => !seenIds.value.includes(spotlight.navItemId)))
    const hasUnseenForId = computed(() => (navItemId?: string): boolean =>
        Boolean(navItemId && unseenSpotlights.value.some((spotlight) => spotlight.navItemId === navItemId)))

    function markSeenById(navItemId?: string) {
        if (!navItemId || seenIds.value.includes(navItemId)) return
        if (!FEATURE_SPOTLIGHTS.some((spotlight) => spotlight.navItemId === navItemId)) return

        seenIds.value = [...seenIds.value, navItemId]
        localStorage.setItem(SEEN_STORAGE_KEY, JSON.stringify(seenIds.value))
    }

    return {
        seenIds,
        unseenSpotlights,
        hasUnseenForId,
        markSeenById,
    }
})
