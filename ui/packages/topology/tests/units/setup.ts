import {installMonacoCssEscapePolyfill} from "../../../../tests/unit/monacoCssEscapePolyfill"

// monaco-editor probes browser APIs jsdom doesn't ship with.
if (typeof document !== "undefined" && typeof document.queryCommandSupported !== "function") {
    document.queryCommandSupported = () => false
}
if (typeof window !== "undefined" && typeof window.matchMedia !== "function") {
    window.matchMedia = (query: string) => ({
        matches: false,
        media: query,
        onchange: null,
        addListener: () => {},
        removeListener: () => {},
        addEventListener: () => {},
        removeEventListener: () => {},
        dispatchEvent: () => false,
    }) as MediaQueryList
}
if (typeof globalThis.ResizeObserver === "undefined") {
    globalThis.ResizeObserver = class {
        observe() {}
        unobserve() {}
        disconnect() {}
    }
}
installMonacoCssEscapePolyfill()
