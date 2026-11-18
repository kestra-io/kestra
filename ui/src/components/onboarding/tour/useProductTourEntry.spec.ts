import {describe, it, expect, vi, beforeEach} from "vitest"
import {defineComponent, nextTick} from "vue"
import {mount} from "@vue/test-utils"
import {createPinia, setActivePinia} from "pinia"

// --- mocks (hoisted) ---
vi.mock("vue-router", () => ({
    useRoute: () => ({name: "home", params: {tenant: "main"}}),
}))

// A variant whose eligibility check each test controls
const eligible = vi.fn<() => Promise<boolean>>()
vi.mock("override/components/onboarding/tour/useTourVariant", () => ({
    useTourVariant: () => ({
        i18nPrefix: "onboarding.tour",
        entryRoute: (tenant?: string) => ({name: "welcome", params: {tenant}}),
        eligible: () => eligible(),
    }),
}))

import {useProductTourMenuEntry, useProductTourNudge} from "./useProductTourEntry"
import {useProductTourStore} from "../../../stores/productTour"

// Lets the eligibility promise (and its catch) settle after mount or a status change
const settle = async () => {
    await Promise.resolve()
    await Promise.resolve()
    await nextTick()
}

// The composables use onMounted, so they run inside a component
function setup<T>(composable: () => T): T {
    let result!: T
    mount(defineComponent({
        setup() {
            result = composable()
            return () => null
        },
    }))
    return result
}

describe("useProductTourMenuEntry", () => {
    beforeEach(() => {
        localStorage.clear()
        setActivePinia(createPinia())
        eligible.mockReset()
    })

    it("is hidden for an established instance whose tour was never skipped", async () => {
        eligible.mockResolvedValue(false)
        const entry = setup(useProductTourMenuEntry)
        await settle()

        expect(eligible).toHaveBeenCalledTimes(1)
        expect(entry.visible.value).toBe(false)
    })

    it("is shown for a new instance", async () => {
        eligible.mockResolvedValue(true)
        const entry = setup(useProductTourMenuEntry)
        await settle()

        expect(entry.visible.value).toBe(true)
    })

    it("is shown once the tour was skipped, even when the instance is not new", async () => {
        eligible.mockResolvedValue(false)
        const store = useProductTourStore()
        store.state.status = "skipped"
        const entry = setup(useProductTourMenuEntry)
        await settle()

        expect(entry.visible.value).toBe(true)
    })

    it("is hidden while dismissed", async () => {
        eligible.mockResolvedValue(true)
        const entry = setup(useProductTourMenuEntry)
        await settle()
        expect(entry.visible.value).toBe(true)

        entry.dismiss()
        expect(entry.visible.value).toBe(false)
    })

    it("is hidden while the guided tour is active", async () => {
        eligible.mockResolvedValue(true)
        const store = useProductTourStore()
        store.state.mode = "guided"
        store.state.status = "in_progress"
        const entry = setup(useProductTourMenuEntry)
        await settle()

        expect(store.isGuidedActive).toBe(true)
        expect(entry.visible.value).toBe(false)
    })

    it("stays hidden, without throwing, when the eligibility check is rejected", async () => {
        eligible.mockRejectedValue(new Error("network down"))
        const entry = setup(useProductTourMenuEntry)
        await settle()

        expect(eligible).toHaveBeenCalledTimes(1)
        expect(entry.visible.value).toBe(false)
    })

    it("hides the entry again when a later eligibility check is rejected", async () => {
        eligible.mockResolvedValue(true)
        const store = useProductTourStore()
        const entry = setup(useProductTourMenuEntry)
        await settle()
        expect(entry.visible.value).toBe(true)

        eligible.mockRejectedValue(new Error("network down"))
        store.state.status = "in_progress"
        await settle()

        expect(eligible).toHaveBeenCalledTimes(2)
        expect(entry.visible.value).toBe(false)
    })

    it("checks eligibility again when the tour status changes", async () => {
        eligible.mockResolvedValue(false)
        const store = useProductTourStore()
        const entry = setup(useProductTourMenuEntry)
        await settle()
        expect(eligible).toHaveBeenCalledTimes(1)
        expect(entry.visible.value).toBe(false)

        // the instance became eligible meanwhile; the next status change picks it up
        eligible.mockResolvedValue(true)
        store.state.status = "completed"
        await settle()
        expect(eligible).toHaveBeenCalledTimes(2)

        store.state.status = "not_started"
        await settle()
        expect(eligible).toHaveBeenCalledTimes(3)
        expect(entry.visible.value).toBe(true)
    })

    it("uses menu_resume once skipped and menu otherwise", async () => {
        eligible.mockResolvedValue(true)
        const store = useProductTourStore()
        const entry = setup(useProductTourMenuEntry)
        await settle()
        expect(entry.menuKey.value).toBe("menu")

        store.state.status = "skipped"
        await settle()
        expect(entry.menuKey.value).toBe("menu_resume")
    })

    it("prefixes translation keys with the variant's i18n prefix", () => {
        eligible.mockResolvedValue(false)
        const entry = setup(useProductTourMenuEntry)

        expect(entry.translationKey("menu")).toBe("onboarding.tour.menu")
        expect(entry.translationKey("menu_resume")).toBe("onboarding.tour.menu_resume")
    })

    it("builds the tour route from the tenant of the current route", () => {
        eligible.mockResolvedValue(false)
        const entry = setup(useProductTourMenuEntry)

        expect(entry.tourRoute.value).toEqual({name: "welcome", params: {tenant: "main"}})
    })
})

describe("useProductTourNudge", () => {
    beforeEach(() => {
        localStorage.clear()
        setActivePinia(createPinia())
        eligible.mockReset()
    })

    it("is visible while the nudge is not dismissed and no guided tour runs", () => {
        const nudge = setup(useProductTourNudge)
        expect(nudge.visible.value).toBe(true)
    })

    it("is hidden once the nudge is dismissed", () => {
        const nudge = setup(useProductTourNudge)
        expect(nudge.visible.value).toBe(true)

        nudge.dismiss()
        expect(nudge.visible.value).toBe(false)
    })

    it("is hidden while the guided tour is active", () => {
        const store = useProductTourStore()
        store.state.mode = "guided"
        store.state.status = "in_progress"
        const nudge = setup(useProductTourNudge)

        expect(nudge.visible.value).toBe(false)
    })

    it("prefixes translation keys with the variant's i18n prefix", () => {
        const nudge = setup(useProductTourNudge)
        expect(nudge.translationKey("nudge")).toBe("onboarding.tour.nudge")
    })

    it("builds the tour route from the tenant of the current route", () => {
        const nudge = setup(useProductTourNudge)
        expect(nudge.tourRoute.value).toEqual({name: "welcome", params: {tenant: "main"}})
    })
})
