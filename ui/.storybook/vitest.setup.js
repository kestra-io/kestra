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
