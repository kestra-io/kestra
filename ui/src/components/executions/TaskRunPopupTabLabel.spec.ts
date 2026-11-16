import {describe, expect, test} from "vitest"
import {defineComponent, h, markRaw} from "vue"
import {i18nMount} from "../../../tests/unit/i18nMount"
import TaskRunPopupTabLabel from "./TaskRunPopupTabLabel.vue"

const SampleIcon = markRaw(defineComponent({render: () => h("svg")}))

const mount = (props: InstanceType<typeof TaskRunPopupTabLabel>["$props"]) =>
    i18nMount(TaskRunPopupTabLabel, {props})

describe("TaskRunPopupTabLabel", () => {
    test("renders the label text", () => {
        const wrapper = mount({label: "Logs", status: "ready", count: 3})

        expect(wrapper.find("span").text()).toContain("Logs")
    })

    test("loading: shows the skeleton wrapper and neither tag", () => {
        const wrapper = mount({label: "Logs", status: "loading", count: 0})

        expect(wrapper.find("[data-test=\"tab-count-loading\"]").exists()).toBe(true)
        expect(wrapper.find("[data-test=\"tab-count-error\"]").exists()).toBe(false)
        expect(wrapper.find("[data-test=\"tab-count\"]").exists()).toBe(false)
    })

    test("error: shows the warning tag and neither the count tag nor the skeleton", () => {
        const wrapper = mount({label: "Logs", status: "error", count: 0})

        expect(wrapper.find("[data-test=\"tab-count-error\"]").exists()).toBe(true)
        expect(wrapper.find("[data-test=\"tab-count\"]").exists()).toBe(false)
        expect(wrapper.find("[data-test=\"tab-count-loading\"]").exists()).toBe(false)
    })

    test("ready: shows the count tag with the count value and neither the skeleton nor the warning tag", () => {
        const wrapper = mount({label: "Logs", status: "ready", count: 7})

        const tag = wrapper.find("[data-test=\"tab-count\"]")
        expect(tag.exists()).toBe(true)
        expect(tag.text()).toBe("7")
        expect(wrapper.find("[data-test=\"tab-count-loading\"]").exists()).toBe(false)
        expect(wrapper.find("[data-test=\"tab-count-error\"]").exists()).toBe(false)
    })

    test("ready: a count of 0 still renders in the count tag", () => {
        const wrapper = mount({label: "Logs", status: "ready", count: 0})

        const tag = wrapper.find("[data-test=\"tab-count\"]")
        expect(tag.exists()).toBe(true)
        expect(tag.text()).toBe("0")
    })

    test("renders the icon only when the icon prop is passed", () => {
        const withoutIcon = mount({label: "Artifacts", status: "ready", count: 2})
        expect(withoutIcon.findComponent({name: "KsIcon"}).exists()).toBe(false)

        const withIcon = mount({label: "Artifacts", status: "ready", count: 2, icon: SampleIcon})
        expect(withIcon.findComponent({name: "KsIcon"}).exists()).toBe(true)
    })
})
