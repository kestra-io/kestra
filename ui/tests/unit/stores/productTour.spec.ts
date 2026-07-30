import {beforeEach, describe, expect, it, vi} from "vitest"
import {createPinia, setActivePinia} from "pinia"

// jsdom runs these tests without an origin, so it provides no localStorage. The store persists to
// it, so the tests bring their own in-memory implementation.
const installLocalStorage = () => {
    const entries = new Map<string, string>()
    vi.stubGlobal("localStorage", {
        getItem: (key: string) => entries.get(key) ?? null,
        setItem: (key: string, value: string) => void entries.set(key, value),
        removeItem: (key: string) => void entries.delete(key),
        clear: () => entries.clear(),
    })
    return entries
}

describe("product tour store", () => {
    let persisted: Map<string, string>

    beforeEach(() => {
        persisted = installLocalStorage()
        persisted.clear()
        setActivePinia(createPinia())
    })

    it("starts the product tour at its first scene", async () => {
        const {useProductTourStore} = await import("../../../src/stores/productTour")
        const store = useProductTourStore()

        store.startGuided()

        expect(store.state.status).toBe("in_progress")
        expect(store.state.mode).toBe("guided")
        expect(store.state.guideId).toBe("product_tour")
        expect(store.state.currentStepId).toBe("copilot")
        expect(store.state.tour.introSeen).toBe(false)
    })

    it("keeps track of what the tour created", async () => {
        const {useProductTourStore} = await import("../../../src/stores/productTour")
        const store = useProductTourStore()

        store.startGuided()
        store.setTourState({webhookKey: "order-events-abc", failedExecutionId: "exec-1"})

        expect(store.state.tour.webhookKey).toBe("order-events-abc")
        expect(store.state.tour.failedExecutionId).toBe("exec-1")
        // Untouched fields keep their defaults.
        expect(store.state.tour.flowId).toBe("order_summary")
    })

    it("hides the left menu entry when it is dismissed by hand", async () => {
        const {useProductTourStore} = await import("../../../src/stores/productTour")
        const store = useProductTourStore()

        expect(store.isDismissed).toBe(false)

        store.dismissMenuEntry()

        expect(store.isDismissed).toBe(true)
        // Dismissing the entry does not count as having taken the tour.
        expect(store.state.status).toBe("not_started")
    })

    it("keeps offering the tour after a skip, and hides it once completed", async () => {
        const {useProductTourStore} = await import("../../../src/stores/productTour")
        const store = useProductTourStore()

        expect(store.isDismissed).toBe(false)

        store.startGuided()
        expect(store.isDismissed).toBe(false)

        // Skipping means "not now": the left menu entry stays.
        store.skip()
        expect(store.isGuidedActive).toBe(false)
        expect(store.isDismissed).toBe(false)

        store.startGuided()
        store.complete()
        expect(store.isDismissed).toBe(true)
    })

    it("dismisses the blueprints nudge on its own, and keeps it across a run", async () => {
        const {useProductTourStore} = await import("../../../src/stores/productTour")
        const store = useProductTourStore()

        store.dismissBlueprintsNudge()

        expect(store.state.tour.blueprintsNudgeDismissed).toBe(true)
        // The nudge and the left menu entry are closed separately.
        expect(store.isDismissed).toBe(false)

        // Both dismissals are preferences, so taking the tour again does not bring them back.
        store.startGuided()
        expect(store.state.tour.blueprintsNudgeDismissed).toBe(true)
    })

    it("forgets progress that belongs to another instance", async () => {
        const {useProductTourStore} = await import("../../../src/stores/productTour")
        const store = useProductTourStore()

        store.syncInstance("instance-a")
        store.startGuided()
        store.setStep("webhook_trigger")
        store.skip()

        // Same instance: nothing changes.
        store.syncInstance("instance-a")
        expect(store.state.status).toBe("skipped")
        expect(store.state.currentStepId).toBe("webhook_trigger")

        // A different instance behind the same address: start over, so the tour is offered again and
        // no execution id from the previous instance is left behind.
        store.syncInstance("instance-b")
        expect(store.state.status).toBe("not_started")
        expect(store.state.currentStepId).toBe(null)
        expect(store.state.instanceUuid).toBe("instance-b")
    })

    it("restores a tour left in the middle of a scene", async () => {
        const {useProductTourStore} = await import("../../../src/stores/productTour")
        const store = useProductTourStore()

        store.startGuided()
        store.setStep("webhook_trigger")
        store.setTourState({introSeen: true})

        setActivePinia(createPinia())
        const {useProductTourStore: reimport} = await import("../../../src/stores/productTour")
        const restored = reimport()

        expect(restored.state.currentStepId).toBe("webhook_trigger")
        expect(restored.state.tour.introSeen).toBe(true)
        expect(restored.isGuidedActive).toBe(true)
    })
})
