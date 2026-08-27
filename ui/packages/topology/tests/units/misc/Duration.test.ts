import {describe, expect, it, beforeEach, afterEach, vi} from "vitest"
import {mount} from "@vue/test-utils"
import {createI18n} from "vue-i18n"
import Duration from "../../../src/misc/Duration.vue"
import {TIMEZONE_STORAGE_KEY} from "../../../src/utils/utils"

const i18n = createI18n({
    legacy: false,
    locale: "en",
    // Every other key intentionally has no message so tests can assert on the raw key as fallback
    // text. These two are the exception: they need real interpolation, to prove two different
    // subjects produce two different aria-labels, and that the attempt count is not concatenated.
    messages: {
        en: {
            state_history: {
                aria_open_for: "Show the state history for {subject}",
                attempt_count: "{count} attempts",
            },
        },
    },
    missingWarn: false,
    fallbackWarn: false,
})

function mountDuration(
    histories: {date: string | number; state: string}[],
    extraProps: {attemptCount?: number; subject?: string; interval?: number} = {},
) {
    return mount(Duration, {
        props: {histories, ...extraProps},
        global: {
            plugins: [i18n],
            stubs: {
                KsPopover: {
                    template: "<div><slot name=\"reference\" /><slot /></div>",
                },
            },
        },
    })
}

function openStateHistory(wrapper: ReturnType<typeof mountDuration>) {
    const openButton = wrapper.findAll("button").find((btn) => btn.text() === "state_history.open")
    return openButton!.trigger("click")
}

const RETRIED_HISTORY = [
    {date: 0, state: "CREATED"},
    {date: 100, state: "RUNNING"},
    {date: 1_100, state: "FAILED"},
    {date: 3_100, state: "RETRYING"},
    {date: 3_200, state: "RUNNING"},
    {date: 4_200, state: "SUCCESS"},
]

describe("Duration", () => {
    beforeEach(() => {
        localStorage.setItem(TIMEZONE_STORAGE_KEY, "UTC")
    })

    afterEach(() => {
        localStorage.removeItem(TIMEZONE_STORAGE_KEY)
        vi.useRealTimers()
    })

    it("should disable the trigger and show a dash when there is no history", () => {
        const wrapper = mountDuration([])

        const trigger = wrapper.find("button.ks-duration-value")
        expect(trigger.text()).toBe("—")
        expect(trigger.attributes("disabled")).toBeDefined()
    })

    it("should show the queued/running split in the tier-1 card for a simple run", () => {
        const wrapper = mountDuration([
            {date: "2026-08-07T15:36:15.804Z", state: "CREATED"},
            {date: "2026-08-07T15:36:16.054Z", state: "RUNNING"},
            {date: "2026-08-07T15:37:27.776Z", state: "SUCCESS"},
        ])

        expect(wrapper.find(".duration-total").text()).toContain("1m 11.97s")

        const rows = wrapper.findAll(".split-row")
        expect(rows).toHaveLength(2)
        expect(rows[0].text()).toContain("250ms")
        expect(rows[0].text()).toContain("0.3%")
        expect(rows[1].text()).toContain("1m 11.72s")
        expect(rows[1].text()).toContain("99.7%")
    })

    it("should render 'did not run' instead of a zero duration for a task that never started", () => {
        const wrapper = mountDuration([
            {date: "2026-08-07T15:36:15.804Z", state: "SKIPPED"},
        ])

        expect(wrapper.find(".duration-total-empty").text()).toBe("—")
        expect(wrapper.find(".duration-total-note").text()).toBe("state_history.did_not_run")
        expect(wrapper.find(".split-bar").exists()).toBe(false)
    })

    it("should group retried attempts and bill the inter-attempt wait as queued in the state history", async () => {
        const wrapper = mountDuration([
            {date: 0, state: "CREATED"},
            {date: 100, state: "RUNNING"},
            {date: 1_100, state: "FAILED"},
            {date: 3_100, state: "RETRYING"},
            {date: 3_200, state: "RUNNING"},
            {date: 4_200, state: "SUCCESS"},
        ])

        await openStateHistory(wrapper)

        const attemptHeaders = wrapper.findAll("[data-test='state-history-attempt-header']")
        expect(attemptHeaders).toHaveLength(2)

        const items = wrapper.findAll("[data-test='state-history-item']")
        expect(items).toHaveLength(6)

        const gaps = wrapper.findAll("[data-test='state-history-gap']").map((gap) => gap.text())
        // The FAILED -> RETRYING gap (inter-attempt wait) is not rendered as a connector: it is
        // absorbed by the attempt boundary, matching the mockup where attempt groups have no gap row.
        expect(gaps).toEqual(["+100ms", "+1s", "+100ms", "+1s"])
    })

    it("should insert a day separator when two consecutive entries cross midnight (in the user's timezone)", async () => {
        const beforeMidnight = Date.UTC(2026, 0, 1, 23, 59, 0)
        const afterMidnight = Date.UTC(2026, 0, 2, 0, 5, 0)

        const wrapper = mountDuration([
            {date: beforeMidnight, state: "CREATED"},
            {date: beforeMidnight + 60_000, state: "RUNNING"},
            {date: afterMidnight, state: "SUCCESS"},
        ])

        await openStateHistory(wrapper)

        const separators = wrapper.findAll("[data-test='state-history-day-separator']")
        expect(separators).toHaveLength(1)
        expect(separators[0].text()).toBe("2026-01-02")
    })

    it("should prefer the authoritative attempt count over the derived group count when they disagree", async () => {
        const wrapper = mountDuration(
            [
                {date: "2026-08-07T15:36:15.804Z", state: "CREATED"},
                {date: "2026-08-07T15:36:16.054Z", state: "RUNNING"},
                {date: "2026-08-07T15:37:27.776Z", state: "SUCCESS"},
            ],
            {attemptCount: 3},
        )

        expect(wrapper.find(".duration-total-note").text()).toBe("3 attempts")

        await openStateHistory(wrapper)

        expect(wrapper.find(".state-history-date").text()).toContain("3 attempts")
        // No RETRYING boundary exists in this history, so there is nothing to group by: rendering
        // must degrade to a flat list rather than fabricate attempt groups.
        expect(wrapper.findAll("[data-test='state-history-attempt-header']")).toHaveLength(0)
        expect(wrapper.findAll("[data-test='state-history-item']")).toHaveLength(3)
    })

    it("should not display an attempt count when the caller provides none, even if RETRYING transitions are present", async () => {
        const wrapper = mountDuration(RETRIED_HISTORY)

        // No authoritative attemptCount was supplied (e.g. the execution-level call site, which
        // has no "attempts" concept at all): the derived RETRYING count must never leak into the
        // displayed label, even though it is perfectly usable to group the state history below.
        expect(wrapper.find(".duration-total-note").exists()).toBe(false)

        await openStateHistory(wrapper)

        expect(wrapper.find(".state-history-date").text()).toBe("1970-01-01")
        const attemptHeaders = wrapper.findAll("[data-test='state-history-attempt-header']")
        // The boundaries still visually separate the groups, but carry no unowned "Attempt" claim.
        expect(attemptHeaders).toHaveLength(2)
        attemptHeaders.forEach((header) => expect(header.text()).toBe(""))
        expect(wrapper.text()).not.toContain("Attempt")
        expect(wrapper.findAll("[data-test='state-history-item']")).toHaveLength(6)
    })

    it("should render the attempt labels when the caller provides an authoritative count", async () => {
        const wrapper = mountDuration(RETRIED_HISTORY, {attemptCount: 2})

        await openStateHistory(wrapper)

        const attemptHeaders = wrapper.findAll("[data-test='state-history-attempt-header']")
        expect(attemptHeaders).toHaveLength(2)
        attemptHeaders.forEach((header) => expect(header.text()).not.toBe(""))
    })

    it("should show sub-second card totals in milliseconds, and leave the trigger label alone", () => {
        const oneMillisecond = mountDuration([
            {date: 0, state: "CREATED"},
            {date: 1, state: "SUCCESS"},
        ])
        expect(oneMillisecond.find(".duration-total").text()).toContain("1ms")
        expect(oneMillisecond.find("button.ks-duration-value").text()).not.toContain("1ms")

        const overOneSecond = mountDuration([
            {date: 0, state: "CREATED"},
            {date: 13_558, state: "SUCCESS"},
        ])
        expect(overOneSecond.find(".duration-total").text()).toContain("13.55s")
        expect(overOneSecond.find("button.ks-duration-value").text()).toContain("13.55s")
    })

    it("should use a generic aria-label by default and a subject-specific one when provided", () => {
        const withoutSubject = mountDuration([
            {date: "2026-08-07T15:36:15.804Z", state: "SUCCESS"},
        ])
        expect(withoutSubject.find("button.ks-duration-value").attributes("aria-label")).toBe("state_history.aria_open")

        const withSubject = mountDuration(
            [{date: "2026-08-07T15:36:15.804Z", state: "SUCCESS"}],
            {subject: "extract"},
        )
        expect(withSubject.find("button.ks-duration-value").attributes("aria-label")).toBe("Show the state history for extract")
    })

    it("should disambiguate a task's attempt-level trigger from its aggregate row via the subject", () => {
        // Mirrors TaskRunLine.vue, which renders one Duration for the taskRun's aggregate history
        // and another per selected attempt: both must announce a distinct aria-label.
        const aggregate = mountDuration(RETRIED_HISTORY, {subject: "flaky"})
        const attempt = mountDuration(RETRIED_HISTORY, {subject: "flaky, Attempt 2"})

        const aggregateLabel = aggregate.find("button.ks-duration-value").attributes("aria-label")
        const attemptLabel = attempt.find("button.ks-duration-value").attributes("aria-label")

        expect(aggregateLabel).toBe("Show the state history for flaky")
        expect(attemptLabel).toBe("Show the state history for flaky, Attempt 2")
        expect(attemptLabel).not.toBe(aggregateLabel)
    })

    it("should resume the live counter when a retried task starts running again", async () => {
        vi.useFakeTimers()
        vi.setSystemTime(4_000)

        const firstAttempt = [
            {date: 0, state: "CREATED"},
            {date: 1_000, state: "RUNNING"},
            {date: 2_000, state: "FAILED"},
            {date: 3_000, state: "RETRYING"},
        ]
        const wrapper = mountDuration(firstAttempt, {interval: 100})
        const label = () => wrapper.find("button.ks-duration-value").text()

        // RETRYING is not a running state, so the elapsed time is frozen at the last transition.
        expect(label()).toBe("3.00s")
        await vi.advanceTimersByTimeAsync(1_000)
        expect(label()).toBe("3.00s")

        await wrapper.setProps({histories: [...firstAttempt, {date: 5_000, state: "RUNNING"}]})
        expect(label()).toBe("5.00s")

        await vi.advanceTimersByTimeAsync(1_000)
        expect(label()).toBe("6.00s")
    })

    it("should not show a state history button when there is no history to show", () => {
        const wrapper = mountDuration([])

        const openButton = wrapper.findAll("button").find((btn) => btn.text() === "state_history.open")
        expect(openButton).toBeUndefined()
    })
})
