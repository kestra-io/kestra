import {vi} from "vitest"
import {createApp} from "vue"
import {createPinia, type Pinia} from "pinia"
import type {Router} from "vue-router"

// The actual module, so the default router still works in a spec that mocks vue-router.
const {createMemoryHistory, createRouter} = await vi.importActual<typeof import("vue-router")>("vue-router")

/**
 * A router for a spec that never navigates: `useRoute()` and `useRouter()` resolve instead of
 * warning, and the catch-all route keeps the initial navigation from reporting a missing match.
 */
export const memoryRouter = (): Router => createRouter({
    history: createMemoryHistory(),
    routes: [{path: "/:pathMatch(.*)*", component: {render: () => null}}],
})

/** True for a router passed to `global.plugins` on its own or as a `[router, ...options]` tuple. */
/** A pinia whose stores can call `useRoute()` at setup outside a component, as they do inside the app. */
export const routedPinia = (): Pinia => {
    const pinia = createPinia()
    createApp({}).use(pinia).use(memoryRouter())
    return pinia
}

export const isRouter = (plugin: unknown): boolean => {
    const candidate = Array.isArray(plugin) ? plugin[0] : plugin
    return typeof candidate === "object" && candidate !== null && "currentRoute" in candidate && "install" in candidate
}
