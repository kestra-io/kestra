// Must stay the FIRST import: it patches window.fetch before any src/ or SDK module is evaluated.
// preview.jsx imports it too — the install is idempotent. Importing from both files removes any
// dependence on the relative order of this setup file and the one addon-vitest injects itself.
import "./apiMock"

// Story templates are runtime-compiled by Vue in the browser, which triggers
// "@vue/compiler-core: decodeEntities option is passed but will be ignored in
// non-browser builds" — a false-positive from the esm-bundler Vue build that
// sets __BROWSER__=false even in browser environments.
// Suppress it so test output stays clean.
const origWarn = console.warn.bind(console)
console.warn = (...args) => {
    if (typeof args[0] === "string" && args[0].includes("decodeEntities")) return
    origWarn(...args)
}

// NAVDEBUG (throwaway branch): name the story that navigates or reloads its iframe mid-run.
import {beforeEach, expect} from "vitest"
let navDebugTest = "(no test yet)"
beforeEach(() => {
    navDebugTest = `${expect.getState().testPath} > ${expect.getState().currentTestName}`
})
const navDebug = (kind, extra) => {
    const payload = {kind, test: navDebugTest, href: window.location.href, ...extra, stack: new Error().stack}
    console.error("[NAVDEBUG]", JSON.stringify(payload))
}
window.navigation?.addEventListener("navigate", (event) => {
    if (event.destination?.sameDocument && event.navigationType !== "reload") return
    navDebug("navigate", {type: event.navigationType, to: event.destination?.url, userInitiated: event.userInitiated})
})
window.addEventListener("beforeunload", () => navDebug("beforeunload"))
document.addEventListener("submit", (event) => navDebug("submit", {action: event.target?.action}), true)
