import {test, expect, describe} from "vitest"
import * as YamlUtils from "../../../src/utils/flowYamlUtils.ts"


describe("extractBlock", () => {
    test("extracting a trigger", () => {
        const yamlString = `
        triggers:
          - id: plugin1
            type: type1
            name: Plugin 1
          - id: plugin2
            type: type2
            name: Plugin 2
        `

        const result = YamlUtils.extractBlock({
            source: yamlString,
            section: "triggers",
            key: "plugin1",
        })

        expect(result).toMatchInlineSnapshot(`
          "id: plugin1
          type: type1
          name: Plugin 1
          "
        `)
    })

    test("extracting a task", () => {
        const yamlString = `
        tasks:
          - id: plugin1
            type: type1
            name: Plugin 1
          - id: plugin2
            type: type2
            name: Plugin 2
        `

        const result = YamlUtils.extractBlock({
            source: yamlString,
            section: "tasks",
            key: "plugin1",
        })

        expect(result).toMatchInlineSnapshot(`
          "id: plugin1
          type: type1
          name: Plugin 1
          "
        `)
    })

    test("extracting a pluginDefaults", () => {
        const yamlString = `
        tasks:
          - id: plugin1
            type: type1
            name: Plugin 1
          - id: plugin2
            type: type2
            name: Plugin 2
        pluginDefaults:
          - type: type1
            name: Plugin Default 1
          - type: type2
            name: Plugin Default 2
        `

        const result = YamlUtils.extractBlock({
            source: yamlString,
            section: "pluginDefaults",
            key: "type1",
            keyName: "type",
        })

        expect(result).toMatchInlineSnapshot(`
          "type: type1
          name: Plugin Default 1
          "
        `)
    })
})

describe("swapPluginProperties", () => {
    test("swapping a trigger", () => {
        const yamlString = `
        triggers:
          - id: plugin1
            type: type1
            name: Plugin 1
          - id: pluginBetween
            type: type1
            name: Plugin 1
          - id: plugin2
            type: type2
            name: Plugin 2
        `

        const result = YamlUtils.swapBlocks({
            source: yamlString,
            section: "triggers",
            key1: "plugin1",
            key2: "plugin2",
        })

        expect(result).toMatchInlineSnapshot(`
          "triggers:
            - id: plugin2
              type: type2
              name: Plugin 2
            - id: pluginBetween
              type: type1
              name: Plugin 1
            - id: plugin1
              type: type1
              name: Plugin 1
          "
        `)
    })
})

describe("deleteBlock", () => {
    test("deleting a trigger", () => {
        const yamlString = `
        triggers:
          - id: plugin1
            type: type1
            name: Plugin 1
          - id: plugin2
            type: type2
            name: Plugin 2
        `
        const result = YamlUtils.deleteBlock({
            source: yamlString,
            section: "triggers",
            key: "plugin1",
        })
        expect(result).not.toContain("- id: plugin1")
    })

    test("deleting a task", () => {
        const yamlString = `
        tasks:
          - id: plugin1
            type: type1
            name: Plugin 1
          - id: plugin2
            type: type2
            name: Plugin 2
        `
        const result = YamlUtils.deleteBlock({
            source: yamlString,
            section: "tasks",
            key: "plugin1",
        })
        expect(result).not.toContain("- id: plugin1")
    })

    test("deleting a task with subtask", () => {
        const yamlString = `
        tasks:
          - id: plugin1
            type: type1
            name: Plugin 1
            tasks:
              - id: plugin2
                type: type2
                name: Plugin 2
          - id: plugin3
            type: type3
            name: Plugin 3
        `
        const result = YamlUtils.deleteBlock({
            source: yamlString,
            section: "tasks",
            key: "plugin1",
        })
        expect(result).not.toContain("- id: plugin1")
    })

    test("deleting a pluginDefaults", () => {
        const yamlString = `
        pluginDefaults:
          - type: type1
            name: Plugin 1
          - type: type2
            name: Plugin 2
        `
        const result = YamlUtils.deleteBlock({
            source: yamlString,
            section: "pluginDefaults",
            key: "type1",
            keyName: "type",
        })
        expect(result).not.toContain("- type: type1")
    })

    test("deleting a pluginDefaults", () => {
        const yamlString = `
        pluginDefaults:
          - type: type1
            values:
              - going: nuts
              - going: bananas

          - type: type2
            name: Plugin 2
        `
        const result = YamlUtils.deleteBlock({
            source: yamlString,
            section: "pluginDefaults",
            key: "type1",
            keyName: "type",
        })
        expect(result).not.toContain("- type: type1")
    })
})

describe("extractFieldFromMaps", () => {
    test("extracts field from maps", () => {
        const yamlSrc = `
            tasks:
              - id: task1
                type: io.kestra.plugin.core.log.Log
                labels:
                  key1: value1
                  key2: value2
              - id: task2
                type: io.kestra.plugin.core.log.Log
                labels:
                  key1: value3
                  key2: value4
            `
            const result = YamlUtils.extractFieldFromMaps(yamlSrc, "labels")
            expect(result).toMatchInlineSnapshot(`
              [
                {
                  "labels": [
                    {
                      "key1": "value1",
                    },
                    {
                      "key2": "value2",
                    },
                  ],
                  "range": [
                    36,
                    184,
                    184,
                  ],
                },
                {
                  "labels": [
                    {
                      "key1": "value3",
                    },
                    {
                      "key2": "value4",
                    },
                  ],
                  "range": [
                    200,
                    348,
                    348,
                  ],
                },
              ]
            `)
            })

    test("returns empty object if field not found", () => {
        const yaml = `
        tasks:
          - id: task1
            type: io.kestra.plugin.core.log.Log
        `
        const result = YamlUtils.extractFieldFromMaps(yaml, "labels")
        expect(result).toEqual([])
    })
    test("returns empty object if no maps found", () => {
        const yaml = `
        tasks:
          - id: task1
            type: io.kestra.plugin.core.log.Log
        `
        const result = YamlUtils.extractFieldFromMaps(yaml, "labels")
        expect(result).toEqual([])
    })
    test("extract fields given keepEmptyFields equals true", () => {
        const yaml = `
        tasks:
          - id: task1
            type: io.kestra.plugin.core.log.Log
            version: 0.0.1
          - id: task2
            type: io.kestra.plugin.core.log.Log
          - id: task3
            type: io.kestra.plugin.core.log.Log
            version: 0.0.2
        `
        const result = YamlUtils.extractFieldFromMaps(yaml, "version", () => true, () => true, true)
        expect(result).toMatchInlineSnapshot(`
            [
              {
                "range": [
                  9,
                  280,
                  280,
                ],
                "version": undefined,
              },
              {
                "range": [
                  28,
                  113,
                  113,
                ],
                "version": "0.0.1",
              },
              {
                "range": [
                  125,
                  183,
                  183,
                ],
                "version": undefined,
              },
              {
                "range": [
                  195,
                  280,
                  280,
                ],
                "version": "0.0.2",
              },
            ]
        `)
    })
})

describe("insertBlockWithPath", () => {
    const srcWithTasks = `
        tasks:
          - id: plugin1
            type: type1
            name: Plugin 1
          - id: plugin2
            type: type2
            name: Plugin 2
        `
    const newValue = `
            id: plugin3
            type: type3
            name: Plugin 3
        `

    test("inserting a task", () => {

        const result = YamlUtils.insertBlockWithPath({
            source: srcWithTasks,
            parentPath: "tasks",
            newBlock: newValue,
            refPath: 0,
            position: "after",
        })
        expect(result).toMatchInlineSnapshot(`
          "tasks:
            - id: plugin1
              type: type1
              name: Plugin 1
            - id: plugin3
              type: type3
              name: Plugin 3
            - id: plugin2
              type: type2
              name: Plugin 2
          "
        `)
    })

    test("inserting a task when no tasks section is present", () => {
        const srcWithTriggers = `
        triggers:
          - id: plugin1
            type: type1
            name: Plugin 1
          - id: plugin2
            type: type2
            name: Plugin 2
        `

        const result = YamlUtils.insertBlockWithPath({
            source: srcWithTriggers,
            parentPath: "tasks",
            newBlock: newValue,
        })
        expect(result).toMatchInlineSnapshot(`
          "triggers:
            - id: plugin1
              type: type1
              name: Plugin 1
            - id: plugin2
              type: type2
              name: Plugin 2
          tasks:
            - id: plugin3
              type: type3
              name: Plugin 3
          "
        `)
    })

    test("inserting a task as a subBlock of another task", () => {
        const srcWithSubTasks = `
        tasks:
          - id: plugin1
            type: type1
            name: Plugin 1
            tasks:
              - id: plugin2
                type: type2
                name: Plugin 2
              - id: plugin5
                type: type5
                name: Plugin 5
          - id: plugin3
            type: type3
            name: Plugin 3
        `
        const subTaskValue = `
            id: plugin4
            type: type4
            name: Plugin 4
        `
        const result = YamlUtils.insertBlockWithPath({
            source: srcWithSubTasks,
            newBlock: subTaskValue,
            parentPath: "tasks[0].tasks",
            refPath: 0,
            position: "before",
        })
        expect(result).toMatchInlineSnapshot(`
          "tasks:
            - id: plugin1
              type: type1
              name: Plugin 1
              tasks:
                - id: plugin4
                  type: type4
                  name: Plugin 4
                - id: plugin2
                  type: type2
                  name: Plugin 2
                - id: plugin5
                  type: type5
                  name: Plugin 5
            - id: plugin3
              type: type3
              name: Plugin 3
          "
        `)
    })

    test("inserting a condition on a trigger", () => {
        const srcWithTriggers = `
        triggers:
          - id: plugin1
            type: type1
            name: Plugin 1
          - id: plugin2
            type: type2
            name: Plugin 2
        `

        const result = YamlUtils.insertBlockWithPath({
            source: srcWithTriggers,
            parentPath: "triggers[0].conditions",
            newBlock: newValue,
        })
        expect(result).toMatchInlineSnapshot(`
          "triggers:
            - id: plugin1
              type: type1
              name: Plugin 1
              conditions:
                - id: plugin3
                  type: type3
                  name: Plugin 3
            - id: plugin2
              type: type2
              name: Plugin 2
          "
        `)
    })

    test("insert parent when double missing", () => {
        const srcWithTriggers = `
        tasks:
          - id: plugin1
          - id: for_each
            type: io.kestra.plugin.core.flow.ForEach
            tasks:
              - id: plugin2
              - id: my_switch
                type: io.kestra.plugin.core.flow.Switch
                value: baz
        `

        const result = YamlUtils.insertBlockWithPath({
            source: srcWithTriggers,
            parentPath: "tasks[1].tasks[1].cases.12",
            newBlock: newValue,
        })
        expect(result).toMatchInlineSnapshot(`
          "tasks:
            - id: plugin1
            - id: for_each
              type: io.kestra.plugin.core.flow.ForEach
              tasks:
                - id: plugin2
                - id: my_switch
                  type: io.kestra.plugin.core.flow.Switch
                  value: baz
                  cases:
                    - "12":
                        - id: plugin3
                          type: type3
                          name: Plugin 3
          "
        `)
    })
})

describe("extractBlockWithPath", () => {
    test("extracting a trigger", () => {
        const yamlString = `
        triggers:
          - id: plugin1
            type: type1
            name: Plugin 1
          - id: plugin2
            type: type2
            name: Plugin 2
        `

        const result = YamlUtils.extractBlockWithPath({
            source: yamlString,
            path: "triggers[1]",
        })
        expect(result).toMatchInlineSnapshot(`
          "id: plugin2
          type: type2
          name: Plugin 2
          "
        `)
    })
    test("extracting a sub-subtask", () => {
        const yamlString = `
        tasks:
          - id: plugin1
            type: type1
            name: Plugin 1
            tasks:
              - id: plugin2
                type: type2
                name: Plugin 2
              - id: plugin3
                type: type3
                name: Plugin 3
                tasks:
                  - id: plugin4
                    type: type4
                    name: Plugin 4
                  - id: plugin5
                    type: type5
                    name: Plugin 5
        `

        const result = YamlUtils.extractBlockWithPath({
            source: yamlString,
            path: "tasks[0].tasks[1].tasks[0]",
        })
        expect(result).toMatchInlineSnapshot(`
          "id: plugin4
          type: type4
          name: Plugin 4
          "
          `)
    })
})

describe("replaceBlockWithPath", () => {
    test("replacing a trigger", () => {
        const yamlString = `
        triggers:
          - id: plugin1
            type: type1
            name: Plugin 1
          - id: plugin2
            type: type2
            name: Plugin 2
        `

        const newValue = `
        id: plugin3
        type: type3
        name: Plugin 3
        `

        const result = YamlUtils.replaceBlockWithPath({
            source: yamlString,
            path: "triggers[1]",
            newContent: newValue,
        })
        expect(result).toMatchInlineSnapshot(`
          "triggers:
            - id: plugin1
              type: type1
              name: Plugin 1
            - id: plugin3
              type: type3
              name: Plugin 3
          "
        `)
    })

    test("replacing a task", () => {
        const yamlString = `
        tasks:
          - id: plugin1
            type: type1
            name: Plugin 1
          - id: plugin2
            type: type2
            name: Plugin 2
        `

        const newValue = `
        id: plugin3
        type: type3
        name: Plugin 3
        `

        const result = YamlUtils.replaceBlockWithPath({
            source: yamlString,
            path: "tasks[1]",
            newContent: newValue,
        })
        expect(result).toMatchInlineSnapshot(`
          "tasks:
            - id: plugin1
              type: type1
              name: Plugin 1
            - id: plugin3
              type: type3
              name: Plugin 3
          "
        `)
    })

    test("replacing a task with subtask", () => {
        const yamlString = `
        tasks:
          - id: plugin1
            type: type1
            name: Plugin 1
            tasks:
              - id: plugin2
                type: type2
                name: Plugin 2
              - id: plugin3
                type: type3
                name: Plugin 3
        `

        const newValue = `
        id: plugin4
        type: type4
        name: Plugin 4
        `

        const result = YamlUtils.replaceBlockWithPath({
            source: yamlString,
            path: "tasks[0].tasks[1]",
            newContent: newValue,
        })
        expect(result).toMatchInlineSnapshot(`
          "tasks:
            - id: plugin1
              type: type1
              name: Plugin 1
              tasks:
                - id: plugin2
                  type: type2
                  name: Plugin 2
                - id: plugin4
                  type: type4
                  name: Plugin 4
          "
        `)
    })

    test("replace a condition in a trigger", () => {
        const yamlString = `
        triggers:
          - id: plugin1
            type: type1
            name: Plugin 1
            conditions:
              - id: plugin2
                type: type2
                name: Plugin 2
              - id: plugin3
                type: type3
                name: Plugin 3
        `

        const newValue = `
        id: plugin4
        type: type4
        name: Plugin 4
        `

        const result = YamlUtils.replaceBlockWithPath({
            source: yamlString,
            path: "triggers[0].conditions[1]",
            newContent: newValue,
        })
        expect(result).toMatchInlineSnapshot(`
          "triggers:
            - id: plugin1
              type: type1
              name: Plugin 1
              conditions:
                - id: plugin2
                  type: type2
                  name: Plugin 2
                - id: plugin4
                  type: type4
                  name: Plugin 4
          "
        `)
    })

    test("replace a subtask with subtask", () => {
        const yamlString = `
tasks:
  - id: test
    type: io.kestra.plugin.core.flow.Dag
    tasks:
      - dependsOn:
          - tweeb
        task:
          id: foo
          type: io.kestra.plugin.core.log.Log
          message: foow
      - task:
          id: tweeb
          type: io.kestra.plugin.core.log.Log
          message: tweeb
`

        const newValue = `
            id: plugin4
            type: type4
            name: Plugin 4
        `

        const result = YamlUtils.replaceBlockWithPath({
            source: yamlString,
            path: "tasks[0].tasks[2].task",
            newContent: newValue,
        })

        expect(result).toMatchInlineSnapshot(`
          "tasks:
            - id: test
              type: io.kestra.plugin.core.flow.Dag
              tasks:
                - dependsOn:
                    - tweeb
                  task:
                    id: foo
                    type: io.kestra.plugin.core.log.Log
                    message: foow
                - task:
                    id: tweeb
                    type: io.kestra.plugin.core.log.Log
                    message: tweeb
                - task:
                    id: plugin4
                    type: type4
                    name: Plugin 4
          "
        `)
    })

    test("insert the key at the right location", () => {
        const yamlString = `
        id: my-flow
        namespace: my.namespace

        tasks:
          - id: plugin1
            type: type1
            name: Plugin 1
          - id: plugin2
            type: type2
            name: Plugin 2
        `

        const newValue = `
        my nice flow
        `

        const result = YamlUtils.replaceBlockWithPath({
            source: yamlString,
            path: "description",
            newContent: newValue,
        })
        expect(result).toMatchInlineSnapshot(`
          "id: my-flow
          namespace: my.namespace
          description: my nice flow

          tasks:
            - id: plugin1
              type: type1
              name: Plugin 1
            - id: plugin2
              type: type2
              name: Plugin 2
          "
        `)
    })

    test("replace with empty content should remove the item at path", () => {
        const yamlString = `
        tasks:
          - id: plugin1
            type: type1
            name: Plugin 1
          - id: plugin2
            type: type2
            name: Plugin 2
        `

        const result = YamlUtils.replaceBlockWithPath({
            source: yamlString,
            path: "tasks[1]",
            newContent: YamlUtils.stringify(undefined),
        })

        expect(result).toMatchInlineSnapshot(`
          "tasks:
            - id: plugin1
              type: type1
              name: Plugin 1
          "
        `)
    })
})

describe("getPathFromSectionAndId", () => {
    test("get path from id", () => {
        const yamlString = `
        tasks:
          - id: plugin1
            type: type1
            name: Plugin 1
          - id: plugin2
            type: type2
            name: Plugin 2
        `
        const result = YamlUtils.getPathFromSectionAndId({
            source: yamlString,
            section: "tasks",
            id: "plugin2",
        })
        expect(result).toBe("tasks[1]")
    })

    test("get path from id with subtask", () => {
        const yamlString = `
        tasks:
          - id: plugin1
            type: type1
            name: Plugin 1
            tasks:
              - id: plugin2
                type: type2
                name: Plugin 2
              - id: plugin3
                type: type3
                name: Plugin 3
        `
        const result = YamlUtils.getPathFromSectionAndId({
            source: yamlString,
            section: "tasks",
            id: "plugin3",
        })
        expect(result).toBe("tasks[0].tasks[1]")
    })

    test("get path from id with subCondition", () => {
        const yamlString = `
        triggers:
          - id: plugin1
            type: type1
            name: Plugin 1
            conditions:
              - id: plugin2
                type: type2
                name: Plugin 2
              - id: plugin3
                type: type3
                name: Plugin 3
          - id: plugin4
            type: type4
            name: Plugin 4

        `
        const result = YamlUtils.getPathFromSectionAndId({
            source: yamlString,
            section: "triggers",
            id: "plugin3",
        })
        expect(result).toBe("triggers[0].conditions[1]")
    })

    test("get path from dag", () => {
        const yamlString = `
            tasks:
              - dependency: t1
                task:
                    id: t2
                    type: type2
                    name: Plugin 2
              - task:
                    id: t3
                    type: type3
                    name: Plugin 3

        `
        const result = YamlUtils.getPathFromSectionAndId({
            source: yamlString,
            section: "tasks",
            id: "t2",
        })
        expect(result).toBe("tasks[0].task")
    })
})

describe("replaceIdAndNamespace", () => {
    test("replaces id and namespace in yaml", () => {
        const yaml = `
id: old-id
namespace: old.namespace
tasks:
  - id: task1
        `
        const result = YamlUtils.replaceIdAndNamespace(yaml, "new-id", "new.namespace")
        expect(result).toContain("id: new-id")
        expect(result).toContain("namespace: new.namespace")
    })

    test("handles quoted values", () => {
        const yaml = `
id: "old-id"
namespace: 'old.namespace'
        `
        const result = YamlUtils.replaceIdAndNamespace(yaml, "new-id", "new.namespace")
        expect(result).toContain("id: \"new-id\"")
        expect(result).toContain("namespace: 'new.namespace'")
    })

    test("handles yaml with only tasks and sets it on top", () => {
        const yaml = `
tasks:
  - id: t1
    type: plugin1
        `
        const result = YamlUtils.replaceIdAndNamespace(yaml, "new-id", "new.namespace")
        const lines = result.split("\n").map((l: string) => l.trim()).filter(Boolean)
        expect(result).toContain("id: new-id")
        expect(lines[0]).toBe("id: new-id")
        expect(result).toContain("namespace: new.namespace")
    })

    test("handles empty yaml", () => {
        const yaml = `

        `
        const result = YamlUtils.replaceIdAndNamespace(yaml, "new-id", "new.namespace")
        expect(result).toContain("id: new-id")
        expect(result).toContain("namespace: new.namespace")
    })
})

describe("getMetadata", () => {
    test("returns all metadata except tasks, triggers, and errors", () => {
        const yaml = `
        id: test
        namespace: test.ns
        description: Test flow

        tasks:
          - id: task1
        triggers:
          - id: trigger1
        errors:
          - id: error1
        finally:
          - id: finally1
        `
        const metadata = YamlUtils.getMetadata(yaml)
        expect(metadata).toEqual({
            id: "test",
            namespace: "test.ns",
            description: "Test flow",
        })
    })

    test("handles complex metadata values", () => {
        const yaml = `
        id: test
        labels:
          env: prod
          team: dev
        variables:
          var1: value1
        `
        const metadata = YamlUtils.getMetadata(yaml)
        expect(metadata).toEqual({
            id: "test",
            labels: {
                env: "prod",
                team: "dev",
            },
            variables: {
                var1: "value1",
            },
        })
    })

    test("does not fail on empty yaml", () => {
        const yaml = ""
        const metadata = YamlUtils.getMetadata(yaml)
        expect(metadata).toEqual({})
    })
})

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

describe("stringify with preserveCronQuotes", () => {
    test("adds quotes to unquoted cron values", () => {
        const yaml = {
            triggers: [
                {
                    id: "trigger1",
                    type: "io.kestra.plugin.core.trigger.Schedule",
                    cron: "0 0 * * *",
                },
            ],
        }
        const result = YamlUtils.stringify(yaml)
        expect(result).toContain("cron: \"0 0 * * *\"")
    })

    test("preserves double quotes in cron values", () => {
        const yaml = {
            triggers: [
                {
                    id: "trigger1",
                    type: "io.kestra.plugin.core.trigger.Schedule",
                    cron: "\"0 0 * * *\"",
                },
            ],
        }
        const result = YamlUtils.stringify(yaml)
        // When input already has quotes, preserveCronQuotes should skip adding quotes
        // The regex should skip values that already start with quotes
        expect(result).toContain("cron:")
        expect(result).toContain("0 0 * * *")
        // Should preserve the existing quotes (js-yaml may escape them)
        expect(result).toMatch(/cron:\s*"\\"0 0 \* \* \*\\""|cron:\s*"0 0 \* \* \*"/)
    })

    test("preserves single quotes in cron values", () => {
        const yaml = {
            triggers: [
                {
                    id: "trigger1",
                    type: "io.kestra.plugin.core.trigger.Schedule",
                    cron: "'0 0 * * *'",
                },
            ],
        }
        const result = YamlUtils.stringify(yaml)
        // Single quotes are preserved in the output
        expect(result).toContain("cron:")
        expect(result).toContain("'0 0 * * *'")
    })

    test("does not quote multiline strings starting with pipe", () => {
        const yaml = {
            triggers: [
                {
                    id: "trigger1",
                    type: "io.kestra.plugin.core.trigger.Schedule",
                    cron: "|\n  0 0 * * *",
                },
            ],
        }
        const result = YamlUtils.stringify(yaml)
        expect(result).toContain("cron: |")
        expect(result).not.toContain("cron: \"|")
    })

    test("does not quote multiline strings starting with greater than", () => {
        const yaml = {
            triggers: [
                {
                    id: "trigger1",
                    type: "io.kestra.plugin.core.trigger.Schedule",
                    cron: ">\n  0 0 * * *",
                },
            ],
        }
        const result = YamlUtils.stringify(yaml)
        // Multiline strings with > are preserved as multiline (not quoted)
        expect(result).toContain("cron:")
        expect(result).not.toContain("cron: \">")
        // The multiline format should be preserved
        expect(result).toMatch(/cron:\s*[|>]/)
    })

    test("handles cron values with comments", () => {
        const yaml = {
            triggers: [
                {
                    id: "trigger1",
                    type: "io.kestra.plugin.core.trigger.Schedule",
                    // include an inline comment marker in the value to ensure it is preserved
                    cron: "0 0 * * * # daily",
                },
            ],
        }
        const result = YamlUtils.stringify(yaml)
        expect(result).toContain("cron: \"0 0 * * * # daily\"")
    })

    test("handles multiple cron triggers", () => {
        const yaml = {
            triggers: [
                {
                    id: "trigger1",
                    type: "io.kestra.plugin.core.trigger.Schedule",
                    cron: "0 0 * * *",
                },
                {
                    id: "trigger2",
                    type: "io.kestra.plugin.core.trigger.Schedule",
                    cron: "0 12 * * *",
                },
            ],
        }
        const result = YamlUtils.stringify(yaml)
        expect(result).toContain("cron: \"0 0 * * *\"")
        expect(result).toContain("cron: \"0 12 * * *\"")
    })

    test("handles cron with indentation", () => {
        const yaml = {
            triggers: [
                {
                    id: "trigger1",
                    type: "io.kestra.plugin.core.trigger.Schedule",
                    cron: "0 0 * * *",
                },
            ],
        }
        const result = YamlUtils.stringify(yaml)
        // Should handle indented cron lines (two spaces before dash, four before cron)
        expect(result).toMatch(/\n {2}- id: trigger1\n {4}type: .*?\n {4}cron: "0 0 \* \* \*"/)
    })

    test("handles empty cron value without adding quotes", () => {
        const yaml = {
            triggers: [
                {
                    id: "trigger1",
                    type: "io.kestra.plugin.core.trigger.Schedule",
                    cron: "",
                },
            ],
        }
        const result = YamlUtils.stringify(yaml)
        // Empty values should not be quoted
        expect(result).not.toContain("cron: \"\"")
        expect(result).toMatch(/cron:\s*$/m)
    })

    test("handles cron with whitespace-only value", () => {
        const yaml = {
            triggers: [
                {
                    id: "trigger1",
                    type: "io.kestra.plugin.core.trigger.Schedule",
                    cron: "   ",
                },
            ],
        }
        const result = YamlUtils.stringify(yaml)
        // Whitespace-only values should be quoted
        expect(result).toContain("cron: \"   \"")
    })

    test("does not affect non-cron fields", () => {
        const yaml = {
            id: "test-flow",
            namespace: "io.kestra.test",
            tasks: [
                {
                    id: "task1",
                    type: "io.kestra.plugin.core.log.Log",
                    message: "0 0 * * *",
                },
            ],
        }
        const result = YamlUtils.stringify(yaml)
        // The message field should not have quotes added (it's not a cron field)
        // preserveCronQuotes only affects lines matching the cron regex pattern
        expect(result).toContain("message:")
        expect(result).toContain("0 0 * * *")
        // Verify the cron regex doesn't match message fields
        expect(result).not.toMatch(/message:\s*"0 0 \* \* \*"/)
    })

    test("handles cron in nested structures", () => {
        const yaml = {
            triggers: [
                {
                    id: "trigger1",
                    type: "io.kestra.plugin.core.trigger.Schedule",
                    cron: "0 0 * * *",
                    conditions: [
                        {
                            id: "condition1",
                            type: "io.kestra.plugin.core.condition.Expression",
                            expression: "{{ true }}",
                        },
                    ],
                },
            ],
        }
        const result = YamlUtils.stringify(yaml)
        expect(result).toContain("cron: \"0 0 * * *\"")
    })
})