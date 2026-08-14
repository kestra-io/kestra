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
        loadDependencies: vi.fn(),
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

    beforeEach(() => {
        vi.useFakeTimers()
        flowStore.flow = undefined
        loadDependencies.mockReset()
    })

    afterEach(() => {
        vi.useRealTimers()
    })

    it("keeps the dependencies tab enabled when the store reports one dependency", async () => {
        loadDependencies.mockResolvedValue({count: 1})
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
})
