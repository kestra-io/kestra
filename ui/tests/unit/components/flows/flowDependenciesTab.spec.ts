import {beforeEach, describe, expect, it, vi} from "vitest"
import {createPinia, setActivePinia} from "pinia"

import {dependenciesTabMeta} from "../../../../src/components/flows/flowTabs"

const flowDependencies = vi.fn()

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
        get: vi.fn(),
        post: vi.fn(),
        put: vi.fn(),
        patch: vi.fn(),
        delete: vi.fn(),
    }),
}))

vi.mock("@kestra-io/kestra-sdk/flows", () => ({
    flowDependencies: (...args: any[]) => flowDependencies(...args),
}))

vi.mock("@kestra-io/design-system", async (importOriginal) => {
    const actual = await importOriginal<typeof import("@kestra-io/design-system")>()
    const KsNotification = Object.assign(vi.fn(), {closeAll: vi.fn()})
    return {...actual, KsMessageBox: vi.fn(), KsNotification}
})

// The payload GET /flows/{namespace}/{id}/dependencies returns for the pair from
// kestra-io/kestra-ee#10028: `submit_to_ray` has a flow trigger on `preprocess_video`.
// It is the same graph whichever of the two flows is asked about - the topology row is
// stored once, and the endpoint matches it as either source or destination.
const ONE_DEPENDENCY_GRAPH = {
    nodes: [
        {uid: "main_solutions.ai_preprocess_video", namespace: "solutions.ai", id: "preprocess_video"},
        {uid: "main_solutions.ai_submit_to_ray", namespace: "solutions.ai", id: "submit_to_ray"},
    ],
    edges: [
        {
            source: "main_solutions.ai_preprocess_video",
            target: "main_solutions.ai_submit_to_ray",
            relation: "FLOW_TRIGGER",
        },
    ],
}

describe("dependenciesTabMeta", () => {
    it("keeps the tab reachable for a flow with a single dependency", () => {
        // Regression guard for kestra-io/kestra-ee#10028: the count the store exposes already
        // excludes the flow's own node, so 1 means "one other flow" - not "nothing to show".
        expect(dependenciesTabMeta(1)).toEqual({count: 1, disabled: false})
    })

    it("disables the tab and hides the badge when the flow has no dependency", () => {
        expect(dependenciesTabMeta(0)).toEqual({count: undefined, disabled: true})
    })

    it("disables the tab while the count has not been fetched yet", () => {
        expect(dependenciesTabMeta(undefined)).toEqual({count: undefined, disabled: true})
    })
})

describe("flow store dependency count", () => {
    beforeEach(() => {
        setActivePinia(createPinia())
        flowDependencies.mockReset()
    })

    it("counts the other flows of the topology, from either end of the edge", async () => {
        flowDependencies.mockResolvedValue(ONE_DEPENDENCY_GRAPH)
        const {useFlowStore} = await import("../../../../src/stores/flow")
        const store = useFlowStore()

        for (const id of ["preprocess_video", "submit_to_ray"]) {
            const {count} = await store.loadDependencies({namespace: "solutions.ai", id, subtype: "FLOW"}, true)

            expect(count, `dependency count returned for ${id}`).toBe(1)
            expect(store.dependenciesCount, `dependency count stored for ${id}`).toBe(1)
            expect(dependenciesTabMeta(store.dependenciesCount).disabled, `Dependencies tab disabled for ${id}`).toBe(false)
        }
    })
})
