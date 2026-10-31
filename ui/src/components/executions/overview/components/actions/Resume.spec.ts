import {afterEach, describe, expect, it, vi} from "vitest"
import {createPinia, setActivePinia} from "pinia"
import {defineComponent, nextTick} from "vue"
import {flushPromises, type VueWrapper} from "@vue/test-utils"
import {i18nMount} from "../../../../../../tests/unit/i18nMount"
import {useExecutionsStore, type Execution} from "../../../../../stores/executions"

vi.mock("override/stores/auth", () => ({
    useAuthStore: () => ({user: {isAllowed: () => true}}),
}))

vi.mock("../../../../../utils/toast", () => ({
    useToast: () => ({success: vi.fn(), confirm: vi.fn()}),
}))

const InputsFormStub = defineComponent({
    name: "InputsForm",
    props: {initialInputs: {type: Array, required: true}},
    emits: ["update:modelValue"],
    render: () => null,
})

import Resume from "./Resume.vue"

const mounted: VueWrapper[] = []

afterEach(() => mounted.splice(0).forEach((wrapper) => wrapper.unmount()))

describe("Resume", () => {
    it("resumes with the inputs of a FORM under their dotted id, next to the other inputs", async () => {
        setActivePinia(createPinia())
        const executionsStore = useExecutionsStore()
        executionsStore.flow = {
            id: "flow1",
            namespace: "tests",
            tasks: [{
                id: "wait",
                type: "io.kestra.plugin.core.flow.Pause",
                inputs: [
                    {id: "approval", type: "FORM", inputs: [{id: "approver", type: "STRING"}]},
                    {id: "note", type: "STRING"},
                ],
            }],
        } as unknown as typeof executionsStore.flow
        executionsStore.loadFlowForExecution = vi.fn()
        const resume = vi.fn().mockResolvedValue({})
        executionsStore.resume = resume

        const wrapper = i18nMount(Resume, {
            props: {
                execution: {
                    id: "exec1",
                    namespace: "tests",
                    flowId: "flow1",
                    state: {current: "PAUSED"},
                    taskRunList: [{id: "tr1", taskId: "wait", state: {current: "PAUSED"}}],
                } as unknown as Execution,
            },
            global: {stubs: {InputsForm: InputsFormStub, KsDialog: {template: "<div><slot /><slot name=\"footer\" /></div>"}}},
        })
        mounted.push(wrapper)

        await wrapper.find("button").trigger("click")
        wrapper.findComponent(InputsFormStub).vm.$emit("update:modelValue", {"approval.approver": "adii", note: "approved"})
        await nextTick()
        await wrapper.findAll("button").at(-1)!.trigger("click")
        await flushPromises()

        const formData: FormData = resume.mock.calls[0][0].formData
        expect(Object.fromEntries(formData.entries())).toEqual({"approval.approver": "adii", note: "approved"})
    })
})
