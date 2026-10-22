import {describe, expect, it} from "vitest"
import {mount} from "@vue/test-utils"
import {defineComponent} from "vue"
import TaskNamespaces from "./TaskNamespaces.vue"

const NamespaceSelectStub = defineComponent({
    name: "NamespaceSelect",
    props: {
        modelValue: {
            type: Array,
            default: undefined,
        },
        multiple: {
            type: Boolean,
            default: false,
        },
        taggable: {
            type: Boolean,
            default: false,
        },
    },
    emits: ["update:modelValue"],
    template: "<div data-test=\"namespace-select\" />",
})

function mountTaskNamespaces(props = {}) {
    return mount(TaskNamespaces, {
        props,
        global: {
            stubs: {
                NamespaceSelect: NamespaceSelectStub,
            },
        },
    })
}

describe("TaskNamespaces", () => {
    it("renders the select in multiple and taggable mode", () => {
        const wrapper = mountTaskNamespaces()
        const select = wrapper.findComponent(NamespaceSelectStub)

        expect(select.exists()).toBe(true)
        expect(select.props("multiple")).toBe(true)
        expect(select.props("taggable")).toBe(true)
    })

    it("passes an explicitly provided modelValue through to the select", () => {
        const namespaces = ["dev", "prod"]
        const wrapper = mountTaskNamespaces({modelValue: namespaces})
        const select = wrapper.findComponent(NamespaceSelectStub)

        expect(select.props("modelValue")).toEqual(namespaces)
    })

    it("emits the updated array when the select emits update:modelValue", async () => {
        const wrapper = mountTaskNamespaces()
        const select = wrapper.findComponent(NamespaceSelectStub)

        await select.vm.$emit("update:modelValue", ["company.team", "company.analytics"])

        expect(wrapper.emitted("update:modelValue")).toBeTruthy()
        expect(wrapper.emitted("update:modelValue")?.[0]).toEqual([["company.team", "company.analytics"]])
    })

    it("defaults to an empty array when no modelValue is given", () => {
        const wrapper = mountTaskNamespaces()
        const select = wrapper.findComponent(NamespaceSelectStub)

        expect(select.props("modelValue")).toEqual([])
    })

    it("does not share the same default array instance between separate mounts", () => {
        const first = mountTaskNamespaces()
        const second = mountTaskNamespaces()

        const firstSelect = first.findComponent(NamespaceSelectStub)
        const secondSelect = second.findComponent(NamespaceSelectStub)

        const firstValue = firstSelect.props("modelValue")
        const secondValue = secondSelect.props("modelValue")

        expect(firstValue).toEqual([])
        expect(secondValue).toEqual([])
        expect(firstValue).not.toBe(secondValue)
    })
})
