import {createApp, nextTick, type App} from "vue"
import {createMemoryHistory, createRouter} from "vue-router"
import {afterEach, beforeEach, describe, expect, test, vi} from "vitest"
import {getAutoRefreshEnabledKey, usePeriodicRefresh} from "./usePeriodicRefresh"

let app: App | undefined

const mountRefresh = async () => {
    const page = {render: () => null}
    const router = createRouter({
        history: createMemoryHistory(),
        routes: [
            {path: "/flows", name: "flows", component: page},
            {path: "/executions", name: "executions", component: page},
        ],
    })
    await router.push({name: "flows"})

    let refresh!: ReturnType<typeof usePeriodicRefresh>
    app = createApp({
        setup() {
            refresh = usePeriodicRefresh()
            return () => null
        },
    })
    app.use(router)
    app.mount(document.createElement("div"))

    return {refresh, router}
}

describe("usePeriodicRefresh", () => {
    beforeEach(() => {
        localStorage.clear()
        vi.useFakeTimers()
    })

    afterEach(() => {
        app?.unmount()
        app = undefined
        vi.clearAllTimers()
        vi.useRealTimers()
        localStorage.clear()
    })

    test("namespaces the enabled storage key by route name", () => {
        expect(getAutoRefreshEnabledKey("flows")).toBe("autoRefreshEnabled_flows")
        expect(getAutoRefreshEnabledKey("executions")).toBe("autoRefreshEnabled_executions")
    })

    test.each([
        {stored: "true", enabled: true},
        {stored: "false", enabled: false},
        {stored: null, enabled: false},
    ])("reads the stored enabled flag $stored on mount", async ({stored, enabled}) => {
        if (stored !== null) localStorage.setItem("autoRefreshEnabled_flows", stored)
        localStorage.setItem("autoRefreshEnabled_executions", String(!enabled))

        const {refresh} = await mountRefresh()

        expect(refresh.isEnabled.value).toBe(enabled)
    })

    test("persists enabled and disabled values under the current route key", async () => {
        const {refresh} = await mountRefresh()

        refresh.isEnabled.value = true
        expect(localStorage.getItem("autoRefreshEnabled_flows")).toBe("true")

        refresh.isEnabled.value = false
        expect(localStorage.getItem("autoRefreshEnabled_flows")).toBe("false")
        expect(localStorage.getItem("autoRefreshEnabled_executions")).toBeNull()
    })

    test("keeps each page's enabled flag independent when navigating", async () => {
        localStorage.setItem("autoRefreshEnabled_flows", "true")
        localStorage.setItem("autoRefreshEnabled_executions", "false")
        const {refresh, router} = await mountRefresh()
        expect(refresh.isEnabled.value).toBe(true)

        await router.push({name: "executions"})
        await nextTick()
        expect(refresh.isEnabled.value).toBe(false)
        refresh.isEnabled.value = true

        await router.push({name: "flows"})
        await nextTick()
        expect(refresh.isEnabled.value).toBe(true)
        refresh.isEnabled.value = false
        expect(localStorage.getItem("autoRefreshEnabled_flows")).toBe("false")
        expect(localStorage.getItem("autoRefreshEnabled_executions")).toBe("true")

        await router.push({name: "executions"})
        await nextTick()
        expect(refresh.isEnabled.value).toBe(true)

        await router.push({name: "flows"})
        await nextTick()
        expect(refresh.isEnabled.value).toBe(false)
    })

    test.each([
        {stored: null, seconds: 10},
        {stored: "3", seconds: 3},
    ])("refreshes every $seconds seconds with stored interval $stored", async ({stored, seconds}) => {
        if (stored !== null) localStorage.setItem("autoRefreshInterval", stored)
        const {refresh} = await mountRefresh()
        const callback = vi.fn()

        refresh.toggleRefresh(true, callback)
        vi.advanceTimersByTime(seconds * 1000 - 1)
        expect(callback).not.toHaveBeenCalled()

        vi.advanceTimersByTime(1)
        expect(callback).toHaveBeenCalledOnce()

        vi.advanceTimersByTime(seconds * 1000)
        expect(callback).toHaveBeenCalledTimes(2)
    })

    test("stops refreshing when toggled off", async () => {
        const {refresh} = await mountRefresh()
        const callback = vi.fn()

        refresh.toggleRefresh(true, callback)
        vi.advanceTimersByTime(10000)
        expect(callback).toHaveBeenCalledOnce()

        refresh.toggleRefresh(false, callback)
        vi.advanceTimersByTime(20000)
        expect(callback).toHaveBeenCalledOnce()
    })

    test("replaces the running interval when toggled on twice", async () => {
        const {refresh} = await mountRefresh()
        const callback = vi.fn()

        refresh.toggleRefresh(true, callback)
        vi.advanceTimersByTime(5000)
        refresh.toggleRefresh(true, callback)
        vi.advanceTimersByTime(5000)
        expect(callback).not.toHaveBeenCalled()

        vi.advanceTimersByTime(5000)
        expect(callback).toHaveBeenCalledOnce()

        vi.advanceTimersByTime(10000)
        expect(callback).toHaveBeenCalledTimes(2)
    })

    test("clears the interval on unmount", async () => {
        const {refresh} = await mountRefresh()
        const callback = vi.fn()

        refresh.toggleRefresh(true, callback)
        vi.advanceTimersByTime(10000)
        expect(callback).toHaveBeenCalledOnce()

        app?.unmount()
        app = undefined
        vi.advanceTimersByTime(20000)
        expect(callback).toHaveBeenCalledOnce()
    })
})
