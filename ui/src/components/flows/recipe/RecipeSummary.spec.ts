import {describe, expect, it, vi} from "vitest"
import {KsAlert, KsButton} from "@kestra-io/design-system"

import {i18nMount} from "../../../../tests/unit/i18nMount"
import RecipeSummary from "./RecipeSummary.vue"

vi.mock("../../../composables/useEditorBindings", () => ({
    useEditorBindings: () => ({}),
}))

const KsEditorStub = {
    props: ["modelValue", "readOnly"],
    template: "<div data-test=\"recipe-yaml-preview\" />",
}

const messages = {
    "recipe.summary.invalid_hint": "Add a channel and complete the trigger.",
    "recipe.then.no_channel_warning": "Select at least one notification channel.",
    "recipe.summary.invalid_hint_trigger": "Complete the trigger configuration.",
}

function mountSummary(props: Partial<InstanceType<typeof RecipeSummary>["$props"]> = {}) {
    return i18nMount(RecipeSummary, {
        props: {
            yamlContent: "id: notify",
            isValid: true,
            hasChannel: true,
            triggerValid: true,
            hasInteracted: true,
            ...props,
        },
        messages,
        global: {stubs: {KsEditor: KsEditorStub}},
    })
}

describe("RecipeSummary", () => {
    it("passes the YAML to the editor in read-only mode", () => {
        const wrapper = mountSummary({yamlContent: "id: my-flow"})
        const editor = wrapper.findComponent(KsEditorStub)

        expect(editor.props("modelValue")).toBe("id: my-flow")
        expect(editor.props("readOnly")).toBe(true)
    })

    it("renders no alert while the form is valid", () => {
        const wrapper = mountSummary({isValid: true, hasInteracted: true})

        expect(wrapper.findComponent(KsAlert).exists()).toBe(false)
    })

    it("renders no alert while the form is invalid but untouched", () => {
        const wrapper = mountSummary({isValid: false, hasChannel: false, hasInteracted: false})

        expect(wrapper.findComponent(KsAlert).exists()).toBe(false)
    })

    it("renders the alert once the form is invalid and touched", () => {
        const wrapper = mountSummary({isValid: false, hasChannel: false, hasInteracted: true})

        expect(wrapper.findComponent(KsAlert).exists()).toBe(true)
    })

    it.each([
        {scenario: "both the channel and the trigger are missing", hasChannel: false, triggerValid: false, hint: messages["recipe.summary.invalid_hint"]},
        {scenario: "only the channel is missing", hasChannel: false, triggerValid: true, hint: messages["recipe.then.no_channel_warning"]},
        {scenario: "only the trigger is missing", hasChannel: true, triggerValid: false, hint: messages["recipe.summary.invalid_hint_trigger"]},
    ])("shows the matching hint when $scenario", ({hasChannel, triggerValid, hint}) => {
        const wrapper = mountSummary({isValid: false, hasChannel, triggerValid, hasInteracted: true})

        expect(wrapper.findComponent(KsAlert).text()).toBe(hint)
    })

    it("keeps the create button disabled until the form is valid, then emits create", async () => {
        const wrapper = mountSummary({isValid: false, hasInteracted: false})
        const button = () => wrapper.findComponent(KsButton)

        expect(button().attributes("disabled")).toBeDefined()
        await button().trigger("click")
        expect(wrapper.emitted("create")).toBeUndefined()

        await wrapper.setProps({isValid: true})

        expect(button().attributes("disabled")).toBeUndefined()
        await button().trigger("click")
        expect(wrapper.emitted("create")).toHaveLength(1)
    })
})
