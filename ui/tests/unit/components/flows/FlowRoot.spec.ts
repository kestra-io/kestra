import {describe, it, expect, vi} from "vitest"

// getTabs only references the tab components, so stub them instead of loading their dependency trees.
vi.mock("../../../../src/components/flows/Topology.vue", () => ({default: {}}))
vi.mock("../../../../src/components/flows/FlowRevisions.vue", () => ({default: {}}))
vi.mock("../../../../src/components/logs/LogsWrapper.vue", () => ({default: {}}))
vi.mock("../../../../src/components/flows/FlowExecutions.vue", () => ({default: {}}))
vi.mock("../../../../src/components/Tabs.vue", () => ({default: {}}))
vi.mock("../../../../src/components/flows/Overview.vue", () => ({default: {}}))
vi.mock("../../../../src/components/dependencies/Dependencies.vue", () => ({default: {}}))
vi.mock("../../../../src/components/flows/FlowMetrics.vue", () => ({default: {}}))
vi.mock("../../../../src/components/flows/MultiPanelFlowEditorView.vue", () => ({default: {}}))
vi.mock("../../../../src/components/flows/FlowTriggers.vue", () => ({default: {}}))
vi.mock("../../../../src/components/flows/FlowRootTopBar.vue", () => ({default: {}}))
vi.mock("../../../../src/components/flows/FlowConcurrency.vue", () => ({default: {}}))
vi.mock("../../../../src/components/demo/AuditLogs.vue", () => ({default: {}}))

import FlowRoot from "../../../../src/components/flows/FlowRoot.vue"

function tabNames(configs: Record<string, any> | undefined) {
    const context = {
        user: {hasAny: () => true, isAllowed: () => true},
        flowStore: {flow: {namespace: "io.kestra.tests", id: "flow"}, expandedSubflows: []},
        miscStore: {configs},
        dependenciesCount: 0,
        $route: {params: {}, query: {}},
        $t: (key: string) => key,
    }
    return (FlowRoot as any).methods.getTabs.call(context).map((tab: {name?: string}) => tab.name)
}

describe("FlowRoot tabs", () => {
    it("shows the Concurrency tab when the concurrency view is enabled", () => {
        expect(tabNames({isConcurrencyViewEnabled: true})).toContain("concurrency")
    })

    it("hides the Concurrency tab when the backend cannot read concurrency limits", () => {
        // Kafka: KafkaConcurrencyLimitService.findById is unsupported, so the tab could only show an error.
        expect(tabNames({isConcurrencyViewEnabled: false})).not.toContain("concurrency")
    })

    it("keeps the Concurrency tab while configs are not loaded", () => {
        expect(tabNames(undefined)).toContain("concurrency")
    })
})
