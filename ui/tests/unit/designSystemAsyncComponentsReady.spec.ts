import {describe, expect, it} from "vitest"
import {createApp, h, nextTick, type App} from "vue"
import {createI18n} from "vue-i18n"
import KestraDesignSystem, {KsMarkdown} from "@kestra-io/design-system"

describe("designSystemAsyncComponentsReady", () => {
    const root = document.createElement("div")
    let app: App

    it("should mount a lazy component and leave its import in flight", () => {
        app = createApp({render: () => h(KsMarkdown, {content: "hello"})})
        app.use(createI18n({legacy: false, locale: "en", messages: {en: {}}}))
        app.use(KestraDesignSystem)
        app.mount(root)

        expect(root.querySelector(".ks-markdown")).toBeNull()
    })

    it("should find that import settled by the setup hook once the previous test ended", async () => {
        await nextTick()

        expect(root.querySelector(".ks-markdown")).not.toBeNull()
        app.unmount()
    })
})
