import {describe, expect, it} from "vitest"
import {resolveEditTaskTarget} from "./useTopologyPanels"
import type {usePluginsStore} from "../../stores/plugins"

const pluginsStore = {flowSchema: {$ref: "#/definitions/io.kestra.core.models.flows.Flow"}} as unknown as ReturnType<typeof usePluginsStore>

const SOURCE = `id: monthly_close
namespace: company.finance
tasks:
  - id: fan_out
    type: io.kestra.plugin.core.flow.Parallel
    tasks:
      - id: extract
        type: io.kestra.plugin.core.log.Log
        message: extracting
      - id: reconcile
        type: io.kestra.plugin.core.log.Log
        message: reconciling
  - id: publish
    type: io.kestra.plugin.core.log.Log
    message: publishing
`

describe("resolveEditTaskTarget", () => {
    it("should point at the parent list and index of a task nested in a flowable", () => {
        expect(resolveEditTaskTarget(SOURCE, pluginsStore, "tasks", "reconcile")).toEqual({
            parentPath: "tasks[0].tasks",
            refPath: 1,
            blockSchemaPath: "#/definitions/io.kestra.core.models.flows.Flow/properties/tasks/items",
        })
    })

    it("should point at the top-level task list for a root task", () => {
        expect(resolveEditTaskTarget(SOURCE, pluginsStore, "tasks", "publish")).toMatchObject({parentPath: "tasks", refPath: 1})
    })

    it("should resolve nothing for a task id the flow does not declare", () => {
        expect(resolveEditTaskTarget(SOURCE, pluginsStore, "tasks", "missing")).toBeUndefined()
    })
})
