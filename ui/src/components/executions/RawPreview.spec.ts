import {describe, expect, it, vi} from "vitest"
import {mount} from "@vue/test-utils"
import {createI18n} from "vue-i18n"
import {defineComponent} from "vue"
import RawPreview from "./RawPreview.vue"

const {copyToClipboard} = vi.hoisted(() => ({copyToClipboard: vi.fn()}))

vi.mock("../../composables/useEditorBindings", () => ({
    useEditorBindings: () => ({}),
}))

vi.mock("@kestra-io/design-system", () => ({
    copyToClipboard,
    KsEditor: defineComponent({
        name: "KsEditor",
        props: {modelValue: String, options: Object},
        template: "<div data-test=\"editor\"><nav><slot name=\"nav\" /></nav><main>{{ modelValue }}<slot name=\"absolute\" /></main></div>",
    }),
    KsButton: defineComponent({
        name: "KsButton",
        props: {tooltip: String},
        emits: ["click"],
        template: "<button :aria-label=\"tooltip\" @click=\"$emit('click')\" />",
    }),
    KsMarkdown: defineComponent({template: "<div />"}),
}))

const i18n = createI18n({
    legacy: false,
    globalInjection: true,
    locale: "en",
    messages: {en: {copy_to_clipboard: "Copy", toggle_word_wrap: "Toggle word wrap"}},
})

describe("RawPreview", () => {
    it("wraps text by default and keeps actions outside the editor content", async () => {
        const content = "a very long output line"
        const wrapper = mount(RawPreview, {
            props: {type: "TEXT", content},
            global: {plugins: [i18n]},
        })

        const editor = wrapper.findComponent({name: "KsEditor"})
        expect(editor.props("options").wordWrap).toBe(true)
        expect(wrapper.find("main button").exists()).toBe(false)

        const buttons = wrapper.findAll("nav button")
        expect(buttons).toHaveLength(2)

        await buttons[0].trigger("click")
        expect(copyToClipboard).toHaveBeenCalledWith(content)

        await buttons[1].trigger("click")
        expect(editor.props("options").wordWrap).toBe(false)
    })
})
