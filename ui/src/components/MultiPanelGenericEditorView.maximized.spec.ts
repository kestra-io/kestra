import {afterEach, beforeEach, describe, expect, test, vi} from "vitest"
import {nextTick} from "vue"
import {createPinia, setActivePinia} from "pinia"
import KestraDesignSystem from "@kestra-io/design-system"
import MultiPanelGenericEditorView from "./MultiPanelGenericEditorView.vue"
import type {EditorElement, Tab} from "../utils/multiPanelTypes"
import {i18nMount} from "../../tests/unit/i18nMount"

vi.mock("vue-router", () => ({
    useRoute: () => ({query: {}, params: {}, name: "flow"}),
    useRouter: () => ({replace: vi.fn(), push: vi.fn()}),
}))

const IconStub = {template: "<span />"}
const PaneStub = {template: "<div />"}

function makeElement(uid: string, extra: Partial<EditorElement> = {}): EditorElement {
    const tab: Tab = {uid, button: {label: uid, icon: IconStub}, component: PaneStub}
    return {...tab, ...extra, deserialize: (value: string) => value === uid ? tab : undefined}
}

const editorElements = [
    makeElement("code"),
    makeElement("topology"),
    makeElement("files", {prepend: true}),
]

function mountEditor(defaultActiveTabs = ["code", "topology"]) {
    return i18nMount(MultiPanelGenericEditorView, {
        global: {plugins: [KestraDesignSystem]},
        props: {editorElements, defaultActiveTabs},
    })
}

const visibleTabs = (wrapper: ReturnType<typeof mountEditor>) =>
    wrapper.findAll(".editor-tabs-container .editor-tab .tab-title").map(t => t.text())

const headerButton = (wrapper: ReturnType<typeof mountEditor>, label: string) =>
    wrapper.findAll(".tabs-wrapper .tabs > button").find(b => b.text() === label)!

async function maximizeFirstPanel(wrapper: ReturnType<typeof mountEditor>) {
    await wrapper.find("[data-test='panel-maximize']").trigger("click")
    expect(visibleTabs(wrapper)).toEqual(["code"])
}

describe("MultiPanelGenericEditorView with a maximized panel", () => {
    beforeEach(() => {
        setActivePinia(createPinia())
        localStorage.clear()
    })

    afterEach(() => localStorage.clear())

    test("opening a closed tab from the header leaves fullscreen so the new panel is visible", async () => {
        // Given
        const wrapper = mountEditor(["code"])
        await maximizeFirstPanel(wrapper)

        // When
        await headerButton(wrapper, "topology").trigger("click")
        await nextTick()

        // Then
        expect(visibleTabs(wrapper)).toEqual(["code", "topology"])
    })

    test("opening a tab that is prepended does not swap the maximized panel", async () => {
        // Given
        const wrapper = mountEditor(["code"])
        await maximizeFirstPanel(wrapper)

        // When
        await headerButton(wrapper, "files").trigger("click")
        await nextTick()

        // Then
        expect(visibleTabs(wrapper)).toEqual(["files", "code"])
    })

    test("closing a tab hidden behind the maximized panel leaves fullscreen to show the outcome", async () => {
        // Given
        const wrapper = mountEditor()
        await maximizeFirstPanel(wrapper)

        // When
        await headerButton(wrapper, "topology").trigger("click")
        await nextTick()

        // Then
        expect(visibleTabs(wrapper)).toEqual(["code"])
        expect(wrapper.find(".panel-maximized").exists()).toBe(false)
    })

    test("closing the maximized tab from the header keeps the editor consistent", async () => {
        // Given
        const wrapper = mountEditor()
        await maximizeFirstPanel(wrapper)

        // When
        await headerButton(wrapper, "code").trigger("click")
        await nextTick()

        // Then
        expect(visibleTabs(wrapper)).toEqual(["topology"])
    })

    test("focusing a tab hidden behind the maximized panel leaves fullscreen and shows it", async () => {
        // Given
        const wrapper = mountEditor()
        await maximizeFirstPanel(wrapper)

        // When
        wrapper.vm.focusTab("topology")
        await nextTick()

        // Then
        expect(wrapper.find(".panel-maximized").exists()).toBe(false)
        expect(visibleTabs(wrapper)).toEqual(["code", "topology"])
    })

    test("focusing the tab of the maximized panel keeps fullscreen", async () => {
        // Given
        const wrapper = mountEditor()
        await maximizeFirstPanel(wrapper)

        // When
        wrapper.vm.focusTab("code")
        await nextTick()

        // Then
        expect(wrapper.find(".panel-maximized").exists()).toBe(true)
        expect(visibleTabs(wrapper)).toEqual(["code"])
    })
})
