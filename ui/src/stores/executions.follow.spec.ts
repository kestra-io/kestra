import {afterEach, beforeEach, describe, expect, it, vi} from "vitest"
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

const {followExecutionMock, flowFromExecutionByIdMock} = vi.hoisted(() => ({
    followExecutionMock: vi.fn(),
    flowFromExecutionByIdMock: vi.fn(),
}))
vi.mock("@kestra-io/kestra-sdk/executions", () => ({
    followExecution: followExecutionMock,
    flowFromExecutionById: flowFromExecutionByIdMock,
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
const {useExecutionsStore} = await import("./executions")

// A follow stream the test can release one event at a time. `onSseEvent` fires
// immediately before the execution is yielded, matching the SDK.
function controllableFollowStream() {
    const queue: FakeEvent[] = []
    let pending: ((result: IteratorResult<Record<string, unknown>>) => void) | undefined
    let ended = false
    let options: {onSseEvent?: (event: {id?: string}) => void} = {}

    const deliver = () => {
        if (!pending) return
        if (queue.length === 0) {
            if (!ended) return
            const resolve = pending
            pending = undefined
            resolve({value: undefined, done: true})
            return
        }
        const event = queue.shift()!
        options.onSseEvent?.({id: event.sseId})
        const resolve = pending
        pending = undefined
        resolve({value: event.execution, done: false})
    }

    followExecutionMock.mockImplementation((_params: unknown, nextOptions: {onSseEvent?: (event: {id?: string}) => void}) => {
        options = nextOptions
        return Promise.resolve({
            stream: {
                [Symbol.asyncIterator]() {
                    return this
                },
                next() {
                    return new Promise<IteratorResult<Record<string, unknown>>>((resolve) => {
                        pending = resolve
                        deliver()
                    })
                },
            },
        })
    })

    return {
        push(event: FakeEvent) {
            queue.push(event)
            deliver()
        },
        end() {
            ended = true
            deliver()
        },
    }
}

// Each call opens the next connection: its events, then a clean close, or with `hang` an open stream until aborted.
type FakeConnection = { events: FakeEvent[]; hang?: boolean }
function scriptedConnections(connections: FakeConnection[]) {
    let opened = 0
    followExecutionMock.mockImplementation((_params: unknown, options: {signal: AbortSignal; onSseEvent?: (e: {id?: string}) => void}) => {
        const connection = connections[Math.min(opened++, connections.length - 1)]
        return Promise.resolve({
            stream: (async function* () {
                for (const event of connection.events) {
                    options.onSseEvent?.({id: event.sseId})
                    yield event.execution
                }
                if (connection.hang) await new Promise((resolve) => options.signal.addEventListener("abort", resolve))
            })(),
        })
    })
}

const running = (id = "exec-1"): FakeEvent => ({sseId: "progress", execution: {id, state: {current: "RUNNING"}}})
const start: FakeEvent = {sseId: "start", execution: {id: "exec-1"}}

describe("executions store follow stream", () => {
    beforeEach(() => {
        setActivePinia(createPinia())
        followExecutionMock.mockReset()
        flowFromExecutionByIdMock.mockReset()
    })

    afterEach(() => {
        vi.useRealTimers()
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
        expect(followExecutionMock).toHaveBeenCalledTimes(1)
    })

    it("reconnects when a stream closes before its end event, and follows the run to its end", async () => {
        vi.useFakeTimers()
        scriptedConnections([
            {events: [start, running()]},
            {events: [start, running(), {sseId: "end", execution: {id: "exec-1", state: {current: "SUCCESS"}}}]},
        ])
        const store = useExecutionsStore()
        const seen: string[] = []
        const onError = vi.fn()
        const onEnd = vi.fn()

        store.subscribeToExecution("exec-1", {
            onExecution: (execution) => seen.push((execution.state as {current: string}).current),
            onError,
            onEnd,
        })
        await vi.advanceTimersByTimeAsync(1000)

        expect(followExecutionMock).toHaveBeenCalledTimes(2)
        expect(seen).toEqual(["RUNNING", "RUNNING", "SUCCESS"])
        expect(onEnd).toHaveBeenCalledTimes(1)
        expect(onError).not.toHaveBeenCalled()
    })

    it("reconnects a stream that stays silent past the idle timeout", async () => {
        vi.useFakeTimers()
        scriptedConnections([{events: [start, running()], hang: true}])
        const store = useExecutionsStore()

        store.subscribeToExecution("exec-1", {onExecution: vi.fn()})
        await vi.advanceTimersByTimeAsync(74_000)
        const {signal} = followExecutionMock.mock.calls[0][1] as {signal: AbortSignal}
        expect(signal.aborted).toBe(false)

        await vi.advanceTimersByTimeAsync(2_000)
        expect(signal.aborted).toBe(true)
        expect(followExecutionMock).toHaveBeenCalledTimes(2)
    })

    it("gives up once a stream keeps dropping right after delivering an execution", async () => {
        vi.useFakeTimers()
        scriptedConnections([{events: [start, running()]}])
        const store = useExecutionsStore()
        const onError = vi.fn()

        store.subscribeToExecution("exec-1", {onExecution: vi.fn(), onError})
        await vi.advanceTimersByTimeAsync(60_000)

        // 1 initial connection + 6 reconnects (FOLLOW_MAX_RECONNECTS) within the 2 minute window
        expect(followExecutionMock).toHaveBeenCalledTimes(7)
        expect(onError).toHaveBeenCalledTimes(1)
    })

    it("close() cancels a pending reconnect", async () => {
        vi.useFakeTimers()
        scriptedConnections([{events: [start, running()]}])
        const store = useExecutionsStore()
        const onEnd = vi.fn()

        const handle = store.subscribeToExecution("exec-1", {onExecution: vi.fn(), onEnd})
        await vi.advanceTimersByTimeAsync(0)
        handle.close()
        await vi.advanceTimersByTimeAsync(60_000)

        expect(followExecutionMock).toHaveBeenCalledTimes(1)
        expect(onEnd).not.toHaveBeenCalled()
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

    it("does not rewind a finished execution when an earlier flow request resolves late", async () => {
        // The route guard loads the execution, not the flow, so the first SSE event
        // always starts /flow. A short run can reach SUCCESS before that request returns.
        const flowRequests: Array<{resolve: (flow: unknown) => void; promise: Promise<unknown>}> = []
        flowFromExecutionByIdMock.mockImplementation(() => {
            let resolve: (flow: unknown) => void = () => {}
            const promise = new Promise((done) => {
                resolve = done
            })
            flowRequests.push({resolve, promise})
            return promise
        })

        const stream = controllableFollowStream()
        const store = useExecutionsStore()
        store.followExecution({id: "exec-1"}, (key) => key)
        await vi.waitFor(() => expect(followExecutionMock).toHaveBeenCalled())

        const running = {
            id: "exec-1",
            namespace: "ns",
            flowId: "flow",
            flowRevision: 1,
            state: {current: "RUNNING"},
        }
        const success = {
            ...running,
            state: {current: "SUCCESS"},
        }

        stream.push({sseId: "progress", execution: running})
        await vi.waitFor(() => expect(store.execution?.state?.current).toBe("RUNNING"))

        stream.push({sseId: "end", execution: success})
        stream.end()
        await vi.waitFor(() => expect(store.execution?.state?.current).toBe("SUCCESS"))

        expect(flowRequests.length).toBeGreaterThan(0)
        const earliest = flowRequests[0]
        const flow = {id: "flow", namespace: "ns", revision: 1}
        for (const request of flowRequests.slice(1)) {
            request.resolve(flow)
            await request.promise
        }

        earliest.resolve(flow)
        await earliest.promise
        // Let that response land. This is the moment the page used to flip back to RUNNING.
        await new Promise((resolve) => setTimeout(resolve, 0))

        expect(store.execution?.state?.current).toBe("SUCCESS")
        expect(store.flow).toMatchObject(flow)
    })
})
