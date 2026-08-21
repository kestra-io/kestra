import {afterEach, beforeEach, describe, expect, it} from "vitest"
import {defineComponent, h} from "vue"
import {mount, VueWrapper} from "@vue/test-utils"
import {createRouter, createMemoryHistory, type Router} from "vue-router"
import useRestoreUrl from "../../../src/composables/useRestoreUrl"

const SAVED_QUERY = {"filters[timeRange][EQUALS]": "PT24H"}

function createTestRouter(): Router {
    return createRouter({
        history: createMemoryHistory(),
        routes: [
            {name: "home", path: "/:tenant?/dashboards/:dashboard?", component: {template: "<div/>"}},
        ],
    })
}

function mountRestoreUrl(router: Router) {
    return mount(defineComponent({
        setup() {
            return useRestoreUrl()
        },
        render: () => h("div"),
    }), {global: {plugins: [router]}})
}

describe("useRestoreUrl", () => {
    let wrapper: VueWrapper

    beforeEach(() => {
        window.sessionStorage.clear()
    })

    afterEach(() => {
        wrapper?.unmount()
        window.sessionStorage.clear()
    })

    it("restores the saved query when the URL carries none", async () => {
        const router = createTestRouter()
        await router.push({name: "home", params: {tenant: "main", dashboard: "default"}})
        window.sessionStorage.setItem("home_main_restore_url", JSON.stringify(SAVED_QUERY))

        wrapper = mountRestoreUrl(router)
        await router.isReady()
        await new Promise((resolve) => setTimeout(resolve, 150))

        expect(router.currentRoute.value.query).toEqual(SAVED_QUERY)
    })

    it("re-asserts the restored query when the page cancels the restore with its own navigation", async () => {
        const router = createTestRouter()
        await router.push({name: "home", params: {tenant: "main"}})
        window.sessionStorage.setItem("home_main_restore_url", JSON.stringify(SAVED_QUERY))

        wrapper = mountRestoreUrl(router)

        // Mimics Dashboard.vue appending its id param right after mount: this navigation
        // cancels the in-flight restore, which used to drop the saved filters for good.
        await router.push({name: "home", params: {tenant: "main", dashboard: "default"}})
        await new Promise((resolve) => setTimeout(resolve, 150))

        expect(router.currentRoute.value.params.dashboard).toBe("default")
        expect(router.currentRoute.value.query).toEqual(SAVED_QUERY)
    })

    it("leaves an explicit query untouched", async () => {
        const router = createTestRouter()
        const explicit = {"filters[timeRange][EQUALS]": "P7D"}
        await router.push({name: "home", params: {tenant: "main", dashboard: "default"}, query: explicit})
        window.sessionStorage.setItem("home_main_restore_url", JSON.stringify(SAVED_QUERY))

        wrapper = mountRestoreUrl(router)
        await new Promise((resolve) => setTimeout(resolve, 150))

        expect(router.currentRoute.value.query).toEqual(explicit)
    })
})
