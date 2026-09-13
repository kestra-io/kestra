import {beforeEach, describe, expect, test} from "vitest"
import {mount} from "@vue/test-utils"
import {createPinia, setActivePinia} from "pinia"
import {createI18n} from "vue-i18n"
import KestraDesignSystem from "@kestra-io/design-system"
import MultiPanelTabs from "../../../src/components/MultiPanelTabs.vue"

const IconStub = {template: "<span class='icon-stub' />"}
const PaneStub = {name: "PaneStub", template: "<div class='pane-stub' />"}

function makePanel(uid: string) {
    const tab = {uid, button: {label: uid, icon: IconStub}, component: PaneStub}
    return {tabs: [tab], activeTab: tab, size: 50}
}

const globalConfig = {
    plugins: [
        createI18n({legacy: false, locale: "en", fallbackWarn: false, missingWarn: false}),
        KestraDesignSystem,
    ],
}

function mountTabs() {
    return mount(MultiPanelTabs, {
        global: globalConfig,
        props: {modelValue: [makePanel("a"), makePanel("b")]},
    })
}

describe("MultiPanelTabs maximize", () => {
    beforeEach(() => setActivePinia(createPinia()))

    test("shows a maximize toggle with an accessible label on every panel", () => {
        const wrapper = mountTabs()
        const buttons = wrapper.findAll("[data-test='panel-maximize']")

        expect(buttons.length).toBe(2)
        expect(buttons[0].attributes("aria-label")).toBeTruthy()
    })

    test("maximizing a panel renders only that panel and hides the others", async () => {
        const wrapper = mountTabs()
        expect(wrapper.findAll(".content-panel").length).toBe(2)

        await wrapper.findAll("[data-test='panel-maximize']")[0].trigger("click")

        expect(wrapper.findAll(".content-panel").length).toBe(1)
        expect(wrapper.find("[data-test='panel-maximize']").attributes("aria-pressed")).toBe("true")
    })

    test("toggling the same panel restores all panels", async () => {
        const wrapper = mountTabs()
        const maximize = () => wrapper.find("[data-test='panel-maximize']")

        await maximize().trigger("click")
        expect(wrapper.findAll(".content-panel").length).toBe(1)

        await maximize().trigger("click")
        expect(wrapper.findAll(".content-panel").length).toBe(2)
        expect(maximize().attributes("aria-pressed")).toBe("false")
    })

    test("closing the maximized panel's last tab drops maximize instead of moving it to another panel", async () => {
        const wrapper = mount(MultiPanelTabs, {
            global: globalConfig,
            props: {modelValue: [makePanel("a"), makePanel("b"), makePanel("c")]},
        })

        // Maximize the middle panel
        await wrapper.findAll("[data-test='panel-maximize']")[1].trigger("click")
        expect(wrapper.findAll(".content-panel").length).toBe(1)

        // Empty that panel's tabs — the zero-tab watch removes it and indices shift
        const vm = wrapper.vm as unknown as {panels: {tabs: unknown[]}[]}
        vm.panels[1].tabs.splice(0)
        await wrapper.vm.$nextTick()
        await wrapper.vm.$nextTick()

        // Maximize must be dropped, not silently reassigned to the shifted panel
        expect(wrapper.findAll(".content-panel").length).toBe(2)
        expect(wrapper.find("[data-test='panel-maximize'][aria-pressed='true']").exists()).toBe(false)
    })
})
