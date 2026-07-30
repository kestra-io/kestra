import {describe, test, expect} from "vitest"
import {mount} from "@vue/test-utils"
import {createI18n} from "vue-i18n"
import FlowRunActions from "../../../../src/components/flows/FlowRunActions.vue"

const i18n = createI18n({legacy: false, locale: "en", messages: {en: {"launch execution": "Execute"}}})

function makeFlowRun(over: Record<string, unknown> = {}) {
    return {
        submit: () => {},
        prefill: () => {},
        canPrefill: false,
        flowCanBeExecuted: true,
        hasBlockingChecks: false,
        buttonText: "launch execution",
        buttonIcon: {},
        buttonTestId: "execute-dialog-button",
        showExecuteButton: true,
        ...over,
    }
}

describe("FlowRunActions Execute gating", () => {
    test("shows Execute when showExecuteButton is true", () => {
        const wrapper = mount(FlowRunActions, {
            props: {flowRun: makeFlowRun({showExecuteButton: true})},
            global: {plugins: [i18n], stubs: {KsButton: {template: "<button class=\"ks-button-stub\"><slot/></button>"}}},
        })
        expect(wrapper.find("[data-onboarding-target='flow-execute-confirm-button']").exists()).toBe(true)
    })

    test("hides Execute when showExecuteButton is false (mid-wizard)", () => {
        const wrapper = mount(FlowRunActions, {
            props: {flowRun: makeFlowRun({showExecuteButton: false})},
            global: {plugins: [i18n], stubs: {KsButton: {template: "<button class=\"ks-button-stub\"><slot/></button>"}}},
        })
        expect(wrapper.find("[data-onboarding-target='flow-execute-confirm-button']").exists()).toBe(false)
    })
})

describe("FlowRunActions validation message", () => {
    const stubs = {
        KsButton: {template: "<button class=\"ks-button-stub\"><slot/></button>"},
        KsText: {template: "<span class=\"ks-text-stub\"><slot/></span>"},
    }

    test("renders the validation message alongside the Execute button in the footer row", () => {
        const wrapper = mount(FlowRunActions, {
            props: {flowRun: makeFlowRun({validationMessages: ["Empty key or value is not allowed in labels"]})},
            global: {plugins: [i18n], stubs},
        })
        const message = wrapper.find(".ks-text-stub")
        expect(message.exists()).toBe(true)
        expect(message.text()).toBe("Empty key or value is not allowed in labels")
    })

    test("renders every validation message together when several apply", () => {
        const wrapper = mount(FlowRunActions, {
            props: {flowRun: makeFlowRun({validationMessages: ["Empty key or value is not allowed in labels", "System labels are not allowed"]})},
            global: {plugins: [i18n], stubs},
        })
        const messages = wrapper.findAll(".ks-text-stub")
        expect(messages).toHaveLength(2)
        expect(messages.map(m => m.text())).toEqual([
            "Empty key or value is not allowed in labels",
            "System labels are not allowed",
        ])
    })

    test("omits the validation message when there is nothing to report", () => {
        const wrapper = mount(FlowRunActions, {
            props: {flowRun: makeFlowRun({validationMessages: []})},
            global: {plugins: [i18n], stubs},
        })
        expect(wrapper.find(".ks-text-stub").exists()).toBe(false)
    })
})
