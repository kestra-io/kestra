import {describe, test, expect, vi} from "vitest"

vi.mock("@kestra-io/topology", () => ({
    flowYamlUtils: {
        parse: (s: string) => {
            if (s.includes("bad: {{{")) throw new Error("YAML parse error: unexpected token")
            if (s === "- item") return ["item"]
            const lines = s.split("\n")
            const result: Record<string, string> = {}
            for (const line of lines) {
                const m = line.match(/^(\w+):\s*(.+)$/)
                if (m) result[m[1]] = m[2].trim()
            }
            return result
        },
    },
}))

import {parseImportYaml} from "../../../src/utils/importYamlUtils"

describe("parseImportYaml", () => {
    test("returns empty error code for blank input", () => {
        // Given / When
        const result = parseImportYaml("   ")

        // Then
        expect(result.errorCode).toBe("empty")
        expect(result.parseMessage).toBeUndefined()
    })

    test("returns parse_error code when YAML fails to parse, with raw message", () => {
        // Given / When
        const result = parseImportYaml("bad: {{{")

        // Then
        expect(result.errorCode).toBe("parse_error")
        expect(result.parseMessage).toContain("YAML parse error")
    })

    test("returns invalid_mapping error code for non-mapping YAML (list)", () => {
        // Given / When
        const result = parseImportYaml("- item")

        // Then
        expect(result.errorCode).toBe("invalid_mapping")
        expect(result.parseMessage).toBeUndefined()
    })

    test("extracts id and namespace from a valid flow mapping without error", () => {
        // Given
        const yaml = "id: my-flow\nnamespace: company.team"

        // When
        const result = parseImportYaml(yaml)

        // Then
        expect(result.errorCode).toBeUndefined()
        expect(result.id).toBe("my-flow")
        expect(result.namespace).toBe("company.team")
    })

    test("returns undefined id and namespace when absent from mapping", () => {
        // Given
        const yaml = "tasks: some-value"

        // When
        const result = parseImportYaml(yaml)

        // Then
        expect(result.errorCode).toBeUndefined()
        expect(result.id).toBeUndefined()
        expect(result.namespace).toBeUndefined()
    })
})
