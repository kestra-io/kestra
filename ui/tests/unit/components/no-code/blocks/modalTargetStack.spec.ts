import {describe, it, expect, vi} from "vitest"
import {ref, computed} from "vue"
import {useBlockSelection, modalItemPathOf} from "../../../../../src/components/no-code/blocks/useBlockSelection"
import {taskCrumbAt} from "../../../../../src/components/no-code/blocks/useEditTarget"

vi.mock("../../../../../src/components/no-code/blocks/taskEditMode", () => ({
    opensInModalByDefault: () => true,
}))

const FLOW = `id: nested
namespace: company.team
tasks:
  - id: branch_block
    type: io.kestra.plugin.core.flow.If
    condition: "{{ true }}"
    then:
      - id: nested_then
        type: io.kestra.plugin.core.log.Log
        message: in then
`

function selection() {
    const onEditTask = vi.fn()
    const block: Record<string, unknown> = {id: "branch_block"}
    const api = useBlockSelection({
        selectedId: computed(() => undefined),
        editorEl: ref(undefined),
        flowYaml: ref(FLOW),
        flowSchemaRoot: ref("#/definitions/Flow"),
        sectionList: () => [block],
        onSelectedIdChange: () => {},
        onEditTask,
        onCloseTask: () => {},
    })
    return {api, block, onEditTask}
}

describe("modal target stack", () => {
    it("starts a fresh stack when a canvas block is selected", () => {
        // Given
        const {api, block} = selection()
        api.pushModalTarget({parentPath: "stale", blockSchemaPath: "s", refPath: 0})

        // When
        api.selectBlock("tasks", block)

        // Then
        expect(api.modalStack.value).toHaveLength(1)
        expect(api.modalTarget.value?.parentPath).toBe("tasks")
    })

    it("drills in without losing the parent", () => {
        // Given
        const {api, block} = selection()
        api.selectBlock("tasks", block)

        // When
        api.pushModalTarget({parentPath: "tasks[0].then", blockSchemaPath: "s", refPath: 0})

        // Then
        expect(api.modalStack.value.map(t => t.parentPath)).toEqual(["tasks", "tasks[0].then"])
        expect(api.modalTarget.value?.parentPath).toBe("tasks[0].then")
    })

    it("navigates back to an ancestor", () => {
        // Given
        const {api, block} = selection()
        api.selectBlock("tasks", block)
        api.pushModalTarget({parentPath: "tasks[0].then", blockSchemaPath: "s", refPath: 0})

        // When
        api.popModalTo(0)

        // Then
        expect(api.modalStack.value).toHaveLength(1)
        expect(api.modalTarget.value?.parentPath).toBe("tasks")
    })

    it("closes the modal by emptying the stack", () => {
        // Given
        const {api, block} = selection()
        api.selectBlock("tasks", block)

        // When
        api.closeModal()

        // Then
        expect(api.modalTarget.value).toBeUndefined()
    })

    it("delegates a split request instead of stacking it", () => {
        // Given
        const {api, block, onEditTask} = selection()

        // When
        api.selectBlock("tasks", block, true)

        // Then
        expect(api.modalStack.value).toHaveLength(0)
        expect(onEditTask).toHaveBeenCalledWith("tasks", "#/definitions/Flow/properties/tasks/items", 0, true)
    })
})

describe("modalItemPathOf", () => {
    it("indexes into the parent list when a ref is given", () => {
        expect(modalItemPathOf({parentPath: "tasks", blockSchemaPath: "s", refPath: 2})).toBe("tasks[2]")
    })

    it("keeps the bare parent path without a ref", () => {
        expect(modalItemPathOf({parentPath: "triggers", blockSchemaPath: "s"})).toBe("triggers")
    })
})

describe("taskCrumbAt", () => {
    it("labels a crumb with the task id", () => {
        expect(taskCrumbAt(FLOW, "tasks[0]")).toEqual({path: "tasks[0]", label: "branch_block"})
    })

    it("labels a nested crumb with the nested task id", () => {
        expect(taskCrumbAt(FLOW, "tasks[0].then[0]")).toEqual({path: "tasks[0].then[0]", label: "nested_then"})
    })

    it("falls back to the path when the task is gone", () => {
        expect(taskCrumbAt(FLOW, "tasks[9]")).toEqual({path: "tasks[9]", label: "tasks[9]"})
    })
})
