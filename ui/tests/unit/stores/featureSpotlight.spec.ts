import {beforeEach, describe, expect, it, vi} from "vitest"
import {createPinia, setActivePinia} from "pinia"

const SEEN_STORAGE_KEY = "featureSpotlightsSeen"

async function setupStore(spotlights: {navItemId: string}[]) {
    vi.resetModules()
    const module = await import("../../../src/stores/featureSpotlight")
    module.FEATURE_SPOTLIGHTS.splice(0, module.FEATURE_SPOTLIGHTS.length, ...spotlights)
    return module.useFeatureSpotlightStore()
}

describe("featureSpotlight store", () => {
    beforeEach(() => {
        localStorage.clear()
        setActivePinia(createPinia())
    })

    it("flags a nav item with an unseen spotlight", async () => {
        const store = await setupStore([{navItemId: "mcp-servers"}])

        expect(store.hasUnseenForId("mcp-servers")).toBe(true)
        expect(store.hasUnseenForId("flows")).toBe(false)
        expect(store.hasUnseenForId(undefined)).toBe(false)
    })

    it("clears the flag and persists once the item is seen", async () => {
        const store = await setupStore([{navItemId: "mcp-servers"}])

        store.markSeenById("mcp-servers")

        expect(store.hasUnseenForId("mcp-servers")).toBe(false)
        expect(JSON.parse(localStorage.getItem(SEEN_STORAGE_KEY) ?? "[]")).toEqual(["mcp-servers"])
    })

    it("is idempotent when an item is marked seen twice", async () => {
        const store = await setupStore([{navItemId: "mcp-servers"}])

        store.markSeenById("mcp-servers")
        store.markSeenById("mcp-servers")

        expect(store.seenIds).toEqual(["mcp-servers"])
        expect(JSON.parse(localStorage.getItem(SEEN_STORAGE_KEY) ?? "[]")).toEqual(["mcp-servers"])
    })

    it("ignores ids unrelated to any spotlight", async () => {
        const store = await setupStore([{navItemId: "mcp-servers"}])

        store.markSeenById("flows")

        expect(store.unseenSpotlights).toHaveLength(1)
        expect(localStorage.getItem(SEEN_STORAGE_KEY)).toBeNull()
    })

    it("restores the seen state from localStorage", async () => {
        localStorage.setItem(SEEN_STORAGE_KEY, JSON.stringify(["mcp-servers"]))

        const store = await setupStore([
            {navItemId: "mcp-servers"},
            {navItemId: "other"},
        ])

        expect(store.hasUnseenForId("mcp-servers")).toBe(false)
        expect(store.hasUnseenForId("other")).toBe(true)
    })

    it("recovers from corrupted localStorage content", async () => {
        localStorage.setItem(SEEN_STORAGE_KEY, "not-json")

        const store = await setupStore([{navItemId: "mcp-servers"}])

        expect(store.hasUnseenForId("mcp-servers")).toBe(true)
    })

    it("falls back to empty when the stored value is not an array", async () => {
        localStorage.setItem(SEEN_STORAGE_KEY, JSON.stringify({seen: true}))

        const store = await setupStore([{navItemId: "mcp-servers"}])

        expect(store.hasUnseenForId("mcp-servers")).toBe(true)
    })

    it("filters non-string entries from a stored array", async () => {
        localStorage.setItem(SEEN_STORAGE_KEY, JSON.stringify([1, "mcp-servers", null]))

        const store = await setupStore([{navItemId: "mcp-servers"}])

        expect(store.hasUnseenForId("mcp-servers")).toBe(false)
    })
})
