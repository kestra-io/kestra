import {beforeEach, describe, expect, it, vi} from "vitest"
import {ref} from "vue"
import {flushPromises} from "@vue/test-utils"
import ExecutionSummaryCard from "./ExecutionSummaryCard.vue"
import type {Execution} from "../../../stores/executions"
import {i18nMount} from "../../../../tests/unit/i18nMount"

const measuredHeight = ref(0)
const isMobile = ref(false)

vi.mock("../../../composables/useIsMobile", () => ({useIsMobile: () => isMobile}))

vi.mock("@vueuse/core", async (importOriginal) => ({
    ...(await importOriginal<typeof import("@vueuse/core")>()),
    useElementSize: () => ({width: ref(0), height: measuredHeight}),
}))

vi.mock("@kestra-io/kestra-sdk/executions", () => ({
    executionAverageDuration: vi.fn().mockResolvedValue({avgDurationMs: 60_000}),
}))

const execution = {id: "execution-id", state: {current: "SUCCESS", histories: []}} as unknown as Execution

describe("ExecutionSummaryCard", () => {
    beforeEach(() => {
        isMobile.value = false
    })

    it("shouldReportItsMeasuredHeightAndResetItToZeroOnUnmount", async () => {
        measuredHeight.value = 198
        const reported: number[] = []
        const wrapper = i18nMount(ExecutionSummaryCard, {
            props: {execution, "onUpdate:height": (height: number) => reported.push(height)},
            global: {stubs: {Banner: true, ExecutionProgress: true}},
        })
        await wrapper.vm.$nextTick()
        expect(reported.at(-1)).toBe(198)

        measuredHeight.value = 240
        await wrapper.vm.$nextTick()
        expect(reported.at(-1)).toBe(240)

        wrapper.unmount()
        expect(reported.at(-1)).toBe(0)
    })

    it.each([
        {mobile: false, rendered: "banner-stub", absent: "mobile-summary-stub"},
        {mobile: true, rendered: "mobile-summary-stub", absent: "banner-stub"},
    ])("shouldRenderTheCompactSummaryOnlyOnMobile (mobile: $mobile)", ({mobile, rendered, absent}) => {
        isMobile.value = mobile
        const wrapper = i18nMount(ExecutionSummaryCard, {
            props: {execution},
            global: {
                stubs: {
                    Banner: {template: "<div data-test='banner-stub' />"},
                    MobileSummary: {template: "<div data-test='mobile-summary-stub' />"},
                    ExecutionProgress: true,
                },
            },
        })

        expect(wrapper.find(`[data-test='${rendered}']`).exists()).toBe(true)
        expect(wrapper.find(`[data-test='${absent}']`).exists()).toBe(false)
    })

    it.each([
        {mobile: false, mode: "desktop"},
        {mobile: true, mode: "mobile"},
    ])("should render execution progress for running executions on $mode", async ({mobile}) => {
        isMobile.value = mobile
        const runningExecution = {
            id: "exec-1",
            namespace: "company.team",
            flowId: "my-flow",
            state: {current: "RUNNING", startDate: new Date().toISOString(), histories: []},
        } as unknown as Execution

        const wrapper = i18nMount(ExecutionSummaryCard, {
            props: {execution: runningExecution},
            global: {
                stubs: {
                    Banner: true,
                    MobileSummary: {template: "<div><slot /></div>"},
                    KsProgress: true,
                },
            },
        })
        await flushPromises()

        expect(wrapper.find("[data-test='execution-progress']").exists()).toBe(true)
    })

    it("should not render execution progress when execution is completed", async () => {
        const completedExecution = {
            id: "exec-1",
            namespace: "company.team",
            flowId: "my-flow",
            state: {current: "SUCCESS", startDate: new Date().toISOString(), histories: []},
        } as unknown as Execution

        const wrapper = i18nMount(ExecutionSummaryCard, {
            props: {execution: completedExecution},
            global: {
                stubs: {
                    Banner: true,
                    MobileSummary: {template: "<div><slot /></div>"},
                    KsProgress: true,
                },
            },
        })
        await flushPromises()

        expect(wrapper.find("[data-test='execution-progress']").exists()).toBe(false)
    })
})
