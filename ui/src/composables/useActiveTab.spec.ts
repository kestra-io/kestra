import {describe, it, expect} from "vitest"
import {createApp} from "vue"
import {createRouter, createMemoryHistory} from "vue-router"

import {useActiveTab} from "./useActiveTab"

function buildRouter() {
    return createRouter({
        history: createMemoryHistory(),
        routes: [
            {path: "/legacy/:tab", name: "legacy", component: {template: "<div/>"}},
            {path: "/new", name: "new", meta: {tab: "new-tab"}, component: {template: "<div/>"}},
            {path: "/both/:tab", name: "both", meta: {tab: "meta-tab"}, component: {template: "<div/>"}},
            {path: "/empty", name: "empty", component: {template: "<div/>"}},
        ],
    })
}

function createTestApp(router: ReturnType<typeof buildRouter>) {
    let activeTab!: ReturnType<typeof useActiveTab>

    const app = createApp({
        setup() {
            activeTab = useActiveTab()
            return {activeTab}
        },
        template: "<div>{{ activeTab }}</div>",
    })

    app.use(router)
    app.mount(document.createElement("div"))

    return {app, activeTab}
}

describe("useActiveTab", () => {
    it("returns route.meta.tab when present", async () => {
        const router = buildRouter()
        const {app, activeTab} = createTestApp(router)

        await router.push({name: "new"})
        await router.isReady()

        expect(activeTab.value).toBe("new-tab")

        app.unmount()
    })
    it("returns route.params.tab when meta.tab is not present", async () => {
        const router = buildRouter()
        const {app, activeTab} = createTestApp(router)

        await router.push({name: "legacy", params: {tab: "old-tab"}})
        await router.isReady()

        expect(activeTab.value).toBe("old-tab")

        app.unmount()
    })

    it("prefers route.meta.tab when both are present", async () => {
        const router = buildRouter()
        const {app, activeTab} = createTestApp(router)

        await router.push({name: "both", params: {tab: "old-tab"}})
        await router.isReady()

        expect(activeTab.value).toBe("meta-tab")

        app.unmount()
    })

    it("returns undefined when neither tab is present", async () => {
        const router = buildRouter()
        const {app, activeTab} = createTestApp(router)

        await router.push({name: "empty"})
        await router.isReady()

        expect(activeTab.value).toBeUndefined()

        app.unmount()
    })
    it("updates when the route changes", async () => {
        const router = buildRouter()
        const {app, activeTab} = createTestApp(router)

        await router.push({name: "new"})
        await router.isReady()

        expect(activeTab.value).toBe("new-tab")

        await router.push({name: "both", params: {tab: "old-tab"}})

        expect(activeTab.value).toBe("meta-tab")

        app.unmount()
    })
})