import {beforeEach, describe, expect, it, vi} from "vitest"
import {ref} from "vue"
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

const execution = {id: "execution-id", state: {current: "SUCCESS", histories: []}} as unknown as Execution

describe("ExecutionSummaryCard height", () => {
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

    it("shouldRenderExecutionProgressInsideTheCardOnDesktop", () => {
        isMobile.value = false
        const runningExecution = {id: "exec-1", state: {current: "RUNNING", histories: []}} as unknown as Execution
        const wrapper = i18nMount(ExecutionSummaryCard, {
            props: {execution: runningExecution},
            global: {
                stubs: {
                    Banner: true,
                    ExecutionProgress: {
                        props: ["execution"],
                        template: "<div data-test='execution-progress-stub' :data-state='execution.state.current' />",
                    },
                },
            },
        })

        const progress = wrapper.find("[data-test='execution-progress-stub']")
        expect(progress.exists()).toBe(true)
        expect(progress.attributes("data-state")).toBe("RUNNING")
    })

    it("shouldPassSlotContentToMobileSummary", () => {
        isMobile.value = true
        const wrapper = i18nMount(ExecutionSummaryCard, {
            props: {execution},
            slots: {
                default: "<div data-test='alert-slot'>Alert</div>",
            },
            global: {
                stubs: {
                    Banner: true,
                    ExecutionProgress: true,
                    MobileSummary: {
                        template: "<div data-test='mobile-summary-stub'><slot /></div>",
                    },
                },
            },
        })

        expect(wrapper.find("[data-test='alert-slot']").exists()).toBe(true)
    })
})
