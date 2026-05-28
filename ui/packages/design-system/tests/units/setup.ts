import {vi} from "vitest"
import {AppContext, ref} from "vue"

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

// jsdom doesn't run layout, so ResizeObserver-backed hooks like useElementSize
// would report 0×0 forever, and any v-if gated on dimensions never renders.
// Stub useElementSize to return non-zero dimensions for chart tests.
vi.mock("@vueuse/core", async (importOriginal) => {
    const actual = await importOriginal<typeof import("@vueuse/core")>()
    return {
        ...actual,
        useElementSize: () => ({width: ref(800), height: ref(600)}),
    }
})
