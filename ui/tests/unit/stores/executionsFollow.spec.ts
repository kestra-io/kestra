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

const {followExecutionMock} = vi.hoisted(() => ({followExecutionMock: vi.fn()}))
vi.mock("@kestra-io/kestra-sdk/executions", () => ({
    followExecution: followExecutionMock,
}))

// Build a fake SDK follow stream: the SDK fires `onSseEvent` for each event (exposing its id)
// right before yielding the already-parsed execution on the async stream.
type FakeEvent = { sseId: string; execution: Record<string, unknown> }
function fakeFollowStream(events: FakeEvent[]) {
    return (_params: unknown, options: {onSseEvent?: (e: {id?: string}) => void}) =>
        Promise.resolve({
            stream: (async function* () {
                for (const event of events) {
                    options.onSseEvent?.({id: event.sseId})
                    yield event.execution
                }
            })(),
        })
}

// static import: the store module drags in heavy singletons (e.g. Monaco); re-importing it
// per test via vi.resetModules() re-runs those singleton registrations and throws
const {useExecutionsStore} = await import("../../../src/stores/executions")

describe("executions store follow stream", () => {
    beforeEach(() => {
        setActivePinia(createPinia())
        followExecutionMock.mockReset()
    })

    it("skips the start stub, forwards real events, and ends without error on completion", async () => {
        followExecutionMock.mockImplementation(fakeFollowStream([
            {sseId: "start", execution: {id: "exec-1"}}, // stub: no state, must be skipped
            {sseId: "progress", execution: {id: "exec-1", state: {current: "RUNNING"}}},
            {sseId: "end", execution: {id: "exec-1", state: {current: "SUCCESS"}}},
        ]))

        const store = useExecutionsStore()
        const seen: Array<Record<string, unknown>> = []
        const onError = vi.fn()
        const onEnd = vi.fn()

        store.subscribeToExecution("exec-1", {
            onExecution: (execution) => seen.push(execution as unknown as Record<string, unknown>),
            onError,
            onEnd,
        })

        await vi.waitFor(() => expect(onEnd).toHaveBeenCalledTimes(1))

        expect(seen).toHaveLength(2)
        expect((seen[0].state as {current: string}).current).toBe("RUNNING")
        expect((seen[1].state as {current: string}).current).toBe("SUCCESS")
        expect(onError).not.toHaveBeenCalled()
        // the previous EventSource auto-reconnect (kestra-io/kestra#16982) must stay disabled
        expect(followExecutionMock).toHaveBeenCalledWith(
            {executionId: "exec-1"},
            expect.objectContaining({sseMaxRetryAttempts: 1}),
        )
    })

    it("reports an error when the stream stops before the terminating end event", async () => {
        followExecutionMock.mockImplementation(fakeFollowStream([
            {sseId: "start", execution: {id: "exec-1"}}, // only the stub, then the connection drops
        ]))

        const store = useExecutionsStore()
        const onError = vi.fn()
        const onEnd = vi.fn()

        store.subscribeToExecution("exec-1", {onExecution: vi.fn(), onError, onEnd})

        await vi.waitFor(() => expect(onEnd).toHaveBeenCalledTimes(1))
        expect(onError).toHaveBeenCalledTimes(1)
    })

    it("close() aborts the underlying stream and suppresses terminal callbacks", async () => {
        let aborted = false
        followExecutionMock.mockImplementation((_params: unknown, options: {signal: AbortSignal}) => {
            options.signal.addEventListener("abort", () => {
                aborted = true
            })
            return Promise.resolve({
                // a stream that stays open (never completes on its own) until close() aborts it
                stream: (async function* () {
                    yield {id: "exec-1", state: {current: "RUNNING"}}
                    await new Promise(() => {})
                })(),
            })
        })

        const store = useExecutionsStore()
        const onEnd = vi.fn()

        const handle = store.subscribeToExecution("exec-1", {onExecution: vi.fn(), onEnd})
        handle.close()

        await vi.waitFor(() => expect(aborted).toBe(true))
        expect(onEnd).not.toHaveBeenCalled()
    })
})
