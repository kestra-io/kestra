import {describe, expect, it} from "vitest"
import {resolveTaskInsertionTargetInAnySection} from "./blockSections"

const SOURCE = `id: flow
namespace: company.team
tasks:
  - id: extract
    type: io.kestra.plugin.core.log.Log
    message: extracting
  - id: route
    type: io.kestra.plugin.core.flow.If
    condition: "true"
    then:
      - id: nested
        type: io.kestra.plugin.core.log.Log
        message: nested
errors:
  - id: notify
    type: io.kestra.plugin.core.log.Log
    message: failed
`

describe("resolveTaskInsertionTargetInAnySection", () => {
    it("should resolve a task sitting in the tasks section", () => {
        expect(resolveTaskInsertionTargetInAnySection(SOURCE, "extract")).toEqual({
            parentPath: "tasks",
            refIndex: 0,
            section: "tasks",
        })
    })

    it("should resolve a task sitting in the errors section", () => {
        expect(resolveTaskInsertionTargetInAnySection(SOURCE, "notify")).toEqual({
            parentPath: "errors",
            refIndex: 0,
            section: "errors",
        })
    })

    it("should resolve a task nested in a flowable lane", () => {
        const target = resolveTaskInsertionTargetInAnySection(SOURCE, "nested")

        expect(target?.section).toBe("tasks")
        expect(target?.parentPath).toBe("tasks[1].then")
        expect(target?.refIndex).toBe(0)
    })

    it("should return undefined for an id no section holds", () => {
        expect(resolveTaskInsertionTargetInAnySection(SOURCE, "absent")).toBeUndefined()
    })
})
