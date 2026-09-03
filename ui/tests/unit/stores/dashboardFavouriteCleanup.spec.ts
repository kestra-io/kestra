import {describe, it, expect, vi, beforeEach, afterEach} from "vitest"
import {setActivePinia, createPinia} from "pinia"

vi.mock("@kestra-io/design-system", () => ({
    stringUtils: {afterLastDot: (s: string) => s?.split(".").pop() ?? s},
    durationUtils: {humanDuration: () => "", duration: () => 0},
    State: {},
}))

vi.mock("nprogress", () => ({
    start: vi.fn(),
    done: vi.fn(),
    set: vi.fn(),
    inc: vi.fn(),
}))

vi.mock("vue-router", () => ({
    useRouter: () => ({
        beforeEach: vi.fn(),
        afterEach: vi.fn(),
        replace: vi.fn(),
        push: vi.fn(),
    }),
}))

vi.mock("vue-i18n", () => ({
    useI18n: () => ({t: (key: string) => key}),
}))

const deleteDashboardFn = vi.fn()

vi.mock("@kestra-io/kestra-sdk", () => ({
    useClient: () => ({get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: (...args: any[]) => deleteDashboardFn(...args)}),
}))

vi.mock("@kestra-io/kestra-sdk/dashboards", () => ({}))

vi.mock("override/utils/route", () => ({
    apiUrl: () => "/api/v1/main",
    apiUrlWithoutTenants: () => "/api/v1",
    basePath: () => "/ui/main",
    baseUrl: "/",
}))

const TEST_TIMEOUT_MS = 20_000

const seedBookmarks = (...paths: string[]) => {
    localStorage.setItem("starred.bookmarks", JSON.stringify(paths.map((path) => ({path, label: path}))))
}

const bookmarkedPaths = async () => {
    const {useBookmarksStore} = await import("../../../src/stores/bookmarks")
    return useBookmarksStore().pages.map((page) => page.path)
}

describe("dashboard store favourite cleanup", () => {
    beforeEach(() => {
        vi.resetModules()
        deleteDashboardFn.mockReset()
        deleteDashboardFn.mockResolvedValue({data: {}})
        localStorage.clear()
        setActivePinia(createPinia())
    })

    afterEach(() => {
        localStorage.clear()
    })

    it("drops the favourites pointing at a deleted dashboard", {timeout: TEST_TIMEOUT_MS}, async () => {
        seedBookmarks(
            "/main/dashboards/dash1",
            "/main/dashboards/dash1/edit",
            "/main/dashboards/dash2/edit",
            "/main/flows/edit/my.namespace/my-flow",
        )

        const {useDashboardStore} = await import("../../../src/stores/dashboard")
        await useDashboardStore().delete("dash1")

        expect(await bookmarkedPaths()).toEqual([
            "/main/dashboards/dash2/edit",
            "/main/flows/edit/my.namespace/my-flow",
        ])
    })

    it("keeps the favourites of a dashboard whose id only shares a prefix", {timeout: TEST_TIMEOUT_MS}, async () => {
        seedBookmarks("/main/dashboards/dash/edit", "/main/dashboards/dash1/edit")

        const {useDashboardStore} = await import("../../../src/stores/dashboard")
        await useDashboardStore().delete("dash")

        expect(await bookmarkedPaths()).toEqual(["/main/dashboards/dash1/edit"])
    })

    it("drops the favourite of a dashboard whose id is url encoded in the path", {timeout: TEST_TIMEOUT_MS}, async () => {
        seedBookmarks("/main/dashboards/my%20dash/edit")

        const {useDashboardStore} = await import("../../../src/stores/dashboard")
        await useDashboardStore().delete("my dash")

        expect(await bookmarkedPaths()).toEqual([])
    })

    it("keeps the favourites when the deletion fails", {timeout: TEST_TIMEOUT_MS}, async () => {
        const error = Object.assign(new Error("Server Error"), {status: 500})
        deleteDashboardFn.mockRejectedValueOnce(error)
        seedBookmarks("/main/dashboards/dash1/edit")

        const {useDashboardStore} = await import("../../../src/stores/dashboard")
        await expect(useDashboardStore().delete("dash1")).rejects.toBe(error)

        expect(await bookmarkedPaths()).toEqual(["/main/dashboards/dash1/edit"])
    })
})
