// Globals whose replacement the runtime leak guard (tests/unit/leakGuard.ts) watches.
// Keep the two lists in sync: lint catches the authoring mistake, the guard catches the rest.
export const WATCHED_GLOBALS = new Set([
    "Image", "EventSource", "fetch", "WebSocket", "XMLHttpRequest",
    "IntersectionObserver", "ResizeObserver", "matchMedia", "DOMMatrix",
    "requestAnimationFrame", "navigator", "location", "history",
    "Date", "crypto", "Notification", "localStorage", "sessionStorage",
])

const GLOBAL_OBJECTS = new Set(["window", "globalThis", "global"])

/** True for `vi.<name>(...)`. */
export function isViCall(node, name) {
    return "CallExpression" === node.type
        && "MemberExpression" === node.callee.type
        && !node.callee.computed
        && "Identifier" === node.callee.object.type
        && "vi" === node.callee.object.name
        && "Identifier" === node.callee.property.type
        && name === node.callee.property.name
}

/** For `window.matchMedia` / `globalThis.fetch`, returns the property name. */
export function globalPropertyName(node) {
    if ("MemberExpression" !== node.type || node.computed) return undefined
    if ("Identifier" !== node.object.type || !GLOBAL_OBJECTS.has(node.object.name)) return undefined
    return "Identifier" === node.property.type ? node.property.name : undefined
}

/** True when the node sits at module scope rather than inside any function body. */
export function isModuleScope(sourceCode, node) {
    return !sourceCode.getAncestors(node).some((ancestor) => ancestor.type.includes("Function"))
}

/** True when the file imports `vi` from vitest, so a `vi.*` autofix is safe to apply. */
export function createViImportTracker() {
    let imported = false
    return {
        visitImport(node) {
            if ("vitest" !== node.source.value) return
            if (node.specifiers.some((specifier) => "ImportSpecifier" === specifier.type && "vi" === specifier.imported.name)) {
                imported = true
            }
        },
        isImported: () => imported,
    }
}
