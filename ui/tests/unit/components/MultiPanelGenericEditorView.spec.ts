import {afterEach, beforeEach, describe, expect, test, vi} from "vitest"
import {mount} from "@vue/test-utils"
import {createPinia, setActivePinia} from "pinia"
import {createI18n} from "vue-i18n"
import KestraDesignSystem from "@kestra-io/design-system"
import MultiPanelGenericEditorView from "../../../src/components/MultiPanelGenericEditorView.vue"

vi.mock("vue-router", () => ({
    useRoute: () => ({query: {}, params: {}, name: "flow"}),
    useRouter: () => ({replace: vi.fn(), push: vi.fn()}),
}))

vi.mock("../../../src/components/MultiPanelTabs.vue", () => ({
    default: {template: "<div />", props: ["modelValue"]},
}))

vi.mock("../../../src/components/MultiPanelEditorTabs.vue", () => ({
    default: {
        template: "<div><slot /></div>",
        props: ["tabs", "openTabs"],
        emits: ["update:tabs"],
    },
}))

const globalConfig = {
    plugins: [
        createI18n({legacy: false, locale: "en", fallbackWarn: false, missingWarn: false}),
        KestraDesignSystem,
    ],
}

const editorElements = [
    {
        uid: "code",
        button: {label: "Code", icon: {template: "<span/>"}},
        component: {template: "<div/>"},
        deserialize: (v: string) => v === "code" ? {uid: "code", component: {template: "<div/>"}} as any : undefined,
    },
]

function mountEditor({withBottomPanel = true} = {}) {
    return mount(MultiPanelGenericEditorView, {
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
        expect((wrapper.vm as any).splitOrientation).toBe("vertical")
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
        expect((wrapper.vm as any).splitOrientation).toBe("vertical")

        // When: the toggle button is clicked
        await wrapper.find(".orientation-toggle").trigger("click")

        // Then: orientation switches to horizontal
        expect((wrapper.vm as any).splitOrientation).toBe("horizontal")
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
        expect((wrapper.vm as any).splitOrientation).toBe("horizontal")
    })

    test("toggles back to vertical after two clicks", async () => {
        // Given: default vertical orientation
        const wrapper = mountEditor()
        const btn = wrapper.find(".orientation-toggle")

        // When: toggle clicked twice
        await btn.trigger("click")
        await btn.trigger("click")

        // Then: back to vertical
        expect((wrapper.vm as any).splitOrientation).toBe("vertical")
    })
})
