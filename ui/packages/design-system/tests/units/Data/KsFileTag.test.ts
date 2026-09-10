import {describe, test, expect} from "vitest"
import {mount} from "@vue/test-utils"
import KestraDesignSystem from "../../../src/index"
import KsFileTag from "../../../src/components/Data/KsFileTag.vue"
import KsTooltip from "../../../src/components/Feedback/KsTooltip.vue"

const globalConfig = {plugins: [KestraDesignSystem]}

describe("KsFileTag", () => {
    test("shows the given name rather than the generated URI segment", () => {
        const wrapper = mount(KsFileTag, {
            props: {uri: "kestra:///company/team/exec/outputs/8f2c1d.txt", name: "report"},
            global: globalConfig,
        })
        expect(wrapper.text()).toBe("report")
    })

    test("falls back to the URI's last segment when no name is given", () => {
        const wrapper = mount(KsFileTag, {
            props: {uri: "kestra:///company/team/exec/outputs/report.csv"},
            global: globalConfig,
        })
        expect(wrapper.text()).toBe("report.csv")
    })

    test("carries the name into the tooltip, since that is the part that gets clipped", () => {
        const wrapper = mount(KsFileTag, {
            props: {uri: "kestra:///company/team/exec/outputs/8f2c1d.parquet", name: "a-very-long-output-name"},
            global: globalConfig,
        })
        expect(wrapper.findComponent(KsTooltip).props("content"))
            .toBe("a-very-long-output-name (kestra:///company/team/exec/outputs/8f2c1d.parquet)")
    })

    test("leaves the tooltip as the bare URI when the label already comes from it", () => {
        const wrapper = mount(KsFileTag, {
            props: {uri: "kestra:///company/team/exec/outputs/report.csv"},
            global: globalConfig,
        })
        expect(wrapper.findComponent(KsTooltip).props("content"))
            .toBe("kestra:///company/team/exec/outputs/report.csv")
    })

    test("scopes URI wrapping to its own popper class so other KsTooltip consumers stay uncapped", () => {
        const wrapper = mount(KsFileTag, {
            props: {uri: "kestra:///company/team/exec/outputs/report.csv"},
            global: globalConfig,
        })
        const attrs = wrapper.findComponent(KsTooltip).vm.$attrs as {popperClass?: string}
        expect(attrs.popperClass).toBe("ks-file-tag-tooltip")
    })

    test("picks the icon from the URI, whose extension the name often lacks", () => {
        const wrapper = mount(KsFileTag, {
            props: {uri: "kestra:///company/team/exec/outputs/8f2c1d.png", name: "screenshot"},
            global: globalConfig,
        })
        expect(wrapper.find(".file-image-outline-icon").exists()).toBe(true)
    })

    test("falls back to the name's extension when the URI carries none", () => {
        const wrapper = mount(KsFileTag, {
            props: {uri: "kestra:///company/team/exec/outputs/8f2c1d", name: "data.csv"},
            global: globalConfig,
        })
        expect(wrapper.find(".file-delimited-outline-icon").exists()).toBe(true)
    })

    test("uses the generic file icon for an unknown extension", () => {
        const wrapper = mount(KsFileTag, {
            props: {uri: "kestra:///company/team/exec/outputs/abc"},
            global: globalConfig,
        })
        expect(wrapper.find(".file-outline-icon").exists()).toBe(true)
    })
})
