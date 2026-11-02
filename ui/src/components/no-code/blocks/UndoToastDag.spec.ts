
import {describe, expect, test} from "vitest"
import {flushPromises} from "@vue/test-utils"
import {nextTick} from "vue"
import UndoToast from "./UndoToast.vue"
import DagDependsOnEditor from "./DagDependsOnEditor.vue"
import {i18nMount} from "../../../../tests/unit/i18nMount"

describe("UndoToast", () => {
    test("renders nothing when state is null", () => {
        const wrapper = i18nMount(UndoToast, {
            props: {state: null},
        })

        expect(wrapper.find(".undo-toast").exists()).toBe(false)
        expect(wrapper.find("[data-test='undo-toast-button']").exists()).toBe(false)
    })

    test("renders the state's label", () => {
        const wrapper = i18nMount(UndoToast, {
            props: {state: {label: "Task deleted"}},
        })

        expect(wrapper.find(".undo-toast").exists()).toBe(true)
        expect(wrapper.find(".undo-toast-label").text()).toBe("Task deleted")
    })

    test("emits undo exactly once when the undo button is clicked", async () => {
        const wrapper = i18nMount(UndoToast, {
            props: {state: {label: "Task deleted"}},
        })

        const undoButton = wrapper.find("[data-test='undo-toast-button']")
        expect(undoButton.exists()).toBe(true)

        await undoButton.trigger("click")

        expect(wrapper.emitted("undo")).toHaveLength(1)
    })
})

describe("DagDependsOnEditor", () => {
    const mountEditor = (dependsOn?: string[]) =>
        i18nMount(DagDependsOnEditor, {
            props: {
                dependsOn,
                siblingIds: ["extract", "transform", "load"],
            },
        })

    test("renders one entry per existing dependency", async () => {
        const wrapper = mountEditor(["extract", "transform"])
        await flushPromises()
        await nextTick()

        expect(wrapper.find(".dag-depends-on").exists()).toBe(true)
        expect(wrapper.findAll(".kel-tag")).toHaveLength(2)
        expect(wrapper.findAll(".kel-tag").map(tag => tag.text()))
            .toEqual(["extract", "transform"])
    })

    test("offers every sibling task id as an option", () => {
        const wrapper = mountEditor(["extract"])

        expect(wrapper.findAllComponents({name: "KsOption"}).map(option => option.props("value")))
            .toEqual(["extract", "transform", "load"])
    })

    test("emits the updated list when a dependency is added", async () => {
        const wrapper = mountEditor(["extract"])
        const select = wrapper.findComponent({name: "KsSelect"})

        select.vm.$emit("update:modelValue", ["extract", "transform"])
        await wrapper.vm.$nextTick()

        expect(wrapper.emitted("update")).toEqual([[["extract", "transform"]]])
    })

    test("emits the list without a dependency when it is removed", async () => {
        const wrapper = mountEditor(["extract", "transform"])
        const select = wrapper.findComponent({name: "KsSelect"})

        select.vm.$emit("update:modelValue", ["transform"])
        await wrapper.vm.$nextTick()

        expect(wrapper.emitted("update")).toEqual([[["transform"]]])
    })

    test("does not emit an update when the select value is undefined", () => {
        const wrapper = mountEditor(["extract"])
        const select = wrapper.findComponent({name: "KsSelect"})

        select.vm.$emit("update:modelValue", undefined)

        expect(wrapper.emitted("update")).toBeUndefined()
    })

    test("renders an empty list when no dependencies are selected", () => {
        const wrapper = mountEditor([])

        expect(wrapper.findComponent({name: "KsSelect"}).props("modelValue")).toEqual([])
        expect(wrapper.findAll(".kel-tag")).toHaveLength(0)
    })

    test("defaults an omitted dependency list to empty", () => {
        const wrapper = mountEditor()

        expect(wrapper.findComponent({name: "KsSelect"}).props("modelValue")).toEqual([])
    })
})
