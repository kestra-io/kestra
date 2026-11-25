import {describe, it, expect, vi, beforeEach, afterEach} from "vitest"
import {defineComponent, nextTick, ref} from "vue"
import {mount} from "@vue/test-utils"
import {createPinia, setActivePinia} from "pinia"

// --- mocks (hoisted) ---
vi.mock("vue-router", () => ({
    useRoute: () => ({name: "home", params: {tenant: "main"}}),
}))

// A variant whose eligibility check and entry gate each test controls
const eligible = vi.fn<() => Promise<boolean>>()
const gate: {entryGate?: {recheckOn: () => unknown}} = {}
vi.mock("override/components/onboarding/tour/useTourVariant", () => ({
    useTourVariant: () => ({
        i18nPrefix: "onboarding.tour",
        entryRoute: (tenant?: string) => ({name: "welcome", params: {tenant}}),
        eligible: () => eligible(),
        ...gate,
    }),
}))

import {useProductTourEntryGate, useProductTourMenuEntry, useProductTourNudge} from "./useProductTourEntry"
import {useProductTourStore} from "../../../stores/productTour"

// Lets the eligibility promise (and its catch) settle after mount or a status change
const settle = async () => {
    await Promise.resolve()
    await Promise.resolve()
    await nextTick()
}

// Two eligibility checks whose answers each test releases, in any order
function deferTwoChecks() {
    const answers: ((value: boolean) => void)[] = []
    for (let i = 0; i < 2; i++) {
        eligible.mockReturnValueOnce(new Promise<boolean>(resolve => answers.push(resolve)))
    }
    return {first: (value: boolean) => answers[0](value), second: (value: boolean) => answers[1](value)}
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

    it("keeps the answer of the latest status re-check when an older one answers last", async () => {
        eligible.mockResolvedValue(true)
        const store = useProductTourStore()
        const entry = setup(useProductTourMenuEntry)
        await settle()

        const checks = deferTwoChecks()
        store.state.status = "completed"
        await nextTick()
        store.state.status = "not_started"
        await nextTick()

        checks.second(false)
        await settle()
        checks.first(true)
        await settle()

        expect(eligible).toHaveBeenCalledTimes(3)
        expect(entry.visible.value).toBe(false)
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

describe("useProductTourMenuEntry with an entry gate", () => {
    const recheck = ref(0)

    beforeEach(() => {
        localStorage.clear()
        setActivePinia(createPinia())
        eligible.mockReset()
        recheck.value = 0
        gate.entryGate = {recheckOn: () => recheck.value}
    })

    afterEach(() => {
        delete gate.entryGate
    })

    it("is hidden once the tour was skipped while the variant is not eligible", async () => {
        eligible.mockResolvedValue(false)
        const store = useProductTourStore()
        store.state.status = "skipped"
        const entry = setup(useProductTourMenuEntry)
        await settle()

        expect(entry.visible.value).toBe(false)
    })

    it("is shown to resume a skipped tour while the variant is eligible", async () => {
        eligible.mockResolvedValue(true)
        const store = useProductTourStore()
        store.state.status = "skipped"
        const entry = setup(useProductTourMenuEntry)
        await settle()

        expect(entry.visible.value).toBe(true)
        expect(entry.menuKey.value).toBe("menu_resume")
    })

    it("hides the entry without a reload when the variant stops being eligible", async () => {
        eligible.mockResolvedValue(true)
        const entry = setup(useProductTourMenuEntry)
        await settle()
        expect(entry.visible.value).toBe(true)

        eligible.mockResolvedValue(false)
        recheck.value++
        await settle()

        expect(eligible).toHaveBeenCalledTimes(2)
        expect(entry.visible.value).toBe(false)
    })

    it("shows the entry again when the variant becomes eligible", async () => {
        eligible.mockResolvedValue(false)
        const entry = setup(useProductTourMenuEntry)
        await settle()
        expect(entry.visible.value).toBe(false)

        eligible.mockResolvedValue(true)
        recheck.value++
        await settle()

        expect(entry.visible.value).toBe(true)
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

describe("useProductTourNudge with an entry gate", () => {
    const recheck = ref(0)

    beforeEach(() => {
        localStorage.clear()
        setActivePinia(createPinia())
        eligible.mockReset()
        recheck.value = 0
        gate.entryGate = {recheckOn: () => recheck.value}
    })

    afterEach(() => {
        delete gate.entryGate
    })

    it("is hidden while the variant is not eligible", async () => {
        eligible.mockResolvedValue(false)
        const nudge = setup(useProductTourNudge)
        await settle()

        expect(nudge.visible.value).toBe(false)
    })

    it("is shown while the variant is eligible", async () => {
        eligible.mockResolvedValue(true)
        const nudge = setup(useProductTourNudge)
        await settle()

        expect(nudge.visible.value).toBe(true)
    })

    it("hides without a reload when the variant stops being eligible, and shows again when it is back", async () => {
        eligible.mockResolvedValue(true)
        const nudge = setup(useProductTourNudge)
        await settle()
        expect(nudge.visible.value).toBe(true)

        eligible.mockResolvedValue(false)
        recheck.value++
        await settle()
        expect(nudge.visible.value).toBe(false)

        eligible.mockResolvedValue(true)
        recheck.value++
        await settle()
        expect(nudge.visible.value).toBe(true)
    })

    it("stays hidden once dismissed, even while the variant is eligible", async () => {
        eligible.mockResolvedValue(true)
        const nudge = setup(useProductTourNudge)
        await settle()

        nudge.dismiss()
        expect(nudge.visible.value).toBe(false)
    })
})

describe("useProductTourEntryGate", () => {
    const recheck = ref(0)

    beforeEach(() => {
        localStorage.clear()
        setActivePinia(createPinia())
        eligible.mockReset()
        recheck.value = 0
    })

    afterEach(() => {
        delete gate.entryGate
    })

    it("is open, without checking eligibility, for a variant with no gate", async () => {
        const entryGate = setup(useProductTourEntryGate)
        await settle()

        expect(entryGate.open.value).toBe(true)
        expect(eligible).not.toHaveBeenCalled()
    })

    it("is closed while the check of a gated variant is pending", async () => {
        gate.entryGate = {recheckOn: () => recheck.value}
        eligible.mockReturnValue(new Promise<boolean>(() => {}))
        const entryGate = setup(useProductTourEntryGate)
        await settle()

        expect(entryGate.open.value).toBe(false)
    })

    it("is closed when the check of a gated variant is rejected", async () => {
        gate.entryGate = {recheckOn: () => recheck.value}
        eligible.mockRejectedValue(new Error("network down"))
        const entryGate = setup(useProductTourEntryGate)
        await settle()

        expect(entryGate.open.value).toBe(false)
    })

    it("follows the eligibility of a gated variant, checked again on its signal", async () => {
        gate.entryGate = {recheckOn: () => recheck.value}
        eligible.mockResolvedValue(true)
        const entryGate = setup(useProductTourEntryGate)
        await settle()
        expect(entryGate.open.value).toBe(true)

        eligible.mockResolvedValue(false)
        recheck.value++
        await settle()

        expect(eligible).toHaveBeenCalledTimes(2)
        expect(entryGate.open.value).toBe(false)
    })

    it("follows the latest check when an older one answers last", async () => {
        gate.entryGate = {recheckOn: () => recheck.value}
        eligible.mockResolvedValue(false)
        const entryGate = setup(useProductTourEntryGate)
        await settle()

        // examples added, then removed before the first re-check answers
        const checks = deferTwoChecks()
        recheck.value++
        await nextTick()
        recheck.value++
        await nextTick()

        checks.second(false)
        await settle()
        checks.first(true)
        await settle()

        expect(eligible).toHaveBeenCalledTimes(3)
        expect(entryGate.open.value).toBe(false)
    })
})
