import {afterEach, beforeEach, describe, expect, it, vi} from "vitest"
import {createPinia, setActivePinia} from "pinia"
import type {Flow} from "./flow"

const routeParams: {tenant?: string} = {}

vi.mock("@kestra-io/kestra-sdk", () => ({
    useClient: () => ({get: vi.fn(() => Promise.resolve({status: 200, data: {}}))}),
}))

// The store reads the route at setup, and here it is created outside any component.
vi.mock("vue-router", () => ({useRoute: () => ({query: {}, params: routeParams})}))

function makeFlow(id: string): Flow {
    return {id, namespace: "ns", revision: 2, source: `id: ${id}\nnamespace: ns\n`}
}

async function storeOn(id: string) {
    const {useFlowStore} = await import("./flow")
    const store = useFlowStore()
    store.isCreating = false
    store.flow = makeFlow(id)
    store.flowYaml = `id: ${id}\nnamespace: ns\ndescription: edited\n`
    return store
}

describe("flow store - the draft outbox", () => {
    beforeEach(() => {
        setActivePinia(createPinia())
        routeParams.tenant = "main"
    })

    afterEach(() => {
        localStorage.clear()
    })

    it("keeps a queued draft with its own flow, so another flow never shows it as pending", async () => {
        const store = await storeOn("first")

        expect(store.queueDraft()).toBe(true)
        expect(store.queuedDraft?.source).toContain("description: edited")
        expect(store.queuedDraft?.baseRevision).toBe(2)

        store.flow = makeFlow("second")
        expect(store.queuedDraft).toBeUndefined()
        expect(store.storedDraft()).toBeUndefined()

        store.flow = makeFlow("first")
        expect(store.queuedDraft).toBeDefined()
    })

    it("leaves nothing behind on sign-out, in memory or in storage, for any flow", async () => {
        const store = await storeOn("first")
        store.queueDraft()
        store.flow = makeFlow("second")
        store.queueDraft()

        store.purgeQueuedDrafts()

        expect(store.queuedDraft).toBeUndefined()
        expect(store.storedDraft()).toBeUndefined()
        store.flow = makeFlow("first")
        expect(store.queuedDraft).toBeUndefined()
        expect(store.storedDraft()).toBeUndefined()
    })
})
