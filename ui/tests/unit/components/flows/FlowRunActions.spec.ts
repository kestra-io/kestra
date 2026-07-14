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
    test("renders the validation message alongside the Execute button in the footer row", () => {
        const wrapper = mount(FlowRunActions, {
            props: {flowRun: makeFlowRun({validationMessage: "Empty key or value is not allowed in labels"})},
            global: {
                plugins: [i18n],
                stubs: {
                    KsButton: {template: "<button class=\"ks-button-stub\"><slot/></button>"},
                    KsText: {template: "<span class=\"ks-text-stub\"><slot/></span>"},
                },
            },
        })
        const message = wrapper.find(".ks-text-stub")
        expect(message.exists()).toBe(true)
        expect(message.text()).toBe("Empty key or value is not allowed in labels")
        expect(wrapper.find(".flow-run-actions").element.children).toHaveLength(2)
    })

    test("omits the validation message when there is nothing to report", () => {
        const wrapper = mount(FlowRunActions, {
            props: {flowRun: makeFlowRun({validationMessage: undefined})},
            global: {
                plugins: [i18n],
                stubs: {
                    KsButton: {template: "<button class=\"ks-button-stub\"><slot/></button>"},
                    KsText: {template: "<span class=\"ks-text-stub\"><slot/></span>"},
                },
            },
        })
        expect(wrapper.find(".ks-text-stub").exists()).toBe(false)
    })
})
