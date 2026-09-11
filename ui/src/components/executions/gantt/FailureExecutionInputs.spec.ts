import {afterEach, describe, expect, it, vi} from "vitest"
import {flushPromises, mount} from "@vue/test-utils"
import {createI18n} from "vue-i18n"
import FailureExecutionInputs from "./FailureExecutionInputs.vue"
import en from "../../../translations/en.json"

const mocks = vi.hoisted(() => ({
    renderExpressions: vi.fn(),
}))

vi.mock("@kestra-io/kestra-sdk/expressions", () => ({
    renderExpressions: mocks.renderExpressions,
}))

const i18n = createI18n({legacy: false, locale: "en", fallbackWarn: false, missingWarn: false, messages: {en: en.en}})

function mountInputs(inputIds: string[]) {
    return mount(FailureExecutionInputs, {
        props: {inputIds, executionId: "exec-1", taskRunId: "tr-1"},
        global: {
            plugins: [i18n],
            // Vars.vue's KsTable/KsTableColumn scoped-slot rendering needs the real design-system
            // plugin (covered by the Storybook story instead) — it throws in jsdom without it.
            stubs: {Vars: {template: "<pre>{{ JSON.stringify(data) }}</pre>", props: ["data"]}},
        },
    })
}

describe("FailureExecutionInputs", () => {
    afterEach(() => {
        vi.clearAllMocks()
    })

    it("should show an empty state when the flow declares no inputs", async () => {
        const wrapper = mountInputs([])
        await flushPromises()

        expect(mocks.renderExpressions).not.toHaveBeenCalled()
        expect(wrapper.text()).toContain("declares no inputs")
    })

    it("should render one expression per declared input, batched in a single call", async () => {
        mocks.renderExpressions.mockResolvedValue({
            rendered: {
                "{{ inputs.region }}": "us-east-1",
                "{{ inputs.token }}": "[secret: token]",
            },
        })

        const wrapper = mountInputs(["region", "token"])
        await flushPromises()

        expect(mocks.renderExpressions).toHaveBeenCalledTimes(1)
        expect(mocks.renderExpressions).toHaveBeenCalledWith(expect.objectContaining({
            expressions: ["{{ inputs.region }}", "{{ inputs.token }}"],
            executionId: "exec-1",
            taskRunId: "tr-1",
        }))
        expect(wrapper.text()).toContain("us-east-1")
        expect(wrapper.text()).toContain("[secret: token]")
    })

    it("should show an error state when the render call fails", async () => {
        mocks.renderExpressions.mockRejectedValue(new Error("network error"))

        const wrapper = mountInputs(["region"])
        await flushPromises()

        expect(wrapper.text()).toContain("Could not resolve")
    })
})
