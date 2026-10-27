import {beforeEach, describe, expect, test, vi} from "vitest"
import {createPinia, setActivePinia} from "pinia"
import {markRaw, ref} from "vue"
import KestraDesignSystem from "@kestra-io/design-system"
import MultiPanelTabs from "./MultiPanelTabs.vue"
import {i18nMount} from "../../tests/unit/i18nMount"

const splitterWidth = ref(0)

vi.mock("@vueuse/core", async (importOriginal) => ({
    ...await importOriginal<typeof import("@vueuse/core")>(),
    useElementSize: () => ({width: splitterWidth, height: ref(0)}),
}))

const IconStub = markRaw({template: "<span class='icon-stub' />"})
const PaneStub = markRaw({name: "PaneStub", template: "<div class='pane-stub' />"})

function makePanel(uid: string) {
    const tab = {uid, button: {label: uid, icon: IconStub}, component: PaneStub}
    return {tabs: [tab], activeTab: tab, size: 50}
}

const globalConfig = {
    plugins: [KestraDesignSystem],
}

function mountTabs() {
    return i18nMount(MultiPanelTabs, {
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
        const wrapper = i18nMount(MultiPanelTabs, {
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

    test("activating a tab in a hidden panel exits fullscreen so the tab is shown", async () => {
        const second = makePanel("b")
        const other = {uid: "c", button: {label: "c", icon: IconStub}, component: PaneStub}
        second.tabs.push(other)
        const wrapper = i18nMount(MultiPanelTabs, {
            global: globalConfig,
            props: {modelValue: [makePanel("a"), second]},
        })
        await wrapper.findAll("[data-test='panel-maximize']")[0].trigger("click")

        const vm = wrapper.vm as unknown as {panels: {activeTab: unknown}[]}
        vm.panels[1].activeTab = other
        await wrapper.vm.$nextTick()

        expect(wrapper.findAll(".content-panel").length).toBe(2)
    })
})

describe("MultiPanelTabs split", () => {
    beforeEach(() => setActivePinia(createPinia()))

    function mountWithTwoTabs() {
        const first = makePanel("a")
        const second = {uid: "c", button: {label: "c", icon: IconStub}, component: PaneStub}
        first.tabs.push(second)
        return i18nMount(MultiPanelTabs, {
            global: globalConfig,
            props: {modelValue: [first, makePanel("b")]},
        })
    }

    test("disables the split when a third panel would fall below the minimum width", () => {
        splitterWidth.value = 900
        const split = mountWithTwoTabs().find("[data-test='panel-split']")

        expect(split.attributes("disabled")).toBeDefined()
        expect(split.attributes("title")).toBe("multi_panel_editor.split_panel_no_room")
    })

    test("splits the active tab into a new panel when there is room", async () => {
        splitterWidth.value = 1600
        const wrapper = mountWithTwoTabs()

        await wrapper.find("[data-test='panel-split']").trigger("click")

        expect(wrapper.findAll(".content-panel").length).toBe(3)
    })
})
