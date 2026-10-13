import {describe, expect, test} from "vitest"
import {nextTick} from "vue"
import {flushPromises} from "@vue/test-utils"
import {KsSelect} from "@kestra-io/design-system"
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

    test("renders the state's label when one is given", () => {
        const wrapper = i18nMount(UndoToast, {
            props: {state: {label: "task deleted"}},
        })

        expect(wrapper.find(".undo-toast").exists()).toBe(true)
        expect(wrapper.find(".undo-toast-label").text()).toBe("task deleted")
    })

    test("activating undo emits the undo event exactly once", async () => {
        const wrapper = i18nMount(UndoToast, {
            props: {state: {label: "task deleted"}},
        })

        const button = wrapper.find("[data-test='undo-toast-button']")
        expect(button.exists()).toBe(true)

        await button.trigger("click")

        expect(wrapper.emitted("undo")).toHaveLength(1)
    })
})

describe("DagDependsOnEditor", () => {
    test("renders one entry per existing dependency", async () => {
        const wrapper = i18nMount(DagDependsOnEditor, {
            props: {
                dependsOn: ["task_a", "task_b"],
                siblingIds: ["task_a", "task_b", "task_c"],
            },
        })

        await flushPromises()
        await nextTick()

        const tags = wrapper.findAll(".kel-tag")
        expect(tags).toHaveLength(2)
        expect(tags.map(t => t.text())).toEqual(["task_a", "task_b"])
    })

    test("adding a dependency emits the updated list", async () => {
        const wrapper = i18nMount(DagDependsOnEditor, {
            props: {
                dependsOn: ["task_a"],
                siblingIds: ["task_a", "task_b", "task_c"],
            },
        })

        const select = wrapper.findComponent(KsSelect)
        select.vm.$emit("update:modelValue", ["task_a", "task_b"])

        expect(wrapper.emitted("update")).toBeTruthy()
        expect(wrapper.emitted("update")![0]).toEqual([["task_a", "task_b"]])
    })

    test("removing one emits the list without it", async () => {
        const wrapper = i18nMount(DagDependsOnEditor, {
            props: {
                dependsOn: ["task_a", "task_b"],
                siblingIds: ["task_a", "task_b", "task_c"],
            },
        })

        await flushPromises()
        await nextTick()

        const closeBtn = wrapper.find(".kel-tag__close")
        if (closeBtn.exists()) {
            await closeBtn.trigger("click")
        } else {
            const select = wrapper.findComponent(KsSelect)
            select.vm.$emit("update:modelValue", ["task_b"])
        }

        expect(wrapper.emitted("update")).toBeTruthy()
        expect(wrapper.emitted("update")![0]).toEqual([["task_b"]])
    })

    test("an empty dependency list renders without throwing", () => {
        expect(() => {
            const wrapper = i18nMount(DagDependsOnEditor, {
                props: {
                    dependsOn: [],
                    siblingIds: ["task_a", "task_b"],
                },
            })
            expect(wrapper.findAll(".kel-tag")).toHaveLength(0)
        }).not.toThrow()

        expect(() => {
            const wrapper = i18nMount(DagDependsOnEditor, {
                props: {
                    dependsOn: undefined,
                    siblingIds: ["task_a", "task_b"],
                },
            })
            expect(wrapper.findAll(".kel-tag")).toHaveLength(0)
        }).not.toThrow()
    })
})
