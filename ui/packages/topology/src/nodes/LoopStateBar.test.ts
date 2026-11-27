import {describe, expect, it, vi} from "vitest"
import LoopStateBar from "./LoopStateBar.vue"
import en from "../../../../src/translations/en.json"
import {i18nMount} from "../../../../tests/unit/i18nMount"

const mountBar = (props: InstanceType<typeof LoopStateBar>["$props"]) => i18nMount(LoopStateBar, {locales: en, props})

describe("LoopStateBar", () => {
    it("shouldRenderOneSegmentPerNonZeroState", () => {
        const wrapper = mountBar({counts: {SUCCESS: 2, FAILED: 0, SKIPPED: 1}})

        expect(wrapper.findAll("[data-test='loop-state-bar-segment']").map((segment) => segment.attributes("data-state")))
            .toEqual(["SUCCESS", "SKIPPED"])
    })

    it("shouldNotLetAClickReachTheNodeThatHostsTheBar", async () => {
        const onClick = vi.fn()
        document.body.addEventListener("click", onClick)
        const wrapper = i18nMount(LoopStateBar, {locales: en, props: {counts: {SUCCESS: 2}}, attachTo: document.body})

        await wrapper.find("[data-test='loop-state-bar']").trigger("click")

        expect(onClick).not.toHaveBeenCalled()
        document.body.removeEventListener("click", onClick)
        wrapper.unmount()
    })
})
