import {beforeEach, describe, expect, it, vi} from "vitest"
import {flushPromises} from "@vue/test-utils"
import {KsPopover} from "@kestra-io/design-system"
import type {LoopLaneData} from "@kestra-io/topology"

const mocks = vi.hoisted(() => ({search: vi.fn()}))

vi.mock("../../utils/loopIterations", async (importOriginal) => ({
    ...(await importOriginal<typeof import("../../utils/loopIterations")>()),
    searchLoopIterations: mocks.search,
}))

import {LoopIterationError} from "../../utils/loopIterations"
import LoopIterationPicker from "./LoopIterationPicker.vue"
import {i18nMount} from "../../../tests/unit/i18nMount"
import en from "../../translations/en.json"

const lane = (overrides: Partial<LoopLaneData>): LoopLaneData => ({
    taskId: "per_quarter",
    status: "ready",
    iterationCount: 4,
    terminatedIterations: {SUCCESS: 4},
    parentScoped: true,
    ...overrides,
})

const mountPicker = (data: LoopLaneData) =>
    i18nMount(LoopIterationPicker, {locales: en, attachTo: document.body, props: {lane: data, hostExecutionId: "execution-id"}})

const iteration = (number: number, value: string, state = "SUCCESS") => ({id: `it-${number}`, number, value, state})
const page = (results: ReturnType<typeof iteration>[], total = results.length) => ({results, total})
const body = (selector: string) => [...document.body.querySelectorAll(selector)].map((element) => element.textContent?.replace(/\s+/g, " ").trim())

async function openPicker(data: LoopLaneData) {
    const wrapper = mountPicker(data)
    wrapper.findComponent(KsPopover).vm.$emit("update:visible", true)
    await flushPromises()
    return wrapper
}

describe("LoopIterationPicker", () => {
    beforeEach(() => {
        mocks.search.mockReset()
        document.body.innerHTML = ""
    })

    it("shouldListFailedIterationsFirstThenTheOthers", async () => {
        mocks.search
            .mockResolvedValueOnce(page([iteration(2, "AMER", "FAILED")]))
            .mockResolvedValueOnce(page([iteration(1, "EMEA"), iteration(3, "APAC")]))

        await openPicker(lane({terminatedIterations: {FAILED: 1, SUCCESS: 3}}))

        expect(body("[data-test='loop-picker-option']")).toEqual(expect.arrayContaining([expect.stringContaining("AMER"), expect.stringContaining("EMEA")]))
        expect(body("[data-test^='loop-picker-group-']")).toEqual(["Failed · 1", "Other iterations"])
        expect(mocks.search).toHaveBeenNthCalledWith(1, expect.objectContaining({state: "FAILED"}))
        expect(mocks.search).toHaveBeenNthCalledWith(2, expect.objectContaining({excludeState: "FAILED"}))
    })

    it("shouldOfferAllIterationsFirstAndLabelRowsWithTheirValueOnly", async () => {
        mocks.search.mockResolvedValue(page([iteration(2, "AMER")]))

        await openPicker(lane({scopedNumber: 2}))

        expect(body("[data-test='loop-picker-all']")).toEqual(["All iterations"])
        const rows = [...document.body.querySelectorAll("[data-test='loop-picker-option']")]
        expect(rows[0].querySelector("[data-test='loop-picker-number']")).toBeNull()
        expect(rows[0].querySelector("[data-test='loop-picker-value']")?.textContent).toBe("AMER")
        expect(rows[0].className).toContain("loop-picker-option-active")
        expect(rows[0].getAttribute("aria-current")).toBe("true")
        expect(document.body.querySelector("[data-test='loop-picker-all']")?.hasAttribute("aria-current")).toBe(false)
    })

    it("shouldMarkAllIterationsAsCurrentWhenNothingIsScoped", async () => {
        mocks.search.mockResolvedValue(page([iteration(2, "AMER")]))

        await openPicker(lane({}))

        expect(document.body.querySelector("[data-test='loop-picker-all']")?.getAttribute("aria-current")).toBe("true")
        expect(document.body.querySelector("[data-test='loop-picker-option']")?.hasAttribute("aria-current")).toBe(false)
    })

    it("shouldLoadMoreFailedIterationsBeyondTheFirstPage", async () => {
        const first = Array.from({length: 50}, (_, index) => iteration(index + 1, `V${index}`, "FAILED"))
        mocks.search
            .mockResolvedValueOnce(page(first, 51))
            .mockResolvedValueOnce(page([]))
            .mockResolvedValueOnce(page([iteration(60, "LAST", "FAILED")], 51))

        await openPicker(lane({terminatedIterations: {FAILED: 51}}))
        ;(document.body.querySelector("[data-test='loop-picker-more-failed']") as HTMLElement).click()
        await flushPromises()

        expect(mocks.search).toHaveBeenLastCalledWith(expect.objectContaining({state: "FAILED", page: 2}))
        expect(body("[data-test='loop-picker-option']")).toHaveLength(51)
        expect(document.body.querySelector("[data-test='loop-picker-more-failed']")).toBeNull()
    })

    it("shouldKeepTheOtherIterationsWhenLoadingMoreFailedBeforeTheyArrive", async () => {
        let resolveOthers!: (value: unknown) => void
        const first = Array.from({length: 50}, (_, index) => iteration(index + 1, `V${index}`, "FAILED"))
        mocks.search
            .mockResolvedValueOnce(page(first, 51))
            .mockReturnValueOnce(new Promise((resolve) => (resolveOthers = resolve)))
            .mockResolvedValueOnce(page([iteration(60, "LAST", "FAILED")], 51))

        await openPicker(lane({iterationCount: 52, terminatedIterations: {FAILED: 51, SUCCESS: 1}}))
        ;(document.body.querySelector("[data-test='loop-picker-more-failed']") as HTMLElement).click()
        await flushPromises()
        resolveOthers(page([iteration(61, "OK")]))
        await flushPromises()

        expect(body("[data-test^='loop-picker-group-']")).toEqual(["Failed · 51", "Other iterations"])
        expect(document.body.querySelector("[data-test='loop-picker-loading']")).toBeNull()
    })

    it("shouldShowTheEmptyStateWhenNothingMatches", async () => {
        mocks.search.mockResolvedValue(page([]))

        await openPicker(lane({}))

        expect(document.body.querySelector("[data-test='loop-picker-empty']")).not.toBeNull()
        expect(document.body.querySelector("[data-test='loop-picker-failure']")).toBeNull()
    })

    it("shouldShowAnErrorWithRetryDistinctFromEmpty", async () => {
        mocks.search.mockRejectedValueOnce(new LoopIterationError("unknown")).mockResolvedValueOnce(page([iteration(1, "EMEA")]))

        await openPicker(lane({}))

        expect(document.body.querySelector("[data-test='loop-picker-failure']")).not.toBeNull()
        expect(document.body.querySelector("[data-test='loop-picker-empty']")).toBeNull()
        ;(document.body.querySelector("[data-test='loop-picker-retry']") as HTMLElement).click()
        await flushPromises()

        expect(body("[data-test='loop-picker-option']")).toHaveLength(1)
    })

    it("shouldExplainAnInaccessibleIterationWithoutOfferingRetry", async () => {
        mocks.search.mockRejectedValue(new LoopIterationError("forbidden"))

        await openPicker(lane({}))

        expect(body("[data-test='loop-picker-failure']")[0]).toContain("do not have access")
        expect(document.body.querySelector("[data-test='loop-picker-retry']")).toBeNull()
    })

    it("shouldRenderTheLoadingStateWhileTheSearchIsPending", async () => {
        mocks.search.mockReturnValue(new Promise(() => {}))

        await openPicker(lane({}))

        expect(document.body.querySelector("[data-test='loop-picker-loading']")).not.toBeNull()
    })

    it("shouldIgnoreASupersededSearch", async () => {
        let resolveFirst!: (value: unknown) => void
        mocks.search
            .mockReturnValueOnce(new Promise((resolve) => (resolveFirst = resolve)))
            .mockResolvedValueOnce(page([iteration(1, "FRESH")]))

        const wrapper = await openPicker(lane({}))
        wrapper.findComponent(KsPopover).vm.$emit("update:visible", false)
        await flushPromises()
        wrapper.findComponent(KsPopover).vm.$emit("update:visible", true)
        await flushPromises()
        resolveFirst(page([iteration(9, "STALE")]))
        await flushPromises()

        expect(body("[data-test='loop-picker-option']").join(" ")).not.toContain("STALE")
    })

    it("shouldSearchByZeroBasedIndexOnTheMatchingPage", async () => {
        mocks.search.mockResolvedValue(page([iteration(7, "SEVEN")]))
        const wrapper = await openPicker(lane({}))
        mocks.search.mockClear()

        const input = document.body.querySelector("input") as HTMLInputElement
        input.value = "6"
        input.dispatchEvent(new Event("input"))
        await new Promise((resolve) => setTimeout(resolve, 350))
        await flushPromises()

        expect(mocks.search).toHaveBeenCalledWith(expect.objectContaining({page: 1}))
        expect(body("[data-test='loop-picker-option']")).toEqual([expect.stringContaining("SEVEN")])
        wrapper.unmount()
    })

    it("shouldNotSearchWhenTheQueryIsNotANumber", async () => {
        mocks.search.mockResolvedValue(page([iteration(1, "EMEA")]))
        await openPicker(lane({}))
        mocks.search.mockClear()

        const input = document.body.querySelector("input") as HTMLInputElement
        input.value = "EMEA"
        input.dispatchEvent(new Event("input"))
        await new Promise((resolve) => setTimeout(resolve, 350))
        await flushPromises()

        expect(mocks.search).not.toHaveBeenCalled()
        expect(document.body.querySelector("[data-test='loop-picker-empty']")).not.toBeNull()
    })

    it("shouldDisableTheNestedPickerUntilTheParentLoopIsScoped", () => {
        const wrapper = mountPicker(lane({parentScoped: false, parentTaskId: "per_region", status: "nested"}))

        expect(wrapper.find("[data-test='loop-picker-disabled']").text()).toBe("Pick per_region first")
        expect(wrapper.find("[data-test='loop-picker-disabled']").attributes("disabled")).toBeDefined()
        expect(wrapper.find("[data-test='loop-picker-trigger']").exists()).toBe(false)
    })

    it("shouldOfferAllIterationsWhileUnscoped", () => {
        const wrapper = mountPicker(lane({}))

        expect(wrapper.find("[data-test='loop-picker-trigger']").text()).toBe("All iterations")
        expect(wrapper.find("[data-test='loop-stepper']").exists()).toBe(false)
    })

    it("shouldShowTheStepperOnceScopedAndEmitTheStep", async () => {
        const wrapper = mountPicker(lane({scopedNumber: 2, scopedValue: "AMER"}))

        expect(wrapper.find("[data-test='loop-picker-trigger']").text()).toBe("AMER")
        expect(wrapper.find("[data-test='loop-stepper']").text()).toBe("")

        await wrapper.find("[data-test='loop-step-next']").trigger("click")

        expect(wrapper.emitted("step")).toEqual([[1]])
    })

    it("shouldFallBackToTheZeroBasedIndexWhenTheItemHasNoValue", () => {
        const wrapper = mountPicker(lane({scopedNumber: 3}))

        expect(wrapper.find("[data-test='loop-picker-trigger']").text()).toBe("index 2")
    })

    it("shouldDisableTheStepperAtTheBounds", () => {
        const first = mountPicker(lane({scopedNumber: 1}))
        const last = mountPicker(lane({scopedNumber: 4}))

        expect(first.find("[data-test='loop-step-previous']").attributes("disabled")).toBeDefined()
        expect(last.find("[data-test='loop-step-next']").attributes("disabled")).toBeDefined()
    })

    it("shouldDisableNextAtTheLastStartedIterationOfARunningLoop", () => {
        const wrapper = mountPicker(lane({scopedNumber: 3, iterationCount: 10, runningIterations: 1, terminatedIterations: {SUCCESS: 2}}))

        expect(wrapper.find("[data-test='loop-step-next']").attributes("disabled")).toBeDefined()
    })

    it("shouldNotSearchForANegativeIndex", async () => {
        mocks.search.mockResolvedValue(page([iteration(1, "EMEA")]))
        await openPicker(lane({}))
        mocks.search.mockClear()

        const input = document.body.querySelector("input") as HTMLInputElement
        input.value = "-1"
        input.dispatchEvent(new Event("input"))
        await new Promise((resolve) => setTimeout(resolve, 350))
        await flushPromises()

        expect(mocks.search).not.toHaveBeenCalled()
    })

    it("shouldAddTheZeroBasedIndexToIterationsSharingAValue", async () => {
        mocks.search.mockResolvedValue(page([iteration(1, "dup"), iteration(2, "dup"), iteration(3, "solo")]))

        await openPicker(lane({}))

        expect(body("[data-test='loop-picker-value']")).toEqual(["dup · index 0", "dup · index 1", "solo"])
    })

    it("shouldSearchIndexZeroOnTheFirstPageAndIndexFiftyOnTheSecond", async () => {
        mocks.search.mockResolvedValue(page([]))
        await openPicker(lane({}))

        for (const [query, expectedPage] of [["0", 1], ["50", 2]] as const) {
            mocks.search.mockClear()
            const input = document.body.querySelector("input") as HTMLInputElement
            input.value = query
            input.dispatchEvent(new Event("input"))
            await new Promise((resolve) => setTimeout(resolve, 350))
            await flushPromises()

            expect(mocks.search).toHaveBeenCalledWith(expect.objectContaining({page: expectedPage}))
        }
    })
})
