import {describe, it, expect, beforeEach, vi} from "vitest"
import {flushPromises, mount} from "@vue/test-utils"

let mockFlow: Record<string, any> | undefined
vi.mock("../../../../src/stores/flow", () => ({
    useFlowStore: () => ({
        get flow() {
            return mockFlow
        },
    }),
}))

const getMock = vi.fn()
vi.mock("../../../../src/utils/axios", () => ({
    useAxios: () => ({
        get: getMock,
    }),
}))

vi.mock("override/utils/route", () => ({apiUrl: () => "/api/v1/main"}))

vi.mock("@kestra-io/ui-libs", () => ({
    Status: {template: "<span />"},
}))

vi.mock("../../../../src/components/executions/Executions.vue", () => ({
    default: {template: "<div class=\"executions\" />"},
}))

import FlowConcurrency from "../../../../src/components/flows/FlowConcurrency.vue"

const stubs = {
    Empty: {props: ["type"], template: "<div class=\"empty\" :data-type=\"type\" />"},
    ElCard: {template: "<div><slot /></div>"},
    ElAlert: {props: ["type"], template: "<div class=\"alert\" :data-type=\"type\"><slot /></div>"},
    ElProgress: {template: "<div />"},
    ElIcon: {template: "<span><slot /></span>"},
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
        getMock.mockReset()
    })

    it("reads the flow-scoped record instead of the instance-wide search", async () => {
        mockFlow = {namespace: "io.kestra.tests", id: "flow", concurrency: {limit: 2, behavior: "QUEUE"}}
        getMock.mockResolvedValue({data: {tenantId: "main", namespace: "io.kestra.tests", flowId: "flow", running: 1}})

        const wrapper = await mountComponent()

        expect(wrapper.text()).toContain("1/2")
        // The regression this endpoint fixes: /concurrency-limit/search is superadmin-only in
        // EE, so every other user got a 403 and an unreadable Concurrency tab.
        expect(getMock).toHaveBeenCalledWith(
            "/api/v1/main/concurrency-limit/io.kestra.tests/flow",
            {ignoreNotFound: true, showMessageOnError: false},
        )
    })

    it("keeps the empty state when the flow has a limit but no record yet", async () => {
        mockFlow = {namespace: "io.kestra.tests", id: "flow", concurrency: {limit: 1, behavior: "CANCEL"}}
        getMock.mockRejectedValue({status: 404, response: {status: 404}})

        const wrapper = await mountComponent()

        expect(wrapper.find(".empty").attributes("data-type")).toBe("concurrency_executions")
        expect(wrapper.find(".alert").exists()).toBe(false)
    })

    it("shows an error when the request fails for a reason other than a missing record", async () => {
        mockFlow = {namespace: "io.kestra.tests", id: "flow", concurrency: {limit: 1, behavior: "CANCEL"}}
        getMock.mockRejectedValue({status: 500, response: {status: 500}})

        const wrapper = await mountComponent()

        expect(wrapper.find(".alert").attributes("data-type")).toBe("error")
    })
})
