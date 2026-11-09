import {describe, it, expect, vi, beforeEach} from "vitest"
import BackfillBanner from "./BackfillBanner.vue"
import {i18nMount} from "../../../tests/unit/i18nMount"

const confirm = vi.hoisted(() => vi.fn())

vi.mock("../../utils/toast", () => ({
    useToast: () => ({confirm}),
}))

function mountBanner() {
    return i18nMount(BackfillBanner, {
        props: {row: {triggerId: "daily", backfill: {start: "2026-01-01T00:00:00Z", end: "2026-01-31T00:00:00Z"}}},
        messages: {"delete backfill confirm": "Delete the backfill of trigger {id}?"},
    })
}

describe("BackfillBanner", () => {
    beforeEach(() => confirm.mockReset())

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
