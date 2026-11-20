import {beforeEach, describe, expect, it, vi} from "vitest"
import {ref} from "vue"
import * as flowYamlUtils from "@kestra-io/topology/flow-yaml-utils"

const trackAuthoringAction = vi.fn()
vi.mock("../../../utils/tabTracking", () => ({
    trackAuthoringAction: (...a: unknown[]) => trackAuthoringAction(...a),
}))

import {useBlockMutations} from "./useBlockMutations"

const SOURCE = [
    "id: my-flow",
    "namespace: company.team",
    "tasks:",
    "  - id: first",
    "    type: io.kestra.plugin.core.log.Log",
    "    message: hello",
    "  - id: second",
    "    type: io.kestra.plugin.core.log.Log",
    "    message: world",
    "    dependsOn:",
    "      - first",
    "",
].join("\n")

describe("useBlockMutations", () => {
    const applyYaml = vi.fn()
    const deleteWithUndo = vi.fn()
    const deselectIfCurrent = vi.fn()

    const setup = (source = SOURCE) =>
        useBlockMutations({flowYaml: ref(source), applyYaml, deleteWithUndo, deselectIfCurrent})

    beforeEach(() => {
        vi.clearAllMocks()
    })

    it("deleteInSection ignores a non-string id", () => {
        setup().deleteInSection("tasks", 42)

        expect(deleteWithUndo).not.toHaveBeenCalled()
        expect(applyYaml).not.toHaveBeenCalled()
    })

    it("deleteInSection applies nothing until the undo callback fires", () => {
        setup().deleteInSection("tasks", "first")

        expect(deleteWithUndo).toHaveBeenCalledWith("first", expect.any(Function))
        expect(applyYaml).not.toHaveBeenCalled()
        expect(deselectIfCurrent).not.toHaveBeenCalled()
    })

    it("deleteInSection deselects the block and applies the YAML without it when the undo callback fires", () => {
        setup().deleteInSection("tasks", "first")
        deleteWithUndo.mock.calls[0][1]()

        expect(deselectIfCurrent).toHaveBeenCalledWith("first")
        const applied = applyYaml.mock.calls[0][0] as string
        expect(applied).not.toContain("id: first")
        expect(applied).toContain("id: second")
        expect(trackAuthoringAction).toHaveBeenCalledWith("task_deleted", "no_code", {task_type: "io.kestra.plugin.core.log.Log"})
    })

    it("deleteAtPath names the entry by its parsed id", () => {
        setup().deleteAtPath("tasks[1]")
        expect(deleteWithUndo).toHaveBeenCalledWith("second", expect.any(Function))

        deleteWithUndo.mock.calls[0][1]()
        expect(deselectIfCurrent).toHaveBeenCalledWith("second")
        expect(applyYaml.mock.calls[0][0]).not.toContain("id: second")
    })

    it("deleteAtPath falls back to the path when the entry has no id", () => {
        setup().deleteAtPath("tasks[5]")
        expect(deleteWithUndo).toHaveBeenCalledWith("tasks[5]", expect.any(Function))

        deleteWithUndo.mock.calls[0][1]()
        expect(deselectIfCurrent).not.toHaveBeenCalled()
    })

    it("duplicateInSection ignores a non-string id", () => {
        setup().duplicateInSection("tasks", undefined)

        expect(applyYaml).not.toHaveBeenCalled()
    })

    it("duplicateInSection applies the duplicated YAML", () => {
        setup().duplicateInSection("tasks", "first")

        const applied = applyYaml.mock.calls[0][0] as string
        expect(applied.match(/message: hello/g)).toHaveLength(2)
    })

    it("updateDependsOn writes the stringified list at the dependsOn path", () => {
        setup().updateDependsOn("tasks[0]", ["second", "third"])

        const applied = flowYamlUtils.parse<{tasks: {dependsOn?: string[]}[]}>(applyYaml.mock.calls[0][0])
        expect(applied?.tasks.map(task => task.dependsOn)).toEqual([["second", "third"], ["first"]])
    })

    it("updateDependsOn clears the key instead of writing an empty array when the list is empty", () => {
        setup().updateDependsOn("tasks[1]", [])

        const applied = applyYaml.mock.calls[0][0] as string
        expect(applied).not.toContain("[]")
        expect(applied).not.toContain("- first")
    })
})
