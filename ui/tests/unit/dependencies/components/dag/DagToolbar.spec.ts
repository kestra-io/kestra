import {describe, it, expect} from "vitest"
import {i18nMount} from "../../../i18nMount"
import DagToolbar from "../../../../../src/components/dependencies/components/dag/DagToolbar.vue"
import en from "../../../../../src/translations/en.json"

describe("DagToolbar.vue — relation-kind legend", () => {
    const mountToolbar = (relationKinds: Set<string> | undefined, layoutMode: "force" | "dag" = "dag") => i18nMount(DagToolbar, {
        locales: en,
        props: {
            nodes: [],
            groupFields: [],
            groupChips: [],
            relationKinds,
            layoutMode,
            groupField: "",
        },
        global: {stubs: {KsTabs: true, KsTabPane: true, KsSelect: true, KsOption: true, GroupPicker: true}},
    })

    it("shows one swatch per relation kind present, labeled and colored", () => {
        const wrapper = mountToolbar(new Set(["PRODUCES", "UPSTREAM_OF"]))

        const items = wrapper.findAll(".legend-item")
        expect(items).toHaveLength(2)
        expect(items[0].text()).toBe("Produces")
        expect(items[1].text()).toBe("Upstream of")
        expect(items[0].find(".swatch").attributes("style")).toContain("--ks-dependencies-edge-produces")
    })

    it("hides the legend when no edge carries a relation kind", () => {
        const wrapper = mountToolbar(new Set())
        expect(wrapper.find(".legend").exists()).toBe(false)
    })

    it("shows the legend in the force layout too", () => {
        const wrapper = mountToolbar(new Set(["PRODUCES"]), "force")
        expect(wrapper.find(".legend").exists()).toBe(true)
    })
})
