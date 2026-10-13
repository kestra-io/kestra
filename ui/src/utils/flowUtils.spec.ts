import {describe, it, expect} from "vitest"
import * as YAML_UTILS from "@kestra-io/topology/flow-yaml-utils"
import * as FlowUtils from "./flowUtils"

const LOG = "io.kestra.plugin.core.log.Log"

export const flat = `
id: flat
namespace: io.kestra.tests

tasks:
  - id: 1-1
    type: io.kestra.plugin.core.log.Log
    # comment to keep
    message: 'echo "1-1"'
  - id: 1-2
    type: io.kestra.plugin.core.log.Log
    message: 'echo "1-2"'
`

export const flowable = `
id: flowable
namespace: io.kestra.tests

tasks:
  - id: nest-1
    type: io.kestra.plugin.core.flow.Parallel
    tasks:
      - id: nest-2
        type: io.kestra.plugin.core.flow.Parallel
        tasks:
        - id: nest-3
          type: io.kestra.plugin.core.flow.Parallel
          tasks:
          - id: nest-4
            type: io.kestra.plugin.core.flow.Parallel
            tasks:
              - id: 1-1
                type: io.kestra.plugin.core.log.Log
                message: 'echo "1-1"'
              - id: 1-2
                type: io.kestra.plugin.core.log.Log
                message: 'echo "1-2"'

  - id: end
    type: io.kestra.plugin.core.log.Log
    commands:
      - 'echo "end"'
`

export const plugins = `
id: flowable
namespace: io.kestra.tests

tasks:
  - id: nest-1
    type: io.kestra.core.tasks.unittest.Example
    task:
      id: 1-1
      type: io.kestra.plugin.core.log.Log
      message: "1-1"
  - id: end
    type: io.kestra.plugin.core.log.Log
    message: "end"
`

describe("FlowUtils", () => {
    it("extractTask from a flat flow", () => {
        const flow = YAML_UTILS.parse(flat)
        const findTaskById = FlowUtils.findTaskById(flow, "1-2")

        expect(findTaskById!.id).toBe("1-2")
        expect(findTaskById!.type).toBe("io.kestra.plugin.core.log.Log")
    })

    it("extractTask from a flowable flow", () => {
        const flow = YAML_UTILS.parse(flowable)
        const findTaskById = FlowUtils.findTaskById(flow, "1-2")

        expect(findTaskById!.id).toBe("1-2")
        expect(findTaskById!.type).toBe("io.kestra.plugin.core.log.Log")
    })

    it("extractTask from a flowable flow", () => {
        const flow = YAML_UTILS.parse(plugins)
        const findTaskById = FlowUtils.findTaskById(flow, "nest-1")

        expect(findTaskById!.id).toBe("nest-1")
        expect(findTaskById!.type).toBe("io.kestra.core.tasks.unittest.Example")
    })

    it("missing task from a flowable flow", () => {
        const flow = YAML_UTILS.parse(flowable)
        const findTaskById = FlowUtils.findTaskById(flow, "undefined")

        expect(findTaskById).toBeUndefined()
    })

    it("getAllTasks returns nested tasks but not task runners", () => {
        const flow = YAML_UTILS.parse<{tasks: unknown}>(`
id: nested
namespace: io.kestra.tests

tasks:
  - id: wd
    type: io.kestra.plugin.core.flow.WorkingDirectory
    tasks:
      - id: dbt
        type: io.kestra.plugin.dbt.cli.DbtCLI
        taskRunner:
          type: io.kestra.plugin.scripts.runner.docker.Docker
  - id: if
    type: io.kestra.plugin.core.flow.If
    then:
      - id: then-log
        type: io.kestra.plugin.core.log.Log
    else:
      - id: else-log
        type: io.kestra.plugin.core.log.Log
`)
        const tasks = FlowUtils.getAllTasks(flow?.tasks)

        expect(tasks.map((t) => t.id)).toEqual(["wd", "dbt", "if", "then-log", "else-log"])
        expect(tasks.find((t) => t.id === "dbt")).toMatchObject({taskRunner: {type: "io.kestra.plugin.scripts.runner.docker.Docker"}})
    })

    it("getAllTasks handles missing tasks", () => {
        expect(FlowUtils.getAllTasks(undefined)).toEqual([])
    })
})

describe("getAllTaskIds", () => {
    it("includes tasks from finally and after blocks", () => {
        const flow = {
            tasks: [{id: "step_1", type: LOG}],
            errors: [{id: "error_1", type: LOG}],
            finally: [{id: "cleanup_step", type: LOG}],
            after: [{id: "after_step", type: LOG}],
        }

        expect(FlowUtils.getAllTaskIds(flow).sort()).toEqual(
            ["after_step", "cleanup_step", "error_1", "step_1"],
        )
    })

    it("works when finally and after are missing", () => {
        const flow = {tasks: [{id: "a", type: LOG}]}

        expect(FlowUtils.getAllTaskIds(flow)).toEqual(["a"])
    })

    it("returns empty array for undefined flow", () => {
        expect(FlowUtils.getAllTaskIds(undefined)).toEqual([])
    })

    it("collects nested tasks inside finally", () => {
        const flow = {
            finally: [{
                id: "parent",
                type: "io.kestra.plugin.core.flow.Sequential",
                tasks: [{id: "child", type: LOG}],
            }],
        }

        expect(FlowUtils.getAllTaskIds(flow).sort()).toEqual(["child", "parent"])
    })

    it("removes duplicate ids", () => {
        const flow = {
            tasks: [{id: "same", type: LOG}],
            finally: [{id: "same", type: LOG}],
        }

        expect(FlowUtils.getAllTaskIds(flow)).toEqual(["same"])
    })
})