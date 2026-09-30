import {afterEach, describe, expect, it} from "vitest"
import {
    BoundedFlowStore,
    CHART_NODE_THRESHOLD,
    countTaskNodes,
    resolveOverviewChart,
} from "../../../../../src/components/executions/overview/chartPreference"

const FLOW_A = {namespace: "company.team", flowId: "flow_a"}
const FLOW_B = {namespace: "company.team", flowId: "flow_b"}

afterEach(() => {
    localStorage.clear()
})

describe("resolveOverviewChart", () => {
    it("prefers a stored choice over the adaptive rule, even above the threshold", () => {
        const result = resolveOverviewChart("topology", CHART_NODE_THRESHOLD + 10)

        expect(result).toEqual({chart: "topology", adaptiveRuleFired: false})
    })

    it("defaults to gantt only once the node count exceeds the threshold", () => {
        expect(resolveOverviewChart(undefined, CHART_NODE_THRESHOLD)).toEqual({chart: "topology", adaptiveRuleFired: false})
        expect(resolveOverviewChart(undefined, CHART_NODE_THRESHOLD + 1)).toEqual({chart: "gantt", adaptiveRuleFired: true})
    })

    it("defaults to topology when there is no stored choice and the flow is small", () => {
        expect(resolveOverviewChart(undefined, 3)).toEqual({chart: "topology", adaptiveRuleFired: false})
    })
})

describe("countTaskNodes", () => {
    it("excludes the synthetic cluster root and end nodes from the graph's raw node list", () => {
        const nodes = [
            {uid: "root", type: "io.kestra.core.models.hierarchies.GraphClusterRoot"},
            {uid: "root.task1", type: "io.kestra.core.models.hierarchies.GraphTask"},
            {uid: "root.task2", type: "io.kestra.core.models.hierarchies.GraphTask"},
            {uid: "end", type: "io.kestra.core.models.hierarchies.GraphClusterEnd"},
        ]

        expect(countTaskNodes(nodes)).toBe(2)
    })

    it("returns 0 when there is no graph yet", () => {
        expect(countTaskNodes(undefined)).toBe(0)
    })
})

describe("BoundedFlowStore", () => {
    it("does not leak a stored choice between flows", () => {
        const store = new BoundedFlowStore<string>("test-chart-by-flow", 50)

        store.set(FLOW_A, "gantt")

        expect(store.get(FLOW_A)).toBe("gantt")
        expect(store.get(FLOW_B)).toBeUndefined()
    })

    it("evicts the least-recently-set flow once the capacity is exceeded", () => {
        const store = new BoundedFlowStore<string>("test-chart-by-flow-capacity", 2)

        store.set({namespace: "ns", flowId: "flow_1"}, "gantt")
        store.set({namespace: "ns", flowId: "flow_2"}, "gantt")
        store.set({namespace: "ns", flowId: "flow_3"}, "gantt")

        expect(store.get({namespace: "ns", flowId: "flow_1"})).toBeUndefined()
        expect(store.get({namespace: "ns", flowId: "flow_2"})).toBe("gantt")
        expect(store.get({namespace: "ns", flowId: "flow_3"})).toBe("gantt")
    })

    it("returns undefined instead of throwing when the stored value is corrupted", () => {
        const key = "test-chart-by-flow-corrupted"
        localStorage.setItem(key, "not json")
        const store = new BoundedFlowStore<string>(key)

        expect(store.get(FLOW_A)).toBeUndefined()
    })
})

describe("notice dismissal", () => {
    it("is independent from the stored chart choice", () => {
        const chartStore = new BoundedFlowStore<string>("test-chart-choice")
        const noticeStore = new BoundedFlowStore<boolean>("test-notice-dismissed")

        noticeStore.set(FLOW_A, true)

        expect(noticeStore.get(FLOW_A)).toBe(true)
        expect(chartStore.get(FLOW_A)).toBeUndefined()
    })
})
