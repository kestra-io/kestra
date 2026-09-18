import {afterEach, beforeEach, describe, expect, test, vi} from "vitest"
import {createPinia, setActivePinia} from "pinia"

vi.mock("vue-router", () => ({
    useRoute: () => ({query: {}}),
}))

vi.mock("@kestra-io/kestra-sdk/executions", () => ({
    followExecution: vi.fn(),
    flowFromExecutionById: vi.fn(() => Promise.resolve({id: "flow", namespace: "io.kestra.tests", revision: 1})),
}))

import * as ExecutionsAPI from "@kestra-io/kestra-sdk/executions"
import {useExecutionsStore, type Execution} from "../../../src/stores/executions"

// A minimal controllable stand-in for the SDK follow stream's async iterable, so a test
// can push events and let them settle before pushing the next one - `subscribeToExecution`
// (stores/executions.ts) drains this with `for await`. Doesn't reuse executionsFollow.spec.ts's
// fakeFollowStream: that one yields a fixed array as fast as it's consumed, with no way to
// pause between two specific events for an assertion in between - which is exactly what these
// races need.
function createControlledStream() {
    const queue: unknown[] = []
    let resolveNext: ((result: IteratorResult<unknown>) => void) | undefined

    return {
        push(event: unknown) {
            if (resolveNext) {
                const resolve = resolveNext
                resolveNext = undefined
                resolve({value: event, done: false})
            } else {
                queue.push(event)
            }
        },
        [Symbol.asyncIterator]() {
            return {
                next(): Promise<IteratorResult<unknown>> {
                    if (queue.length) return Promise.resolve({value: queue.shift(), done: false})
                    return new Promise((resolve) => {
                        resolveNext = resolve
                    })
                },
            }
        },
    }
}

function buildExecution(overrides: Record<string, unknown> = {}) {
    return {
        id: "execution-id",
        originalId: "execution-id",
        flowId: "flow",
        namespace: "io.kestra.tests",
        flowRevision: 1,
        labels: [],
        state: {current: "RUNNING", histories: []},
        metadata: {originalCreatedDate: new Date().toISOString()},
        ...overrides,
    } as unknown as Execution
}

describe("executions store: SSE live-follow vs. a local write (#18766)", () => {
    beforeEach(() => {
        setActivePinia(createPinia())
        vi.useFakeTimers()
    })

    afterEach(() => {
        vi.useRealTimers()
        vi.clearAllMocks()
    })

    test("applyLocalExecutionUpdate is not clobbered by a throttled SSE update already queued before it", async () => {
        const store = useExecutionsStore()
        const stream = createControlledStream()
        vi.mocked(ExecutionsAPI.followExecution).mockResolvedValue(
            {stream} as unknown as Awaited<ReturnType<typeof ExecutionsAPI.followExecution>>,
        )

        store.followExecution({id: "execution-id"}, (s: string) => s)
        // Let subscribeToExecution's `.then(async ({stream}) => ...)` attach before pushing.
        await vi.advanceTimersByTimeAsync(0)

        // First SSE push: throttle's leading edge applies it immediately.
        stream.push(buildExecution())
        await vi.advanceTimersByTimeAsync(0)
        expect(store.execution?.labels).toEqual([])

        // Second SSE push, still within the same 500ms throttle window: this one
        // is queued for the trailing edge rather than applied right away - exactly
        // the "already queued" SSE update the fix needs to discard.
        stream.push(buildExecution())
        await vi.advanceTimersByTimeAsync(0)

        // The save lands before that trailing edge fires.
        store.applyLocalExecutionUpdate(buildExecution({
            labels: [{key: "env", value: "prod"}],
        }))
        expect(store.execution?.labels).toEqual([{key: "env", value: "prod"}])

        // Let the throttle window fully elapse. Without the fix, the queued
        // second push (no labels) would land on top of the save here.
        await vi.advanceTimersByTimeAsync(500)

        expect(store.execution?.labels).toEqual([{key: "env", value: "prod"}])
    })

    test("a local write during the async flow-reload branch is not clobbered once that reload resolves", async () => {
        const store = useExecutionsStore()
        const stream = createControlledStream()
        vi.mocked(ExecutionsAPI.followExecution).mockResolvedValue(
            {stream} as unknown as Awaited<ReturnType<typeof ExecutionsAPI.followExecution>>,
        )

        // Held open so throttledExecutionUpdate's flow-reload branch doesn't resolve
        // until the test says so - this is the second write path `.cancel()` cannot
        // reach, since it's a promise chain already running, not a pending timer.
        let resolveFlowLoad: (() => void) | undefined
        vi.mocked(ExecutionsAPI.flowFromExecutionById).mockReturnValue(
            new Promise((resolve) => {
                resolveFlowLoad = () => resolve(
                    {id: "flow", namespace: "io.kestra.tests", revision: 1} as unknown as Awaited<ReturnType<typeof ExecutionsAPI.flowFromExecutionById>>,
                )
            }),
        )

        store.followExecution({id: "execution-id"}, (s: string) => s)
        await vi.advanceTimersByTimeAsync(0)

        // flow.value starts unset, so this push's flow mismatch triggers the reload
        // branch - throttle's leading edge still applies the push itself immediately.
        stream.push(buildExecution())
        await vi.advanceTimersByTimeAsync(0)
        expect(store.execution?.labels).toEqual([])

        // A save lands while that flow reload is still in flight.
        store.applyLocalExecutionUpdate(buildExecution({
            labels: [{key: "env", value: "prod"}],
        }))
        expect(store.execution?.labels).toEqual([{key: "env", value: "prod"}])

        // The flow reload finally resolves. Without the generation guard, its callback
        // re-applies the original (label-less) push and clobbers the save here.
        resolveFlowLoad?.()
        await vi.advanceTimersByTimeAsync(0)

        expect(store.execution?.labels).toEqual([{key: "env", value: "prod"}])
    })
})
