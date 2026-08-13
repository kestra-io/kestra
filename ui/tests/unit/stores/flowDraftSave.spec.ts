import {beforeAll, beforeEach, describe, expect, it, vi} from "vitest"
import {createPinia, setActivePinia} from "pinia"

// Capture the `draft` param the store sends to the backend on save.
// Typed with varargs so `updateFlow.mock.calls.at(-1)?.[0]` type-checks; an argless
// `vi.fn(() => …)` infers zero-length call tuples and trips TS2493 under vue-tsc.
const updateFlow = vi.fn((..._args: any[]) => Promise.resolve({id: "f", namespace: "ns", draft: false, source: ""}))
const createFlow = vi.fn((..._args: any[]) => Promise.resolve({id: "f", namespace: "ns", draft: false, source: ""}))
const validateFlows = vi.fn(() => Promise.resolve([{}]))
// GET /flows/{namespace}/{id} - used by loadFlow() to fetch a flow's source
const getFlow = vi.fn((..._args: any[]) => Promise.resolve({id: "f", namespace: "ns", draft: true, revision: 1, source: ""}))

vi.mock("@kestra-io/kestra-sdk", () => ({
    useClient: () => ({get: vi.fn(() => Promise.resolve({status: 200, data: {}}))}),
}))

// saveFlow()/createFlow() and validateFlow() go through the SDK's flows submodule, not
// useClient()'s axios instance
vi.mock("@kestra-io/kestra-sdk/flows", () => ({
    validateFlows,
    updateFlow,
    createFlow,
    flow: getFlow,
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
    // saveFlow() goes through FlowsAPI.updateFlow({..., draft})
    const call = updateFlow.mock.calls.at(-1)
    return call?.[0]?.draft
}

function lastUpdateFlowCall() {
    return updateFlow.mock.calls.at(-1)?.[0]
}

describe("flow draft save — draft resolution per entry point", () => {
    // First import of the flow store pulls in heavy deps (monaco, element-plus — the latter is
    // inlined for the test transform, see vitest.config.unit.js); warm it so no single test pays
    // that cost and trips the default 5s timeout.
    beforeAll(async () => {
        await import("../../../src/stores/flow")
    }, 90000)

    beforeEach(() => {
        localStorage.clear()
        updateFlow.mockClear()
        createFlow.mockClear()
        getFlow.mockClear()
        validateFlows.mockReset()
        validateFlows.mockResolvedValue([{}])
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

    it("saveAll() sends an invalid published flow to the backend", async () => {
        const store = await freshStore()
        validateFlows.mockResolvedValue([{constraints: "Invalid task configuration"}])

        await store.saveAll()

        expect(updateFlow)
            .toHaveBeenCalledTimes(1)
    })

    it("save() sends an invalid published flow to the backend", async () => {
        const store = await freshStore()
        validateFlows.mockResolvedValue([{constraints: "Invalid task configuration"}])

        await store.save()

        expect(updateFlow)
            .toHaveBeenCalledTimes(1)
    })

    // FlowRun's executionsStore.flow (the run-panel's flow) has no `source` field: publishDraft(target)
    // must load the flow's source itself instead of silently no-op'ing on the missing source.
    it("publishDraft(target) fetches the target's source via loadFlow(store:false) and publishes it", async () => {
        const store = await freshStore()
        store.flowYaml = ""
        const target = {id: "f", namespace: "ns", draft: true} as any

        getFlow.mockResolvedValueOnce({id: "f", namespace: "ns", draft: true, revision: 1, source: VALID_FLOW})

        const outcome = await store.publishDraft(target)

        expect(getFlow).toHaveBeenCalledWith(expect.objectContaining({namespace: "ns", id: "f", source: true}))
        expect(lastUpdateFlowCall())
            .toMatchObject({body: VALID_FLOW, draft: false})
        expect(outcome)
            .toBe("saved")
    })

    // TriggerFlow/FlowRun is embedded in the flow editor's own top bar, so useFlowStore() (a Pinia
    // singleton) is shared with Monaco: publishDraft(target) must publish the last-saved draft
    // source without touching flowYaml/flowYamlOrigin, or it silently wipes unsaved keystrokes.
    it("publishDraft(target) does not clobber unsaved editor buffer content", async () => {
        const store = await freshStore()
        const unsavedEdits = `${VALID_FLOW}  # unsaved local edit\n`
        store.flowYaml = unsavedEdits
        store.flowYamlOrigin = VALID_FLOW
        const target = {id: "f", namespace: "ns", draft: true} as any

        const savedDraftSource = VALID_FLOW.replace("hi", "saved draft revision")
        getFlow.mockResolvedValueOnce({id: "f", namespace: "ns", draft: true, revision: 1, source: savedDraftSource})

        expect(store.haveChange).toBe(true)

        const outcome = await store.publishDraft(target)

        expect(store.flowYaml).toBe(unsavedEdits)
        expect(store.flowYamlOrigin).toBe(VALID_FLOW)
        expect(store.haveChange).toBe(true)
        expect(lastUpdateFlowCall())
            .toMatchObject({body: savedDraftSource, draft: false})
        expect(outcome)
            .toBe("saved")
    })

    it("publishDraft() with no target publishes the store's own in-progress source", async () => {
        const store = await freshStore()
        await store.publishDraft()
        expect(getFlow).not.toHaveBeenCalled()
        expect(lastDraftParam())
            .toBe(false)
    })
})
