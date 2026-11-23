import {describe, expect, it} from "vitest"
import {createApp, h} from "vue"
import {createI18n} from "vue-i18n"
import KestraDesignSystem, {KsMarkdown, designSystemAsyncComponentsReady} from "@kestra-io/design-system"

describe("designSystemAsyncComponentsReady render", () => {
    it("should resolve only once the lazy component has rendered, not just once its import settled", async () => {
        const root = document.createElement("div")
        const app = createApp({render: () => h(KsMarkdown, {content: "hello"})})
        app.use(createI18n({legacy: false, locale: "en", messages: {en: {}}}))
        app.use(KestraDesignSystem)
        app.mount(root)

        expect(root.querySelector(".ks-markdown")).toBeNull()

        await designSystemAsyncComponentsReady()

        expect(root.querySelector(".ks-markdown")).not.toBeNull()
        app.unmount()
    })
})
