import {describe, test, expect} from "vitest"
import {mount} from "@vue/test-utils"
import KestraDesignSystem from "../../../src/index"
import KsMenu from "../../../src/components/Navigation/KsMenu/KsMenu.vue"
import KsMenuItem from "../../../src/components/Navigation/KsMenu/KsMenuItem.vue"

const globalConfig = {plugins: [KestraDesignSystem]}

describe("KsMenu", () => {
    test("renders menu element", () => {
        const wrapper = mount(KsMenu, {
            global: globalConfig,
        })
        expect(wrapper.find(".kel-menu").exists()).toBe(true)
    })

    test("horizontal mode applies correct class", () => {
        const wrapper = mount(KsMenu, {
            props: {mode: "horizontal"},
            global: globalConfig,
        })
        expect(wrapper.find(".kel-menu--horizontal").exists()).toBe(true)
    })

    test("renders menu items", () => {
        const wrapper = mount({
            components: {KsMenu, KsMenuItem},
            template: `
                <ks-menu default-active="flows">
                    <ks-menu-item index="flows">Flows</ks-menu-item>
                    <ks-menu-item index="executions">Executions</ks-menu-item>
                </ks-menu>
            `,
        }, {global: globalConfig})
        expect(wrapper.findAll(".kel-menu-item").length).toBe(2)
    })

    test("disabled menu item has is-disabled class", () => {
        const wrapper = mount({
            components: {KsMenu, KsMenuItem},
            template: `
                <ks-menu>
                    <ks-menu-item index="settings" disabled>Settings</ks-menu-item>
                </ks-menu>
            `,
        }, {global: globalConfig})
        expect(wrapper.find(".kel-menu-item.is-disabled").exists()).toBe(true)
    })
})
