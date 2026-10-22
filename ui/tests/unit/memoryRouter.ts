import {vi} from "vitest"
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

export const isRouter = (plugin: unknown): boolean =>
    typeof plugin === "object" && plugin !== null && "currentRoute" in plugin && "install" in plugin
