import {describe, expect, it} from "vitest"
import {checkDefinition, extractTaskDefinition} from "./replayEditedTask"

const source = `id: flow
namespace: io.kestra.tests
tasks:
  - id: check
    type: io.kestra.plugin.core.flow.If
    condition: "{{ true }}"
    then:
      - id: reserve
        type: io.kestra.plugin.core.log.Log
        message: hi
errors:
  - id: alert
    type: io.kestra.plugin.core.log.Log
    message: failed
`

describe("extractTaskDefinition", () => {
    it("finds a nested task and a task outside the tasks section", () => {
        expect(extractTaskDefinition(source, "reserve")).toContain("message: hi")
        expect(extractTaskDefinition(source, "alert")).toContain("message: failed")
        expect(extractTaskDefinition(source, "missing")).toBeUndefined()
    })
})

describe("checkDefinition", () => {
    const original = "id: alert\ntype: io.kestra.plugin.core.log.Log\nmessage: failed\n"

    it.each([
        ["id: alert\ntype: io.kestra.plugin.core.log.Log\nmessage: fixed\n", {}],
        ["id: other\ntype: io.kestra.plugin.core.log.Log\nmessage: fixed\n", {error: "identity"}],
        ["id: alert\ntype: io.kestra.plugin.core.debug.Return\n", {error: "identity"}],
        ["id: alert\n  type: [", {error: "yaml"}],
    ])("checks %j", (edited, expected) => {
        expect(checkDefinition(original, edited)).toEqual(expected)
    })
})
