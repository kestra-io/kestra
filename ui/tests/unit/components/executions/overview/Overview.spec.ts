import {afterAll, describe, expect, it, vi} from "vitest"
import {createPinia, setActivePinia} from "pinia"
import {nextTick} from "vue"
import type {FlowGraph} from "@kestra-io/topology/vue-flow-utils"
import {i18nShallowMount} from "../../../i18nMount"
import {useExecutionsStore, type Execution} from "../../../../../src/stores/executions"
import Overview from "../../../../../src/components/executions/overview/Overview.vue"

vi.mock("vue-router", () => ({
    useRoute: () => ({query: {}, params: {id: "execution-id"}, name: "executions/update"}),
    useRouter: () => ({push: vi.fn(), resolve: vi.fn(() => ({href: ""})), currentRoute: {value: {params: {}}}}),
}))

function buildExecution(taskRunList: Execution["taskRunList"]): Execution {
    return {
        id: "execution-id",
        originalId: "execution-id",
        namespace: "io.kestra.tests",
        flowId: "flow",
        flowRevision: 1,
        labels: [],
        taskRunList,
        metadata: {
            originalCreatedDate: "2026-01-01T00:00:00Z",
            attemptNumber: 1,
        },
        state: {
            current: "RUNNING",
            histories: [],
            getStartDate: "2026-01-01T00:00:00Z",
            getEndDate: "",
            getDuration: "PT1S",
        },
    } as Execution
}

// Importing Overview.vue statically imports Logs.vue, which imports useLogDisplay.ts, and that
// module writes its font-size defaults to localStorage as an import-time side effect.
afterAll(() => localStorage.clear())

function mountOverview(taskRunList: Execution["taskRunList"]) {
    setActivePinia(createPinia())
    const store = useExecutionsStore()
    store.execution = buildExecution(taskRunList)
    const wrapper = i18nShallowMount(Overview)
    return {wrapper, store}
}

describe("Overview", () => {
    it("shows the topology panel once the graph is ready, even with no task runs yet", async () => {
        const {wrapper, store} = mountOverview([])

        store.flowGraph = {nodes: [], clusters: [], edges: []} as FlowGraph
        await nextTick()

        expect(wrapper.find("[data-test='topology-panel']").isVisible()).toBe(true)
        expect(wrapper.find("[data-test='chart-empty']").exists()).toBe(false)
    })

    it("stops loading once the graph fetch fails, instead of leaving the skeleton up forever", async () => {
        const {wrapper, store} = mountOverview([
            {id: "tr1", taskId: "task1", state: {current: "RUNNING"}},
        ] as Execution["taskRunList"])

        expect(wrapper.find("[data-test='chart-loading']").exists()).toBe(true)

        store.flowGraphError = true
        await nextTick()

        expect(wrapper.find("[data-test='chart-loading']").exists()).toBe(false)
        expect(wrapper.find("[data-test='topology-panel']").isVisible()).toBe(true)
    })
})
