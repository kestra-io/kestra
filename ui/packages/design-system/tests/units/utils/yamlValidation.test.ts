import {describe, test, expect} from "vitest"
import {findDuplicateTaskIds} from "../../../src/utils/yamlValidation"

describe("findDuplicateTaskIds", () => {
    test("a valid flow with unique ids produces no markers", () => {
        const yaml = `
id: flow_valid
namespace: company.team

tasks:
  - id: task_one
    type: io.kestra.plugin.core.log.Log
    message: Hello 1
  - id: task_two
    type: io.kestra.plugin.core.log.Log
    message: Hello 2
`
        const markers = findDuplicateTaskIds(yaml)
        expect(markers).toEqual([])
    })

    test("two tasks sharing an id produce exactly one marker, for the second occurrence", () => {
        const yaml = `
id: flow_duplicate
namespace: company.team

tasks:
  - id: my_task
    type: io.kestra.plugin.core.log.Log
    message: First
  - id: my_task
    type: io.kestra.plugin.core.log.Log
    message: Second
`
        const markers = findDuplicateTaskIds(yaml)
        expect(markers).toHaveLength(1)
        expect(markers[0].taskId).toBe("my_task")
        expect(markers[0].severity).toBe("error")
    })

    test("the marker's line and column point at the duplicate id scalar, and the message names the id", () => {
        const yaml = [
            "id: flow_coords",
            "namespace: company.team",
            "",
            "tasks:",
            "  - id: sample_id",
            "    type: io.kestra.plugin.core.log.Log",
            "  - id: sample_id",
            "    type: io.kestra.plugin.core.log.Log",
        ].join("\n")

        const markers = findDuplicateTaskIds(yaml)
        expect(markers).toHaveLength(1)
        const marker = markers[0]

        expect(marker.taskId).toBe("sample_id")
        expect(marker.message).toBe("Duplicate task id: \"sample_id\"")
        expect(marker.startLineNumber).toBe(7)
        expect(marker.startColumn).toBe(9)
        expect(marker.endLineNumber).toBe(7)
        expect(marker.endColumn).toBe(18)
        expect(marker.severity).toBe("error")
    })

    test("duplicates nested inside a flowable's tasks are found", () => {
        const yaml = `
id: flow_nested
namespace: company.team

tasks:
  - id: parent_task
    type: io.kestra.plugin.core.flow.Parallel
    tasks:
      - id: child_duplicate
        type: io.kestra.plugin.core.log.Log
      - id: child_duplicate
        type: io.kestra.plugin.core.log.Log
`
        const markers = findDuplicateTaskIds(yaml)
        expect(markers).toHaveLength(1)
        expect(markers[0].taskId).toBe("child_duplicate")
        expect(markers[0].message).toBe("Duplicate task id: \"child_duplicate\"")
    })

    test("duplicates inside errors and inside finally are found", () => {
        const yaml = `
id: flow_errors_finally
namespace: company.team

tasks:
  - id: main_task
    type: io.kestra.plugin.core.flow.Parallel
    tasks:
      - id: normal_task
        type: io.kestra.plugin.core.log.Log
    errors:
      - id: err_task
        type: io.kestra.plugin.core.log.Log
      - id: err_task
        type: io.kestra.plugin.core.log.Log
    finally:
      - id: fin_task
        type: io.kestra.plugin.core.log.Log
      - id: fin_task
        type: io.kestra.plugin.core.log.Log
`
        const markers = findDuplicateTaskIds(yaml)
        expect(markers).toHaveLength(2)
        expect(markers.map((m) => m.taskId)).toEqual(["err_task", "fin_task"])
    })

    test("an id repeated across a top-level task and a nested one is reported", () => {
        const yaml = `
id: flow_cross_scope
namespace: company.team

tasks:
  - id: shared_task_id
    type: io.kestra.plugin.core.log.Log
  - id: group
    type: io.kestra.plugin.core.flow.Parallel
    tasks:
      - id: shared_task_id
        type: io.kestra.plugin.core.log.Log
`
        const markers = findDuplicateTaskIds(yaml)
        expect(markers).toHaveLength(1)
        expect(markers[0].taskId).toBe("shared_task_id")
        expect(markers[0].message).toBe("Duplicate task id: \"shared_task_id\"")
    })

    test("malformed YAML returns an empty array instead of throwing", () => {
        const malformedYaml = `
id: broken_flow
namespace: [invalid: {
tasks:
  - id:
`
        expect(() => findDuplicateTaskIds(malformedYaml)).not.toThrow()
        expect(findDuplicateTaskIds(malformedYaml)).toEqual([])
    })
})
