import {describe, expect, it, vi} from "vitest"
import {KsCheckTag, KsSelect} from "@kestra-io/design-system"

import {i18nMount} from "../../../../../tests/unit/i18nMount"
import ExecutionPanel from "./ExecutionPanel.vue"

function mountPanel(props: Partial<InstanceType<typeof ExecutionPanel>["$props"]> = {}) {
    return i18nMount(ExecutionPanel, {
        props: {
            states: [],
            namespaceOptions: ["company.team"],
            includeSub: false,
            toggleState: vi.fn(),
            ...props,
        },
    })
}

describe("ExecutionPanel", () => {
    it("renders one pill per watchable state, in order", () => {
        const wrapper = mountPanel()
        const pills = wrapper.findAllComponents(KsCheckTag)

        expect(pills.map((pill) => pill.text().trim())).toEqual([
            "FAILED",
            "WARNING",
            "SUCCESS",
            "KILLED",
            "PAUSED",
        ])
    })

    it("marks a pill as checked exactly when its state is in states", () => {
        const wrapper = mountPanel({states: ["FAILED", "SUCCESS"]})
        const pills = wrapper.findAllComponents(KsCheckTag)

        expect(pills.map((pill) => pill.props("checked"))).toEqual([
            true,
            false,
            true,
            false,
            false,
        ])
    })

    it("calls toggleState with the state name when a pill is clicked", async () => {
        const toggleState = vi.fn()
        const wrapper = mountPanel({toggleState})
        const pills = wrapper.findAllComponents(KsCheckTag)

        await pills[0].trigger("click")
        expect(toggleState).toHaveBeenCalledWith("FAILED")

        await pills[1].trigger("click")
        expect(toggleState).toHaveBeenCalledWith("WARNING")
    })

    it("shows the error hint when states is empty", () => {
        const wrapper = mountPanel({states: []})

        expect(wrapper.find(".hint-error").exists()).toBe(true)
        expect(wrapper.find(".hint-reco").exists()).toBe(false)
    })

    it("shows the recommendation hint when states contains FAILED", () => {
        const wrapper = mountPanel({states: ["FAILED"]})

        expect(wrapper.find(".hint-reco").exists()).toBe(true)
        expect(wrapper.find(".hint-error").exists()).toBe(false)
    })

    it("shows neither hint when states holds other states only", () => {
        const wrapper = mountPanel({states: ["SUCCESS", "WARNING"]})

        expect(wrapper.find(".hint-error").exists()).toBe(false)
        expect(wrapper.find(".hint-reco").exists()).toBe(false)
    })

    it("switches the include-sub hint text with the checkbox, and defaults namespacesLoading to false", async () => {
        const wrapper = mountPanel({includeSub: false})

        expect(wrapper.props("namespacesLoading")).toBe(false)
        expect(wrapper.findComponent(KsSelect).props("loading")).toBe(false)

        const hint = wrapper.find(".hint")
        expect(hint.text()).toContain("recipe.execution.include_sub_hint_off")

        await wrapper.setProps({includeSub: true})
        expect(hint.text()).toContain("recipe.execution.include_sub_hint_on")
    })
})

