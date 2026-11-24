import {describe, test, expect} from "vitest"
import FlowRunActions from "./FlowRunActions.vue"
import {i18nMount} from "../../../tests/unit/i18nMount"

const enMessages = {"launch execution": "Execute"}

function makeFlowRun(over: Record<string, unknown> = {}) {
    return {
        submit: () => {},
        prefill: () => {},
        canPrefill: false,
        flowCanBeExecuted: true,
        hasBlockingChecks: false,
        failingChecksCount: 0,
        buttonText: "launch execution",
        buttonIcon: {},
        buttonTestId: "execute-dialog-button",
        showExecuteButton: true,
        ...over,
    }
}

describe("FlowRunActions Execute gating", () => {
    test("shows Execute when showExecuteButton is true", () => {
        const wrapper = i18nMount(FlowRunActions, {
            messages: enMessages,
            props: {flowRun: makeFlowRun({showExecuteButton: true})},
            global: {stubs: {KsButton: {template: "<button class=\"ks-button-stub\"><slot/></button>"}}},
        })
        expect(wrapper.find("[data-onboarding-target='flow-execute-confirm-button']").exists()).toBe(true)
    })

    test("hides Execute when showExecuteButton is false (mid-wizard)", () => {
        const wrapper = i18nMount(FlowRunActions, {
            messages: enMessages,
            props: {flowRun: makeFlowRun({showExecuteButton: false})},
            global: {stubs: {KsButton: {template: "<button class=\"ks-button-stub\"><slot/></button>"}}},
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
        const wrapper = i18nMount(FlowRunActions, {
            messages: enMessages,
            props: {flowRun: makeFlowRun({validationMessages: ["Empty key or value is not allowed in labels"]})},
            global: {stubs},
        })
        const message = wrapper.find(".ks-text-stub")
        expect(message.exists()).toBe(true)
        expect(message.text()).toBe("Empty key or value is not allowed in labels")
    })

    test("renders every validation message together when several apply", () => {
        const wrapper = i18nMount(FlowRunActions, {
            messages: enMessages,
            props: {flowRun: makeFlowRun({validationMessages: ["Empty key or value is not allowed in labels", "System labels are not allowed"]})},
            global: {stubs},
        })
        const messages = wrapper.findAll(".ks-text-stub")
        expect(messages).toHaveLength(2)
        expect(messages.map(m => m.text())).toEqual([
            "Empty key or value is not allowed in labels",
            "System labels are not allowed",
        ])
    })

    test("omits the validation message when there is nothing to report", () => {
        const wrapper = i18nMount(FlowRunActions, {
            messages: enMessages,
            props: {flowRun: makeFlowRun({validationMessages: []})},
            global: {stubs},
        })
        expect(wrapper.find(".ks-text-stub").exists()).toBe(false)
    })

    test("shows the number of failing checks on the disabled Execute button while a check blocks", () => {
        const wrapper = i18nMount(FlowRunActions, {
            messages: {...enMessages, checks_failing: "{count} check failing | {count} checks failing"},
            props: {flowRun: makeFlowRun({hasBlockingChecks: true, failingChecksCount: 2})},
            global: {stubs: {KsTooltip: {props: ["content", "disabled"], template: "<div data-test=\"tooltip\" :data-disabled=\"disabled\">{{ content }}<slot/></div>"}}},
        })

        const tooltip = wrapper.find("[data-test='tooltip']")
        expect(tooltip.attributes("data-disabled")).toBe("false")
        expect(tooltip.text()).toContain("2 checks failing")
        expect(wrapper.find("[data-test='execute-dialog-button']").attributes("disabled")).toBeDefined()
    })
})
