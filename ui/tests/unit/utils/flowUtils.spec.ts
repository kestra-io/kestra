import {describe, it, expect} from "vitest"
import * as YAML_UTILS from "@kestra-io/topology/flow-yaml-utils"
import * as FlowUtils from "../../../src/utils/flowUtils"

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

    it("loopOver traverses nested arrays and objects in order", () => {
        const input = {
            first: [
                {id: "a", type: "task"},
                {
                    nested: [
                        {id: "b", type: "task"},
                        {id: "c", type: "task"},
                    ],
                },
            ],
            last: {id: "d", type: "task"},
        }

        const result = FlowUtils.loopOver(
            input,
            (value) => value instanceof Object && value.type === "task",
        )

        expect(result.map((value) => value.id)).toEqual(["a", "b", "c", "d"])
    })

    it("loopOver handles empty and primitive values", () => {
        expect(FlowUtils.loopOver([], () => true)).toEqual([[]])
        expect(FlowUtils.loopOver({}, () => true)).toEqual([{}])
        expect(FlowUtils.loopOver(null, () => true)).toEqual([null])
        expect(FlowUtils.loopOver("value", () => true)).toEqual(["value"])
    })

    it("getAllTaskIds returns task ids from top-level and nested tasks", () => {
        const flow = {
            tasks: [
                {
                    id: "parent",
                    type: "io.kestra.plugin.core.flow.Parallel",
                    tasks: [
                        {
                            id: "child",
                            type: "io.kestra.plugin.core.log.Log",
                        },
                    ],
                },
                {
                    id: "top-level",
                    type: "io.kestra.plugin.core.log.Log",
                },
            ],
        }

        expect(FlowUtils.getAllTaskIds(flow)).toEqual([
            "parent",
            "child",
            "top-level",
        ])
    })

    it("getAllTaskIds includes tasks from errors", () => {
        const flow = {
            tasks: [
                {
                    id: "main",
                    type: "io.kestra.plugin.core.log.Log",
                },
            ],
            errors: [
                {
                    id: "error-handler",
                    type: "io.kestra.plugin.core.log.Log",
                },
                {
                    id: "nested-error",
                    type: "io.kestra.plugin.core.flow.Parallel",
                    tasks: [
                        {
                            id: "nested-error-task",
                            type: "io.kestra.plugin.core.log.Log",
                        },
                    ],
                },
            ],
        }

        expect(FlowUtils.getAllTaskIds(flow)).toEqual([
            "main",
            "error-handler",
            "nested-error",
            "nested-error-task",
        ])
    })

    it("getAllTaskIds removes duplicate task ids", () => {
        const flow = {
            tasks: [
                {id: "duplicate", type: "task"},
                {id: "duplicate", type: "task"},
            ],
        }

        expect(FlowUtils.getAllTaskIds(flow)).toEqual(["duplicate"])
    })

    it("getAllTaskIds handles empty or missing flows", () => {
        expect(FlowUtils.getAllTaskIds(undefined)).toEqual([])
        expect(FlowUtils.getAllTaskIds(null)).toEqual([])
        expect(FlowUtils.getAllTaskIds({})).toEqual([])
        expect(FlowUtils.getAllTaskIds({tasks: []})).toEqual([])
    })
})