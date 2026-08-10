import {afterEach, vi} from "vitest"
import {config, disableAutoUnmount, enableAutoUnmount} from "@vue/test-utils"

// Required by `isolate: false` (vitest.config.unit.js): workers reuse one module registry, so a
// module cached while another file's vi.mock was active keeps that mock. Setup files run before
// each spec's own imports, so resetting here hands every file a fresh `src/**` registry.
// Externalized node_modules (vue, @vue/test-utils, …) are unaffected.
vi.resetModules()

// Components that teleport (dialogs, drawers, poppers) keep their content attached
// to document.body until the wrapper unmounts, and with `isolate: false` that debris
// outlives the spec file. Auto-unmount every mounted wrapper instead of relying on
// each test to remember. `enableAutoUnmount` refuses to run twice and the test-utils
// module is shared across files here, so reset it before re-arming per file.
disableAutoUnmount()
enableAutoUnmount(afterEach)

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
// monaco-editor 0.56 sanitizes class names/URLs via the native CSS.escape(), which jsdom doesn't implement.
// Polyfill per the CSSOM spec: https://drafts.csswg.org/cssom/#serialize-an-identifier
function cssEscape(value: string): string {
    const string = String(value)
    const length = string.length
    const firstCodeUnit = string.charCodeAt(0)
    if (length === 1 && firstCodeUnit === 0x002d) {
        return `\\${string}`
    }
    let result = ""
    for (let index = 0; index < length; index++) {
        const codeUnit = string.charCodeAt(index)
        if (codeUnit === 0x0000) {
            result += "�"
        } else if (
            (codeUnit >= 0x0001 && codeUnit <= 0x001f) || codeUnit === 0x007f ||
            (index === 0 && codeUnit >= 0x0030 && codeUnit <= 0x0039) ||
            (index === 1 && codeUnit >= 0x0030 && codeUnit <= 0x0039 && firstCodeUnit === 0x002d)
        ) {
            result += `\\${codeUnit.toString(16)} `
        } else if (
            codeUnit >= 0x0080 || codeUnit === 0x002d || codeUnit === 0x005f ||
            (codeUnit >= 0x0030 && codeUnit <= 0x0039) ||
            (codeUnit >= 0x0041 && codeUnit <= 0x005a) ||
            (codeUnit >= 0x0061 && codeUnit <= 0x007a)
        ) {
            result += string.charAt(index)
        } else {
            result += `\\${string.charAt(index)}`
        }
    }
    return result
}
if (typeof globalThis.CSS === "undefined") {
    (globalThis as any).CSS = {}
}
if (typeof globalThis.CSS.escape !== "function") {
    (globalThis as any).CSS.escape = cssEscape
}
