import {afterEach, beforeEach, describe, expect, test, vi} from "vitest"
import {createPinia, setActivePinia} from "pinia"

import {normalizeFilePreview, useExecutionsStore} from "../../../src/stores/executions"

vi.mock("vue-router", () => ({
    useRoute: () => ({query: {}}),
}))

vi.mock("@kestra-io/kestra-sdk/executions", () => ({
    followExecution: vi.fn(),
    flowFromExecutionById: vi.fn(() => Promise.resolve({id: "flow", namespace: "io.kestra.tests", revision: 1})),
}))

import * as ExecutionsAPI from "@kestra-io/kestra-sdk/executions"

// A minimal controllable stand-in for the SDK follow stream's async iterable,
// so a test can push events and let them settle before pushing the next one -
// `subscribeToExecution` (stores/executions.ts) drains this with `for await`.
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
        flowId: "flow",
        namespace: "io.kestra.tests",
        flowRevision: 1,
        labels: [],
        state: {current: "RUNNING", histories: []},
        ...overrides,
    }
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
        vi.mocked(ExecutionsAPI.followExecution).mockResolvedValue({stream} as any)

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
})

describe("executions store", () => {
    test("keeps Ion preview objects without requiring array helpers", () => {
        const preview = {
            extension: "ion",
            type: "RAW",
            content: {message: "hello from ship logs", level: "INFO"},
            truncated: false,
        }

        expect(normalizeFilePreview(preview)).toEqual(preview)
    })

    test("keeps the Ion array workaround for scalar content", () => {
        expect(normalizeFilePreview({
            extension: "ion",
            type: "LIST",
            content: ["first", "second"],
            truncated: false,
        })).toEqual({
            extension: "ion",
            type: "TEXT",
            content: "first\nsecond",
            truncated: false,
        })
    })
})
