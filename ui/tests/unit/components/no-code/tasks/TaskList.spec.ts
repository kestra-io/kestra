import {describe, test, expect, beforeEach, vi} from "vitest"
import {mount} from "@vue/test-utils"
import {createPinia, setActivePinia} from "pinia"
import {createI18n} from "vue-i18n"
import {ref} from "vue"
import KestraDesignSystem from "@kestra-io/design-system"
import TaskList from "../../../../../src/components/no-code/components/tasks/TaskList.vue"
import {
    BLOCK_SCHEMA_PATH_INJECTION_KEY,
    CREATING_TASK_INJECTION_KEY,
    EDIT_TASK_FUNCTION_INJECTION_KEY,
    FULL_SCHEMA_INJECTION_KEY,
    FULL_SOURCE_INJECTION_KEY,
    PARENT_PATH_INJECTION_KEY,
    REF_PATH_INJECTION_KEY,
    UPDATE_YAML_FUNCTION_INJECTION_KEY,
} from "../../../../../src/components/no-code/injectionKeys"

const SOURCE = `
id: qa
namespace: company.team
tasks:
  - id: my_if
    type: io.kestra.plugin.core.flow.If
    then:
      - id: then_ok
        type: io.kestra.plugin.core.log.Log
        message: ok
      - id: then_bad
        type: io.kestra.plugin.core.log.Log
        message: bad
`.trim()

const dragEvent = () => ({preventDefault: () => {}, dataTransfer: {setData: () => {}, effectAllowed: "", dropEffect: ""}})

function mountList() {
    const updateYaml = vi.fn()
    const editTask = vi.fn()
    const wrapper = mount(TaskList, {
        props: {
            modelValue: [
                {id: "then_ok", type: "io.kestra.plugin.core.log.Log"},
                {id: "then_bad", type: "io.kestra.plugin.core.log.Log"},
            ],
            root: "then",
        },
        global: {
            plugins: [
                createI18n({legacy: false, locale: "en", fallbackWarn: false, missingWarn: false}),
                KestraDesignSystem,
            ],
            provide: {
                [FULL_SOURCE_INJECTION_KEY as symbol]: ref(SOURCE),
                [UPDATE_YAML_FUNCTION_INJECTION_KEY as symbol]: updateYaml,
                [EDIT_TASK_FUNCTION_INJECTION_KEY as symbol]: editTask,
                [PARENT_PATH_INJECTION_KEY as symbol]: "tasks[0]",
                [REF_PATH_INJECTION_KEY as symbol]: undefined,
                [CREATING_TASK_INJECTION_KEY as symbol]: false,
                [BLOCK_SCHEMA_PATH_INJECTION_KEY as symbol]: ref("#/schema"),
                [FULL_SCHEMA_INJECTION_KEY as symbol]: ref({}),
            },
            stubs: {
                KsCollapse: {template: "<div><slot /></div>"},
                KsCollapseItem: {template: "<div><slot name='icon' /><slot /></div>"},
                Creation: {template: "<div class='creation-stub' />"},
                LeafBlockCard: {
                    name: "LeafBlockCard",
                    props: ["block", "path", "label", "draggable", "dragOver", "runnable", "showDuplicate", "icons"],
                    emits: ["select", "open-split", "delete", "duplicate", "run", "drag-start", "drag-over", "drop", "drag-end"],
                    template: "<div class='leaf-card' :data-path='path' />",
                },
            },
        },
    })
    return {wrapper, updateYaml, editTask}
}

describe("TaskList (properties-panel sub-task list)", () => {
    beforeEach(() => setActivePinia(createPinia()))

    test("renders one card per sub-task with the YAML-path", () => {
        const {wrapper} = mountList()
        const cards = wrapper.findAllComponents({name: "LeafBlockCard"})
        expect(cards).toHaveLength(2)
        expect(cards[0].props("path")).toBe("tasks[0].then[0]")
        expect(cards[1].props("path")).toBe("tasks[0].then[1]")
    })

    test("select opens the task editor at the computed path and index", async () => {
        const {wrapper, editTask} = mountList()
        await wrapper.findAllComponents({name: "LeafBlockCard"})[1].vm.$emit("select")
        expect(editTask).toHaveBeenCalledWith("tasks[0].then", expect.any(String), 1, false)
    })

    test("open-split opens the editor with the split flag", async () => {
        const {wrapper, editTask} = mountList()
        await wrapper.findAllComponents({name: "LeafBlockCard"})[0].vm.$emit("open-split")
        expect(editTask).toHaveBeenCalledWith("tasks[0].then", expect.any(String), 0, true)
    })

    test("delete rewrites the YAML without the targeted sub-task", async () => {
        const {wrapper, updateYaml} = mountList()
        await wrapper.findAllComponents({name: "LeafBlockCard"})[0].vm.$emit("delete")
        expect(updateYaml).toHaveBeenCalledTimes(1)
        const newYaml = updateYaml.mock.calls[0][0] as string
        expect(newYaml).not.toContain("then_ok")
        expect(newYaml).toContain("then_bad")
    })

    test("duplicate rewrites the YAML with a copy of the targeted sub-task", async () => {
        const {wrapper, updateYaml} = mountList()
        await wrapper.findAllComponents({name: "LeafBlockCard"})[0].vm.$emit("duplicate")
        expect(updateYaml).toHaveBeenCalledTimes(1)
        expect(updateYaml.mock.calls[0][0] as string).toContain("then_ok_copy")
    })

    test("drag-and-drop reorders the sub-tasks in the YAML", async () => {
        const {wrapper, updateYaml} = mountList()
        const cards = wrapper.findAllComponents({name: "LeafBlockCard"})
        await cards[0].vm.$emit("drag-start", dragEvent())
        await cards[1].vm.$emit("drop", dragEvent())
        expect(updateYaml).toHaveBeenCalledTimes(1)
        const newYaml = updateYaml.mock.calls[0][0] as string
        expect(newYaml.indexOf("then_bad")).toBeLessThan(newYaml.indexOf("then_ok"))
    })
})
