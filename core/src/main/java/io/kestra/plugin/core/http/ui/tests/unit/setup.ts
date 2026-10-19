import { vi } from "vitest";
import { config } from "@vue/test-utils";

// Components mounted in isolation without vue-router can't resolve a literal <router-link>, which
// spams "[Vue warn]: Failed to resolve component: router-link". Register a minimal global stub.
config.global.stubs = {
    ...config.global.stubs,
    RouterLink: {
        name: "RouterLink",
        props: ["to"],
        template: "<a><slot /></a>",
    },
};

// Tests build their own createI18n() with only the messages they assert on, so unrelated keys used
// by mounted children are legitimately absent. Default missing/fallback warnings off so that
// expected gap doesn't spam "[intlify] Not found '<key>' key ..."; tests can still override.
vi.mock("vue-i18n", async (importOriginal) => {
    const actual = await importOriginal<typeof import("vue-i18n")>();
    return {
        ...actual,
        createI18n: (options: Record<string, unknown> = {}) =>
            actual.createI18n({ missingWarn: false, fallbackWarn: false, ...options }),
    };
});

// jsdom lacks a handful of browser APIs the design-system / Monaco (KsEditor) touch at mount time.
// Stub the ones story rendering needs so `play` functions can run headlessly.
if (typeof document !== "undefined") {
    const d = document as unknown as Record<string, unknown>;
    if (typeof d.queryCommandSupported !== "function") d.queryCommandSupported = () => false;
    if (typeof d.queryCommandState !== "function") d.queryCommandState = () => false;
    if (typeof d.execCommand !== "function") d.execCommand = () => false;
    // Monaco (KsEditor) awaits `document.fonts.ready` on mount; jsdom has no FontFaceSet.
    if (!("fonts" in document)) {
        Object.defineProperty(document, "fonts", {
            configurable: true,
            value: {
                ready: Promise.resolve(),
                add() {},
                delete() {},
                load: () => Promise.resolve([]),
                addEventListener() {},
                removeEventListener() {},
            },
        });
    }
}

if (typeof window !== "undefined") {
    // KsEditor (Monaco) reads localStorage for editor settings; jsdom's implementation isn't always
    // usable under vitest, so install a simple in-memory store.
    if (typeof window.localStorage?.getItem !== "function") {
        const store = new Map<string, string>();
        Object.defineProperty(window, "localStorage", {
            configurable: true,
            value: {
                getItem: (k: string) => (store.has(k) ? store.get(k) : null),
                setItem: (k: string, v: string) => void store.set(k, String(v)),
                removeItem: (k: string) => void store.delete(k),
                clear: () => store.clear(),
                key: (i: number) => Array.from(store.keys())[i] ?? null,
                get length() {
                    return store.size;
                },
            },
        });
    }
    if (typeof window.matchMedia !== "function") {
        (window as unknown as { matchMedia: (q: string) => object }).matchMedia = (
            query: string,
        ) => ({
            matches: false,
            media: query,
            onchange: null,
            addListener: () => {},
            removeListener: () => {},
            addEventListener: () => {},
            removeEventListener: () => {},
            dispatchEvent: () => false,
        });
    }
    for (const name of ["ResizeObserver", "IntersectionObserver"]) {
        if (!(name in window)) {
            (window as unknown as Record<string, unknown>)[name] = class {
                observe() {}
                unobserve() {}
                disconnect() {}
                takeRecords() {
                    return [];
                }
            };
        }
    }
}

if (typeof Element !== "undefined" && !Element.prototype.scrollIntoView) {
    Element.prototype.scrollIntoView = () => {};
}