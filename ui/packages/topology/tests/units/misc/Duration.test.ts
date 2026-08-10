import {describe, expect, it, beforeEach, afterEach} from "vitest"
import {mount} from "@vue/test-utils"
import {createI18n} from "vue-i18n"
import Duration from "../../../src/misc/Duration.vue"
import {TIMEZONE_STORAGE_KEY} from "../../../src/utils/utils"

const i18n = createI18n({
    legacy: false,
    locale: "en",
    messages: {en: {}},
    missingWarn: false,
    fallbackWarn: false,
})

function mountDuration(histories: {date: string | number; state: string}[], attemptCount?: number) {
    return mount(Duration, {
        props: {histories, attemptCount},
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

describe("Duration", () => {
    beforeEach(() => {
        localStorage.setItem(TIMEZONE_STORAGE_KEY, "UTC")
    })

    afterEach(() => {
        localStorage.removeItem(TIMEZONE_STORAGE_KEY)
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
            3,
        )

        expect(wrapper.find(".duration-total-note").text()).toContain("3")

        await openStateHistory(wrapper)

        expect(wrapper.find(".state-history-date").text()).toContain("3")
        // No RETRYING boundary exists in this history, so there is nothing to group by: rendering
        // must degrade to a flat list rather than fabricate attempt groups.
        expect(wrapper.findAll("[data-test='state-history-attempt-header']")).toHaveLength(0)
        expect(wrapper.findAll("[data-test='state-history-item']")).toHaveLength(3)
    })

    it("should not show a state history button when there is no history to show", () => {
        const wrapper = mountDuration([])

        const openButton = wrapper.findAll("button").find((btn) => btn.text() === "state_history.open")
        expect(openButton).toBeUndefined()
    })
})
