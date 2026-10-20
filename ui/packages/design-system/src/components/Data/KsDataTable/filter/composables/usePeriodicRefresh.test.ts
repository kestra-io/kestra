import {afterEach, beforeEach, describe, expect, test, vi} from "vitest"
import {createApp, defineComponent, h, nextTick} from "vue"
import type {App} from "vue"
import {createI18n} from "vue-i18n"

vi.mock("vue-router", () => ({
    useRoute: () => ({name: "logs", path: "/logs"}),
}))

const ENABLED_KEY = "autoRefreshEnabled_logs"
const INTERVAL_MS = 10_000

let apps: App[] = []

const mountSettingUp = (setup: () => void) => {
    const app = createApp(
        defineComponent({
            setup() {
                setup()
                return () => h("div")
            },
        }),
    )
    app.use(createI18n({legacy: false, locale: "en", missingWarn: false, fallbackWarn: false}))
    app.mount(document.createElement("div"))
    apps.push(app)
    return app
}

beforeEach(() => {
    vi.resetModules()
    vi.useFakeTimers()
    localStorage.clear()
    apps = []
})

afterEach(() => {
    apps.forEach((app) => app.unmount())
    vi.useRealTimers()
})

describe("usePeriodicRefresh", () => {
    // https://github.com/kestra-io/kestra/issues/20526: the interval used to be created only inside the
    // settings popover, so it died with the popover and never started on page load.
    // https://github.com/kestra-io/kestra/issues/20525: the tick called the refresh callback twice.
    test("shouldRefreshOncePerIntervalWhenEnabledFlagWasLeftOn", async () => {
        localStorage.setItem(ENABLED_KEY, "true")
        const {usePeriodicRefresh} = await import("./usePeriodicRefresh")
        const refresh = vi.fn()

        mountSettingUp(() => usePeriodicRefresh(refresh))
        vi.advanceTimersByTime(INTERVAL_MS * 3)

        expect(refresh).toHaveBeenCalledTimes(3)
    })

    test("shouldKeepRefreshingAfterSettingsPanelUnmounts", async () => {
        localStorage.setItem(ENABLED_KEY, "true")
        const {usePeriodicRefresh} = await import("./usePeriodicRefresh")
        const refresh = vi.fn()

        mountSettingUp(() => usePeriodicRefresh(refresh))
        const settingsPanel = mountSettingUp(() => usePeriodicRefresh())
        settingsPanel.unmount()

        vi.advanceTimersByTime(INTERVAL_MS * 2)

        expect(refresh).toHaveBeenCalledTimes(2)
    })

    test("shouldStopWhenDisabledAndRestartWhenEnabledAgain", async () => {
        localStorage.setItem(ENABLED_KEY, "true")
        const {usePeriodicRefresh} = await import("./usePeriodicRefresh")
        const refresh = vi.fn()
        let controls: ReturnType<typeof usePeriodicRefresh> | undefined

        mountSettingUp(() => {
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
})
