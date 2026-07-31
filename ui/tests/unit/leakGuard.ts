import {afterAll, expect, vi} from "vitest"
import {writeSync} from "node:fs"

// `isolate: false` shares one jsdom environment per worker, so shared state a file mutates and never
// restores leaks into the next file. Reported against the first file in a worker that pollutes it.
const WATCHED_GLOBALS = [
    "Image", "EventSource", "fetch", "WebSocket", "XMLHttpRequest",
    "IntersectionObserver", "ResizeObserver", "matchMedia", "DOMMatrix",
    "requestAnimationFrame", "navigator", "location", "history",
] as const

const snapshot = () => ({
    globals: new Map(WATCHED_GLOBALS.map(key => [key, (globalThis as any)[key]])),
    title: document.title,
    bodyChildren: document.body.childElementCount,
    fakeTimers: vi.isFakeTimers(),
    localStorageKeys: Object.keys(localStorage).join(","),
})

const before = snapshot()

afterAll(() => {
    const after = snapshot()
    const leaks: string[] = []

    for (const key of WATCHED_GLOBALS) {
        if (before.globals.get(key) !== after.globals.get(key)) {
            leaks.push(`globalThis.${key} was replaced and not restored (call vi.unstubAllGlobals() in afterAll)`)
        }
    }
    if (before.title !== after.title) leaks.push(`document.title left as "${after.title}" (was "${before.title}")`)
    if (before.bodyChildren !== after.bodyChildren) leaks.push(`document.body left ${after.bodyChildren} node(s) attached (was ${before.bodyChildren}) — unmount wrappers or mount without attachTo`)
    if (!before.fakeTimers && after.fakeTimers) leaks.push("fake timers left installed (call vi.useRealTimers())")
    if (before.localStorageKeys !== after.localStorageKeys) leaks.push(`localStorage left keys [${after.localStorageKeys}] (was [${before.localStorageKeys}])`)

    if (leaks.length) {
        const relative = String(expect.getState().testPath ?? "unknown file").replace(`${process.cwd()}/`, "")
        // Written straight to stderr: the root vitest.config.js swallows console output of passing files.
        writeSync(2, `\n⚠️  state leak in ${relative}:\n${leaks.map(l => `   - ${l}`).join("\n")}\n`)
        if (process.env.VITEST_LEAK_GUARD === "error") {
            throw new Error(`Shared state leaked from ${relative}: ${leaks.join("; ")}`)
        }
    }
})
