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
        props: {inputIds, executionId: "exec-1"},
        global: {
            plugins: [i18n],
            stubs: {Vars: {template: "<pre>{{ JSON.stringify(data) }}</pre>", props: ["data"]}},
        },
    })
}

function deferred<T>() {
    let resolve!: (value: T) => void
    const promise = new Promise<T>((res) => { resolve = res })
    return {promise, resolve}
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
                "{{ inputs['region'] }}": "us-east-1",
                "{{ inputs['token'] }}": "[secret: token]",
            },
        })

        const wrapper = mountInputs(["region", "token"])
        await flushPromises()

        expect(mocks.renderExpressions).toHaveBeenCalledTimes(1)
        expect(mocks.renderExpressions).toHaveBeenCalledWith(expect.objectContaining({
            expressions: ["{{ inputs['region'] }}", "{{ inputs['token'] }}"],
            executionId: "exec-1",
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

    it("should read hyphenated and dotted input ids with bracket access, one bracket per segment", async () => {
        mocks.renderExpressions.mockResolvedValue({
            rendered: {
                "{{ inputs['my-input'] }}": "hyphen value",
                "{{ inputs['group']['child'] }}": "nested value",
            },
        })

        const wrapper = mountInputs(["my-input", "group.child"])
        await flushPromises()

        expect(mocks.renderExpressions).toHaveBeenCalledWith(expect.objectContaining({
            expressions: ["{{ inputs['my-input'] }}", "{{ inputs['group']['child'] }}"],
        }))
        expect(wrapper.text()).toContain("hyphen value")
        expect(wrapper.text()).toContain("nested value")
    })

    it("should keep the current execution's inputs when an earlier render answers last", async () => {
        const first = deferred<{rendered: Record<string, string>}>()
        const second = deferred<{rendered: Record<string, string>}>()
        mocks.renderExpressions.mockReturnValueOnce(first.promise).mockReturnValueOnce(second.promise)

        const wrapper = mountInputs(["region"])
        await wrapper.setProps({executionId: "exec-2"})
        second.resolve({rendered: {"{{ inputs['region'] }}": "region of exec-2"}})
        await flushPromises()
        first.resolve({rendered: {"{{ inputs['region'] }}": "region of exec-1"}})
        await flushPromises()

        expect(wrapper.text()).toContain("region of exec-2")
        expect(wrapper.text()).not.toContain("region of exec-1")
    })
})
