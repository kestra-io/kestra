import {afterEach, beforeEach, describe, expect, test, vi} from "vitest"
import {flushPromises} from "@vue/test-utils"
import {i18nMount} from "../../i18nMount"

import {createPinia, setActivePinia} from "pinia"
import KestraDesignSystem from "@kestra-io/design-system"
import InputsForm from "../../../../src/components/inputs/InputsForm.vue"
import {useExecutionsStore, type Execution, type ValidationResponse} from "../../../../src/stores/executions"

vi.mock("vue-router", () => ({
    useRoute: () => ({query: {}, params: {}, name: "execution"}),
    useRouter: () => ({replace: vi.fn(), push: vi.fn()}),
}))

const response: ValidationResponse = {
    checks: [],
    inputs: [{
        enabled: true,
        isDefault: true,
        value: "",
        input: {id: "approval_reason", type: "STRING", required: false},
    }],
}

function execution(state: string): Execution {
    return {id: "exec1", state: {current: state}} as unknown as Execution
}

// The execution SSE pushes the RUNNING execution while `/resume` is still in flight; validating it
// again hits a 409 on an execution that is no longer paused.
describe("InputsForm resume validation", () => {
    beforeEach(() => {
        setActivePinia(createPinia())
    })

    afterEach(() => {
        document.body.innerHTML = ""
    })

    test("does not validate again when the same execution leaves PAUSED", async () => {
        const store = useExecutionsStore()
        const validate = vi.fn(() => Promise.resolve(response))
        store.validateResume = validate

        const wrapper = i18nMount(InputsForm, {
            global: {plugins: [KestraDesignSystem]},
            shallow: true,
            props: {
                execution: execution("PAUSED"),
                initialInputs: [{id: "approval_reason", type: "STRING"}],
            },
        })
        await flushPromises()
        expect(validate).toHaveBeenCalledTimes(1)

        await wrapper.setProps({execution: execution("RUNNING")})
        await flushPromises()

        expect(validate).toHaveBeenCalledTimes(1)
    })
})
