import {describe, test, expect} from "vitest"
import {mount} from "@vue/test-utils"
import KestraDesignSystem from "../../index"
import KsChangeReview, {KS_CHANGE_REVIEW_ALL, type KsChangeReviewSegment} from "./KsChangeReview.vue"

const globalConfig = {plugins: [KestraDesignSystem]}

const segments: KsChangeReviewSegment[] = [
    {name: "CREATE", glyph: "+", value: 2, label: "Create", tone: "success"},
    {name: "UPDATE", glyph: "~", value: 0, label: "Update", tone: "warning"},
    {name: "DELETE", glyph: "−", value: 1, label: "Destroy", tone: "danger"},
    {value: 18, label: "18 resources unchanged", tone: "neutral"},
]

function mountReview(tab = "resources") {
    return mount(KsChangeReview, {
        props: {
            heading: "3 changes",
            meta: ["21 resources in the plan", "Terraform 1.9.8"],
            segments,
            allLabel: "All changes",
            allCount: 3,
            filterLabel: "Filter the changes by action",
            tabs: [{name: "resources", label: "Resources", count: 3}, {name: "outputs", label: "Outputs"}],
            tab,
        },
        slots: {
            resources: "<p data-test=\"resources\">resources</p>",
            outputs: "<p data-test=\"outputs\">outputs</p>",
        },
        attachTo: document.body,
        global: globalConfig,
    })
}

function chips(wrapper: ReturnType<typeof mountReview>) {
    return wrapper.findAll(".ks-change-review__filters .kel-check-tag")
}

describe("KsChangeReview", () => {
    test("leaves the empty segments out of the bar, and offers a filter only for the named ones", () => {
        const wrapper = mountReview()

        expect(wrapper.findAll(".ks-change-review__bar span")).toHaveLength(3)
        expect(chips(wrapper).map(chip => chip.text())).toEqual(["All changes 3", "+ Create 2", "− Destroy 1"])
        expect(wrapper.find(".ks-change-review__passive").text()).toBe("18 resources unchanged")
    })

    test("filters on the segment of the chip clicked, and on everything again from the first chip", async () => {
        const wrapper = mountReview()

        expect(chips(wrapper)[0].classes()).toContain("is-checked")
        await chips(wrapper)[2].trigger("click")
        await chips(wrapper)[0].trigger("click")

        expect(wrapper.emitted("update:filter")).toEqual([["DELETE"], [KS_CHANGE_REVIEW_ALL]])
    })

    test("shows only the slot of the selected tab, with the count of a tab next to its label", () => {
        const wrapper = mountReview("outputs")

        expect(wrapper.find("[data-test='outputs']").isVisible()).toBe(true)
        expect(wrapper.find("[data-test='resources']").isVisible()).toBe(false)
        expect(wrapper.find("[role=tab][aria-selected=true]").text()).toBe("Outputs")
        expect(wrapper.findAll("[role=tab]")[0].text()).toBe("Resources 3")
    })
})
