import {describe, expect, it, vi} from "vitest"
import {mount} from "@vue/test-utils"
import {createPinia, setActivePinia} from "pinia"
import {defineComponent} from "vue"
import {useExecutionsStore, type Execution} from "../../stores/executions"

vi.mock("vue-router", () => ({
    useRoute: () => ({params: {}, query: {}}),
    useRouter: () => ({push: vi.fn()}),
}))

vi.mock("vue-i18n", () => ({useI18n: () => ({t: (key: string) => key})}))

vi.mock("../../utils/toast", () => ({useToast: () => ({success: vi.fn()})}))

vi.mock("@kestra-io/kestra-sdk", () => ({useClient: () => ({})}))

vi.mock("../flows/FlowRun.vue", () => ({
    default: defineComponent({name: "FlowRun", props: {replaySubmit: {type: Function, required: true}}, render: () => null}),
}))

import ReplayWithInputs from "./ReplayWithInputs.vue"
import FlowRun, {type ReplaySubmitOptions} from "../flows/FlowRun.vue"

describe("ReplayWithInputs", () => {
    it("replays with the inputs of a FORM under their dotted id, next to the other inputs", async () => {
        setActivePinia(createPinia())
        const executionsStore = useExecutionsStore()
        executionsStore.flow = {
            id: "flow1",
            namespace: "tests",
            inputs: [
                {id: "env", type: "FORM", inputs: [{id: "region", type: "STRING"}, {id: "day", type: "DATE"}]},
                {id: "top", type: "STRING"},
            ],
        } as unknown as typeof executionsStore.flow
        const replayExecutionWithInputs = vi.fn().mockResolvedValue({id: "replayed", namespace: "tests", flowId: "flow1"} as Execution)
        executionsStore.replayExecutionWithInputs = replayExecutionWithInputs

        const wrapper = mount(ReplayWithInputs, {props: {execution: {id: "exec1"}}})
        const replaySubmit = wrapper.findComponent(FlowRun).props("replaySubmit") as (options: ReplaySubmitOptions) => Promise<void>
        await replaySubmit({
            inputs: {"env.region": "us-east-2", "env.day": "2026-01-15", top: "custom"},
            breakpoints: [],
        } as unknown as ReplaySubmitOptions)

        const formData: FormData = replayExecutionWithInputs.mock.calls[0][0].formData
        expect(Object.fromEntries(formData.entries())).toEqual({"env.region": "us-east-2", "env.day": "2026-01-15", top: "custom"})
    })
})
