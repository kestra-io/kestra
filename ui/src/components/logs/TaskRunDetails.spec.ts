import {afterAll, afterEach, beforeEach, describe, expect, it, vi} from "vitest"
import {flushPromises, mount} from "@vue/test-utils"
import {createI18n} from "vue-i18n"
import {nextTick, reactive} from "vue"

const store = vi.hoisted(() => ({
    executions: {} as Record<string, any>,
}))

vi.mock("../../stores/executions", () => ({
    useExecutionsStore: () => store.executions,
}))
vi.mock("../../stores/core", () => ({
    useCoreStore: () => ({message: undefined}),
}))
vi.mock("@kestra-io/kestra-sdk", () => ({
    useClient: () => ({}),
}))
vi.mock("@kestra-io/kestra-sdk/outputs", () => ({
    taskRunOutputs: vi.fn().mockResolvedValue({}),
}))

import TaskRunDetails from "./TaskRunDetails.vue"

const i18n = createI18n({legacy: false, globalInjection: true, locale: "en", messages: {en: {}}})

const execution = (id: string, state: string) => ({
    id,
    namespace: "company.team",
    flowId: "simple-dag",
    flowRevision: 1,
    kind: "PLAYGROUND",
    state: {current: state},
    // Kept empty so the component's root `v-if` renders nothing: this spec drives the
    // log-loading watcher, not the taskrun list.
    taskRunList: [],
})

const logsFor = (id: string) => ({results: [{level: "INFO", message: `log of ${id}`}], total: 1})

function mountDetails() {
    return mount(TaskRunDetails, {
        props: {targetFlow: {id: "simple-dag", namespace: "company.team"}},
        global: {plugins: [i18n]},
    })
}

describe("TaskRunDetails log loading across executions", () => {
    let openedStreams: string[]

    afterEach(() => {
        vi.useRealTimers()
    })

    // `useLogDisplay` seeds its display settings into localStorage when the component is imported.
    afterAll(() => {
        localStorage.clear()
    })

    beforeEach(() => {
        vi.useFakeTimers()
        openedStreams = []
        store.executions = reactive({
            execution: undefined as any,
            logs: undefined,
            loadLogs: vi.fn(({executionId}: {executionId: string}) => Promise.resolve(logsFor(executionId))),
            followLogs: vi.fn(({id}: {id: string}) => {
                openedStreams.push(id)
                return Promise.resolve({close: vi.fn(), onmessage: null, onerror: null})
            }),
            loadFlowForExecution: vi.fn().mockResolvedValue({id: "simple-dag"}),
            subscribeToExecution: vi.fn(() => ({close: vi.fn()})),
        })
    })

    /**
     * Regression test for kestra-io/kestra#14018: a playground re-run lands while the previous
     * execution's log stream is still inside its two-second grace period. The `!logsSSE` guards
     * used to read that stream as "these logs are already covered", so the new execution's logs
     * were never fetched and its taskruns rendered with empty consoles.
     */
    it("should load the new execution logs when re-run before the previous stream is closed", async () => {
        store.executions.execution = execution("exec-1", "RUNNING")
        mountDetails()
        await flushPromises()
        expect(openedStreams).toEqual(["exec-1"])

        store.executions.execution = {...execution("exec-1", "SUCCESS")}
        await nextTick()
        await flushPromises()

        // Re-run inside the grace period, so exec-1's stream is still open.
        vi.advanceTimersByTime(500)
        store.executions.execution = execution("exec-2", "RESTARTED")
        await nextTick()
        await flushPromises()

        expect(store.executions.loadLogs).toHaveBeenCalledWith(
            expect.objectContaining({executionId: "exec-2"}),
        )
    })

    it("should drop the previous execution logs when the followed execution changes", async () => {
        store.executions.execution = execution("exec-1", "SUCCESS")
        const wrapper = mountDetails()
        await flushPromises()
        expect((wrapper.vm as any).filteredLogs).toEqual([{level: "INFO", message: "log of exec-1"}])

        store.executions.loadLogs.mockReturnValue(new Promise(() => {}))
        store.executions.execution = execution("exec-2", "RESTARTED")
        await nextTick()
        await flushPromises()

        expect((wrapper.vm as any).filteredLogs).toEqual([])
    })

    /**
     * A replay starts in RESTARTED, which is not a running state, so the grace-period close is
     * armed before the execution reaches RUNNING. Left pending it closed the stream that had
     * just been opened for that same execution, two seconds into the run.
     */
    it("should keep the stream open when the execution reaches running after a restarted state", async () => {
        store.executions.execution = execution("exec-1", "RESTARTED")
        mountDetails()
        await flushPromises()

        store.executions.execution = execution("exec-1", "RUNNING")
        await nextTick()
        await flushPromises()
        expect(openedStreams).toEqual(["exec-1"])

        const stream = await store.executions.followLogs.mock.results[0].value
        vi.advanceTimersByTime(5000)
        expect(stream.close).not.toHaveBeenCalled()
    })
})
