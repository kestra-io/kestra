import {describe, it, expect, beforeEach, vi} from "vitest"
import {flushPromises, mount} from "@vue/test-utils"

let mockFlow: Record<string, any> | undefined
vi.mock("../../../src/stores/flow", () => ({
    useFlowStore: () => ({
        get flow() {
            return mockFlow
        },
    }),
}))

let mockLimits: Record<string, any>[] = []
vi.mock("@kestra-io/kestra-sdk", () => ({
    useClient: () => ({
        get: vi.fn(async () => ({data: {results: mockLimits}})),
    }),
}))

vi.mock("override/utils/route", () => ({apiUrl: () => "/api/v1/main"}))

vi.mock("@kestra-io/design-system", () => ({
    KsExecutionStatus: {template: "<span />"},
}))

vi.mock("../../../src/components/executions/Executions.vue", () => ({
    default: {template: "<div class=\"executions\" />"},
}))

import FlowConcurrency from "../../../src/components/flows/FlowConcurrency.vue"

const stubs = {
    Empty: {props: ["type"], template: "<div class=\"empty\" :data-type=\"type\" />"},
    KsCard: {template: "<div><slot /></div>"},
    KsAlert: {props: ["type", "title"], template: "<div class=\"alert\" :data-type=\"type\"><slot /></div>"},
    KsProgress: {template: "<div />"},
    KsIcon: {template: "<span><slot /></span>"},
}

async function mountComponent() {
    const wrapper = mount(FlowConcurrency, {
        global: {
            stubs,
            mocks: {$t: (key: string) => key},
        },
    })
    await flushPromises()
    return wrapper
}

describe("FlowConcurrency", () => {
    beforeEach(() => {
        mockFlow = undefined
        mockLimits = []
    })

    it("shows the running ratio when the flow declares a concurrency block", async () => {
        mockFlow = {namespace: "io.kestra.tests", id: "flow", concurrency: {limit: 2, behavior: "QUEUE"}}
        mockLimits = [{tenantId: "main", namespace: "io.kestra.tests", flowId: "flow", running: 1}]

        const wrapper = await mountComponent()

        expect(wrapper.find("[data-test=\"concurrency-limit\"]").exists()).toBe(true)
        expect(wrapper.text()).toContain("1/2")
        expect(wrapper.find("[data-test=\"concurrency-stale-limit\"]").exists()).toBe(false)
    })

    it("surfaces a leftover limit still holding slots for a flow without a concurrency block", async () => {
        // The reported symptom of kestra-ee#9200: the stuck counter was only visible to a
        // superadmin because this tab used to render nothing without a concurrency block.
        mockFlow = {namespace: "io.kestra.tests", id: "flow"}
        mockLimits = [{tenantId: "main", namespace: "io.kestra.tests", flowId: "flow", running: 1}]

        const wrapper = await mountComponent()

        expect(wrapper.find("[data-test=\"concurrency-stale-limit\"]").exists()).toBe(true)
        expect(wrapper.text()).toContain("flowConcurrency.staleLimit.message")
    })

    it("stays quiet when a leftover limit holds no slot", async () => {
        mockFlow = {namespace: "io.kestra.tests", id: "flow"}
        mockLimits = [{tenantId: "main", namespace: "io.kestra.tests", flowId: "flow", running: 0}]

        const wrapper = await mountComponent()

        expect(wrapper.find("[data-test=\"concurrency-stale-limit\"]").exists()).toBe(false)
        expect(wrapper.find(".empty").attributes("data-type")).toBe("concurrency_limit")
    })

    it("keeps the empty state when the flow has a limit but no record yet", async () => {
        mockFlow = {namespace: "io.kestra.tests", id: "flow", concurrency: {limit: 1, behavior: "CANCEL"}}
        mockLimits = []

        const wrapper = await mountComponent()

        expect(wrapper.find(".empty").attributes("data-type")).toBe("concurrency_executions")
    })
})
