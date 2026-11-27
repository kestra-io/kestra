import {describe, expect, test, vi} from "vitest"
import {i18nMount} from "../../../../tests/unit/i18nMount"
import DiffView from "./DiffView.vue"

const editorBindings = vi.hoisted(() => ({
    theme: "kestra-dark",
    loadTaskIcon: vi.fn(),
    configureLanguage: vi.fn(),
}))

vi.mock("../../../composables/useEditorBindings", () => ({
    useEditorBindings: () => editorBindings,
}))

vi.mock("@kestra-io/design-system", async (importOriginal) => ({
    ...await importOriginal<typeof import("@kestra-io/design-system")>(),
    KsEditor: {
        name: "KsEditor",
        props: {
            modelValue: String,
            original: String,
            readOnly: Boolean,
            options: Object,
            lang: String,
            theme: String,
            loadTaskIcon: Function,
            configureLanguage: Function,
        },
        template: "<div />",
    },
}))

function mountDiffView(oldValue = "id: before\n") {
    return i18nMount(DiffView, {
        props: {oldValue, newValue: "id: after\n"},
    })
}

describe("DiffView", () => {
    test("passes newValue as modelValue and oldValue as original", () => {
        const editor = mountDiffView().getComponent({name: "KsEditor"})

        expect(editor.props("modelValue")).toBe("id: after\n")
        expect(editor.props("original")).toBe("id: before\n")
    })

    test("makes the editor read-only", () => {
        const editor = mountDiffView().getComponent({name: "KsEditor"})

        expect(editor.props("readOnly")).toBe(true)
    })

    test("renders the diff inline", () => {
        const editor = mountDiffView().getComponent({name: "KsEditor"})

        expect(editor.props("options")).toMatchObject({diffSideBySide: false})
    })

    test("uses the yaml language", () => {
        const editor = mountDiffView().getComponent({name: "KsEditor"})

        expect(editor.props("lang")).toBe("yaml")
    })

    test("forwards an empty oldValue unchanged", () => {
        const editor = mountDiffView("").getComponent({name: "KsEditor"})

        expect(editor.props("original")).toBe("")
    })

    test("forwards the editor bindings", () => {
        const editor = mountDiffView().getComponent({name: "KsEditor"})

        expect(editor.props("theme")).toBe(editorBindings.theme)
        expect(editor.props("loadTaskIcon")).toBe(editorBindings.loadTaskIcon)
        expect(editor.props("configureLanguage")).toBe(editorBindings.configureLanguage)
    })

    test("exposes the copilot-diff test handle", () => {
        const wrapper = mountDiffView()

        expect(wrapper.find("[data-test=\"copilot-diff\"]").exists()).toBe(true)
    })
})
