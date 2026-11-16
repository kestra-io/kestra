import {beforeEach, describe, expect, it, vi} from "vitest"
import {flushPromises} from "@vue/test-utils"
import ExecutionProgress from "./ExecutionProgress.vue"
import {i18nMount} from "../../../tests/unit/i18nMount"
import type {Execution} from "../../stores/executions"

const executionAverageDurationMock = vi.fn()

vi.mock("@kestra-io/kestra-sdk/executions", () => ({
    executionAverageDuration: (...args: unknown[]) => executionAverageDurationMock(...args),
}))

const messages = {
    executionProgress: {
        title: "Execution progress",
        estimatedRemaining: "Est. remaining: {duration}",
        pastEstimate: "Running longer than usual",
    },
}

function buildExecution(current = "RUNNING", startedMsAgo = 10000): Execution {
    return {
        id: "exec-1",
        namespace: "company.team",
        flowId: "my-flow",
        state: {
            current,
            startDate: new Date(Date.now() - startedMsAgo).toISOString(),
            histories: [],
        },
    } as unknown as Execution
}

describe("ExecutionProgress", () => {
    beforeEach(() => {
        executionAverageDurationMock.mockReset()
    })

    it("renders compact progress bar and estimate when execution is running and baseline exists", async () => {
        executionAverageDurationMock.mockResolvedValue({avgDurationMs: 60000})

        const wrapper = i18nMount(ExecutionProgress, {
            props: {execution: buildExecution("RUNNING", 15000)},
            messages,
        })
        await flushPromises()

        expect(wrapper.find("[data-test=\"execution-progress\"]").exists()).toBe(true)
        expect(wrapper.find("[data-test=\"execution-progress-title\"]").text()).toBe("Execution progress")
        expect(wrapper.find("[data-test=\"execution-progress-estimate\"]").text()).toContain("Est. remaining:")
        expect(wrapper.find(".progress-header").exists()).toBe(true)

        const progress = wrapper.findComponent({name: "KsProgress"})
        expect(progress.exists()).toBe(true)
        expect(progress.props("strokeWidth")).toBe(8)
    })

    it("renders past estimate label when execution has exceeded average duration", async () => {
        executionAverageDurationMock.mockResolvedValue({avgDurationMs: 20000})

        const wrapper = i18nMount(ExecutionProgress, {
            props: {execution: buildExecution("RUNNING", 30000)},
            messages,
        })
        await flushPromises()

        expect(wrapper.find("[data-test=\"execution-progress\"]").exists()).toBe(true)
        expect(wrapper.find("[data-test=\"execution-progress-estimate\"]").text()).toBe("Running longer than usual")
    })

    it("does not render when there is no historical average duration baseline", async () => {
        executionAverageDurationMock.mockResolvedValue({})

        const wrapper = i18nMount(ExecutionProgress, {
            props: {execution: buildExecution("RUNNING", 10000)},
            messages,
        })
        await flushPromises()

        expect(wrapper.find("[data-test=\"execution-progress\"]").exists()).toBe(false)
    })

    it("does not render when execution is not running", async () => {
        executionAverageDurationMock.mockResolvedValue({avgDurationMs: 60000})

        const wrapper = i18nMount(ExecutionProgress, {
            props: {execution: buildExecution("SUCCESS", 10000)},
            messages,
        })
        await flushPromises()

        expect(wrapper.find("[data-test=\"execution-progress\"]").exists()).toBe(false)
    })
})
