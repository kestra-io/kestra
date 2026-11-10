import {describe, test, expect} from "vitest"
import {mount} from "@vue/test-utils"
import KestraDesignSystem from "../../index"
import KsAlert, {KS_ALERT_TYPES} from "./KsAlert.vue"
// @ts-expect-error - plain .mjs module, shared with the linters
import {knownTokens} from "../../../../../scripts/tokens/knownTokens.mjs"

const globalConfig = {plugins: [KestraDesignSystem]}

describe("KsAlert", () => {
    test("renders alert element", () => {
        const wrapper = mount(KsAlert, {
            props: {type: "info", title: "Info"},
            global: globalConfig,
        })
        expect(wrapper.find(".kel-alert").exists()).toBe(true)
    })

    test("type prop applies correct class", () => {
        const wrapper = mount(KsAlert, {
            props: {type: "warning"},
            global: globalConfig,
        })
        expect(wrapper.find(".kel-alert--warning").exists()).toBe(true)
    })

    test("error type applies correct class", () => {
        const wrapper = mount(KsAlert, {
            props: {type: "error"},
            global: globalConfig,
        })
        expect(wrapper.find(".kel-alert--error").exists()).toBe(true)
    })

    test("title prop renders title text", () => {
        const wrapper = mount(KsAlert, {
            props: {type: "info", title: "Test Alert Title"},
            global: globalConfig,
        })
        expect(wrapper.text()).toContain("Test Alert Title")
    })

    test("declares the border, background, text and icon token of every type", () => {
        const known: Set<string> = knownTokens()
        const missing = KS_ALERT_TYPES
            .flatMap(type => ["border", "bg", "text", "icon"].map(family => `--ks-${family}-${type}`))
            .filter(token => !known.has(token))

        expect(missing).toEqual([])
    })
})
