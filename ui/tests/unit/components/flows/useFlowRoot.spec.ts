import {afterEach, beforeEach, describe, expect, it, vi} from "vitest"
import {effectScope, nextTick} from "vue"

vi.mock("vue-router", () => ({
    useRoute: () => ({
        params: {namespace: "company.team", id: "myflow"},
        query: {},
        meta: {tab: "edit"},
        name: "flows/update/edit",
    }),
    useRouter: () => ({replace: vi.fn()}),
}))

vi.mock("vue-i18n", () => ({
    useI18n: () => ({t: (key: string) => key}),
}))

vi.mock("../../../../src/stores/flow", async () => {
    const {reactive} = await import("vue")
    const flowStore = reactive({
        flow: undefined,
        dependenciesCount: undefined,
        loadDependencies: vi.fn(),
        loadFlow: vi.fn(),
        loadGraph: vi.fn(),
        isCreating: false,
    })

    return {useFlowStore: () => flowStore}
})

vi.mock("../../../../src/stores/routeTabs", () => ({
    useRouteTabsStore: () => ({
        setTabs: vi.fn(),
        clearTabsIfOwner: vi.fn(),
    }),
}))

vi.mock("override/stores/auth", () => ({
    useAuthStore: () => ({
        user: {
            hasAny: () => true,
            isAllowed: () => true,
        },
    }),
}))

vi.mock("override/stores/misc", () => ({
    useMiscStore: () => ({configs: {chartDefaultDuration: "PT24H"}}),
}))

import {useFlowStore} from "../../../../src/stores/flow"
import {useFlowRoot} from "../../../../src/components/flows/composables/useFlowRoot"

describe("useFlowRoot", () => {
    const flowStore = useFlowStore()
    const loadDependencies = vi.mocked(flowStore.loadDependencies)
    const loadFlow = vi.mocked(flowStore.loadFlow)

    beforeEach(() => {
        vi.useFakeTimers()
        flowStore.flow = undefined
        flowStore.dependenciesCount = undefined
        loadDependencies.mockReset()
        loadFlow.mockReset()
        loadFlow.mockResolvedValue(undefined)
        vi.mocked(flowStore.loadGraph).mockReset()
    })

    afterEach(() => {
        vi.useRealTimers()
    })

    it("keeps the dependencies tab enabled when the store reports one dependency", async () => {
        loadDependencies.mockImplementation(async () => {
            flowStore.dependenciesCount = 1
            return {count: 1}
        })
        const scope = effectScope()
        const flowRoot = scope.run(() => useFlowRoot())!

        flowStore.flow = {id: "myflow", namespace: "company.team"} as any
        await nextTick()
        await vi.advanceTimersByTimeAsync(1000)
        await nextTick()

        expect(loadDependencies).toHaveBeenCalledWith({
            subtype: "FLOW",
            namespace: "company.team",
            id: "myflow",
        }, true)
        expect(flowRoot.dependenciesCount.value).toBe(1)
        expect(flowRoot.tabs.value.find(tab => tab.name === "dependencies")).toMatchObject({
            count: 1,
            disabled: false,
        })

        scope.stop()
    })

    it("reuses the flow the route guard loaded, instead of fetching it a second time", async () => {
        flowStore.flow = {id: "myflow", namespace: "company.team"} as any
        const scope = effectScope()

        scope.run(() => useFlowRoot().setupLifecycle())
        await nextTick()

        expect(loadFlow).not.toHaveBeenCalled()
        expect(flowStore.loadGraph).toHaveBeenCalledWith({flow: flowStore.flow})

        scope.stop()
    })

    it("fetches the flow when the store holds another one", async () => {
        flowStore.flow = {id: "otherflow", namespace: "company.team"} as any
        const scope = effectScope()

        scope.run(() => useFlowRoot().setupLifecycle())
        await nextTick()

        expect(loadFlow).toHaveBeenCalledWith(expect.objectContaining({
            namespace: "company.team",
            id: "myflow",
            allowDeleted: true,
        }))

        scope.stop()
    })
})
