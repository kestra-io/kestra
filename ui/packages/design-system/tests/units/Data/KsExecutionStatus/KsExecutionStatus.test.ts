import {describe, test, expect} from "vitest"
import {mount} from "@vue/test-utils"
import KestraDesignSystem from "../../../../src/index"
import KsExecutionStatus from "../../../../src/components/Data/KsExecutionStatus/KsExecutionStatus.vue"

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

    test("renders all status variants", () => {
        const statuses = [
            "CREATED", "RESTARTED", "SUCCESS", "RUNNING", "KILLING",
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
})
