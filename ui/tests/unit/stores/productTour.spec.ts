import {afterAll, beforeEach, describe, expect, it, vi} from "vitest"
import {createPinia, setActivePinia} from "pinia"

// jsdom runs without an origin, so it provides no localStorage for the store to persist to.
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

    afterAll(() => {
        vi.unstubAllGlobals()
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
        expect(store.state.tour.flowId).toBe("order_summary")
    })

    it("hides the left menu entry when it is dismissed by hand", async () => {
        const {useProductTourStore} = await import("../../../src/stores/productTour")
        const store = useProductTourStore()

        expect(store.isDismissed).toBe(false)

        store.dismissMenuEntry()

        expect(store.isDismissed).toBe(true)
        expect(store.state.status).toBe("not_started")
    })

    it("keeps offering the tour after a skip, and hides it once completed", async () => {
        const {useProductTourStore} = await import("../../../src/stores/productTour")
        const store = useProductTourStore()

        expect(store.isDismissed).toBe(false)

        store.startGuided()
        expect(store.isDismissed).toBe(false)

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
        expect(store.isDismissed).toBe(false)

        store.startGuided()
        expect(store.state.tour.blueprintsNudgeDismissed).toBe(true)
    })

    it("forgets progress that belongs to another instance, tenant or variant", async () => {
        const {useProductTourStore} = await import("../../../src/stores/productTour")
        const store = useProductTourStore()

        store.syncScope("instance-a:main:product_tour")
        store.startGuided()
        store.setStep("webhook_trigger")
        store.skip()

        store.syncScope("instance-a:main:product_tour")
        expect(store.state.status).toBe("skipped")
        expect(store.state.currentStepId).toBe("webhook_trigger")

        // Another tenant of the same instance is another scope: same instance is not enough.
        store.syncScope("instance-a:other:product_tour")
        expect(store.state.status).toBe("not_started")
        expect(store.state.currentStepId).toBe(null)
        expect(store.state.scope).toBe("instance-a:other:product_tour")
    })

    it("adopts a scope without discarding progress made before there was one", async () => {
        const {useProductTourStore} = await import("../../../src/stores/productTour")
        const store = useProductTourStore()

        store.startGuided()
        store.setStep("webhook_trigger")

        store.syncScope("instance-a:main:product_tour")

        expect(store.state.currentStepId).toBe("webhook_trigger")
        expect(store.state.scope).toBe("instance-a:main:product_tour")
    })

    it("starts a variant at its own first scene, under its own guide id", async () => {
        const {useProductTourStore} = await import("../../../src/stores/productTour")
        const store = useProductTourStore()

        store.startGuided({id: "infrastructure_tour", scenes: [{id: "catalog"}, {id: "request_form"}]})

        expect(store.state.guideId).toBe("infrastructure_tour")
        expect(store.state.currentStepId).toBe("catalog")
    })

    it("carries variant-owned progress, and keeps it out of the default tour's state", async () => {
        const {useProductTourStore} = await import("../../../src/stores/productTour")
        const store = useProductTourStore()

        store.startGuided()
        store.setData({deploymentId: "tour-sandbox"})

        expect(store.state.data.deploymentId).toBe("tour-sandbox")
        expect(store.state.tour).not.toHaveProperty("deploymentId")

        store.startGuided()
        expect(store.state.data).toEqual({})
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
