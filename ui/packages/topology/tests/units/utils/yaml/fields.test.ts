import {describe, expect, test} from "vitest"
import * as YamlUtils from "../../../../src/utils/yaml/fields.ts"

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

describe("extractTypedBlocks", () => {
    test("returns every block carrying a type with its range", () => {
        const source = `id: my-flow
namespace: company.team
tasks:
  - id: hello
    type: io.kestra.plugin.core.log.Log
    message: Hello World
triggers:
  - id: webhook
    type: io.kestra.plugin.core.trigger.Webhook
    key: admin1234
`

        const blocks = YamlUtils.extractTypedBlocks(source)

        const types = blocks.map((block) => block.type)
        expect(types).toContain("io.kestra.plugin.core.log.Log")
        expect(types).toContain("io.kestra.plugin.core.trigger.Webhook")

        const webhook = blocks.find((block) => block.type === "io.kestra.plugin.core.trigger.Webhook")
        expect(webhook?.value.id).toBe("webhook")
        expect(webhook?.value.key).toBe("admin1234")
        expect(webhook?.range[0]).toBeTypeOf("number")
        expect(webhook?.path).toBe("triggers")

        const log = blocks.find((block) => block.type === "io.kestra.plugin.core.log.Log")
        expect(log?.path).toBe("tasks")
    })

    test("captures the parent section path so artifacts can scope to it", () => {
        const source = `id: my-flow
namespace: company.team
pluginDefaults:
  - type: io.kestra.plugin.core.trigger.Webhook
    values:
      key: shared
triggers:
  - id: webhook
    type: io.kestra.plugin.core.trigger.Webhook
    key: admin1234
`

        const webhookBlocks = YamlUtils.extractTypedBlocks(source)
            .filter((block) => block.type === "io.kestra.plugin.core.trigger.Webhook")

        expect(webhookBlocks.map((block) => block.path).sort()).toEqual(["pluginDefaults", "triggers"])
    })

    test("ignores blocks without a type", () => {
        const source = `id: my-flow
namespace: company.team
`
        expect(YamlUtils.extractTypedBlocks(source)).toEqual([])
    })
})

describe("extractTypedBlocksWithMeta", () => {
    test("returns blocks alongside top-level namespace and id in a single parse", () => {
        const source = `id: my-flow
namespace: company.team
triggers:
  - id: webhook
    type: io.kestra.plugin.core.trigger.Webhook
    key: admin1234
`

        const {blocks, namespace, id} = YamlUtils.extractTypedBlocksWithMeta(source)

        expect(namespace).toBe("company.team")
        expect(id).toBe("my-flow")
        expect(blocks).toHaveLength(1)
        expect(blocks[0].type).toBe("io.kestra.plugin.core.trigger.Webhook")
    })

    test("returns undefined namespace and id when not present in the source", () => {
        const source = `triggers:
  - id: webhook
    type: io.kestra.plugin.core.trigger.Webhook
    key: admin1234
`

        const {namespace, id} = YamlUtils.extractTypedBlocksWithMeta(source)

        expect(namespace).toBeUndefined()
        expect(id).toBeUndefined()
    })
})
