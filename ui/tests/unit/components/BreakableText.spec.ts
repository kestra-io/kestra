import {describe, expect, test} from "vitest"
import {mount} from "@vue/test-utils"
import BreakableText from "../../../src/components/BreakableText"

describe("BreakableText", () => {
    test("keeps the copied text clean (no zero-width space) — regression for #13079", () => {
        const wrapper = mount(BreakableText, {props: {value: "company.team.project"}})

        // textContent is what the browser copies to the clipboard.
        expect(wrapper.text()).toBe("company.team.project")
        expect(wrapper.text()).not.toContain("​")
    })

    test("adds a <wbr> break opportunity before each dot", () => {
        const wrapper = mount(BreakableText, {props: {value: "a.b.c"}})

        // Two dots -> two break opportunities.
        expect(wrapper.findAll("wbr")).toHaveLength(2)
    })

    test("renders a value without dots unchanged and adds no <wbr>", () => {
        const wrapper = mount(BreakableText, {props: {value: "namespace"}})

        expect(wrapper.text()).toBe("namespace")
        expect(wrapper.findAll("wbr")).toHaveLength(0)
    })

    test("handles an undefined value gracefully", () => {
        const wrapper = mount(BreakableText, {props: {value: undefined}})

        expect(wrapper.text()).toBe("")
    })
})
