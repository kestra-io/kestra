import {vi} from "vitest"
import {config} from "@vue/test-utils"

// Most unit tests mount a component in isolation, without installing vue-router,
// so a literal <router-link> in its template can never resolve and spams
// "[Vue warn]: Failed to resolve component: router-link" on every mount.
// Register a minimal fake globally so it resolves like the real component would.
config.global.stubs = {
    ...config.global.stubs,
    RouterLink: {
        name: "RouterLink",
        props: ["to"],
        template: "<a><slot /></a>",
    },
}

// Many tests build a `createI18n()` instance with only the messages relevant to
// what they assert, so unrelated keys used by mounted child components (e.g. a
// design-system component's own locale) are legitimately absent. Default
// missingWarn/fallbackWarn to false so that expected gap doesn't spam
// "[intlify] Not found '<key>' key in '<locale>' locale messages." — tests that
// explicitly want to assert missing-key warnings can still pass their own
// missingWarn/fallbackWarn to override this default.
vi.mock("vue-i18n", async (importOriginal) => {
    const actual = await importOriginal<typeof import("vue-i18n")>()
    return {
        ...actual,
        createI18n: (options: Record<string, unknown> = {}) => actual.createI18n({
            missingWarn: false,
            fallbackWarn: false,
            ...options,
        }),
    }
})

// jsdom polyfills for Monaco editor (KsEditor)
if (typeof document !== "undefined" && typeof document.queryCommandSupported !== "function") {
    (document as any).queryCommandSupported = () => false
}
if (typeof document !== "undefined" && typeof document.execCommand !== "function") {
    (document as any).execCommand = () => false
}
// pdfjs-dist (pulled in transitively via PdfPreview.vue) constructs a DOMMatrix
// at module load time, which jsdom doesn't provide.
if (typeof globalThis.DOMMatrix === "undefined") {
    (globalThis as any).DOMMatrix = class DOMMatrix {}
}
if (typeof window !== "undefined" && typeof window.matchMedia !== "function") {
    (window as any).matchMedia = (query: string) => ({
        matches: false,
        media: query,
        onchange: null,
        addListener: () => {},
        removeListener: () => {},
        addEventListener: () => {},
        removeEventListener: () => {},
        dispatchEvent: () => false,
    })
}
