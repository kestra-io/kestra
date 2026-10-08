import {beforeEach, describe, expect, it} from "vitest"
import {createPinia, setActivePinia} from "pinia"
import {createMemoryHistory, createRouter, type RouteLocationRaw} from "vue-router"
import {activeScopeTab, useRouteTabsStore, type RouteTab} from "../../../src/stores/routeTabs"

const Empty = {template: "<div />"}
const router = createRouter({
    history: createMemoryHistory(),
    routes: [
        {path: "/admin", name: "admin", component: Empty},
        {path: "/admin/stats/:type?", name: "admin/stats", component: Empty},
        {path: "/flows/edit/:namespace/:id/:tab?", name: "flows/update", component: Empty},
    ],
})

const tab = (title: string, route: RouteLocationRaw, extra: Partial<RouteTab> = {}): RouteTab => ({title, route, ...extra})
const activeAt = async (path: string, tabs: RouteTab[]) => {
    await router.push(path)
    return activeScopeTab(router.currentRoute.value, tabs, router)?.title
}

describe("activeScopeTab", () => {
    it("picks the tab with the longest route the page sits under, never a header or an excluded tab", async () => {
        const tabs = [
            tab("Admin", "/admin"),
            tab("Stats", "/admin/stats"),
            tab("Header", "/admin/stats/instance", {header: true}),
            tab("Excluded", "/admin/stats/instance", {excludeFromScope: true}),
        ]

        expect(await activeAt("/admin/stats/instance", tabs)).toBe("Stats")
    })

    it("keeps an entity's tab active across its sub-views, but not on another entity", async () => {
        const tabs = [tab("Hello", {name: "flows/update", params: {namespace: "company", id: "hello", tab: "overview"}})]

        expect(await activeAt("/flows/edit/company/hello/logs", tabs)).toBe("Hello")
        expect(await activeAt("/flows/edit/company/other/logs", tabs)).toBeUndefined()
    })
})

describe("route tabs store", () => {
    beforeEach(() => setActivePinia(createPinia()))

    it("lets only the owner that set the tabs clear them", () => {
        const store = useRouteTabsStore()
        const owner = Symbol("owner")
        store.setTabs({ownerId: owner, tabs: [tab("Stats", "/admin/stats")]})

        store.clearTabsIfOwner(Symbol("another page"))
        expect(store.tabs).toHaveLength(1)

        store.clearTabsIfOwner(owner)
        expect(store.tabs).toHaveLength(0)
    })
})
