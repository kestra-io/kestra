import {describe, test, expect} from "vitest"
import {mount} from "@vue/test-utils"
import KestraDesignSystem from "../../../index"
import KsExecutionStatus from "./KsExecutionStatus.vue"
import {EXECUTION_STATUSES} from "./types"
// @ts-expect-error - plain .mjs module, shared with the linters
import {knownTokens} from "../../../../../../scripts/tokens/knownTokens.mjs"

const globalConfig = {plugins: [KestraDesignSystem]}

describe("KsExecutionStatus", () => {
    test("renders status element with text", () => {
        const wrapper = mount(KsExecutionStatus, {
            props: {status: "SUCCESS"},
            global: globalConfig,
        })
        expect(wrapper.find(".ks-execution-status").exists()).toBe(true)
        expect(wrapper.text()).toContain("Success")
    })

    test("applies status class", () => {
        const wrapper = mount(KsExecutionStatus, {
            props: {status: "RUNNING"},
            global: globalConfig,
        })
        expect(wrapper.find(".ks-execution-status--running").exists()).toBe(true)
    })

    test("applies size class for small", () => {
        const wrapper = mount(KsExecutionStatus, {
            props: {status: "SUCCESS", size: "small"},
            global: globalConfig,
        })
        expect(wrapper.find(".ks-execution-status--small").exists()).toBe(true)
    })

    test("applies size class for large", () => {
        const wrapper = mount(KsExecutionStatus, {
            props: {status: "SUCCESS", size: "large"},
            global: globalConfig,
        })
        expect(wrapper.find(".ks-execution-status--large").exists()).toBe(true)
    })

    test("renders custom title instead of status", () => {
        const wrapper = mount(KsExecutionStatus, {
            props: {status: "FAILED", title: "Custom Title"},
            global: globalConfig,
        })
        expect(wrapper.text()).toContain("Custom Title")
        expect(wrapper.text()).not.toContain("FAILED")
    })

    test("renders icon when icon prop is true", () => {
        const wrapper = mount(KsExecutionStatus, {
            props: {status: "SUCCESS", icon: true},
            global: globalConfig,
        })
        expect(wrapper.find(".ks-execution-status__icon").exists()).toBe(true)
    })

    test("does not render icon when icon prop is false", () => {
        const wrapper = mount(KsExecutionStatus, {
            props: {status: "SUCCESS", icon: false},
            global: globalConfig,
        })
        expect(wrapper.find(".ks-execution-status__icon").exists()).toBe(false)
    })

    test("is not clickable by default", () => {
        const wrapper = mount(KsExecutionStatus, {
            props: {status: "SUCCESS"},
            global: globalConfig,
        })
        expect(wrapper.find(".ks-execution-status--clickable").exists()).toBe(false)
    })

    test("applies clickable class when clickable prop is true", () => {
        const wrapper = mount(KsExecutionStatus, {
            props: {status: "SUCCESS", clickable: true},
            global: globalConfig,
        })
        expect(wrapper.find(".ks-execution-status--clickable").exists()).toBe(true)
    })

    test("emits a native click when clickable", async () => {
        const wrapper = mount(KsExecutionStatus, {
            props: {status: "FAILED", clickable: true},
            global: globalConfig,
        })
        await wrapper.find("button").trigger("click")
        expect(wrapper.emitted("click")).toHaveLength(1)
    })

    test("renders native disabled attribute when disabled prop is true", () => {
        const wrapper = mount(KsExecutionStatus, {
            props: {status: "SUCCESS", disabled: true},
            global: globalConfig,
        })
        expect(wrapper.find("button").attributes("disabled")).toBeDefined()
    })

    test("does not render disabled attribute by default", () => {
        const wrapper = mount(KsExecutionStatus, {
            props: {status: "SUCCESS"},
            global: globalConfig,
        })
        expect(wrapper.find("button").attributes("disabled")).toBeUndefined()
    })

    test("drops clickable affordance when disabled is true", () => {
        const wrapper = mount(KsExecutionStatus, {
            props: {status: "SUCCESS", clickable: true, disabled: true},
            global: globalConfig,
        })
        expect(wrapper.find(".ks-execution-status--clickable").exists()).toBe(false)
        expect(wrapper.find("button").attributes("disabled")).toBeDefined()
    })

    test("collapses to a non-interactive dot named for assistive technology", () => {
        const wrapper = mount(KsExecutionStatus, {
            props: {status: "FAILED", dot: true, clickable: true},
            global: globalConfig,
        })
        const dot = wrapper.find(".ks-execution-status--dot")
        expect(dot.element.tagName).toBe("SPAN")
        expect(dot.attributes("role")).toBe("img")
        expect(dot.attributes("aria-label")).toBe("Failed")
        expect(wrapper.find("button").exists()).toBe(false)
        expect(wrapper.find(".ks-execution-status__icon").exists()).toBe(false)
    })

    test("keeps the state label on a dot when a caller passes an undefined aria-label", () => {
        const wrapper = mount(KsExecutionStatus, {
            props: {status: "FAILED", dot: true},
            attrs: {"aria-label": undefined},
            global: globalConfig,
        })
        expect(wrapper.find(".ks-execution-status--dot").attributes("aria-label")).toBe("Failed")
    })

    test("lets a caller name a dot explicitly", () => {
        const wrapper = mount(KsExecutionStatus, {
            props: {status: "FAILED", dot: true},
            attrs: {"aria-label": "Last run failed"},
            global: globalConfig,
        })
        expect(wrapper.find(".ks-execution-status--dot").attributes("aria-label")).toBe("Last run failed")
    })

    test("renders all status variants", () => {
        const statuses = [
            "CREATED", "SUBMITTED", "ACCEPTED", "RESTARTED", "SUCCESS", "RUNNING", "KILLING",
            "KILLED", "WARNING", "FAILED", "PAUSED", "CANCELLED",
            "SKIPPED", "QUEUED", "RETRYING", "RETRIED", "BREAKPOINT",
        ] as const

        for (const status of statuses) {
            const wrapper = mount(KsExecutionStatus, {
                props: {status},
                global: globalConfig,
            })
            expect(wrapper.find(`.ks-execution-status--${status.toLowerCase()}`).exists()).toBe(true)
        }
    })

    test("declares the colour and background token of every status", () => {
        const known: Set<string> = knownTokens()
        const missing = Object.keys(EXECUTION_STATUSES)
            .flatMap(status => [`--ks-status-${status.toLowerCase()}`, `--ks-status-background-${status.toLowerCase()}`])
            .filter(token => !known.has(token))

        expect(missing).toEqual([])
    })
})
