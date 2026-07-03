import {beforeAll, beforeEach, describe, expect, it, vi} from "vitest"
import {createPinia, setActivePinia} from "pinia"

// Capture the `draft` query param the store sends to the backend on save.
// Typed with varargs so `put.mock.calls.at(-1)?.[2]` (the request config, index 2) type-checks;
// an argless `vi.fn(() => …)` infers zero-length call tuples and trips TS2493 under vue-tsc.
const put = vi.fn((..._args: any[]) => Promise.resolve({status: 200, data: {id: "f", namespace: "ns", draft: false}}))
const post = vi.fn((..._args: any[]) => Promise.resolve({status: 200, data: {id: "f", namespace: "ns", draft: false}}))

vi.mock("@kestra-io/kestra-sdk", () => ({
    useClient: () => ({put, post, get: vi.fn(() => Promise.resolve({status: 200, data: {}}))}),
}))

// validateFlow() goes through the SDK's flows submodule, not useClient()'s axios instance
vi.mock("@kestra-io/kestra-sdk/flows", () => ({
    validateFlows: vi.fn(() => Promise.resolve([{}])),
}))

// Avoid mounting the notification service when notifySaved fires.
vi.mock("../../../src/utils/toast", () => ({
    makeToast: () => ({saved: vi.fn(), success: vi.fn(), error: vi.fn(), deleted: vi.fn()}),
    useToast: () => ({saved: vi.fn(), success: vi.fn(), error: vi.fn(), deleted: vi.fn()}),
}))

const VALID_FLOW = `id: f
namespace: ns
tasks:
  - id: log
    type: io.kestra.plugin.core.log.Log
    message: hi
`

async function freshStore() {
    const {useFlowStore} = await import("../../../src/stores/flow")
    const store = useFlowStore()
    store.flowYaml = VALID_FLOW
    store.isCreating = false
    // saveAll() returns early ("blocked") when flow.value is unset; seed an existing flow so the
    // save path actually reaches the client. draft here is the *current* state the no-arg save reads.
    store.flow = {id: "f", namespace: "ns", draft: false} as any
    return store
}

function lastDraftParam() {
    // saveFlow() goes through client.put with { params: { draft } }
    const call = put.mock.calls.at(-1)
    return call?.[2]?.params?.draft
}

describe("flow draft save — draft resolution per entry point", () => {
    // First import of the flow store pulls in heavy deps (monaco, ...); warm it so no single test
    // pays that cost and trips the default 5s timeout.
    beforeAll(async () => {
        await import("../../../src/stores/flow")
    }, 30000)

    beforeEach(() => {
        localStorage.clear()
        put.mockClear()
        post.mockClear()
        setActivePinia(createPinia())
    })

    it("exposes the save actions on the flow store", async () => {
        const store = await freshStore()
        expect(typeof store.saveAll).toBe("function")
        expect(typeof store.saveAsDraft).toBe("function")
        expect(typeof store.save).toBe("function")
    })

    it("saveAll(false) publishes (draft=false)", async () => {
        const store = await freshStore()
        store.flow = {id: "f", namespace: "ns", draft: true} as any
        await store.saveAll(false)
        expect(lastDraftParam())
            .toBe(false)
    })

    it("saveAsDraft() saves as a draft (draft=true)", async () => {
        const store = await freshStore()
        await store.saveAsDraft()
        expect(lastDraftParam())
            .toBe(true)
    })

    it("saveAll() with no argument preserves the flow's current draft state (draft → draft)", async () => {
        const store = await freshStore()
        store.flow = {id: "f", namespace: "ns", draft: true} as any
        await store.saveAll()
        expect(lastDraftParam())
            .toBe(true)
    })

    it("saveAll() with no argument on a published flow stays published (draft=false)", async () => {
        const store = await freshStore()
        store.flow = {id: "f", namespace: "ns", draft: false} as any
        await store.saveAll()
        expect(lastDraftParam())
            .toBe(false)
    })
})
