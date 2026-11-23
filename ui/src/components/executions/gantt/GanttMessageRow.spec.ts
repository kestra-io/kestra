import {describe, expect, it} from "vitest"
import {mount} from "@vue/test-utils"
import GanttMessageRow from "./GanttMessageRow.vue"
import type {Ancestor} from "./ganttTree"

const ancestor = (key: string): Ancestor => ({key, label: key})

function mountRow(ancestors: Ancestor[], slot = "message") {
    return mount(GanttMessageRow, {props: {ancestors}, slots: {default: slot}})
}

describe("GanttMessageRow", () => {
    it("shouldSetAriaLevelToAncestorCountPlusOne", () => {
        const wrapper = mountRow([ancestor("a"), ancestor("b"), ancestor("c")])

        expect(wrapper.attributes("aria-level")).toBe("4")
    })

    it("shouldSetAriaLevelToOneWhenThereAreNoAncestors", () => {
        const wrapper = mountRow([])

        expect(wrapper.attributes("aria-level")).toBe("1")
    })

    it("shouldRenderOneGuidePerAncestor", () => {
        const wrapper = mountRow([ancestor("a"), ancestor("b")])

        expect(wrapper.findAll(".guide")).toHaveLength(2)
    })

    it("shouldRenderNoGuideWhenThereAreNoAncestors", () => {
        const wrapper = mountRow([])

        expect(wrapper.findAll(".guide")).toHaveLength(0)
    })

    it("shouldRenderTheDefaultSlotInsideTheCell", () => {
        const wrapper = mountRow([ancestor("a")], "No more iterations")

        expect(wrapper.get("[role='gridcell']").text()).toBe("No more iterations")
    })

    it("shouldExposeRowAndGridcellRoles", () => {
        const wrapper = mountRow([])

        expect(wrapper.attributes("role")).toBe("row")
        expect(wrapper.find("[role='gridcell']").exists()).toBe(true)
    })
})
