import {describe, expect, test} from "vitest"
import {mount} from "@vue/test-utils"
import LockIcon from "vue-material-design-icons/LockOutline.vue"
import EnterpriseBadge from "./EnterpriseBadge.vue"

describe("EnterpriseBadge", () => {
    test("renders the default slot content", () => {
        const wrapper = mount(EnterpriseBadge, {slots: {default: "Audit logs"}})

        expect(wrapper.text()).toContain("Audit logs")
    })

    test("does not render the padlock icon when enable is not passed", () => {
        const wrapper = mount(EnterpriseBadge, {slots: {default: "Audit logs"}})

        // Looked up by component, not by class, so a dropped class cannot fake an absent icon.
        expect(wrapper.findComponent(LockIcon).exists()).toBe(false)
    })

    test("renders the padlock icon when enable is true", () => {
        const wrapper = mount(EnterpriseBadge, {props: {enable: true}, slots: {default: "Audit logs"}})

        expect(wrapper.findComponent(LockIcon).exists()).toBe(true)
    })

    test("gives the padlock icon the lock-ee class", () => {
        const wrapper = mount(EnterpriseBadge, {props: {enable: true}, slots: {default: "Audit logs"}})

        expect(wrapper.findComponent(LockIcon).classes()).toContain("lock-ee")
    })
})
