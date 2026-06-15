import {defineStore} from "pinia"

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

interface State {
    seenIds: string[];
}

export const useFeatureSpotlightStore = defineStore("featureSpotlight", {
    state: (): State => ({
        seenIds: loadSeenIds(),
    }),
    getters: {
        unseenSpotlights(state): FeatureSpotlight[] {
            return FEATURE_SPOTLIGHTS.filter((spotlight) => !state.seenIds.includes(spotlight.navItemId))
        },
        hasUnseenForId(): (navItemId?: string) => boolean {
            return (navItemId) =>
                Boolean(navItemId && this.unseenSpotlights.some((spotlight) => spotlight.navItemId === navItemId))
        },
    },
    actions: {
        markSeenById(navItemId?: string) {
            if (!navItemId || this.seenIds.includes(navItemId)) return
            if (!FEATURE_SPOTLIGHTS.some((spotlight) => spotlight.navItemId === navItemId)) return

            this.seenIds = [...this.seenIds, navItemId]
            localStorage.setItem(SEEN_STORAGE_KEY, JSON.stringify(this.seenIds))
        },
    },
})
