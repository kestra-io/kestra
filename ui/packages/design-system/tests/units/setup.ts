import {vi} from "vitest"
import {AppContext, ref} from "vue"
import {config} from "@vue/test-utils"

// Most unit tests mount a component in isolation, without installing vue-router,
// so a literal <router-link> in its template (or a dynamic :is="'router-link'")
// can never resolve and spams "[Vue warn]: Failed to resolve component:
// router-link" on every mount. Register a minimal fake globally so it resolves
// like the real component would.
config.global.stubs = {
    ...config.global.stubs,
    RouterLink: {
        name: "RouterLink",
        props: ["to"],
        template: "<a><slot /></a>",
    },
}

// monaco-editor probes browser APIs jsdom doesn't ship with.
if (typeof document !== "undefined" && typeof document.queryCommandSupported !== "function") {
    document.queryCommandSupported = () => false
}
if (typeof window !== "undefined" && typeof window.matchMedia !== "function") {
    window.matchMedia = (query: string) => ({
        matches: false,
        media: query,
        onchange: null,
        addListener: () => {},
        removeListener: () => {},
        addEventListener: () => {},
        removeEventListener: () => {},
        dispatchEvent: () => false,
    })
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
