import {describe, expect, test} from "vitest"
import * as YamlUtils from "../../../../src/utils/yaml/metadata.ts"

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

describe("updateMetadata", () => {
    test("keeps a comment that precedes a task", () => {
        const yaml = `id: flow
namespace: dev
tasks:
  - id: first
    type: io.kestra.plugin.core.log.Log
  # keep me
  - id: second
    type: io.kestra.plugin.core.log.Log
`
        const updated = YamlUtils.updateMetadata(yaml, {description: "updated"})

        expect(updated).toContain("# keep me")
        expect(updated).toContain("description: updated")
    })
})
