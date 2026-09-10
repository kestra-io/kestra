import {describe, expect, test} from "vitest"
import * as YamlUtils from "../../../../src/utils/yaml/blocks.ts"
import {parse, stringify} from "../../../../src/utils/yaml/serialization.ts"

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

    test("fills an empty section rather than repeating its key", () => {
        const result = YamlUtils.insertBlockWithPath({
            source: "id: t\nnamespace: n\ntasks:\n",
            parentPath: "tasks",
            newBlock: "id: new\ntype: io.kestra.plugin.core.log.Log\n",
        })

        expect(result.match(/^tasks:/gm)).toHaveLength(1)
        expect(result).toContain("id: new")
    })

    test("fills an empty nested section rather than repeating its key", () => {
        const result = YamlUtils.insertBlockWithPath({
            source: "id: t\ntasks:\n  - id: sw\n    type: io.kestra.plugin.core.flow.Switch\n    cases:\n",
            parentPath: "tasks[0].cases[\"1.0\"]",
            newBlock: "id: new\ntype: io.kestra.plugin.core.log.Log\n",
        })

        expect(result.match(/cases:/g)).toHaveLength(1)
        expect(result).toContain("id: new")
    })

    test("rejects a parent path that holds a scalar rather than a collection", () => {
        expect(() =>
            YamlUtils.insertBlockWithPath({
                source: "id: t\nnamespace: n\nfoo: bar\n",
                parentPath: "foo.tasks",
                newBlock: "id: new\ntype: io.kestra.plugin.core.log.Log\n",
            }),
        ).toThrow(/foo.*not a collection/)
    })

    test("names a created parent after its own path segment, not the quoted leaf", () => {
        const result = YamlUtils.insertBlockWithPath({
            source: "id: t\nnamespace: n\ntasks:\n  - id: sw\n    type: io.kestra.plugin.core.flow.Switch\n",
            parentPath: "tasks[0].cases[\"1.0\"]",
            newBlock: "id: new\ntype: io.kestra.plugin.core.log.Log\n",
        })

        expect(result).toContain("cases:")
    })

    test("rejects a path that holds a mapping rather than a sequence", () => {
        expect(() =>
            YamlUtils.insertBlockWithPath({
                source: srcWithTasks,
                parentPath: "tasks[0]",
                newBlock: newValue,
            }),
        ).toThrow(/tasks\[0\].*not a sequence/)
    })

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
            newContent: stringify(undefined),
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

describe("pruneEmptySequences", () => {
    test("removes a key whose value is an empty sequence", () => {
        const source = `tasks:
  - id: if_task
    type: io.kestra.plugin.core.flow.If
    then: []
    else:
      - id: keep
        type: io.kestra.plugin.core.log.Log
`
        const result = YamlUtils.pruneEmptySequences(source)
        expect(result).not.toContain("then:")
        expect(result).toContain("id: keep")
    })

    test("preserves comments elsewhere in the document", () => {
        const source = `tasks:
  # an unrelated reminder
  - id: leaf
    type: io.kestra.plugin.core.log.Log
  - id: if_task
    type: io.kestra.plugin.core.flow.If
    then: []
`
        const result = YamlUtils.pruneEmptySequences(source)
        expect(result).toContain("# an unrelated reminder")
        expect(result).not.toContain("then:")
    })

    test("leaves non-empty sequences untouched", () => {
        const source = `tasks:
  - id: keep
    type: io.kestra.plugin.core.log.Log
`
        const result = YamlUtils.pruneEmptySequences(source)
        expect(result).toContain("id: keep")
    })
})

describe("insertBlockWithPath into a dotted case key", () => {
    test("creates a single case whose key contains a dot", () => {
        const source = `id: my_flow
namespace: company.team
tasks:
  - id: sw
    type: io.kestra.plugin.core.flow.Switch
    value: "{{ inputs.version }}"
    cases:
      stable:
        - id: stable_log
          type: io.kestra.plugin.core.log.Log
`
        const result = YamlUtils.insertBlockWithPath({
            source,
            parentPath: "tasks[0].cases[\"1.0\"]",
            newBlock: "id: v1_log\ntype: io.kestra.plugin.core.log.Log",
        })

        const parsed = parse(result) as any
        expect(parsed.tasks[0].cases["1.0"]).toHaveLength(1)
        expect(parsed.tasks[0].cases["1.0"][0].id).toBe("v1_log")
        expect(parsed.tasks[0].cases["1"]).toBeUndefined()
        expect(parsed.tasks[0].cases.stable).toHaveLength(1)
    })
})
