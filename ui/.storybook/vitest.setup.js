// Story templates are runtime-compiled by Vue in the browser, which triggers
// "@vue/compiler-core: decodeEntities option is passed but will be ignored in
// non-browser builds" — a false-positive from the esm-bundler Vue build that
// sets __BROWSER__=false even in browser environments.
//
// preview.jsx also deliberately re-registers "RouterLink" after vue-router's
// own install() already registered it, to swap in a fake that can't trigger a
// real browser navigation — Vue warns on that re-registration by design, but
// here it's the expected outcome, not a bug.
//
// Suppress both so test output stays clean.
const origWarn = console.warn.bind(console)
console.warn = (...args) => {
    if (typeof args[0] !== "string") return origWarn(...args)
    if (args[0].includes("decodeEntities")) return
    if (args[0].includes("\"RouterLink\" has already been registered")) return
    origWarn(...args)
}
