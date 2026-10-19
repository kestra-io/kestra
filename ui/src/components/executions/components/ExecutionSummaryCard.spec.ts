import {describe, expect, it, vi} from "vitest"
import {ref} from "vue"
import ExecutionSummaryCard from "./ExecutionSummaryCard.vue"
import type {Execution} from "../../../stores/executions"
import {i18nMount} from "../../../../tests/unit/i18nMount"

const measuredHeight = ref(0)

vi.mock("@vueuse/core", async (importOriginal) => ({
    ...(await importOriginal<typeof import("@vueuse/core")>()),
    useElementSize: () => ({width: ref(0), height: measuredHeight}),
}))

const execution = {id: "execution-id", state: {current: "SUCCESS", histories: []}} as unknown as Execution

describe("ExecutionSummaryCard height", () => {
    it("shouldReportItsMeasuredHeightAndResetItToZeroOnUnmount", async () => {
        measuredHeight.value = 198
        const reported: number[] = []
        const wrapper = i18nMount(ExecutionSummaryCard, {
            props: {execution, "onUpdate:height": (height: number) => reported.push(height)},
            global: {stubs: {Banner: true}},
        })
        await wrapper.vm.$nextTick()
        expect(reported.at(-1)).toBe(198)

        measuredHeight.value = 240
        await wrapper.vm.$nextTick()
        expect(reported.at(-1)).toBe(240)

        wrapper.unmount()
        expect(reported.at(-1)).toBe(0)
    })
})
