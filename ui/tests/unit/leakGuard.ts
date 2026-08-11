import {afterAll, expect, vi} from "vitest"
import {writeSync} from "node:fs"

// `isolate: false` (vitest.config.unit.js) shares one jsdom environment per worker, so shared
// state a file mutates and never restores leaks into the next file — the classic source of
// "passes alone, fails in CI" flakiness. Leaking fails the suite; set VITEST_LEAK_GUARD=off
// to silence it while debugging. Module leakage is prevented upstream of this, by the
// vi.resetModules() in setup.ts.
//
// This file is a setup file, so it re-executes per test file: the snapshot below is taken
// before the spec's own module-level code runs, and the afterAll diff blames that spec.

// Watched explicitly rather than diffing every global: dependencies install one-time globals on
// first import (Vue devtools bridges, tslib helpers, monaco's vscodeWindowId) that no test can
// restore, and blaming whichever spec imported them first would be a permanent false positive.
const WATCHED_GLOBALS = [
    "Image", "EventSource", "fetch", "WebSocket", "XMLHttpRequest",
    "IntersectionObserver", "ResizeObserver", "matchMedia", "DOMMatrix",
    "requestAnimationFrame", "navigator", "location", "history",
    "Date", "crypto", "Notification", "localStorage", "sessionStorage",
] as const

// Element Plus lazily appends one popper container to <body> and reuses it for the whole page;
// it is a singleton, not a per-test leak. Its *contents* are teleported poppers, and those must
// be gone once the owning wrapper unmounts.
const POPPER_CONTAINER_ID = /^k?el-popper-container-/

const isPopperContainer = (node: Element) => POPPER_CONTAINER_ID.test(node.id)

const bodyElements = () => Array.from(document.body.children).filter((node) => !isPopperContainer(node))

const teleportedCount = () => Array.from(document.body.children)
    .filter(isPopperContainer)
    .reduce((total, container) => total + container.childElementCount, 0)

/** Short, greppable description of a stray node, e.g. `div.el-overlay#app`. */
function describeNode(node: Element): string {
    const id = node.id ? `#${node.id}` : ""
    const cls = node.classList.length ? `.${Array.from(node.classList).join(".")}` : ""
    return `${node.tagName.toLowerCase()}${id}${cls}`
}

const snapshot = () => ({
    globals: new Map(WATCHED_GLOBALS.map((key) => [key, (globalThis as any)[key]])),
    title: document.title,
    bodyChildren: bodyElements().length,
    teleported: teleportedCount(),
    bodyClass: document.body.className,
    fakeTimers: vi.isFakeTimers(),
    localStorageKeys: Object.keys(localStorage).sort().join(","),
    sessionStorageKeys: Object.keys(sessionStorage).sort().join(","),
})

const before = snapshot()

afterAll(() => {
    if ("off" === process.env.VITEST_LEAK_GUARD?.toLowerCase()) return

    const after = snapshot()
    const leaks: string[] = []

    for (const key of WATCHED_GLOBALS) {
        if (before.globals.get(key) !== after.globals.get(key)) {
            leaks.push(`globalThis.${key} was replaced and not restored (call vi.unstubAllGlobals() in afterAll)`)
        }
    }
    if (before.title !== after.title) leaks.push(`document.title left as "${after.title}" (was "${before.title}")`)
    if (after.bodyChildren > before.bodyChildren) {
        const strays = bodyElements().slice(before.bodyChildren).map(describeNode).join(", ")
        leaks.push(`document.body left ${after.bodyChildren - before.bodyChildren} node(s) attached [${strays}] — unmount wrappers or mount without attachTo`)
    }
    if (after.teleported > before.teleported) {
        leaks.push(`${after.teleported - before.teleported} teleported popper node(s) left behind — unmount the wrapper that opened them`)
    }
    if (before.bodyClass !== after.bodyClass) leaks.push(`document.body class left as "${after.bodyClass}" (was "${before.bodyClass}")`)
    if (!before.fakeTimers && after.fakeTimers) leaks.push("fake timers left installed (call vi.useRealTimers())")
    if (before.localStorageKeys !== after.localStorageKeys) leaks.push(`localStorage left keys [${after.localStorageKeys}] (was [${before.localStorageKeys}])`)
    if (before.sessionStorageKeys !== after.sessionStorageKeys) leaks.push(`sessionStorage left keys [${after.sessionStorageKeys}] (was [${before.sessionStorageKeys}])`)

    if (!leaks.length) return

    const relative = String(expect.getState().testPath ?? "unknown file").replace(`${process.cwd()}/`, "")
    // Written straight to stderr: vitest discards console output of passing files, and the
    // thrown error alone does not show the per-leak detail.
    writeSync(2, `\n❌ state leak in ${relative}:\n${leaks.map((leak) => `   - ${leak}`).join("\n")}\n`)
    throw new Error(`Shared state leaked from ${relative}: ${leaks.join("; ")}`)
})
