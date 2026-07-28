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
