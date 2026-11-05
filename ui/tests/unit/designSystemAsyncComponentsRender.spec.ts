import {describe, expect, it} from "vitest"
import {writeSync} from "node:fs"
import {createApp, h} from "vue"
import {createI18n} from "vue-i18n"
import KestraDesignSystem, {KsMarkdown, designSystemAsyncComponentsReady} from "@kestra-io/design-system"

// TEMPORARY DIAGNOSTICS for the flaky render assertion, removed before merge.
const dump = (label: string, extra: Record<string, unknown>) => {
    const set = (globalThis as Record<symbol, unknown>)[Symbol.for("kestra.designSystem.startedAsyncComponents")] as Set<{__asyncResolved?: unknown, name?: string}> | undefined
    writeSync(2, `\n[diag] ${label} ${JSON.stringify({
        ...extra,
        pool: process.env.VITEST_POOL_ID,
        ksMarkdownResolved: Boolean((KsMarkdown as {__asyncResolved?: unknown}).__asyncResolved),
        started: [...(set ?? [])].map(wrapper => `${wrapper.name}:${Boolean(wrapper.__asyncResolved)}`),
    })}\n`)
}

describe("designSystemAsyncComponentsReady render", () => {
    it("should resolve only once the lazy component has rendered, not just once its import settled", async () => {
        const root = document.createElement("div")
        const errors: string[] = []
        const app = createApp({render: () => h(KsMarkdown, {content: "hello"})})
        app.config.errorHandler = (error) => {
            errors.push(String(error))
        }
        app.use(createI18n({legacy: false, locale: "en", messages: {en: {}}}))
        app.use(KestraDesignSystem)

        const mountedAt = performance.now()
        app.mount(root)

        if (root.querySelector(".ks-markdown")) {
            dump("pre-condition failed: rendered at mount", {html: root.innerHTML})
        }
        expect(root.querySelector(".ks-markdown")).toBeNull()

        await designSystemAsyncComponentsReady()

        if (!root.querySelector(".ks-markdown")) {
            const tookMs = Math.round(performance.now() - mountedAt)
            const htmlAtReturn = root.innerHTML
            await new Promise<void>(resolve => setTimeout(resolve, 100))
            dump("post-condition failed: not rendered after the helper", {tookMs, htmlAtReturn, htmlAfter100ms: root.innerHTML, errors})
        }
        expect(root.querySelector(".ks-markdown")).not.toBeNull()
        app.unmount()
    })
})
