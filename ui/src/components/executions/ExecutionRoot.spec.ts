import {afterAll, beforeAll, beforeEach, describe, expect, it, vi} from "vitest"
import {reactive} from "vue"

const route = reactive<{params: Record<string, string>}>({
    params: {namespace: "company.team", flowId: "demo_breadcrumb_fix", id: "exec-1"},
})

vi.mock("vue-router", () => ({
    useRoute: () => route,
}))

vi.mock("../../stores/executions", () => ({
    // Enough of the store for the page to mount: it holds the execution the route guard loaded,
    // so the page renders instead of waiting on the loading state, and the lifecycle hook can
    // follow that execution and tear the subscription down again.
    useExecutionsStore: () => ({
        execution: {id: "exec-1"},
        followExecution: vi.fn(),
        resetLogs: vi.fn(),
        closeSSE: vi.fn(),
    }),
}))

vi.mock("../../stores/flow", async () => {
    const {reactive: reactiveVue} = await import("vue")
    const flowStore = reactiveVue({
        flow: undefined,
        flowGraph: undefined,
        invalidGraph: false,
        loadDependencies: vi.fn().mockResolvedValue({count: 0}),
    })
    return {useFlowStore: () => flowStore}
})

vi.mock("./executionTabs", () => ({
    EXECUTION_PARENT_ROUTE: "executions/update",
    EXECUTION_TAB_ROUTES: [],
}))

// The tab bar and the top bar are replaced at module level so the actions, stores and edition
// overrides they import never load: this page is exercised for the title it derives from the
// route, not for what its children render.
vi.mock("../Tabs.vue", () => ({
    default: {name: "Tabs", props: ["routeName", "tabs"], template: "<nav />"},
}))

vi.mock("./ExecutionRootTopBar.vue", () => ({
    default: {name: "ExecutionRootTopBar", props: ["routeInfo"], template: "<header />"},
}))

import ExecutionRoot from "./ExecutionRoot.vue"
import {i18nMount} from "../../../tests/unit/i18nMount"

describe("ExecutionRoot", () => {
    let originalTitle: string

    beforeAll(() => {
        originalTitle = document.title
    })

    afterAll(() => {
        document.title = originalTitle
    })

    beforeEach(() => {
        document.title = "Kestra"
        route.params = {namespace: "company.team", flowId: "demo_breadcrumb_fix", id: "exec-1"}
    })

    // The execution detail page has no heading of its own: the execution it shows is named by the
    // browser tab, which `useRouteContext` takes from the route info this page hands it.
    it("names the browser tab after the execution the route points at", () => {
        const wrapper = i18nMount(ExecutionRoot)

        expect(document.title).toBe("exec-1 | Kestra")

        wrapper.unmount()
    })
})