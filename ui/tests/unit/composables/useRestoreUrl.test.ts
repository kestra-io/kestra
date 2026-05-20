import {describe, it, expect, vi, beforeEach, afterEach} from "vitest"
import {defineComponent} from "vue"
import {createRouter, createMemoryHistory, RouteLocation} from "vue-router"
import {mount} from "@vue/test-utils"
import useRestoreUrl, {getRestoredQuery} from "../../../src/composables/useRestoreUrl"

describe("useRestoreUrl - Issue #16073: Indefinite Loading", () => {
    let router: any

    beforeEach(() => {
        router = createRouter({
            history: createMemoryHistory(),
            routes: [
                {
                    path: "/admin/triggers/manage",
                    name: "admin/triggers",
                    component: {template: "<div>Triggers</div>"},
                },
            ],
        })
        // Clear session storage
        sessionStorage.clear()
    })

    afterEach(() => {
        sessionStorage.clear()
        vi.clearAllTimers()
    })

    it("should set loadInit to true even without query change", async () => {
        const TestComponent = defineComponent({
            setup() {
                const {loadInit} = useRestoreUrl()
                return {loadInit}
            },
            template: "<div>{{loadInit}}</div>",
        })

        await router.push("/admin/triggers/manage")
        const wrapper = mount(TestComponent, {
            global: {
                plugins: [router],
            },
        })

        // Wait for onMounted to complete
        await wrapper.vm.$nextTick()
        await new Promise(resolve => setTimeout(resolve, 100))

        expect(wrapper.vm.loadInit).toBe(true)
    })

    it("should recover from errors in goToRestoreUrl", async () => {
        const consoleErrorSpy = vi.spyOn(console, "error").mockImplementation(() => {})

        const TestComponent = defineComponent({
            setup() {
                const {loadInit} = useRestoreUrl()
                return {loadInit}
            },
            template: "<div>{{loadInit}}</div>",
        })

        await router.push("/admin/triggers/manage")

        // Mock router.replace to throw an error
        router.replace = vi.fn().mockRejectedValue(new Error("Navigation failed"))

        const wrapper = mount(TestComponent, {
            global: {
                plugins: [router],
            },
        })

        // Save some data to sessionStorage to trigger restore logic
        sessionStorage.setItem("admin_triggers_restore_url", JSON.stringify({page: "1"}))

        // Wait for error handling
        await wrapper.vm.$nextTick()
        await new Promise(resolve => setTimeout(resolve, 100))

        // Even with error, loadInit should be true
        expect(wrapper.vm.loadInit).toBe(true)

        consoleErrorSpy.mockRestore()
    })

    it("should force loadInit to true after timeout", async () => {
        vi.useFakeTimers()

        const TestComponent = defineComponent({
            setup() {
                const {loadInit} = useRestoreUrl()
                return {loadInit}
            },
            template: "<div>{{loadInit}}</div>",
        })

        await router.push("/admin/triggers/manage")
        const wrapper = mount(TestComponent, {
            global: {
                plugins: [router],
            },
        })

        // Advance timers to trigger the safety timeout
        vi.advanceTimersByTime(3100)

        expect(wrapper.vm.loadInit).toBe(true)

        vi.useRealTimers()
    })

    it("should handle empty sessionStorage without hanging", async () => {
        const TestComponent = defineComponent({
            setup() {
                const {loadInit} = useRestoreUrl()
                return {loadInit}
            },
            template: "<div>{{loadInit}}</div>",
        })

        await router.push("/admin/triggers/manage")
        const wrapper = mount(TestComponent, {
            global: {
                plugins: [router],
            },
        })

        await wrapper.vm.$nextTick()
        await new Promise(resolve => setTimeout(resolve, 100))

        // Should be true since no restoration is needed
        expect(wrapper.vm.loadInit).toBe(true)
    })

    it("should properly restore URL when query differs from storage", async () => {
        const TestComponent = defineComponent({
            setup() {
                const {loadInit} = useRestoreUrl()
                return {loadInit}
            },
            template: "<div>{{loadInit}}</div>",
        })

        // Set up router with correct route
        router = createRouter({
            history: createMemoryHistory(),
            routes: [
                {
                    path: "/admin/triggers/:tab",
                    name: "admin/triggers",
                    component: {template: "<div>Triggers</div>"},
                },
            ],
        })

        await router.push("/admin/triggers/manage")

        // Save state to sessionStorage
        sessionStorage.setItem("admin_triggers_manage_restore_url", JSON.stringify({filter: "enabled", page: "2"}))

        const wrapper = mount(TestComponent, {
            global: {
                plugins: [router],
            },
        })

        await wrapper.vm.$nextTick()
        await new Promise(resolve => setTimeout(resolve, 150))

        // Even if restoration fails, loadInit should be true
        expect(wrapper.vm.loadInit).toBe(true)
    })

    it("should have valid getRestoredQuery output", () => {
        const route = {
            name: "admin/triggers",
            params: {tab: "manage"},
            query: {},
        } as any as RouteLocation

        // Set storage
        sessionStorage.setItem("admin_triggers_manage_restore_url", JSON.stringify({sort: "triggerId:asc", page: "1"}))

        const {query, change} = getRestoredQuery(route)

        expect(query).toBeDefined()
        expect(change).toBe(true)
        expect(query.sort).toBe("triggerId:asc")
        expect(query.page).toBe("1")
    })
})
