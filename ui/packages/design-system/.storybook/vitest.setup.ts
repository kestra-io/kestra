import {vi} from "vitest"
import {type AppContext} from "vue"
import {type Environment} from "monaco-editor"

declare global {
    var _VSCODE_FILE_ROOT: string
    var MonacoEnvironment: Environment | undefined
}
// monaco-editor's `editor.api` resolves worker URLs at module load via
// FileAccess.toUri, which dereferences `globalThis._VSCODE_FILE_ROOT` or
// a require-shim. Neither exists under sb-vitest's browser runner, so set
// a dummy root so the module can load. Workers themselves are not exercised
// in storybook tests.
globalThis._VSCODE_FILE_ROOT = "/"
globalThis.MonacoEnvironment = globalThis.MonacoEnvironment ?? {
    // getWorker: () => ({postMessage: () => {}, terminate: () => {}, addEventListener: () => {}, removeEventListener: () => {}}),
    getWorker: () => Object.create(Worker.prototype) as Worker,

}

vi.mock("vue-i18n", () => ({
  useI18n: () => ({
    t: (key:string) => key,
  }),
  createI18n: () => ({
    install(app:AppContext) {
      app.config.globalProperties.$t = (key:string) => key
    },
  }),
}))

// Story templates are runtime-compiled by Vue in the browser, which triggers
// "@vue/compiler-core: decodeEntities option is passed but will be ignored in
// non-browser builds" — a false-positive from the esm-bundler Vue build that
// sets __BROWSER__=false even in browser environments.
const warnBak = console.warn.bind(console)
console.warn = (...args: unknown[]) => {
    if (typeof args[0] === "string" && args[0].includes("decodeEntities")) return
    warnBak(...args)
}

