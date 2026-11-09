import {describe, expect, it} from "vitest"
import {mount} from "@vue/test-utils"
import {createApp, h} from "vue"
import {createI18n} from "vue-i18n"
import KestraDesignSystem, {KsMarkdown, designSystemAsyncComponentsReady} from "@kestra-io/design-system"
import {resetVNodeTransformer} from "./resetVNodeTransformer"

const renderWithPlainApp = async () => {
    const root = document.createElement("div")
    const app = createApp({render: () => h(KsMarkdown, {content: "hello"})})
    app.use(createI18n({legacy: false, locale: "en", messages: {en: {}}}))
    app.use(KestraDesignSystem)
    app.mount(root)
    await designSystemAsyncComponentsReady()
    const html = root.innerHTML
    app.unmount()
    return html
}

describe("resetVNodeTransformer", () => {
    it("should leave the stubs of an earlier mount active until it is called", async () => {
        const wrapper = mount({render: () => h(KsMarkdown, {content: "x"})}, {global: {stubs: {KsMarkdown: true}}})
        expect(wrapper.html()).toContain("ks-markdown-stub")

        expect(await renderWithPlainApp()).toContain("ks-markdown-stub")
    })

    it("should render the real component in a plain app once the leaked transformer is cleared", async () => {
        resetVNodeTransformer()

        const html = await renderWithPlainApp()

        expect(html).toContain("ks-markdown")
        expect(html).not.toContain("ks-markdown-stub")
    })
})
