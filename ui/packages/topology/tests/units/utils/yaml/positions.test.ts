import {describe, expect, test} from "vitest"
import * as YamlUtils from "../../../../src/utils/yaml/positions.ts"

describe("get lines infos", () => {
    test("get tasks lines", () => {
        const yamlString = `# this count as an empty line
        tasks:
          - id: plugin1
            type: type1
            name: Plugin 1
            extrafield: Extra field 1
          - id: plugin2
            type: type2
            name: Plugin 2
        `

        const tasksLines = YamlUtils.getTasksLines(yamlString)
        expect(tasksLines).to.containSubset({plugin1: {start: 3, end: 6}})
        expect(tasksLines).to.containSubset({plugin2: {start: 7, end: 9}})
    })
    test("get tasks lines including comments and line breaks", () => {
        const yamlString = `# this count as an empty line
        # second comment
        tasks:
            # third comment
          - id: plugin1
            type: type1
            # fourth comment
            name: Plugin 1

            # end comment
        `

        const tasksLines = YamlUtils.getTasksLines(yamlString)
        expect(tasksLines).to.containSubset({plugin1: {start: 5, end: 8}})
    })
    test("get tasks lines including multiline field", () => {
        const yamlString = `# this count as an empty line
        tasks:
          - id: plugin1
            type: type1
            name: Plugin 1
            payload: |
              {
                "text": "Failure alert for flow {{ flow.namespace }}.{{ flow.id }} with ID {{ execution.id }}"
              }
         # end comment
        `

        const tasksLines = YamlUtils.getTasksLines(yamlString)
        expect(tasksLines).to.containSubset({plugin1: {start: 3, end: 9}})
    })
    test("get tasks lines for 'Dag' tasks", () => {
        const yamlString = `# this count as an empty line
        tasks:
          - id: log_task
            type: io.kestra.plugin.core.log.Log
            message: 'log msg'
          - id: dag_task
            type: io.kestra.plugin.core.flow.Dag
            tasks:
              - task:
                  id: nested_task_1_inside_dag
                  type: io.kestra.plugin.core.log.Log
                  message: test1
              - task:
                  id: nested_task_2_inside_dag
                  type: io.kestra.plugin.core.log.Log
                  message: test2
        `

        const tasksLines = YamlUtils.getTasksLines(yamlString)
        expect(tasksLines).to.containSubset({dag_task: {start: 6, end: 16}})
        expect(tasksLines).to.containSubset({nested_task_1_inside_dag: {start: 10, end: 12}})
        expect(tasksLines).to.containSubset({nested_task_2_inside_dag: {start: 14, end: 16}})
    })
    test("get tasks lines for 'Foreach' tasks", () => {
        const yamlString = `# this count as an empty line
        tasks:
          - id: for_each_task
            type: io.kestra.plugin.core.flow.ForEach
            values:
              - value 1
            tasks:
              - id: for_each_task_1
                type: io.kestra.plugin.core.log.Log
                message: test1
        `

        const tasksLines = YamlUtils.getTasksLines(yamlString)
        expect(tasksLines).to.containSubset({for_each_task: {start: 3, end: 10}})
        expect(tasksLines).to.containSubset({for_each_task_1: {start: 8, end: 10}})
    })
    test("get tasks lines for nested 'Foreach' tasks", () => {
        const yamlString = `# this count as an empty line
        tasks:
          - id: for_each
            type: io.kestra.plugin.core.flow.ForEach
            values:
              - value 1
            tasks:
              - id: for_each_task_1
                type: io.kestra.plugin.core.log.Log
                message: test1
              - id: nested_foreach
                type: io.kestra.plugin.core.flow.ForEach
                values:
                    - value 2
                tasks:
                  - id: nested_foreach_task1
                    type: io.kestra.plugin.core.log.Log
                    message: test2
        `

        const tasksLines = YamlUtils.getTasksLines(yamlString)
        expect(tasksLines).to.containSubset({for_each: {start: 3, end: 18}})
        expect(tasksLines).to.containSubset({for_each_task_1: {start: 8, end: 10}})
        expect(tasksLines).to.containSubset({nested_foreach: {start: 11, end: 18}})
        expect(tasksLines).to.containSubset({nested_foreach_task1: {start: 16, end: 18}})
    })
    test("get tasks lines for 'Condition' task", () => {
        const yamlString = `# this count as an empty line
        tasks:
          - id: if_task
            type: io.kestra.plugin.core.flow.If
            condition: "{{ inputs.string == 'Condition' }}"
            then:
              - id: when_true
                type: io.kestra.plugin.core.log.Log
                message: "Condition was true"
            else:
              - id: when_false
                type: io.kestra.plugin.core.log.Log
                message: "Condition was false"
        `

        const tasksLines = YamlUtils.getTasksLines(yamlString)
        expect(tasksLines).to.containSubset({if_task: {start: 3, end: 13}})
        expect(tasksLines).to.containSubset({when_true: {start: 7, end: 9}})
        expect(tasksLines).to.containSubset({when_false: {start: 11, end: 13}})
    })
    test("if a task ends on the last line, it should be included", () => {
        const yamlString = `# this count as an empty line
        tasks:
          - id: plugin1
            type: type1
            name: Plugin 1
            extrafield: Extra field 1`

        const tasksLines = YamlUtils.getTasksLines(yamlString)
        expect(tasksLines).to.containSubset({plugin1: {start: 3, end: 6}})
    })
})

describe("getTypeAtPosition", () => {
    test("gets type at given line and column", () => {
        const yamlString = `
        id: sqlserver_v3
        namespace: io.kestra.blx

        tasks:
        - type: io.kestra.plugin.core.log.Log
          id: asda
          message: hoo
        - type: io.kestra.plugin.jdbc.sqlserver.Query
          version: 1.0.0
          id: select
          url: help
        `
        const result = YamlUtils.getTypeAtPosition(yamlString, {
            lineNumber:9,
            column: 15,
        }, [
            "io.kestra.plugin.jdbc.sqlserver.Query",
            "io.kestra.plugin.core.log.Log",
        ]) // line 9, column 15 corresponds to io.kestra.plugin.jdbc.sqlserver.Query
        expect(result).toBe("io.kestra.plugin.jdbc.sqlserver.Query")
    })

    test("returns null if no type found at position", () => {
        const yamlString = `
        tasks:
          - id: plugin1
            type: type1
            name: Plugin 1
        `
        const result = YamlUtils.getTypeAtPosition(yamlString, {lineNumber: 2, column:5}, ["type1"]) // line 2, column 5 is 'tasks' field
        expect(result).toBeNull()
    })
})
