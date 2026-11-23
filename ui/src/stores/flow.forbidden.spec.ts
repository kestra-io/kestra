import {beforeEach, describe, expect, it, vi} from "vitest"
import {createPinia, setActivePinia} from "pinia"

const flow = vi.hoisted(() => vi.fn())
const flowDependencies = vi.hoisted(() => vi.fn())

vi.mock("vue-router", () => ({
    useRoute: () => ({query: {}, params: {}}),
    useRouter: () => ({
        push: vi.fn(),
        replace: vi.fn(),
        beforeEach: vi.fn(),
        afterEach: vi.fn(),
    }),
}))

vi.mock("@kestra-io/kestra-sdk/flows", () => ({flow, flowDependencies}))

const {useFlowStore} = await import("./flow")

describe("flow store requests a viewer may be forbidden", () => {
    beforeEach(() => {
        setActivePinia(createPinia())
        flow.mockReset()
        flowDependencies.mockReset()
    })

    it("loads an optional flow as empty, without the global error, when the viewer may not read it", async () => {
        const forbidden = {status: 403}
        flow.mockRejectedValue(forbidden)

        await expect(useFlowStore().loadFlow({namespace: "ns", id: "f", optional: true})).resolves.toEqual({})
        expect(forbidden).toHaveProperty("__kestra_handled", true)
    })

    it.each([
        ["an optional flow that fails for another reason", true, 500],
        ["a flow that is not optional", false, 403],
    ])("still rejects %s", async (_case, optional, status) => {
        flow.mockRejectedValue({status})

        await expect(useFlowStore().loadFlow({namespace: "ns", id: "f", optional})).rejects.toEqual({status})
    })

    it("counts no dependency, without the global error, when the viewer may not read them", async () => {
        const forbidden = {status: 403}
        flowDependencies.mockRejectedValue(forbidden)

        await expect(useFlowStore().loadDependencies({namespace: "ns", id: "f", subtype: "FLOW"}, true)).resolves.toEqual({count: 0})
        expect(forbidden).toHaveProperty("__kestra_handled", true)
    })

    it("still rejects a dependency count that fails for another reason", async () => {
        flowDependencies.mockRejectedValue({status: 500})

        await expect(useFlowStore().loadDependencies({namespace: "ns", id: "f", subtype: "FLOW"}, true)).rejects.toEqual({status: 500})
    })
})
