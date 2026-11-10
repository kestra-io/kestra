import {describe, it, expect, vi, beforeEach} from "vitest"
import {KsProgress} from "@kestra-io/design-system"
import BackfillBanner from "./BackfillBanner.vue"
import {i18nMount} from "../../../tests/unit/i18nMount"

const confirm = vi.hoisted(() => vi.fn())

vi.mock("../../utils/toast", () => ({
    useToast: () => ({confirm}),
}))

type Row = InstanceType<typeof BackfillBanner>["$props"]["row"]

const backfill = {
    start: "2026-01-01T00:00:00Z",
    end: "2026-01-31T00:00:00Z",
    currentDate: "2026-01-16T00:00:00Z",
    paused: false,
}

function mountBanner(row: Row = {triggerId: "daily", backfill}) {
    return i18nMount(BackfillBanner, {
        props: {row},
        messages: {"delete backfill confirm": "Delete the backfill of trigger {id}?"},
    })
}

describe("BackfillBanner", () => {
    beforeEach(() => confirm.mockReset())

    it("renders nothing without a backfill", () => {
        const wrapper = mountBanner({triggerId: "daily"})

        expect(wrapper.text()).toBe("")
        expect(wrapper.findComponent(KsProgress).exists()).toBe(false)
        expect(wrapper.find("[data-test=backfill-stop]").exists()).toBe(false)
    })

    it.each(["start", "end", "currentDate"] as const)("shows zero progress when %s is missing", (date) => {
        const wrapper = mountBanner({backfill: {...backfill, [date]: undefined}})

        expect(wrapper.getComponent(KsProgress).props("percentage")).toBe(0)
        expect(wrapper.text()).toContain("· 0%")
    })

    it.each([
        ["equal to", backfill.start],
        ["before", "2025-12-31T00:00:00Z"],
    ])("shows complete progress when end is %s start", (_, end) => {
        const wrapper = mountBanner({backfill: {...backfill, end}})

        expect(wrapper.getComponent(KsProgress).props("percentage")).toBe(100)
        expect(wrapper.text()).toContain("· 100%")
    })

    it.each([
        ["halfway through", backfill.currentDate, 50],
        ["partway through", "2026-01-11T00:00:00Z", 33],
        ["past the end", "2026-02-01T00:00:00Z", 100],
        ["before the start", "2025-12-31T00:00:00Z", 0],
    ])("shows clamped, rounded progress %s the range", (_, currentDate, percentage) => {
        const wrapper = mountBanner({backfill: {...backfill, currentDate}})

        expect(wrapper.getComponent(KsProgress).props("percentage")).toBe(percentage)
        expect(wrapper.text()).toContain(`· ${percentage}%`)
    })

    it("switches between pause and resume as the backfill state changes", async () => {
        const wrapper = mountBanner()

        expect(wrapper.find("[data-test=backfill-resume]").exists()).toBe(false)
        await wrapper.get("[data-test=backfill-pause]").trigger("click")
        expect(wrapper.emitted("pause")).toEqual([[]])
        expect(wrapper.emitted("resume")).toBeUndefined()

        await wrapper.setProps({row: {backfill: {...backfill, paused: true}}})

        expect(wrapper.find("[data-test=backfill-pause]").exists()).toBe(false)
        await wrapper.get("[data-test=backfill-resume]").trigger("click")
        expect(wrapper.emitted("resume")).toEqual([[]])
        expect(wrapper.emitted("pause")).toEqual([[]])
    })

    it("asks before deleting the backfill", async () => {
        const wrapper = mountBanner()

        await wrapper.find("[data-test=backfill-stop]").trigger("click")

        expect(confirm).toHaveBeenCalledWith("Delete the backfill of trigger daily?", expect.any(Function))
        expect(wrapper.emitted("stop")).toBeUndefined()
    })

    it("deletes the backfill once confirmed", async () => {
        const wrapper = mountBanner()

        await wrapper.find("[data-test=backfill-stop]").trigger("click")
        confirm.mock.calls[0][1]()

        expect(wrapper.emitted("stop")).toHaveLength(1)
    })
})
