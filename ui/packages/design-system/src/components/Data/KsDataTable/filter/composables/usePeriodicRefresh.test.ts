import {afterEach, beforeEach, describe, expect, test, vi} from "vitest"
import {defineComponent, h, nextTick} from "vue"
import {createMemoryHistory, createRouter} from "vue-router"
import type {VueWrapper} from "@vue/test-utils"
import {i18nMount} from "../../../../../../tests/units/i18nMount"

const ENABLED_KEY = "autoRefreshEnabled_logs"
const INTERVAL_MS = 10_000

let apps: VueWrapper[] = []

const mountSettingUp = async (setup: () => void) => {
    const page = {render: () => null}
    const router = createRouter({
        history: createMemoryHistory(),
        routes: [
            {path: "/logs", name: "logs", component: page},
            {path: "/executions", name: "executions", component: page},
        ],
    })
    await router.push({name: "logs"})

    const app = i18nMount(
        defineComponent({
            setup() {
                setup()
                return () => h("div")
            },
        }),
        {global: {plugins: [router]}},
    )
    apps.push(app)
    return {app, router}
}

const mountRefresh = async (callback?: () => void) => {
    const {usePeriodicRefresh} = await import("./usePeriodicRefresh")
    let refresh!: ReturnType<typeof usePeriodicRefresh>
    const mounted = await mountSettingUp(() => {
        refresh = usePeriodicRefresh(callback)
    })
    return {...mounted, refresh}
}

beforeEach(() => {
    vi.resetModules()
    vi.useFakeTimers()
    localStorage.clear()
    apps = []
})

afterEach(() => {
    apps.forEach((app) => app.unmount())
    vi.clearAllTimers()
    vi.useRealTimers()
    localStorage.clear()
})

describe("usePeriodicRefresh", () => {
    test("shouldNamespaceEnabledKeyByRouteName", async () => {
        const {getAutoRefreshEnabledKey} = await import("./usePeriodicRefresh")

        expect(getAutoRefreshEnabledKey("executions")).toBe("autoRefreshEnabled_executions")
    })

    // https://github.com/kestra-io/kestra/issues/20526: the interval used to be created only inside the
    // settings popover, so it died with the popover and never started on page load.
    // https://github.com/kestra-io/kestra/issues/20525: the tick called the refresh callback twice.
    test("shouldRefreshOncePerIntervalWhenEnabledFlagWasLeftOn", async () => {
        localStorage.setItem(ENABLED_KEY, "true")
        const {usePeriodicRefresh} = await import("./usePeriodicRefresh")
        const refresh = vi.fn()

        await mountSettingUp(() => usePeriodicRefresh(refresh))
        vi.advanceTimersByTime(INTERVAL_MS * 3)

        expect(refresh).toHaveBeenCalledTimes(3)
    })

    test("shouldKeepRefreshingAfterSettingsPanelUnmounts", async () => {
        localStorage.setItem(ENABLED_KEY, "true")
        const {usePeriodicRefresh} = await import("./usePeriodicRefresh")
        const refresh = vi.fn()

        await mountSettingUp(() => usePeriodicRefresh(refresh))
        const {app: settingsPanel} = await mountSettingUp(() => usePeriodicRefresh())
        settingsPanel.unmount()

        vi.advanceTimersByTime(INTERVAL_MS * 2)

        expect(refresh).toHaveBeenCalledTimes(2)
    })

    test("shouldStopWhenDisabledAndRestartWhenEnabledAgain", async () => {
        localStorage.setItem(ENABLED_KEY, "true")
        const {usePeriodicRefresh} = await import("./usePeriodicRefresh")
        const refresh = vi.fn()
        let controls: ReturnType<typeof usePeriodicRefresh> | undefined

        await mountSettingUp(() => {
            controls = usePeriodicRefresh(refresh)
        })

        vi.advanceTimersByTime(INTERVAL_MS * 2)
        expect(refresh).toHaveBeenCalledTimes(2)

        controls!.isEnabled.value = false
        await nextTick()
        vi.advanceTimersByTime(INTERVAL_MS * 5)
        expect(refresh).toHaveBeenCalledTimes(2)

        controls!.isEnabled.value = true
        await nextTick()
        vi.advanceTimersByTime(INTERVAL_MS * 2)
        expect(refresh).toHaveBeenCalledTimes(4)
    })

    test.each([
        {stored: "true", enabled: true},
        {stored: "false", enabled: false},
        {stored: null, enabled: false},
    ])("reads the stored enabled flag $stored on mount", async ({stored, enabled}) => {
        if (stored !== null) localStorage.setItem(ENABLED_KEY, stored)
        localStorage.setItem("autoRefreshEnabled_executions", String(!enabled))

        const {refresh} = await mountRefresh()

        expect(refresh.isEnabled.value).toBe(enabled)
    })

    test("persists enabled and disabled values under the current route key", async () => {
        const {refresh} = await mountRefresh()

        refresh.isEnabled.value = true
        expect(localStorage.getItem(ENABLED_KEY)).toBe("true")

        refresh.isEnabled.value = false
        expect(localStorage.getItem(ENABLED_KEY)).toBe("false")
        expect(localStorage.getItem("autoRefreshEnabled_executions")).toBeNull()
    })

    test("keeps each page's enabled flag independent when navigating", async () => {
        localStorage.setItem(ENABLED_KEY, "true")
        localStorage.setItem("autoRefreshEnabled_executions", "false")
        const {refresh, router} = await mountRefresh()
        expect(refresh.isEnabled.value).toBe(true)

        await router.push({name: "executions"})
        await nextTick()
        expect(refresh.isEnabled.value).toBe(false)
        refresh.isEnabled.value = true

        await router.push({name: "logs"})
        await nextTick()
        expect(refresh.isEnabled.value).toBe(true)
        refresh.isEnabled.value = false
        expect(localStorage.getItem(ENABLED_KEY)).toBe("false")
        expect(localStorage.getItem("autoRefreshEnabled_executions")).toBe("true")

        await router.push({name: "executions"})
        await nextTick()
        expect(refresh.isEnabled.value).toBe(true)

        await router.push({name: "logs"})
        await nextTick()
        expect(refresh.isEnabled.value).toBe(false)
    })

    test.each([
        {stored: null, seconds: 10},
        {stored: "3", seconds: 3},
    ])("refreshes every $seconds seconds with stored interval $stored", async ({stored, seconds}) => {
        if (stored !== null) localStorage.setItem("autoRefreshInterval", stored)
        const callback = vi.fn()
        const {refresh} = await mountRefresh(callback)

        vi.advanceTimersByTime(seconds * 1000)
        expect(callback).not.toHaveBeenCalled()

        refresh.isEnabled.value = true
        await nextTick()
        vi.advanceTimersByTime(seconds * 1000 - 1)
        expect(callback).not.toHaveBeenCalled()

        vi.advanceTimersByTime(1)
        expect(callback).toHaveBeenCalledOnce()

        vi.advanceTimersByTime(seconds * 1000)
        expect(callback).toHaveBeenCalledTimes(2)
    })

    test("clears the interval when the page unmounts", async () => {
        localStorage.setItem(ENABLED_KEY, "true")
        const callback = vi.fn()
        const {app} = await mountRefresh(callback)

        vi.advanceTimersByTime(INTERVAL_MS)
        expect(callback).toHaveBeenCalledOnce()

        app.unmount()
        vi.advanceTimersByTime(INTERVAL_MS * 2)
        expect(callback).toHaveBeenCalledOnce()
    })
})
