import {createMemoryHistory, createRouter} from "vue-router"

/**
 * A router for a spec that never navigates: `useRoute()` and `useRouter()` resolve instead of
 * warning, and the catch-all route keeps the initial navigation from reporting a missing match.
 */
export const memoryRouter = () => createRouter({
    history: createMemoryHistory(),
    routes: [{path: "/:pathMatch(.*)*", component: {render: () => null}}],
})
