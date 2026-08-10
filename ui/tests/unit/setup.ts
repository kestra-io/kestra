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
if (typeof Element !== "undefined" && typeof Element.prototype.scrollIntoView !== "function") {
    Element.prototype.scrollIntoView = () => {}
}
// jsdom implements neither navigator.clipboard nor ClipboardItem; Monaco's WebKit
// clipboard workaround binds click/keydown handlers on document.body that call
// `navigator.clipboard.write([new ClipboardItem(...)])`, so any event bubbling to
// body in an attached mount otherwise throws "ClipboardItem is not defined".
// That workaround also hands the ClipboardItem ctor a pending promise which the next
// event cancels; the real ctor consumes it, so the stub has to do the same or the
// cancellation surfaces as an unhandled rejection and fails the whole run.
if (typeof globalThis.ClipboardItem === "undefined") {
    (globalThis as any).ClipboardItem = class ClipboardItem {
        items: Record<string, unknown>
        constructor(items: Record<string, unknown>) {
            this.items = items
            for (const value of Object.values(items ?? {})) {
                if (value && typeof (value as Promise<unknown>).then === "function") {
                    (value as Promise<unknown>).catch(() => {})
                }
            }
        }
    }
}
if (typeof navigator !== "undefined" && !navigator.clipboard) {
    Object.defineProperty(navigator, "clipboard", {
        configurable: true,
        value: {
            write: () => Promise.resolve(),
            writeText: () => Promise.resolve(),
            read: () => Promise.resolve([]),
            readText: () => Promise.resolve(""),
        },
    })
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
// jsdom doesn't implement ResizeObserver (used by TaskEdit's stacked-layout detection)
if (typeof globalThis.ResizeObserver === "undefined") {
    (globalThis as any).ResizeObserver = class {
        observe() {}
        unobserve() {}
        disconnect() {}
    }
}
// jsdom doesn't implement CSS.escape (used by BlockEditor's data-dock-pane-id lookups)
if (typeof globalThis.CSS === "undefined" || typeof globalThis.CSS.escape !== "function") {
    (globalThis as any).CSS = {
        ...(globalThis as any).CSS,
        escape: (value: string) => String(value).replace(/([!"#$%&'()*+,./:;<=>?@[\]^`{|}~])/g, "\\$1"),
    }
}
