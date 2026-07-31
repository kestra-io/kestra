import {describe, test, expect} from "vitest"
import {parseImportYaml} from "../../../src/utils/importYamlUtils"

describe("parseImportYaml", () => {
    test("returns empty error code for blank input", () => {
        // Given / When
        const result = parseImportYaml("   ")

        // Then
        expect(result.errorCode).toBe("empty")
        expect(result.parseMessage).toBeUndefined()
    })

    test("returns parse_error code with the parser's own message on malformed YAML", () => {
        // Given — an unclosed flow mapping the real parser rejects
        const result = parseImportYaml("id: my-flow\ntasks: {unclosed")

        // Then
        expect(result.errorCode).toBe("parse_error")
        expect(result.parseMessage).toBeTruthy()
    })

    test("returns parse_error code on duplicate keys", () => {
        // Given / When
        const result = parseImportYaml("id: a\nid: b")

        // Then
        expect(result.errorCode).toBe("parse_error")
    })

    test("returns invalid_mapping error code for a top-level list", () => {
        // Given / When
        const result = parseImportYaml("- id: my-flow\n- id: other")

        // Then
        expect(result.errorCode).toBe("invalid_mapping")
        expect(result.parseMessage).toBeUndefined()
    })

    test("returns invalid_mapping error code for a top-level scalar", () => {
        // Given / When
        const result = parseImportYaml("just a string")

        // Then
        expect(result.errorCode).toBe("invalid_mapping")
    })

    test("extracts id and namespace from a real flow definition", () => {
        // Given
        const yaml = `id: my-flow
namespace: company.team

tasks:
  - id: hello
    type: io.kestra.plugin.core.log.Log
    message: Hello World!
`

        // When
        const result = parseImportYaml(yaml)

        // Then
        expect(result.errorCode).toBeUndefined()
        expect(result.id).toBe("my-flow")
        expect(result.namespace).toBe("company.team")
    })

    test("returns undefined id and namespace for a fragment that omits them", () => {
        // Given
        const yaml = `tasks:
  - id: hello
    type: io.kestra.plugin.core.log.Log
    message: Hello World!
`

        // When
        const result = parseImportYaml(yaml)

        // Then
        expect(result.errorCode).toBeUndefined()
        expect(result.id).toBeUndefined()
        expect(result.namespace).toBeUndefined()
    })

    test("ignores a non-string id or namespace", () => {
        // Given / When
        const result = parseImportYaml("id: 42\nnamespace:\n  nested: true\n")

        // Then
        expect(result.errorCode).toBeUndefined()
        expect(result.id).toBeUndefined()
        expect(result.namespace).toBeUndefined()
    })
})
