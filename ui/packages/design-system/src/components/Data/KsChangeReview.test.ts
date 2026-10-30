import {describe, test, expect} from "vitest"
import {mount} from "@vue/test-utils"
import KestraDesignSystem from "../../index"
import KsChangeReview, {type KsChangeReviewSegment, type KsChangeReviewTile} from "./KsChangeReview.vue"

const globalConfig = {plugins: [KestraDesignSystem]}

const tiles: KsChangeReviewTile[] = [
    {glyph: "+", count: 3, label: "to add", hint: "2 new, 1 replaced", tone: "success"},
    {glyph: "~", count: 0, label: "to change", tone: "warning"},
]

const segments: KsChangeReviewSegment[] = [
    {value: 2, label: "create", tone: "success"},
    {value: 0, label: "update", tone: "warning"},
    {value: 18, label: "unchanged", tone: "neutral"},
]

function mountReview(tab = "resources") {
    return mount(KsChangeReview, {
        props: {
            heading: "Terraform plan",
            meta: ["Terraform 1.9.8"],
            tiles,
            segments,
            total: "20 resources in the plan",
            tabs: [{name: "resources", label: "Resources", count: 2}, {name: "outputs", label: "Outputs"}],
            tab,
        },
        slots: {
            resources: "<p data-test=\"resources\">resources</p>",
            outputs: "<p data-test=\"outputs\">outputs</p>",
        },
        global: globalConfig,
    })
}

describe("KsChangeReview", () => {
    test("greys out a tile with nothing in it", () => {
        const rendered = mountReview().findAll(".ks-change-review__tile")

        expect(rendered[0].classes()).not.toContain("ks-change-review__tile--zero")
        expect(rendered[0].text()).toContain("2 new, 1 replaced")
        expect(rendered[1].classes()).toContain("ks-change-review__tile--zero")
    })

    test("leaves the empty segments out of the bar and its legend", () => {
        const wrapper = mountReview()

        expect(wrapper.findAll(".ks-change-review__bar span")).toHaveLength(2)
        expect(wrapper.find(".ks-change-review__legend").text()).not.toContain("update")
        expect(wrapper.find(".ks-change-review__legend").text()).toContain("20 resources in the plan")
    })

    test("shows only the slot of the selected tab, with the tab counts in the labels", () => {
        const wrapper = mountReview("outputs")

        expect(wrapper.find("[data-test='outputs']").exists()).toBe(true)
        expect(wrapper.find("[data-test='resources']").exists()).toBe(false)
        expect(wrapper.find(".ks-change-review__tabs").text()).toContain("Resources 2")
    })
})
