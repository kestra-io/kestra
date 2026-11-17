import {afterEach, beforeEach, describe, expect, it, vi} from "vitest"
import {flushPromises} from "@vue/test-utils"
import {defineComponent, h} from "vue"
import * as ExecutionsAPI from "@kestra-io/kestra-sdk/executions"

import ExecutionProgress from "./ExecutionProgress.vue"
import {i18nMount} from "../../../tests/unit/i18nMount"
import type {Execution} from "../../stores/executions"

vi.mock("@kestra-io/kestra-sdk/executions", () => ({
    executionAverageDuration: vi.fn(),
}))

const START = "2026-11-16T10:00:00.000Z"
const NOW = "2026-11-16T10:00:10.000Z"

const KsProgressStub = defineComponent({
    props: {percentage: {type: Number, required: true}},
    setup(props) {
        return () => h("div", {"data-test": "progress", "data-percentage": String(props.percentage)})
    },
})

const messages = {
    executionProgress: {
        title: "Execution progress",
        estimatedRemaining: "Est. remaining: {duration}",
        pastEstimate: "Running longer than usual",
    },
}

const averageDuration = vi.mocked(ExecutionsAPI.executionAverageDuration)

function mountProgress(startDate = START) {
    const execution = {namespace: "company.team", flowId: "my-flow", state: {startDate}} as unknown as Execution
    return i18nMount(ExecutionProgress, {
        props: {execution},
        messages,
        global: {stubs: {KsProgress: KsProgressStub}},
    })
}

async function mountWithAverage(avgDurationMs: number, startDate = START) {
    averageDuration.mockResolvedValue({avgDurationMs})
    const wrapper = mountProgress(startDate)
    await flushPromises()
    return wrapper
}

function percentage(wrapper: ReturnType<typeof mountProgress>) {
    return Number(wrapper.get("[data-test='progress']").attributes("data-percentage"))
}

describe("ExecutionProgress", () => {
    beforeEach(() => {
        vi.useFakeTimers()
        vi.setSystemTime(new Date(NOW))
    })

    afterEach(() => {
        vi.useRealTimers()
        vi.resetAllMocks()
    })

    it("should render nothing until the average duration arrives", async () => {
        let resolve!: (value: {avgDurationMs: number}) => void
        averageDuration.mockReturnValue(new Promise((r) => (resolve = r)))
        const wrapper = mountProgress()

        expect(wrapper.text()).toBe("")

        resolve({avgDurationMs: 60_000})
        await flushPromises()

        expect(wrapper.text()).toContain("Execution progress")
    })

    it("should render nothing when the flow has no execution history", async () => {
        averageDuration.mockResolvedValue({})
        const wrapper = mountProgress()
        await flushPromises()

        expect(wrapper.text()).toBe("")
    })

    it("should render nothing when the request fails", async () => {
        averageDuration.mockRejectedValue(new Error("Request failed"))
        const wrapper = mountProgress()
        await flushPromises()

        expect(wrapper.text()).toBe("")
    })

    it("should compute the percentage as elapsed over average, capped at 99", async () => {
        const wrapper = await mountWithAverage(20_000)
        expect(percentage(wrapper)).toBe(50)

        await vi.advanceTimersByTimeAsync(50_000)
        expect(percentage(wrapper)).toBe(99)
    })

    it("should clamp elapsed time to zero when the start date is in the future", async () => {
        const wrapper = await mountWithAverage(20_000, "2026-11-16T10:01:00.000Z")

        expect(percentage(wrapper)).toBe(0)
        expect(wrapper.text()).toContain("Est. remaining: 20s")
    })

    it("should show the remaining time as the average minus elapsed, counting down with the ticker", async () => {
        const wrapper = await mountWithAverage(60_000)
        expect(wrapper.text()).toContain("Est. remaining: 50s")

        await vi.advanceTimersByTimeAsync(5_000)
        expect(wrapper.text()).toContain("Est. remaining: 45s")
    })

    it("should show the estimate label while elapsed is below the average", async () => {
        const wrapper = await mountWithAverage(10_001)

        expect(wrapper.text()).toContain("Est. remaining:")
        expect(wrapper.text()).not.toContain("Running longer than usual")
    })

    it("should show the past-estimate label once elapsed reaches the average, boundary included", async () => {
        const wrapper = await mountWithAverage(10_000)

        expect(wrapper.text()).toContain("Running longer than usual")
        expect(wrapper.text()).not.toContain("Est. remaining")
    })

    it("should clear the ticker on unmount", async () => {
        const setIntervalSpy = vi.spyOn(globalThis, "setInterval")
        const clearIntervalSpy = vi.spyOn(globalThis, "clearInterval")
        const wrapper = await mountWithAverage(60_000)
        const ticker = setIntervalSpy.mock.results.at(-1)?.value
        expect(clearIntervalSpy).not.toHaveBeenCalledWith(ticker)

        wrapper.unmount()

        expect(clearIntervalSpy).toHaveBeenCalledWith(ticker)
    })
})
