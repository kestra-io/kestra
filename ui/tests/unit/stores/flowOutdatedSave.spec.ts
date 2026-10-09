import {beforeEach, describe, expect, it, vi} from "vitest"
import {createPinia, setActivePinia} from "pinia"
import * as FlowsAPI from "@kestra-io/kestra-sdk/flows"
import {KsMessageBox} from "@kestra-io/design-system"
import type {Flow} from "../../../src/stores/flow"

const axiosGet = vi.fn()
const axiosPost = vi.fn()
const axiosPut = vi.fn()
const validateFlows = vi.fn<typeof FlowsAPI.validateFlows>()
const updateFlow = vi.fn<typeof FlowsAPI.updateFlow>()
const CONFIRMED = "confirm" as unknown as Awaited<ReturnType<typeof KsMessageBox>>

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
    validateFlows: (...args: Parameters<typeof FlowsAPI.validateFlows>) => validateFlows(...args),
    updateFlow: (...args: Parameters<typeof FlowsAPI.updateFlow>) => updateFlow(...args),
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

    const flow: Flow = {id: "my-flow", namespace: "my.ns", revision: 1, source: FLOW_YAML}
    store.flow = flow
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

        // /flows/validate -> backend flags the in-progress edit as outdated
        validateFlows.mockResolvedValue([{index: 0, outdated: true}])
        // /flows/{ns}/{id} (save) -> succeeds
        updateFlow.mockResolvedValue({
            id: "my-flow",
            namespace: "my.ns",
            revision: 2,
            source: FLOW_YAML,
            disabled: false,
            draft: false,
            deleted: false,
            tasks: [],
        })

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
        // Element Plus types this resolved value as an intersection that no literal test value satisfies.
        vi.mocked(KsMessageBox).mockResolvedValue(CONFIRMED)

        const store = await setupOutdatedStore()
        const outcome = await store.saveAll()

        expect(KsMessageBox).toHaveBeenCalledTimes(1)
        expect(updateFlow).toHaveBeenCalledTimes(1)
        expect(outcome).toBe("saved")
    })

    it("does not prompt when the edited revision is up to date", async () => {
        validateFlows.mockResolvedValue([{index: 0, outdated: false}])

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
        vi.mocked(KsMessageBox).mockResolvedValue(CONFIRMED)

        const store = await setupOutdatedStore()
        const outcome = await store.save()

        expect(KsMessageBox).toHaveBeenCalledTimes(1)
        expect(updateFlow).toHaveBeenCalledTimes(1)
        expect(outcome).toBe("saved")
    })
})
