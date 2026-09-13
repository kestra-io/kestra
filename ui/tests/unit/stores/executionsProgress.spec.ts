import {beforeEach, describe, expect, it, vi} from "vitest"
import {createPinia, setActivePinia} from "pinia"

vi.mock("vue-router", () => ({
    useRoute: () => ({query: {}, params: {}}),
    useRouter: () => ({
        push: vi.fn(),
        replace: vi.fn(),
        beforeEach: vi.fn(),
        afterEach: vi.fn(),
    }),
}))

vi.mock("@kestra-io/kestra-sdk", () => ({
    useClient: () => ({
        get: vi.fn(),
        post: vi.fn(),
        put: vi.fn(),
        patch: vi.fn(),
        delete: vi.fn(),
    }),
}))

// static import: the store module drags in heavy singletons (e.g. Monaco); re-importing it
// per test via vi.resetModules() re-runs those singleton registrations and throws
const {useExecutionsStore} = await import("../../../src/stores/executions")

describe("executions store progress events", () => {
    beforeEach(() => {
        setActivePinia(createPinia())
    })

    it("addProgressEvent appends a new (taskRunId, step) pair", () => {
        const store = useExecutionsStore()

        store.addProgressEvent({taskId: "launch", taskRunId: "tr-1", step: "pod.created", timestamp: "2026-07-01T10:00:00Z"})

        expect(store.progressEvents).toEqual([
            {taskId: "launch", taskRunId: "tr-1", step: "pod.created", timestamp: "2026-07-01T10:00:00Z"},
        ])
    })

    it("addProgressEvent dedupes on (taskRunId, step) without duplicating entries", () => {
        const store = useExecutionsStore()

        store.addProgressEvent({taskId: "launch", taskRunId: "tr-1", step: "pod.created", timestamp: "2026-07-01T10:00:00Z"})
        // same taskRunId+step arriving again (e.g. SSE reconnect replay) must not duplicate
        store.addProgressEvent({taskId: "launch", taskRunId: "tr-1", step: "pod.created", timestamp: "2026-07-01T10:00:00Z"})

        expect(store.progressEvents).toHaveLength(1)
    })

    it("addProgressEvent overwrites a stale timestamp when a task retries", () => {
        const store = useExecutionsStore()

        // first attempt
        store.addProgressEvent({taskId: "launch", taskRunId: "tr-1", step: "pod.created", timestamp: "2026-07-01T10:00:00Z"})
        // retry reuses the same taskRunId but reports a later, correct timestamp — must replace,
        // not be silently dropped, or the UI gets stuck showing the failed attempt's numbers
        store.addProgressEvent({taskId: "launch", taskRunId: "tr-1", step: "pod.created", timestamp: "2026-07-01T10:00:30Z"})

        expect(store.progressEvents).toHaveLength(1)
        expect(store.progressEvents[0].timestamp).toBe("2026-07-01T10:00:30Z")
    })

    it("addProgressEvent keeps distinct steps and distinct taskRunIds separate", () => {
        const store = useExecutionsStore()

        store.addProgressEvent({taskId: "launch", taskRunId: "tr-1", step: "pod.created", timestamp: "t0"})
        store.addProgressEvent({taskId: "launch", taskRunId: "tr-1", step: "pod.scheduled", timestamp: "t1"})
        store.addProgressEvent({taskId: "launch", taskRunId: "tr-2", step: "pod.created", timestamp: "t2"})

        expect(store.progressEvents).toHaveLength(3)
    })
})
