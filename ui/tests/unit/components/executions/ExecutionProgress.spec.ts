import {afterEach, beforeEach, describe, expect, test, vi} from "vitest"
import {flushPromises, mount} from "@vue/test-utils"
import {createPinia, setActivePinia} from "pinia"
import {createI18n} from "vue-i18n"

import ExecutionProgress from "../../../../src/components/executions/ExecutionProgress.vue"
import {useExecutionsStore} from "../../../../src/stores/executions"

const i18n = createI18n({
    legacy: false,
    locale: "en",
    messages: {
        en: {
            executionProgress: {
                title: "Execution progress",
                estimatedRemaining: "Est. remaining: {duration}",
                noBaseline: "No historical data",
            },
        },
    },
})

const globalConfig = {
    plugins: [i18n],
    stubs: {
        KsProgress: {name: "KsProgress", props: ["percentage"], template: "<div />"},
        KsText: {name: "KsText", template: "<span><slot /></span>"},
    },
}

const START_DATE = "2026-01-01T00:00:00Z"

function mountProgress() {
    return mount(ExecutionProgress, {
        global: globalConfig,
        props: {
            execution: {
                id: "execution-id",
                namespace: "io.kestra.tests",
                flowId: "flow",
                state: {current: "RUNNING", startDate: START_DATE},
            } as any,
        },
    })
}

/** Freezes the clock `elapsedMs` after the execution started. */
function elapsed(elapsedMs: number) {
    vi.setSystemTime(new Date(new Date(START_DATE).getTime() + elapsedMs))
}

describe("ExecutionProgress", () => {
    beforeEach(() => {
        setActivePinia(createPinia())
        vi.useFakeTimers()
    })

    afterEach(() => {
        vi.useRealTimers()
    })

    test("shows the elapsed share of the average duration and the remaining time", async () => {
        const store = useExecutionsStore()
        store.loadFlowAverageDuration = vi.fn().mockResolvedValue({avgDurationMs: 100_000, count: 4})

        elapsed(25_000)
        const wrapper = mountProgress()
        await flushPromises()

        expect(store.loadFlowAverageDuration).toHaveBeenCalledWith({namespace: "io.kestra.tests", flowId: "flow"})
        expect(wrapper.findComponent({name: "KsProgress"}).props("percentage")).toBe(25)
        expect(wrapper.text()).toContain("Est. remaining:")
    })

    test("caps the bar below completion when the execution outlives its average duration", async () => {
        const store = useExecutionsStore()
        store.loadFlowAverageDuration = vi.fn().mockResolvedValue({avgDurationMs: 10_000, count: 4})

        elapsed(60_000)
        const wrapper = mountProgress()
        await flushPromises()

        expect(wrapper.findComponent({name: "KsProgress"}).props("percentage")).toBe(99)
    })

    test("stays indeterminate and says so when the flow has no execution history", async () => {
        const store = useExecutionsStore()
        store.loadFlowAverageDuration = vi.fn().mockResolvedValue({avgDurationMs: null, count: 0})

        elapsed(25_000)
        const wrapper = mountProgress()
        await flushPromises()

        expect(wrapper.findComponent({name: "KsProgress"}).props("percentage")).toBe(0)
        expect(wrapper.text()).toContain("No historical data")
    })

    test("stays indeterminate when the baseline request fails", async () => {
        const store = useExecutionsStore()
        store.loadFlowAverageDuration = vi.fn().mockRejectedValue(new Error("boom"))

        elapsed(25_000)
        const wrapper = mountProgress()
        await flushPromises()

        expect(wrapper.findComponent({name: "KsProgress"}).props("percentage")).toBe(0)
        expect(wrapper.text()).toContain("No historical data")
    })
})
