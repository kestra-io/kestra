import {describe, it, expect, vi, beforeEach} from "vitest"
import {setActivePinia, createPinia} from "pinia"
import {nextTick} from "vue"
import {setMockClient} from "@kestra-io/kestra-sdk"

// Avoid pulling in the full design-system (monaco-editor) on cold import.
// Provide minimal stubs for the symbols `@kestra-io/topology` reads at module
// top level (`utils/utils.ts` reads stringUtils/durationUtils; `index.ts`
// re-exports State).
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

const axiosGet = vi.fn()
const axiosPost = vi.fn().mockResolvedValue({data: {}})
const axiosPut = vi.fn().mockResolvedValue({data: {}})

// Each `it` re-imports the dashboard store after `vi.resetModules()` (see
// beforeEach). The first cold import under full-suite contention can exceed
// the 5s default, so allow extra headroom.
const TEST_TIMEOUT_MS = 20_000

describe("dashboard store dirty tracking", () => {
    beforeEach(() => {
        vi.resetModules()
        setMockClient({
            get: axiosGet,
            post: axiosPost,
            put: axiosPut,
            delete: vi.fn(),
        })
        axiosGet.mockReset()
        axiosPost.mockReset().mockResolvedValue({data: {}})
        axiosPut.mockReset().mockResolvedValue({data: {}})
        setActivePinia(createPinia())
    })

    it("haveChange is false when source matches origin", {timeout: TEST_TIMEOUT_MS}, async () => {
        const {useDashboardStore} = await import("../../../src/stores/dashboard")
        const dashboardStore = useDashboardStore()

        expect(dashboardStore.haveChange).toBe(false)

        dashboardStore.sourceCode = "id: foo"
        dashboardStore.sourceCodeOrigin = "id: foo"

        expect(dashboardStore.haveChange).toBe(false)
    })

    it("haveChange is true when source diverges from origin", {timeout: TEST_TIMEOUT_MS}, async () => {
        const {useDashboardStore} = await import("../../../src/stores/dashboard")
        const dashboardStore = useDashboardStore()

        dashboardStore.sourceCodeOrigin = "id: foo"
        dashboardStore.sourceCode = "id: bar"

        expect(dashboardStore.haveChange).toBe(true)
    })

    it("syncs unsavedChange to unsavedChangesStore when source changes", {timeout: TEST_TIMEOUT_MS}, async () => {
        const {useDashboardStore} = await import("../../../src/stores/dashboard")
        const {useUnsavedChangesStore} = await import("../../../src/stores/unsavedChanges")
        const dashboardStore = useDashboardStore()
        const unsavedChangesStore = useUnsavedChangesStore()

        expect(unsavedChangesStore.unsavedChange).toBe(false)

        dashboardStore.sourceCode = "id: foo"
        await nextTick()
        expect(unsavedChangesStore.unsavedChange).toBe(true)

        dashboardStore.sourceCodeOrigin = dashboardStore.sourceCode
        await nextTick()
        expect(unsavedChangesStore.unsavedChange).toBe(false)
    })

    it("load seeds sourceCodeOrigin so haveChange stays false after fetch", {timeout: TEST_TIMEOUT_MS}, async () => {
        axiosGet.mockResolvedValueOnce({status: 200, data: {id: "d1", sourceCode: "id: d1"}})

        const {useDashboardStore} = await import("../../../src/stores/dashboard")
        const dashboardStore = useDashboardStore()

        await dashboardStore.load("d1")

        expect(dashboardStore.sourceCode).toBe("id: d1")
        expect(dashboardStore.sourceCodeOrigin).toBe("id: d1")
        expect(dashboardStore.haveChange).toBe(false)
    })

    it("update resets sourceCodeOrigin so haveChange clears post-save", {timeout: TEST_TIMEOUT_MS}, async () => {
        const {useDashboardStore} = await import("../../../src/stores/dashboard")
        const dashboardStore = useDashboardStore()

        dashboardStore.sourceCodeOrigin = "id: d1"
        dashboardStore.sourceCode = "id: d1\ntitle: edited"
        expect(dashboardStore.haveChange).toBe(true)

        await dashboardStore.update({id: "d1", source: dashboardStore.sourceCode})

        expect(dashboardStore.haveChange).toBe(false)
    })
})
