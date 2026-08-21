import {describe, test, expect, beforeEach} from "vitest"
import {builtInFlowTemplate, resolveFlowTemplate, userFlowTemplate, validateFlowTemplate} from "../../../src/utils/newFlowTemplate"
import {storageKeys} from "../../../src/utils/constants"

const USER_TEMPLATE = "labels:\n  owner: Thibault\ntasks:\n  - id: hello\n    type: io.kestra.plugin.core.log.Log"
const INSTANCE_TEMPLATE = "tasks:\n  - id: configured\n    type: io.kestra.plugin.core.log.Log"

beforeEach(() => {
    localStorage.clear()
})

describe("resolveFlowTemplate fallback chain", () => {
    test("falls back to the built-in template when nothing is configured", () => {
        // Given / When
        const template = resolveFlowTemplate("my-flow", "company.team")

        // Then
        expect(template).toBe(builtInFlowTemplate("my-flow", "company.team"))
        expect(template).toContain("id: my-flow")
        expect(template).toContain("namespace: company.team")
    })

    test("prefers the instance template over the built-in one", () => {
        // Given / When
        const template = resolveFlowTemplate("my-flow", "company.team", INSTANCE_TEMPLATE)

        // Then
        expect(template).toBe(INSTANCE_TEMPLATE)
    })

    test("prefers the user template over the instance one", () => {
        // Given
        localStorage.setItem(storageKeys.FLOW_TEMPLATE, USER_TEMPLATE)

        // When
        const template = resolveFlowTemplate("my-flow", "company.team", INSTANCE_TEMPLATE)

        // Then
        expect(template).toBe(USER_TEMPLATE)
    })

    test("uses the user template when the instance has none", () => {
        // Given
        localStorage.setItem(storageKeys.FLOW_TEMPLATE, USER_TEMPLATE)

        // When / Then
        expect(resolveFlowTemplate("my-flow", "company.team")).toBe(USER_TEMPLATE)
    })

    test.each([["", "an empty"], ["   \n  ", "a blank"]])(
        "ignores %s user template and keeps the instance one",
        (stored) => {
            // Given
            localStorage.setItem(storageKeys.FLOW_TEMPLATE, stored)

            // When / Then
            expect(resolveFlowTemplate("my-flow", "company.team", INSTANCE_TEMPLATE)).toBe(INSTANCE_TEMPLATE)
        },
    )

    test("ignores a blank instance template and keeps the built-in one", () => {
        // Given / When
        const template = resolveFlowTemplate("my-flow", "company.team", "   \n ")

        // Then
        expect(template).toBe(builtInFlowTemplate("my-flow", "company.team"))
    })

    test("trims surrounding whitespace off a configured template", () => {
        // Given
        localStorage.setItem(storageKeys.FLOW_TEMPLATE, `\n\n${USER_TEMPLATE}\n\n`)

        // When / Then
        expect(resolveFlowTemplate("my-flow", "company.team")).toBe(USER_TEMPLATE)
    })
})

describe("userFlowTemplate", () => {
    test("is undefined when the user never saved one", () => {
        // Given / When / Then
        expect(userFlowTemplate()).toBeUndefined()
    })

    test("is undefined once the setting is cleared", () => {
        // Given
        localStorage.setItem(storageKeys.FLOW_TEMPLATE, "")

        // When / Then
        expect(userFlowTemplate()).toBeUndefined()
    })
})

describe("validateFlowTemplate", () => {
    test("accepts an empty template, which clears the setting", () => {
        // Given / When / Then
        expect(validateFlowTemplate("   ")).toEqual({})
    })

    test("accepts a fragment without id and namespace", () => {
        // Given / When / Then
        expect(validateFlowTemplate(USER_TEMPLATE)).toEqual({})
    })

    test("reports parse_error with the parser's own message on malformed YAML", () => {
        // Given / When
        const result = validateFlowTemplate("tasks: {unclosed")

        // Then
        expect(result.errorCode).toBe("parse_error")
        expect(result.parseMessage).toBeTruthy()
    })

    test("reports invalid_mapping for a top-level list", () => {
        // Given / When
        const result = validateFlowTemplate("- id: hello\n- id: other")

        // Then
        expect(result.errorCode).toBe("invalid_mapping")
        expect(result.parseMessage).toBeUndefined()
    })
})
