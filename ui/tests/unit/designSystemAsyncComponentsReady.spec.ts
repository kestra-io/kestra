import {describe, expect, it} from "vitest"
import {createApp, h, nextTick} from "vue"
import {createI18n} from "vue-i18n"
import KestraDesignSystem, {KsMarkdown, designSystemAsyncComponentsReady} from "@kestra-io/design-system"

describe("designSystemAsyncComponentsReady", () => {
    it("should resolve once a mounted lazy component has finished importing", async () => {
        const root = document.createElement("div")
        const app = createApp({render: () => h(KsMarkdown, {content: "hello"})})
        app.use(createI18n({legacy: false, locale: "en", messages: {en: {}}}))
        app.use(KestraDesignSystem)
        app.mount(root)

        expect(root.querySelector(".ks-markdown")).toBeNull()

        await designSystemAsyncComponentsReady()
        await nextTick()

        expect(root.querySelector(".ks-markdown")).not.toBeNull()
        app.unmount()
    })
})
