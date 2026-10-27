import {afterEach, beforeEach, describe, expect, test, vi} from "vitest"
import {createPinia, setActivePinia} from "pinia"
import KestraDesignSystem from "@kestra-io/design-system"
import MultiPanelGenericEditorView from "./MultiPanelGenericEditorView.vue"
import type {EditorElement, Tab} from "../utils/multiPanelTypes"
import {i18nMount} from "../../tests/unit/i18nMount"

vi.mock("vue-router", () => ({
    useRoute: () => ({query: {}, params: {}, name: "flow"}),
    useRouter: () => ({replace: vi.fn(), push: vi.fn()}),
}))

vi.mock("./MultiPanelTabs.vue", () => ({
    default: {template: "<div />", props: ["modelValue"]},
}))

vi.mock("./MultiPanelEditorTabs.vue", () => ({
    default: {
        template: "<div><slot /></div>",
        props: ["tabs", "openTabs"],
        emits: ["update:tabs"],
    },
}))

const globalConfig = {
    plugins: [KestraDesignSystem],
}

const codeTab: Tab = {
    uid: "code",
    button: {label: "Code", icon: {template: "<span/>"}},
    component: {template: "<div/>"},
}

const editorElements: EditorElement[] = [
    {
        ...codeTab,
        deserialize: (v: string) => v === "code" ? codeTab : undefined,
    },
]

function mountEditor({withBottomPanel = true} = {}) {
    return i18nMount(MultiPanelGenericEditorView, {
        global: globalConfig,
        props: {
            editorElements,
            defaultActiveTabs: ["code"],
            bottomVisible: withBottomPanel,
        },
        slots: withBottomPanel ? {"bottom-panel": "<div />"} : {},
    })
}

describe("MultiPanelGenericEditorView split orientation", () => {
    beforeEach(() => {
        setActivePinia(createPinia())
        localStorage.clear()
    })

    afterEach(() => {
        localStorage.clear()
    })

    test("defaults to vertical orientation", () => {
        // Given: no stored preference
        // When: component mounts
        const wrapper = mountEditor()

        // Then: splitOrientation is "vertical"
        expect(wrapper.vm.splitOrientation).toBe("vertical")
    })

    test("hides the toggle button when there is no bottom panel", () => {
        // Given: the component is mounted without a bottom panel
        const wrapper = mountEditor({withBottomPanel: false})

        // Then: the orientation toggle is not rendered
        expect(wrapper.find(".orientation-toggle").exists()).toBe(false)
    })

    test("toggle button exists with accessible aria-label", () => {
        // Given: the component is mounted
        const wrapper = mountEditor()

        // When: looking for the orientation toggle
        const btn = wrapper.find(".orientation-toggle")

        // Then: it exists and has an aria-label
        expect(btn.exists()).toBe(true)
        expect(btn.attributes("aria-label")).toBeTruthy()
    })

    test("toggles orientation to horizontal when button is clicked", async () => {
        // Given: default vertical orientation
        const wrapper = mountEditor()
        expect(wrapper.vm.splitOrientation).toBe("vertical")

        // When: the toggle button is clicked
        await wrapper.find(".orientation-toggle").trigger("click")

        // Then: orientation switches to horizontal
        expect(wrapper.vm.splitOrientation).toBe("horizontal")
    })

    test("persists orientation toggle to localStorage", async () => {
        // Given: default vertical orientation
        const wrapper = mountEditor()

        // When: the toggle button is clicked
        await wrapper.find(".orientation-toggle").trigger("click")

        // Then: the preference is saved in localStorage (VueUse useStorage stores string values unquoted)
        const stored = localStorage.getItem("editor-split-orientation")
        expect(stored === "\"horizontal\"" || stored === "horizontal").toBe(true)
    })

    test("reads persisted orientation on mount", () => {
        // Given: a horizontal orientation stored in localStorage (try both forms VueUse may read)
        localStorage.setItem("editor-split-orientation", "horizontal")

        // When: component mounts
        const wrapper = mountEditor()

        // Then: splitOrientation starts as horizontal
        expect(wrapper.vm.splitOrientation).toBe("horizontal")
    })

    test("toggles back to vertical after two clicks", async () => {
        // Given: default vertical orientation
        const wrapper = mountEditor()
        const btn = wrapper.find(".orientation-toggle")

        // When: toggle clicked twice
        await btn.trigger("click")
        await btn.trigger("click")

        // Then: back to vertical
        expect(wrapper.vm.splitOrientation).toBe("vertical")
    })
})

function makeElement(uid: string, extra: Partial<EditorElement> = {}): EditorElement {
    const tab: Tab = {uid, button: {label: uid, icon: {template: "<span/>"}}, component: {template: "<div/>"}}
    return {...tab, ...extra, deserialize: (v: string) => v === uid ? tab : undefined}
}

const placementElements = [
    makeElement("code", {main: true}),
    makeElement("nocode", {main: true}),
    makeElement("doc"),
    makeElement("blueprints"),
]

function mountPlacement(defaultActiveTabs: string[]) {
    return i18nMount(MultiPanelGenericEditorView, {
        global: globalConfig,
        props: {editorElements: placementElements, defaultActiveTabs},
    })
}

function layout(wrapper: ReturnType<typeof mountPlacement>) {
    return wrapper.vm.panels.map(p => ({tabs: p.tabs.map(t => t.uid), active: p.activeTab.uid}))
}

describe("MultiPanelGenericEditorView view placement", () => {
    beforeEach(() => {
        setActivePinia(createPinia())
        localStorage.clear()
    })

    afterEach(() => {
        localStorage.clear()
    })

    test("opens a main view as a tab of the main panel and a companion in the panel beside it", () => {
        const wrapper = mountPlacement(["code", "doc"])

        wrapper.vm.setTabValue("nocode")
        wrapper.vm.setTabValue("blueprints")

        expect(layout(wrapper)).toEqual([
            {tabs: ["code", "nocode"], active: "nocode"},
            {tabs: ["doc", "blueprints"], active: "blueprints"},
        ])
    })

    test("opens a second panel for the first companion when only one panel is open", () => {
        const wrapper = mountPlacement(["code"])

        wrapper.vm.setTabValue("doc")
        wrapper.vm.setTabValue("blueprints")

        expect(layout(wrapper)).toEqual([
            {tabs: ["code"], active: "code"},
            {tabs: ["doc", "blueprints"], active: "blueprints"},
        ])
    })

    test("focuses a view that is already open instead of closing it", () => {
        const wrapper = mountPlacement(["code", "doc"])
        wrapper.vm.setTabValue("nocode")

        wrapper.vm.setTabValue("code")

        expect(layout(wrapper)[0]).toEqual({tabs: ["code", "nocode"], active: "code"})
    })
})
