import {vi} from "vitest"
import {type AppContext} from "vue"

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

