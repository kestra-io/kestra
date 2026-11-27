import {beforeEach, describe, expect, it, vi} from "vitest"
import {createPinia, setActivePinia} from "pinia"
import {KsMessageBox} from "@kestra-io/design-system"

const axiosGet = vi.fn()
const axiosPost = vi.fn()
const axiosPut = vi.fn()
const validateFlows = vi.fn()
const updateFlow = vi.fn()
const getFlow = vi.fn()
const createFlow = vi.fn()

vi.mock("nprogress", () => ({
    start: vi.fn(),
    done: vi.fn(),
    set: vi.fn(),
    inc: vi.fn(),
}))

vi.mock("vue-router", () => ({
    useRoute: () => ({query: {}, params: {}}),
    useRouter: () => ({
        push: vi.fn(),
        replace: vi.fn(),
        beforeEach: vi.fn(),
        afterEach: vi.fn(),
    }),
}))

vi.mock("@kestra-io/kestra-sdk", () => ({
    useClient: () => ({
        get: axiosGet,
        post: axiosPost,
        put: axiosPut,
        patch: vi.fn(),
        delete: vi.fn(),
    }),
}))

// validateFlow()/saveFlow() go through the SDK's flows submodule, not useClient()'s axios instance
vi.mock("@kestra-io/kestra-sdk/flows", () => ({
    validateFlows: (...args: any[]) => validateFlows(...args),
    updateFlow: (...args: any[]) => updateFlow(...args),
    flow: (...args: any[]) => getFlow(...args),
    createFlow: (...args: any[]) => createFlow(...args),
}))

vi.mock("@kestra-io/design-system", async (importOriginal) => {
    const actual = await importOriginal<typeof import("@kestra-io/design-system")>()
    const KsNotification = Object.assign(vi.fn(), {closeAll: vi.fn()})
    return {...actual, KsMessageBox: vi.fn(), KsNotification}
})

const FLOW_YAML = [
    "id: my-flow",
    "namespace: my.ns",
    "tasks:",
    "  - id: t1",
    "    type: io.kestra.plugin.core.log.Log",
    "    message: hello",
].join("\n")

async function setupOutdatedStore() {
    const {useFlowStore} = await import("../../../src/stores/flow")
    const store = useFlowStore()

    store.flow = {id: "my-flow", namespace: "my.ns", revision: 1} as any
    store.flowYaml = FLOW_YAML
    store.flowYamlOrigin = ""
    store.isCreating = false

    return store
}

describe("flow store outdated save confirmation", () => {
    beforeEach(() => {
        vi.resetModules()
        vi.mocked(KsMessageBox).mockReset()
        axiosGet.mockReset()
        axiosPost.mockReset()
        axiosPut.mockReset()
        validateFlows.mockReset()
        updateFlow.mockReset()
        getFlow.mockReset()
        createFlow.mockReset()
        getFlow.mockResolvedValue({id: "my-flow", namespace: "my.ns", revision: 2, source: FLOW_YAML})

        // /flows/validate -> backend flags the in-progress edit as outdated
        validateFlows.mockResolvedValue([{outdated: true}])
        // /flows/{ns}/{id} (save) -> succeeds
        updateFlow.mockResolvedValue({id: "my-flow", namespace: "my.ns", revision: 2, source: FLOW_YAML})

        setActivePinia(createPinia())
        localStorage.clear()
    })

    it("prompts before overwriting an outdated revision and aborts on cancel", async () => {
        vi.mocked(KsMessageBox).mockRejectedValue(new Error("cancel"))

        const store = await setupOutdatedStore()
        const outcome = await store.saveAll()

        expect(KsMessageBox).toHaveBeenCalledTimes(1)
        expect(updateFlow).not.toHaveBeenCalled()
        expect(outcome).toBe("no_op")
    })

    it("overwrites the outdated revision when the prompt is confirmed", async () => {
        vi.mocked(KsMessageBox).mockResolvedValue("confirm" as any)

        const store = await setupOutdatedStore()
        const outcome = await store.saveAll()

        expect(KsMessageBox).toHaveBeenCalledTimes(1)
        expect(updateFlow).toHaveBeenCalledTimes(1)
        expect(outcome).toBe("saved")
    })

    it("does not prompt when the edited revision is up to date", async () => {
        validateFlows.mockResolvedValue([{}])

        const store = await setupOutdatedStore()
        const outcome = await store.saveAll()

        expect(KsMessageBox).not.toHaveBeenCalled()
        expect(updateFlow).toHaveBeenCalledTimes(1)
        expect(outcome).toBe("saved")
    })

    // save() backs the no-code editor's Ctrl+S (useKeyboardSave) and must gate too
    it("prompts and aborts on cancel when saving an outdated revision via save()", async () => {
        vi.mocked(KsMessageBox).mockRejectedValue(new Error("cancel"))

        const store = await setupOutdatedStore()
        const outcome = await store.save()

        expect(KsMessageBox).toHaveBeenCalledTimes(1)
        expect(updateFlow).not.toHaveBeenCalled()
        expect(outcome).toBe("no_op")
    })

    it("overwrites the outdated revision via save() when the prompt is confirmed", async () => {
        vi.mocked(KsMessageBox).mockResolvedValue("confirm" as any)

        const store = await setupOutdatedStore()
        const outcome = await store.save()

        expect(KsMessageBox).toHaveBeenCalledTimes(1)
        expect(updateFlow).toHaveBeenCalledTimes(1)
        expect(outcome).toBe("saved")
    })

    describe("when the flow was deleted after it was opened", () => {
        const notFound = {status: 404}

        beforeEach(() => {
            getFlow.mockRejectedValue(notFound)
            updateFlow.mockRejectedValue(notFound)
            createFlow.mockResolvedValue({id: "my-flow", namespace: "my.ns", revision: 3, source: FLOW_YAML})
        })

        it("offers to recreate it instead of reporting an outdated revision, and recreates it on confirm", async () => {
            vi.mocked(KsMessageBox).mockResolvedValue("confirm" as any)

            const store = await setupOutdatedStore()
            const outcome = await store.saveAll()

            expect(KsMessageBox).toHaveBeenCalledTimes(1)
            expect(vi.mocked(KsMessageBox).mock.calls[0][0]).toMatchObject({title: "deleted flow save confirmation.title"})
            expect(updateFlow).toHaveBeenCalledWith(expect.anything(), {ignoreNotFound: true})
            expect(createFlow).toHaveBeenCalledWith(expect.objectContaining({body: FLOW_YAML}))
            expect(outcome).toBe("saved")
        })

        it("keeps the edit in the editor when recreating is declined", async () => {
            vi.mocked(KsMessageBox).mockRejectedValue(new Error("cancel"))

            const store = await setupOutdatedStore()
            const outcome = await store.saveAll()

            expect(createFlow).not.toHaveBeenCalled()
            expect(store.flowYaml).toBe(FLOW_YAML)
            expect(outcome).toBe("blocked")
        })
    })
})
