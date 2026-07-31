import {afterAll, beforeAll, expect} from "vitest"

// The unit project runs with `isolate: false`, so every spec file shares one
// jsdom/global scope. This guard snapshots that shared state before each file
// and diffs it after, so the file that leaks is the file that gets blamed.

type Mode = "off" | "warn" | "error"

const MODE: Mode = (() => {
    const raw = process.env.VITEST_LEAK_GUARD?.toLowerCase()
    return "off" === raw || "warn" === raw || "error" === raw ? raw : "warn"
})()

// Globals a dependency installs once, on first import, and never removes: Vue/Pinia
// devtools bridges, vue-i18n feature flags, tslib's UMD helpers, xss, VueUse's SSR
// handlers. Whichever spec imports the dependency first would otherwise be blamed
// for a one-time module-init side effect no test can restore.
const IGNORED_GLOBAL_PREFIXES = ["__VUE", "__INTLIFY", "__PINIA", "__vueuse", "__vitest", "__vue_test_utils"]

const IGNORED_GLOBALS = new Set([
    // tslib UMD helpers
    "__extends", "__assign", "__rest", "__decorate", "__param", "__metadata",
    "__awaiter", "__generator", "__exportStar", "__createBinding", "__values",
    "__read", "__spread", "__spreadArrays", "__spreadArray", "__await",
    "__asyncGenerator", "__asyncDelegator", "__asyncValues", "__makeTemplateObject",
    "__importStar", "__importDefault", "__classPrivateFieldGet", "__classPrivateFieldSet",
    // xss
    "filterCSS", "filterXSS",
    // monaco-editor tags the window on import (vs/base/browser/window.js)
    "vscodeWindowId",
    // jsdom churn, not state a test owns
    "event", "performance",
])

function isIgnoredGlobal(key: string): boolean {
    return IGNORED_GLOBALS.has(key) || IGNORED_GLOBAL_PREFIXES.some((prefix) => key.startsWith(prefix))
}

/**
 * Reads every own global we can touch, keyed by name, for identity diffing.
 * getOwnPropertyNames (not Object.keys) so non-enumerable jsdom globals like
 * `Image` are in the baseline — a stub of one must read as replaced, not added.
 */
function snapshotGlobals(): Map<string, unknown> {
    const snapshot = new Map<string, unknown>()
    for (const key of Object.getOwnPropertyNames(globalThis)) {
        if (isIgnoredGlobal(key)) continue
        try {
            snapshot.set(key, (globalThis as Record<string, unknown>)[key])
        } catch {
            // Getters that throw in jsdom are not state we can track.
        }
    }
    return snapshot
}

// Element Plus lazily appends one popper container to <body> and reuses it for the
// whole page; it is a singleton, not a per-test leak. Its *contents* are teleported
// poppers, and those must be gone once the owning wrapper unmounts.
const POPPER_CONTAINER_ID = /^k?el-popper-container-/

function isPopperContainer(node: Node): boolean {
    return node instanceof Element && POPPER_CONTAINER_ID.test(node.id)
}

function bodyNodes(): Node[] {
    return Array.from(document.body.childNodes).filter((node) => !isPopperContainer(node))
}

function teleportedNodes(): number {
    let total = 0
    for (const child of Array.from(document.body.children)) {
        if (isPopperContainer(child)) total += child.childNodes.length
    }
    return total
}

function storageKeys(storage: Storage): string[] {
    try {
        return Object.keys(storage).sort()
    } catch {
        return []
    }
}

/** Short, greppable description of a stray node, e.g. `div.el-overlay#app`. */
function describeNode(node: Node): string {
    if (!(node instanceof Element)) return `${node.nodeName}(${(node.textContent ?? "").trim().slice(0, 20)})`
    const id = node.id ? `#${node.id}` : ""
    const cls = node.classList.length ? `.${Array.from(node.classList).join(".")}` : ""
    return `${node.tagName.toLowerCase()}${id}${cls}`
}

interface Snapshot {
    localStorage: string[]
    sessionStorage: string[]
    title: string
    bodyChildren: number
    teleported: number
    bodyClass: string
    globals: Map<string, unknown>
}

function takeSnapshot(): Snapshot {
    return {
        localStorage: storageKeys(localStorage),
        sessionStorage: storageKeys(sessionStorage),
        title: document.title,
        bodyChildren: bodyNodes().length,
        teleported: teleportedNodes(),
        bodyClass: document.body.className,
        globals: snapshotGlobals(),
    }
}

function diffKeys(before: string[], after: string[]): string[] {
    const seen = new Set(before)
    return after.filter((key) => !seen.has(key))
}

function report(before: Snapshot, after: Snapshot): string[] {
    const problems: string[] = []

    const addedLocal = diffKeys(before.localStorage, after.localStorage)
    if (addedLocal.length) {
        problems.push(`localStorage keys left behind: ${addedLocal.join(", ")} — clear them in afterEach/afterAll.`)
    }

    const addedSession = diffKeys(before.sessionStorage, after.sessionStorage)
    if (addedSession.length) {
        problems.push(`sessionStorage keys left behind: ${addedSession.join(", ")} — clear them in afterEach/afterAll.`)
    }

    if (before.title !== after.title) {
        problems.push(`document.title not restored: "${before.title}" became "${after.title}" — restore it in afterAll.`)
    }

    if (after.bodyChildren > before.bodyChildren) {
        const strays = bodyNodes().slice(before.bodyChildren).map(describeNode)
        problems.push(`${after.bodyChildren - before.bodyChildren} node(s) left attached to document.body: ${strays.join(", ")} — call wrapper.unmount() or drop attachTo.`)
    }

    if (after.teleported > before.teleported) {
        problems.push(`${after.teleported - before.teleported} teleported popper node(s) left in the Element Plus container — unmount the wrapper that opened them.`)
    }

    if (before.bodyClass !== after.bodyClass) {
        problems.push(`document.body class not restored: "${before.bodyClass}" became "${after.bodyClass}".`)
    }

    const changed: string[] = []
    for (const [key, value] of after.globals) {
        if (!before.globals.has(key)) {
            changed.push(`+${key}`)
        } else if (!Object.is(before.globals.get(key), value)) {
            changed.push(`~${key}`)
        }
    }
    for (const key of before.globals.keys()) {
        if (!after.globals.has(key)) changed.push(`-${key}`)
    }
    if (changed.length) {
        problems.push(`globals replaced and not restored: ${changed.join(", ")} — use vi.stubGlobal + vi.unstubAllGlobals(), or save and restore the original.`)
    }

    return problems
}

if ("off" !== MODE) {
    let before: Snapshot
    let file = "unknown spec"

    beforeAll(() => {
        const testPath = expect.getState().testPath
        file = testPath ? testPath.replace(`${process.cwd()}/`, "") : file
        before = takeSnapshot()
    })

    afterAll(() => {
        const problems = report(before, takeSnapshot())
        if (!problems.length) return

        const message = [`[leak-guard] ${file} leaked shared state:`, ...problems.map((problem) => `  - ${problem}`)].join("\n")
        if ("error" === MODE) throw new Error(message)
        // Not console.warn: Vitest discards console output written from afterAll,
        // which would make warn mode silent.
        process.stderr.write(`${message}\n`)
    })
}
