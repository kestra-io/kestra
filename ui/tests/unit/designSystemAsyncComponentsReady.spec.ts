import {describe, expect, it} from "vitest"
import {writeSync} from "node:fs"
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

        if (root.querySelector(".ks-markdown")) {
            writeSync(2, `\n[diag] ready: pre-condition failed, rendered at mount ${JSON.stringify({resolved: Boolean((KsMarkdown as {__asyncResolved?: unknown}).__asyncResolved), pool: process.env.VITEST_POOL_ID})}\n`)
        }
        expect(root.querySelector(".ks-markdown")).toBeNull()
    })

    it("should find that import settled by the setup hook once the previous test ended", async () => {
        await nextTick()

        if (!root.querySelector(".ks-markdown")) {
            const htmlAtCheck = root.innerHTML
            await new Promise<void>(resolve => setTimeout(resolve, 100))
            writeSync(2, `\n[diag] ready: post-condition failed ${JSON.stringify({htmlAtCheck, htmlAfter100ms: root.innerHTML, resolved: Boolean((KsMarkdown as {__asyncResolved?: unknown}).__asyncResolved), pool: process.env.VITEST_POOL_ID})}\n`)
        }
        expect(root.querySelector(".ks-markdown")).not.toBeNull()
        app.unmount()
    })
})
