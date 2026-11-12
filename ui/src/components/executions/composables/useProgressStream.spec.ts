import {beforeEach, describe, expect, it, vi} from "vitest"
import {createPinia, setActivePinia} from "pinia"
import {defineComponent, h, reactive} from "vue"
import {mount} from "@vue/test-utils"

vi.mock("vue-router", () => ({
    useRoute: () => ({query: {}, params: {}}),
    useRouter: () => ({push: vi.fn(), replace: vi.fn(), beforeEach: vi.fn(), afterEach: vi.fn()}),
}))

vi.mock("@kestra-io/kestra-sdk", () => ({
    useClient: () => ({get: vi.fn(), post: vi.fn(), put: vi.fn(), patch: vi.fn(), delete: vi.fn()}),
}))

const {useExecutionsStore} = await import("../../../stores/executions")
const {useProgressStream} = await import("./useProgressStream")

const consumer = (execution: {id: string; state: {current: string}}) => defineComponent({
    setup() {
        useProgressStream(() => execution as never)
        return () => h("div")
    },
})

function setup() {
    setActivePinia(createPinia())
    const store = useExecutionsStore()
    const sources: {close: ReturnType<typeof vi.fn>; onmessage?: (event: MessageEvent) => void}[] = []
    store.followLogs = vi.fn(() => {
        const sse = {close: vi.fn()}
        sources.push(sse)
        return Promise.resolve(sse as never)
    }) as never
    return {store, sources}
}

describe("useProgressStream", () => {
    beforeEach(() => vi.clearAllMocks())

    it("shouldShareOneStreamBetweenConsumersAndCloseItWhenTheLastOneUnmounts", async () => {
        const {store, sources} = setup()
        const execution = reactive({id: "exec-1", state: {current: "RUNNING"}})
        const first = mount(consumer(execution))
        const second = mount(consumer(execution))
        await Promise.resolve()

        expect(store.followLogs).toHaveBeenCalledTimes(1)
        sources[0].onmessage?.({lastEventId: "1", data: JSON.stringify({taskId: "t", taskRunId: "r", progress: "pod.created", timestamp: "ts"})} as MessageEvent)
        expect(store.progressEvents).toEqual([{taskId: "t", taskRunId: "r", step: "pod.created", timestamp: "ts"}])

        first.unmount()
        expect(sources[0].close).not.toHaveBeenCalled()
        second.unmount()
        expect(sources[0].close).toHaveBeenCalledTimes(1)
    })

    it("shouldCloseTheStreamWhenTheExecutionStopsRunning", async () => {
        const {store, sources} = setup()
        const execution = reactive({id: "exec-2", state: {current: "RUNNING"}})
        const wrapper = mount(consumer(execution))
        await Promise.resolve()

        execution.state.current = "SUCCESS"
        await wrapper.vm.$nextTick()

        expect(sources[0].close).toHaveBeenCalledTimes(1)
        expect(store.followLogs).toHaveBeenCalledTimes(1)
    })

    it("shouldNotOpenAStreamForAnExecutionThatIsNotRunning", () => {
        const {store} = setup()

        mount(consumer({id: "exec-3", state: {current: "SUCCESS"}}))

        expect(store.followLogs).not.toHaveBeenCalled()
    })
})
