import {describe, expect, it, vi} from "vitest"
import {mount, type VueWrapper} from "@vue/test-utils"
import {createI18n} from "vue-i18n"

vi.mock("@kestra-io/design-system", async (importOriginal) => {
    const actual = await importOriginal<typeof import("@kestra-io/design-system")>()
    return {
        ...actual,
        dateUtils: {dateFilter: (iso: string) => iso},
        durationUtils: {...actual.durationUtils, humanDuration: (seconds: number) => `${seconds}s`},
    }
})
import TimelineScrubber from "./TimelineScrubber.vue"
import type {TimelineExecution} from "../../../utils/executionsTimeline"
import {MIN_RANGE_MS} from "../../../composables/useTimelineRange"

const i18n = createI18n({
    legacy: false,
    globalInjection: true,
    locale: "en",
    messages: {en: {executionsTimeline: {scrubber: {label: "density"}, toolbar: {range: "{start} {end} {duration}"}}}},
})

const RANGE_START = 0
const RANGE_END = 86_400_000
const DOMAIN_START = -43_200_000
const DOMAIN_END = 129_600_000
const DOMAIN_SPAN = DOMAIN_END - DOMAIN_START
const TRACK_WIDTH = 500

const executions: TimelineExecution[] = [
    {id: "1", namespace: "company.team", flowId: "flow", state: "SUCCESS", startMs: 0.1 * RANGE_END, endMs: 0.1 * RANGE_END + 1_000},
    {id: "2", namespace: "company.team", flowId: "flow", state: "FAILED", startMs: 0.8 * RANGE_END, endMs: 0.8 * RANGE_END + 1_000},
]

function mountScrubber(overrides: {executions?: TimelineExecution[]} = {}) {
    const wrapper = mount(TimelineScrubber, {
        props: {
            domainStartMs: DOMAIN_START,
            domainEndMs: DOMAIN_END,
            rangeStartMs: RANGE_START,
            rangeEndMs: RANGE_END,
            executions,
            widthPx: TRACK_WIDTH,
            ...overrides,
        },
        global: {plugins: [i18n]},
    })

    // jsdom never lays elements out, so the track measures 0 and every pointer position would collapse onto the range start.
    wrapper.element.getBoundingClientRect = () => ({
        x: 0, y: 0, left: 0, top: 0, right: TRACK_WIDTH, bottom: 48, width: TRACK_WIDTH, height: 48, toJSON: () => ({}),
    })

    return wrapper
}

// jsdom defines PointerEvent coordinates as getter-only, so `trigger()` cannot set them post-hoc.
function drag(wrapper: VueWrapper, fromX: number, toX: number) {
    const fire = (type: string, init: PointerEventInit) =>
        wrapper.element.dispatchEvent(new PointerEvent(type, {bubbles: true, pointerId: 1, ...init}))

    fire("pointerdown", {clientX: fromX})
    fire("pointermove", {clientX: toX})
    fire("pointerup", {})
}

function lastRange(wrapper: VueWrapper): {startMs: number; endMs: number} {
    const emitted = wrapper.emitted("change")
    expect(emitted).toBeTruthy()
    return (emitted!.at(-1) as [{startMs: number; endMs: number}])[0]
}

describe("TimelineScrubber", () => {
    it("should emit the brushed slice of the context domain", () => {
        const wrapper = mountScrubber()

        drag(wrapper, 0.05 * TRACK_WIDTH, 0.2 * TRACK_WIDTH)

        const range = lastRange(wrapper)
        expect(range.startMs).toBe(Math.round(DOMAIN_START + 0.05 * DOMAIN_SPAN))
        expect(range.endMs).toBe(Math.round(DOMAIN_START + 0.2 * DOMAIN_SPAN))
    })

    it("should pan the existing window when the press lands inside the selection", () => {
        const wrapper = mountScrubber()

        drag(wrapper, 0.4 * TRACK_WIDTH, 0.45 * TRACK_WIDTH)

        const range = lastRange(wrapper)
        expect(range.endMs - range.startMs).toBe(RANGE_END - RANGE_START)
        expect(range.startMs).toBe(Math.round(RANGE_START + 0.05 * DOMAIN_SPAN))
    })

    it("should resize the window from its edge instead of starting a new one there", () => {
        const wrapper = mountScrubber()

        const startEdge = ((RANGE_START - DOMAIN_START) / DOMAIN_SPAN) * TRACK_WIDTH
        drag(wrapper, startEdge, startEdge - 0.05 * TRACK_WIDTH)

        const range = lastRange(wrapper)
        expect(range.endMs).toBe(RANGE_END)
        expect(range.startMs).toBe(Math.round(RANGE_START - 0.05 * DOMAIN_SPAN))
    })

    it("should report the bucket under the pointer while hovering", async () => {
        const wrapper = mountScrubber()

        const successAt = ((0.1 * RANGE_END - DOMAIN_START) / DOMAIN_SPAN) * TRACK_WIDTH
        wrapper.element.dispatchEvent(new PointerEvent("pointermove", {bubbles: true, pointerId: 1, clientX: successAt}))
        await wrapper.vm.$nextTick()

        expect(wrapper.find(".scrubber-hover").exists()).toBe(true)
    })

    it("should keep the selection band visible when no brush is in progress", () => {
        const wrapper = mountScrubber()

        expect(wrapper.find("[data-test=scrubber-selection]").exists()).toBe(true)
    })

    it("should widen a click to the minimum range instead of emitting an empty window", () => {
        const wrapper = mountScrubber()

        drag(wrapper, 0.95 * TRACK_WIDTH, 0.95 * TRACK_WIDTH)

        const range = lastRange(wrapper)
        expect(range.endMs - range.startMs).toBe(MIN_RANGE_MS)
    })

    it("should stack a failure under the successes it shares a bucket with instead of hiding it", () => {
        const busy: TimelineExecution[] = [
            ...Array.from({length: 5}, (_, index) => ({
                id: `ok-${index}`,
                namespace: "company.team",
                flowId: "flow",
                state: "SUCCESS",
                startMs: 0.5 * RANGE_END,
                endMs: 0.5 * RANGE_END + 1_000,
            })),
            {id: "ko", namespace: "company.team", flowId: "flow", state: "FAILED", startMs: 0.5 * RANGE_END, endMs: 0.5 * RANGE_END + 1_000},
        ]

        const wrapper = mountScrubber({executions: busy})

        expect(wrapper.findAll(".scrubber-segment").map(segment => segment.attributes("data-state"))).toEqual(["FAILED", "SUCCESS"])
    })

    it("should place each filled bar at the time it covers rather than packing them side by side", () => {
        const wrapper = mountScrubber()

        const lefts = wrapper.findAll(".scrubber-bar")
            .filter(bar => bar.findAll(".scrubber-segment").length > 0)
            .map(bar => Number.parseFloat(bar.attributes("style")!.match(/left: ([\d.]+)%/)![1]))

        expect(lefts[0]).toBeCloseTo(30, -1)
        expect(lefts[1]).toBeCloseTo(65, -1)
    })

    it("should draw a baseline across the empty stretches instead of leaving the track blank", () => {
        const wrapper = mountScrubber()

        const bars = wrapper.findAll(".scrubber-bar")
        const empty = bars.filter(bar => bar.classes("empty"))

        expect(bars.length).toBeGreaterThan(10)
        expect(empty.length).toBe(bars.length - 2)
    })

    it("should label the track with the time it spans", () => {
        const wrapper = mountScrubber()

        expect(wrapper.findAll(".scrubber-axis span")).toHaveLength(2)
    })

})
