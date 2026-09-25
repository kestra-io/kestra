import {describe, expect, test} from "vitest"
import {mount} from "@vue/test-utils"
import EnterpriseBadge from "../../../src/components/EnterpriseBadge.vue"

describe("EnterpriseBadge", () => {
    test("renders the default slot content", () => {
        const wrapper = mount(EnterpriseBadge, {
            slots: {default: "Audit logs"},
        })

        expect(wrapper.text()).toContain("Audit logs")
    })

    test("does not render the padlock icon when enable is not passed", () => {
        const wrapper = mount(EnterpriseBadge, {
            slots: {default: "Audit logs"},
        })

        expect(wrapper.find(".lock-ee").exists()).toBe(false)
    })

    test("renders the padlock icon when enable is true", () => {
        const wrapper = mount(EnterpriseBadge, {
            props: {enable: true},
            slots: {default: "Audit logs"},
        })

        expect(wrapper.find(".lock-ee").exists()).toBe(true)
    })

    test("icon carries the lock-ee class", () => {
        const wrapper = mount(EnterpriseBadge, {
            props: {enable: true},
            slots: {default: "Audit logs"},
        })

        expect(wrapper.find(".lock-ee").classes()).toContain("lock-ee")
    })
})